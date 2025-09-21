package com.zionhuang.music.ui.component

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private enum class ViewMode { Lyrics, Chords }

@Composable
fun Lyrics(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    val chordsEntity by playerConnection.currentChords.collectAsState(initial = null)

    val lyricsText = lyricsEntity?.lyrics?.takeIf { !it.isNullOrBlank() && it != LYRICS_NOT_FOUND }
    val lyricLines = remember(lyricsText) {
        lyricsText?.lines()?.filter { it.isNotEmpty() } ?: emptyList()
    }

    val rawChords = chordsEntity?.chords
    var songText by remember { mutableStateOf<SongText?>(null) }
    LaunchedEffect(mediaMetadata, rawChords) {
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

    var viewMode by rememberSaveable { mutableStateOf(ViewMode.Lyrics) }
    var autoScrollActive by rememberSaveable { mutableStateOf(false) }
    var autoScrollSpeed by rememberSaveable { mutableStateOf(0.4f) }
    var showSpeedDialog by rememberSaveable { mutableStateOf(false) }

    val lyricsListState = rememberLazyListState()
    val chordsListState = rememberLazyListState()

    val hasLyrics = lyricLines.isNotEmpty()
    val hasChords = songText?.sections?.any { section -> section.lines.isNotEmpty() } == true

    LaunchedEffect(hasLyrics, hasChords) {
        if (!hasLyrics && hasChords) {
            viewMode = ViewMode.Chords
        } else if (!hasChords && hasLyrics) {
            viewMode = ViewMode.Lyrics
        }
    }

    LaunchedEffect(viewMode, hasLyrics, hasChords) {
        if (viewMode == ViewMode.Lyrics && !hasLyrics) {
            autoScrollActive = false
        }
        if (viewMode == ViewMode.Chords && !hasChords) {
            autoScrollActive = false
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
        while (isActive && autoScrollActive) {
            state.scrollBy(pixelsPerSecond / 60f)
            delay(16)
        }
    }

    LaunchedEffect(lyricsListState.isScrollInProgress) {
        if (viewMode == ViewMode.Lyrics && lyricsListState.isScrollInProgress) {
            autoScrollActive = false
        }
    }

    LaunchedEffect(chordsListState.isScrollInProgress) {
        if (viewMode == ViewMode.Chords && chordsListState.isScrollInProgress) {
            autoScrollActive = false
        }
    }

    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            icon = { Icon(painterResource(R.drawable.speed), contentDescription = null) },
            title = { Text(text = stringResource(R.string.auto_scroll_speed)) },
            text = {
                AutoScrollSpeedContent(
                    speed = autoScrollSpeed,
                    onSpeedChange = { autoScrollSpeed = it },
                )
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }

    val floatingButtons: @Composable () -> Unit = {
        val canAutoScroll = when (viewMode) {
            ViewMode.Lyrics -> hasLyrics
            ViewMode.Chords -> hasChords
        }
        if (canAutoScroll) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FloatingActionButton(onClick = { autoScrollActive = !autoScrollActive }) {
                    Text(text = if (autoScrollActive) "❚❚" else "▶")
                }
                IconButton(onClick = { showSpeedDialog = true }) {
                    Icon(painterResource(R.drawable.speed), contentDescription = null)
                }
            }
        }
    }

    Scaffold(
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
        floatingActionButton = floatingButtons,
        modifier = modifier
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = viewMode.ordinal) {
                Tab(
                    selected = viewMode == ViewMode.Lyrics,
                    onClick = { viewMode = ViewMode.Lyrics },
                    enabled = hasLyrics,
                    text = { Text(stringResource(R.string.lyrics)) }
                )
                Tab(
                    selected = viewMode == ViewMode.Chords,
                    onClick = { viewMode = ViewMode.Chords },
                    enabled = hasChords,
                    text = { Text(stringResource(R.string.chords)) }
                )
            }

            val contentPadding = PaddingValues(
                top = 16.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = padding.calculateBottomPadding()
            )
            when (viewMode) {
                ViewMode.Lyrics -> {
                    LyricsPage(
                        lines = lyricLines,
                        hint = if (!hasChords) stringResource(R.string.chords_not_found) else null,
                        listState = lyricsListState,
                        contentPadding = contentPadding,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                ViewMode.Chords -> {
                    ChordsPage(
                        songText = songText,
                        hint = if (!hasLyrics) stringResource(R.string.lyrics_not_found) else null,
                        listState = chordsListState,
                        contentPadding = contentPadding,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoScrollSpeedContent(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Slider(
            value = speed,
            onValueChange = onSpeedChange,
        )
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = { onSpeedChange(0.2f) }) {
                Text(text = stringResource(R.string.auto_scroll_preset_slow))
            }
            TextButton(onClick = { onSpeedChange(0.4f) }) {
                Text(text = stringResource(R.string.auto_scroll_preset_medium))
            }
            TextButton(onClick = { onSpeedChange(0.7f) }) {
                Text(text = stringResource(R.string.auto_scroll_preset_fast))
            }
        }
        Text(
            text = stringResource(R.string.auto_scroll_speed_value, (speed * 100).toInt()),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
