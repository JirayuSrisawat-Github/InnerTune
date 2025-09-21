package com.zionhuang.music.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zionhuang.music.LocalPlayerConnection
import com.zionhuang.music.R
import com.zionhuang.music.constants.PlayerTextAlignmentKey
import com.zionhuang.music.constants.NavigationBarHeight
import com.zionhuang.music.db.entities.ChordsEntity.Companion.CHORDS_NOT_FOUND
import com.zionhuang.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.zionhuang.music.lyrics.LyricsEntry
import com.zionhuang.music.lyrics.LyricsUtils.findCurrentLineIndex
import com.zionhuang.music.lyrics.LyricsUtils.parseLyrics
import com.zionhuang.music.songtext.SongText
import com.zionhuang.music.songtext.SongTextParser
import com.zionhuang.music.ui.menu.LyricsMenu
import com.zionhuang.music.ui.screens.settings.PlayerTextAlignment
import com.zionhuang.music.utils.rememberEnumPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

private enum class ViewMode { Lyrics, Chords }

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun Lyrics(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val view = LocalView.current

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    val chordsEntity by playerConnection.currentChords.collectAsState(initial = null)
    val playerTextAlignment by rememberEnumPreference(PlayerTextAlignmentKey, PlayerTextAlignment.CENTER)

    val lyricsRaw = lyricsEntity?.lyrics
    val lyricsEntries = remember(lyricsRaw) {
        when {
            lyricsRaw.isNullOrEmpty() || lyricsRaw == LYRICS_NOT_FOUND -> emptyList()
            lyricsRaw.startsWith("[") -> parseLyrics(lyricsRaw)
            else -> lyricsRaw.lines().mapIndexedNotNull { index, line ->
                if (line.isBlank()) null else LyricsEntry(index * 100L, line)
            }
        }
    }
    val isSyncedLyrics = remember(lyricsRaw) {
        !lyricsRaw.isNullOrEmpty() && lyricsRaw.startsWith("[")
    }
    val lyricsAvailable = lyricsRaw == LYRICS_NOT_FOUND || (!lyricsRaw.isNullOrEmpty() && lyricsEntries.isNotEmpty())

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

    var currentLineIndex by rememberSaveable(metadataId) { mutableIntStateOf(-1) }
    var deferredCurrentLineIndex by rememberSaveable(metadataId) { mutableIntStateOf(0) }
    var lastPreviewTime by rememberSaveable(metadataId) { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }

    val hasChords = songText?.sections?.any { section -> section.lines.isNotEmpty() } == true
    val hasLyricsContent = lyricsEntries.isNotEmpty()
    val canAutoScroll = when (viewMode) {
        ViewMode.Lyrics -> hasLyricsContent
        ViewMode.Chords -> hasChords
    }

    LaunchedEffect(metadataId, lyricsAvailable, hasChords) {
        when {
            !lyricsAvailable && hasChords -> viewMode = ViewMode.Chords
            lyricsAvailable && !hasChords -> viewMode = ViewMode.Lyrics
        }
    }

    LaunchedEffect(viewMode, canAutoScroll) {
        if (!canAutoScroll && autoScrollActive) {
            autoScrollActive = false
        }
        if (!canAutoScroll && showSpeedSheet) {
            showSpeedSheet = false
        }
    }

    val density = LocalDensity.current

    val lyricsScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (consumed.y != 0f) {
                    lastPreviewTime = System.currentTimeMillis()
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                lastPreviewTime = System.currentTimeMillis()
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(lyricsEntries, lyricsRaw, metadataId) {
        if (!isSyncedLyrics || lyricsEntries.isEmpty()) {
            isSeeking = false
            currentLineIndex = -1
            deferredCurrentLineIndex = 0
            return@LaunchedEffect
        }
        while (isActive) {
            delay(50)
            val sliderPosition = sliderPositionProvider()
            isSeeking = sliderPosition != null
            val position = sliderPosition ?: playerConnection.player.currentPosition
            currentLineIndex = findCurrentLineIndex(lyricsEntries, position)
        }
    }

    LaunchedEffect(isSyncedLyrics, lyricsEntries) {
        if (!isSyncedLyrics || lyricsEntries.isEmpty()) {
            isSeeking = false
            currentLineIndex = -1
            deferredCurrentLineIndex = 0
        }
    }

    LaunchedEffect(isSeeking, lastPreviewTime) {
        if (isSeeking) {
            lastPreviewTime = 0L
        } else if (lastPreviewTime != 0L) {
            delay(LyricsPreviewTime)
            lastPreviewTime = 0L
        }
    }

    LaunchedEffect(viewMode, currentLineIndex, lastPreviewTime, isSyncedLyrics, lyricsEntries, autoScrollActive) {
        if (viewMode != ViewMode.Lyrics || !isSyncedLyrics || lyricsEntries.isEmpty() || autoScrollActive) return@LaunchedEffect
        if (currentLineIndex != -1) {
            deferredCurrentLineIndex = currentLineIndex
            if (lastPreviewTime == 0L) {
                val targetIndex = currentLineIndex.coerceIn(0, lyricsEntries.lastIndex)
                val offset = with(density) { 36.dp.toPx().toInt() }
                if (isSeeking) {
                    lyricsListState.scrollToItem(targetIndex, offset)
                } else {
                    lyricsListState.animateScrollToItem(targetIndex, offset)
                }
            }
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

    LaunchedEffect(viewMode, autoScrollActive, autoScrollSpeed, canAutoScroll, density) {
        if (!autoScrollActive || !canAutoScroll) return@LaunchedEffect
        val state = when (viewMode) {
            ViewMode.Lyrics -> lyricsListState
            ViewMode.Chords -> chordsListState
        }
        val pixelsPerSecond = with(density) { 40.dp.toPx() } * autoScrollSpeed.clamp(0f, 1f)
        if (pixelsPerSecond <= 0f) {
            autoScrollActive = false
            return@LaunchedEffect
        }
        var lastFrameTime = withFrameNanos { it }
        while (isActive && autoScrollActive && canAutoScroll) {
            val frameTime = withFrameNanos { it }
            val deltaSeconds = (frameTime - lastFrameTime) / 1_000_000_000f
            lastFrameTime = frameTime
            if (deltaSeconds <= 0f) continue
            val consumed = state.scrollByPixels(pixelsPerSecond * deltaSeconds)
            if (consumed == 0f && !state.canScrollForward) {
                autoScrollActive = false
                break
            }
        }
    }

    LaunchedEffect(viewMode, autoScrollActive) {
        if (!autoScrollActive) return@LaunchedEffect
        val state = when (viewMode) {
            ViewMode.Lyrics -> lyricsListState
            ViewMode.Chords -> chordsListState
        }
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

    val holdToPauseModifier = if (autoScrollActive) {
        Modifier.pointerInput(viewMode, autoScrollActive) {
            detectTapGestures(
                onPress = {
                    val wasActive = autoScrollActive
                    autoScrollActive = false
                    val released = tryAwaitRelease()
                    if (released && wasActive && !autoScrollActive) {
                        autoScrollActive = true
                    }
                }
            )
        }
    } else {
        Modifier
    }

    val speedSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (showSpeedSheet && canAutoScroll) {
        ModalBottomSheet(
            sheetState = speedSheetState,
            onDismissRequest = { showSpeedSheet = false }
        ) {
            AutoScrollBottomSheet(
                speed = autoScrollSpeed,
                onSpeedChange = { autoScrollSpeed = it.clamp(0f, 1f) },
                isActive = autoScrollActive,
                onToggle = {
                    if (canAutoScroll) {
                        autoScrollActive = !autoScrollActive
                    }
                },
                enabled = canAutoScroll,
                onDismiss = {
                    showSpeedSheet = false
                }
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
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
                                    onDismiss = { menuState.dismiss() },
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
            if (canAutoScroll) {
                FloatingActionButton(
                    onClick = { showSpeedSheet = true },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(end = 16.dp, bottom = NavigationBarHeight + 16.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.speed),
                        contentDescription = stringResource(R.string.auto_scroll)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(NavigationBarDefaults.Elevation))
                .padding(innerPadding)
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp)
            ) {
                SegmentedButton(
                    selected = viewMode == ViewMode.Lyrics,
                    onClick = { viewMode = ViewMode.Lyrics },
                    enabled = lyricsAvailable || lyricsEntity == null,
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text(text = stringResource(R.string.lyrics_tab))
                }
                SegmentedButton(
                    selected = viewMode == ViewMode.Chords,
                    onClick = { viewMode = ViewMode.Chords },
                    enabled = hasChords,
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text(text = stringResource(R.string.chords_tab))
                }
            }

            val displayedLineIndex = if (isSeeking) deferredCurrentLineIndex else currentLineIndex
            val chordsContentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 16.dp
            )

            when (viewMode) {
                ViewMode.Lyrics -> {
                    val hint = if (!hasChords) stringResource(R.string.chords_not_found) else null
                    when {
                        lyricsEntity == null && lyricsRaw != LYRICS_NOT_FOUND -> {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        lyricsRaw == LYRICS_NOT_FOUND -> {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.lyrics_not_found),
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center
                                    )
                                    if (hint != null) {
                                        Text(
                                            text = hint,
                                            style = MaterialTheme.typography.bodySmall,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        lyricsEntries.isEmpty() -> {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = lyricsRaw.orEmpty().ifEmpty { stringResource(R.string.lyrics_not_found) },
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(24.dp)
                                )
                            }
                        }
                        else -> {
                            LazyColumn(
                                state = lyricsListState,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .nestedScroll(lyricsScrollConnection)
                                    .then(holdToPauseModifier)
                            ) {
                                itemsIndexed(lyricsEntries) { index, entry ->
                                    val isHighlighted = isSyncedLyrics && index == displayedLineIndex
                                    Text(
                                        text = entry.text,
                                        color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = when (playerTextAlignment) {
                                            PlayerTextAlignment.SIDED -> TextAlign.Start
                                            PlayerTextAlignment.CENTER -> TextAlign.Center
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                            .alpha(if (!isSyncedLyrics || isHighlighted) 1f else 0.5f)
                                            .clickable(enabled = isSyncedLyrics) {
                                                playerConnection.player.seekTo(entry.time)
                                                lastPreviewTime = 0L
                                            }
                                    )
                                }
                                if (hint != null) {
                                    item {
                                        Text(
                                            text = hint,
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                ViewMode.Chords -> {
                    ChordsPage(
                        songText = songText,
                        hint = if (!lyricsAvailable) stringResource(R.string.lyrics_not_found) else null,
                        listState = chordsListState,
                        contentPadding = chordsContentPadding,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(holdToPauseModifier)
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
                value = speed.clamp(0f, 1f),
                onValueChange = { onSpeedChange(it.clamp(0f, 1f)) },
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
                val iconRes = if (isActive) R.drawable.pause else R.drawable.play
                Icon(painterResource(iconRes), contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(if (isActive) R.string.auto_scroll_pause else R.string.auto_scroll_play))
            }
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        }
    }
}

private suspend fun LazyListState.scrollByPixels(delta: Float): Float {
    if (delta == 0f) return 0f
    val info = layoutInfo
    if (info.totalItemsCount == 0) return 0f

    val startIndex = firstVisibleItemIndex
    val startOffset = firstVisibleItemScrollOffset

    val visibleSizes = info.visibleItemsInfo.associate { it.index to it.size }
    val defaultSize = info.visibleItemsInfo.maxOfOrNull { it.size } ?: 0

    fun itemSize(index: Int): Int =
        visibleSizes[index]?.takeIf { it > 0 } ?: defaultSize

    var targetIndex = startIndex
    var targetOffset = startOffset + delta

    if (delta > 0f) {
        var remaining = targetOffset
        var currentIndex = targetIndex
        var currentSize = itemSize(currentIndex)
        while (remaining >= currentSize && currentIndex < info.totalItemsCount - 1 && currentSize > 0) {
            remaining -= currentSize
            currentIndex += 1
            currentSize = itemSize(currentIndex)
        }
        targetIndex = currentIndex
        targetOffset = remaining.coerceAtLeast(0f)
    } else {
        var remaining = targetOffset
        var currentIndex = targetIndex
        var currentSize = itemSize(currentIndex)
        while (remaining < 0f && currentIndex > 0 && currentSize > 0) {
            currentIndex -= 1
            currentSize = itemSize(currentIndex)
            remaining += currentSize
        }
        targetIndex = currentIndex
        targetOffset = remaining.coerceAtLeast(0f)
    }

    val targetOffsetInt = targetOffset.roundToInt().coerceAtLeast(0)
    if (targetIndex == startIndex && targetOffsetInt == startOffset) {
        return 0f
    }

    scrollToItem(targetIndex, targetOffsetInt)

    val endIndex = firstVisibleItemIndex
    val endOffset = firstVisibleItemScrollOffset
    return if (endIndex != startIndex || endOffset != startOffset) delta else 0f
}

private fun Float.clamp(min: Float, max: Float): Float = when {
    this < min -> min
    this > max -> max
    else -> this
}

private val LyricsPreviewTime = 4.seconds

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
