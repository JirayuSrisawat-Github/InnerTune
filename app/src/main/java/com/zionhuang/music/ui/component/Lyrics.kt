package com.zionhuang.music.ui.component

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zionhuang.music.LocalPlayerConnection
import com.zionhuang.music.R
import com.zionhuang.music.db.entities.ChordsEntity.Companion.CHORDS_NOT_FOUND
import com.zionhuang.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.zionhuang.music.songtext.SongText
import com.zionhuang.music.songtext.SongTextParser
import com.zionhuang.music.ui.menu.LyricsMenu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withFrameNanos
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class ViewMode { Lyrics, Chords }

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Lyrics(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val view = LocalView.current

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    val chordsEntity by playerConnection.currentChords.collectAsState(initial = null)

    val lyricsText = lyricsEntity?.lyrics?.takeIf { !it.isNullOrBlank() && it != LYRICS_NOT_FOUND }
    val lyricLines = remember(lyricsText) {
        lyricsText?.lines()?.filter { it.isNotEmpty() } ?: emptyList()
    }

    val rawChords = chordsEntity?.chords
    var songText by remember { mutableStateOf<SongText?>(null) }
    LaunchedEffect(mediaMetadata?.id, rawChords) {
        val metadata = mediaMetadata
        val chords = rawChords
        if (metadata == null || chords.isNullOrBlank() || chords == CHORDS_NOT_FOUND) {
            songText = null
        } else {
            songText = withContext(Dispatchers.Default) {
                runCatching {
                    SongTextParser.parse(
                        id = metadata.id,
                        title = metadata.title,
                        artist = metadata.artists.joinToString(separator = ", ") { it.name }
                            .takeIf { it.isNotBlank() },
                        raw = chords,
                    )
                }.getOrNull()
            }
        }
    }

    val metadataId = mediaMetadata?.id
    var viewMode by rememberSaveable(metadataId) { mutableStateOf(ViewMode.Lyrics) }
    var autoScrollActive by rememberSaveable(metadataId) { mutableStateOf(false) }
    var showSpeedSheet by rememberSaveable { mutableStateOf(false) }
    var autoScrollSpeed by rememberSaveable { mutableStateOf(0.4f) }

    val lyricsListState = rememberLazyListState()
    val chordsListState = rememberLazyListState()

    val hasLyrics = lyricLines.isNotEmpty()
    val hasChords = songText?.sections?.any { section -> section.lines.isNotEmpty() } == true

    LaunchedEffect(metadataId, hasLyrics, hasChords) {
        when {
            !hasLyrics && hasChords -> viewMode = ViewMode.Chords
            hasLyrics && !hasChords -> viewMode = ViewMode.Lyrics
        }
    }

    LaunchedEffect(viewMode, hasLyrics, hasChords) {
        val canAutoScroll = when (viewMode) {
            ViewMode.Lyrics -> hasLyrics
            ViewMode.Chords -> hasChords
        }
        if (!canAutoScroll && autoScrollActive) {
            autoScrollActive = false
        }
    }

    DisposableEffect(autoScrollActive) {
        view.keepScreenOn = autoScrollActive
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(viewMode) {
        val announcement = when (viewMode) {
            ViewMode.Lyrics -> context.getString(R.string.lyrics_view_selected)
            ViewMode.Chords -> context.getString(R.string.chords_view_selected)
        }
        if (announcement.isNotEmpty()) {
            view.announceForAccessibility(announcement)
        }
    }

    val density = LocalDensity.current
    LaunchedEffect(viewMode, autoScrollActive, autoScrollSpeed, hasLyrics, hasChords, density) {
        if (!autoScrollActive) return@LaunchedEffect
        val state = if (viewMode == ViewMode.Lyrics) lyricsListState else chordsListState
        val canScroll = when (viewMode) {
            ViewMode.Lyrics -> hasLyrics
            ViewMode.Chords -> hasChords
        }
        if (!canScroll) {
            autoScrollActive = false
            return@LaunchedEffect
        }
        val pixelsPerSecond = with(density) { 40.dp.toPx() } * autoScrollSpeed.coerceIn(0f, 1f)
        if (pixelsPerSecond <= 0f) {
            autoScrollActive = false
            return@LaunchedEffect
        }
        var lastFrameTime = withFrameNanos { it }
        while (isActive && autoScrollActive) {
            val frameTime = withFrameNanos { it }
            val deltaSeconds = (frameTime - lastFrameTime) / 1_000_000_000f
            lastFrameTime = frameTime
            if (deltaSeconds <= 0f) continue
            val consumed = state.scrollBy(pixelsPerSecond * deltaSeconds)
            if (consumed == 0f && !state.canScrollForward) {
                autoScrollActive = false
                break
            }
        }
    }

    LaunchedEffect(viewMode, autoScrollActive) {
        val state = if (viewMode == ViewMode.Lyrics) lyricsListState else chordsListState
        snapshotFlow { state.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                if (isScrolling && autoScrollActive) {
                    autoScrollActive = false
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.auto_scroll_paused),
                        actionLabel = context.getString(R.string.auto_scroll_resume)
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        autoScrollActive = true
                    }
                }
            }
    }

    val holdToPauseModifier = Modifier.pointerInput(viewMode, autoScrollActive) {
        detectTapGestures(
            onPress = {
                if (!autoScrollActive) {
                    tryAwaitRelease()
                    return@detectTapGestures
                }
                val wasActive = autoScrollActive
                autoScrollActive = false
                val released = tryAwaitRelease()
                if (released && wasActive && !autoScrollActive) {
                    autoScrollActive = true
                }
            }
        )
    }

    val canAutoScroll = when (viewMode) {
        ViewMode.Lyrics -> hasLyrics
        ViewMode.Chords -> hasChords
    }

    val speedSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (showSpeedSheet) {
        ModalBottomSheet(
            sheetState = speedSheetState,
            onDismissRequest = { showSpeedSheet = false }
        ) {
            AutoScrollBottomSheet(
                speed = autoScrollSpeed,
                onSpeedChange = { autoScrollSpeed = it.coerceIn(0f, 1f) },
                isActive = autoScrollActive,
                onToggle = {
                    if (canAutoScroll) {
                        autoScrollActive = !autoScrollActive
                    }
                },
                enabled = canAutoScroll,
                onDismiss = {
                    coroutineScope.launch {
                        speedSheetState.hide()
                        showSpeedSheet = false
                    }
                }
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = mediaMetadata?.title.orEmpty()) },
                actions = {
                    mediaMetadata?.let { metadata ->
                        IconButton(onClick = {
                            menuState.show {
                                LyricsMenu(
                                    lyricsProvider = { lyricsEntity },
                                    mediaMetadataProvider = { metadata },
                                    onDismiss = { menuState.hide() },
                                )
                            }
                        }) {
                            Icon(painterResource(R.drawable.more_vert), contentDescription = null)
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (hasLyrics || hasChords) {
                ExtendedFloatingActionButton(
                    onClick = { showSpeedSheet = true },
                    icon = { Icon(painterResource(R.drawable.speed), contentDescription = null) },
                    text = { Text(text = stringResource(R.string.auto_scroll)) }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp)
            ) {
                SegmentedButton(
                    selected = viewMode == ViewMode.Lyrics,
                    onClick = { viewMode = ViewMode.Lyrics },
                    enabled = hasLyrics,
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text(text = stringResource(R.string.lyrics))
                }
                SegmentedButton(
                    selected = viewMode == ViewMode.Chords,
                    onClick = { viewMode = ViewMode.Chords },
                    enabled = hasChords,
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text(text = stringResource(R.string.chords))
                }
            }

            val listModifier = Modifier
                .fillMaxSize()
                .then(holdToPauseModifier)

            val contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 16.dp
            )

            when (viewMode) {
                ViewMode.Lyrics -> {
                    LyricsPage(
                        lines = lyricLines,
                        hint = if (!hasChords) stringResource(R.string.chords_not_found) else null,
                        listState = lyricsListState,
                        contentPadding = contentPadding,
                        modifier = listModifier
                    )
                }
                ViewMode.Chords -> {
                    ChordsPage(
                        songText = songText,
                        hint = if (!hasLyrics) stringResource(R.string.lyrics_not_found) else null,
                        listState = chordsListState,
                        contentPadding = contentPadding,
                        modifier = listModifier
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoScrollBottomSheet(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    isActive: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean,
    onDismiss: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.auto_scroll),
            style = MaterialTheme.typography.titleMedium
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.auto_scroll_speed),
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = speed.coerceIn(0f, 1f),
                onValueChange = { onSpeedChange(it.coerceIn(0f, 1f)) },
                valueRange = 0f..1f
            )
            AutoScrollPresetRow(speed = speed, onSpeedChange = onSpeedChange)
            Text(
                text = stringResource(R.string.auto_scroll_speed_value, (speed * 100).roundToInt()),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = stringResource(R.string.auto_scroll_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            FilledTonalButton(
                onClick = onToggle,
                enabled = enabled,
                modifier = Modifier.weight(1f)
            ) {
                val icon = if (isActive) Icons.Filled.Pause else Icons.Filled.PlayArrow
                Icon(imageVector = icon, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(if (isActive) R.string.auto_scroll_pause else R.string.auto_scroll_play))
            }
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        }
    }
}

@Composable
private fun AutoScrollPresetRow(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
) {
    val presets = listOf(
        0.2f to R.string.auto_scroll_preset_slow,
        0.4f to R.string.auto_scroll_preset_medium,
        0.7f to R.string.auto_scroll_preset_fast,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        presets.forEach { (value, labelRes) ->
            val selected = abs(speed - value) < 0.05f
            FilterChip(
                selected = selected,
                onClick = { onSpeedChange(value) },
                label = { Text(text = stringResource(labelRes)) }
            )
        }
    }
}
