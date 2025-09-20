package com.zionhuang.music.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.stickyHeader
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zionhuang.music.lyrics.chords.LyricLine
import com.zionhuang.music.lyrics.chords.SongText
import com.zionhuang.music.lyrics.chords.hasChords
import com.zionhuang.music.lyrics.chords.hasLyrics
import com.zionhuang.music.ui.component.LyricsChordsPlayerDefaults.baseAutoScrollSpeed
import com.zionhuang.music.ui.component.LyricsChordsPlayerDefaults.lyricLineHeightFactor
import com.zionhuang.music.viewmodels.PlayerTextViewModel
import com.zionhuang.music.viewmodels.ViewMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.math.roundToInt

@Composable
fun PlayerTextScreen(
    songText: SongText,
    modifier: Modifier = Modifier,
    initialMode: ViewMode = if (songText.hasChords()) ViewMode.CHORDS else ViewMode.LYRICS,
    viewModel: PlayerTextViewModel = viewModel()
) {
    LaunchedEffect(songText.id, songText.sections) {
        viewModel.setSong(songText, initialMode)
    }

    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    var showSpeedControl by remember { mutableStateOf(false) }

    val autoScrollController = remember(listState, coroutineScope) {
        AutoScrollController(listState, coroutineScope)
    }

    DisposableEffect(Unit) {
        onDispose { autoScrollController.stopAutoScroll() }
    }

    LaunchedEffect(uiState.autoScrollEnabled, uiState.autoScrollSpeed, density) {
        if (uiState.autoScrollEnabled) {
            autoScrollController.startAutoScroll(uiState.autoScrollSpeed, density)
        } else {
            autoScrollController.stopAutoScroll()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        HeaderRow(
            songText = songText,
            currentMode = uiState.viewMode,
            hasLyrics = songText.hasLyrics(),
            hasChords = songText.hasChords(),
            onModeChange = { viewModel.handleAction(PlayerTextViewModel.Action.SwitchView(it)) },
            onAutoScrollClick = { showSpeedControl = true },
            autoScrollEnabled = uiState.autoScrollEnabled
        )

        AutoScrollDetector(autoScrollController) {
            AnimatedVisibility(visible = uiState.currentSong != null) {
                when (uiState.viewMode) {
                    ViewMode.LYRICS -> LyricsView(songText = songText, listState = listState)
                    ViewMode.CHORDS -> ChordsView(songText = songText, listState = listState)
                }
            }
        }
    }

    if (showSpeedControl) {
        SpeedControlBottomSheet(
            currentSpeed = uiState.autoScrollSpeed,
            isEnabled = uiState.autoScrollEnabled,
            onSpeedChange = { speed ->
                viewModel.handleAction(PlayerTextViewModel.Action.SetAutoScrollSpeed(speed))
                autoScrollController.restartIfNeeded(uiState.autoScrollEnabled, speed, density)
            },
            onToggleEnabled = { enabled ->
                viewModel.handleAction(PlayerTextViewModel.Action.SetAutoScrollEnabled(enabled))
                if (enabled) {
                    autoScrollController.startAutoScroll(uiState.autoScrollSpeed, density)
                } else {
                    autoScrollController.stopAutoScroll()
                }
            },
            onDismiss = { showSpeedControl = false }
        )
    }
}

@Composable
private fun HeaderRow(
    songText: SongText,
    currentMode: ViewMode,
    hasLyrics: Boolean,
    hasChords: Boolean,
    onModeChange: (ViewMode) -> Unit,
    onAutoScrollClick: () -> Unit,
    autoScrollEnabled: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = songText.title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1
        )
        songText.artist?.takeIf { it.isNotEmpty() }?.let { artist ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            ViewModeSelector(
                currentMode = currentMode,
                hasLyrics = hasLyrics,
                hasChords = hasChords,
                onModeChange = onModeChange
            )
            FloatingActionButton(onClick = onAutoScrollClick, modifier = Modifier.height(40.dp)) {
                Icon(
                    imageVector = if (autoScrollEnabled) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null
                )
            }
        }
    }
}

class AutoScrollController(
    private val listState: LazyListState,
    private val coroutineScope: CoroutineScope
) {
    private var scrollJob: Job? = null
    private var userInteracting = false

    fun startAutoScroll(speedFactor: Float, density: Density) {
        stopAutoScroll()
        scrollJob = coroutineScope.launch {
            val baseSpeedPx = with(density) { baseAutoScrollSpeed.toPx() }
            var lastFrameTime: Long? = null
            while (isActive) {
                if (userInteracting) {
                    lastFrameTime = null
                    delay(16)
                    continue
                }
                var distancePx = 0f
                withFrameNanos { frameTime ->
                    val previous = lastFrameTime
                    lastFrameTime = frameTime
                    if (previous != null) {
                        val deltaSeconds = (frameTime - previous) / 1_000_000_000f
                        distancePx = baseSpeedPx * speedFactor * deltaSeconds
                    }
                }
                if (distancePx != 0f) {
                    listState.scrollBy(distancePx)
                } else {
                    yield()
                }
            }
        }
    }

    fun restartIfNeeded(enabled: Boolean, speedFactor: Float, density: Density) {
        if (enabled) {
            startAutoScroll(speedFactor, density)
        }
    }

    fun stopAutoScroll() {
        scrollJob?.cancel()
        scrollJob = null
    }

    fun pauseForUserInteraction() {
        userInteracting = true
    }

    fun resumeAfterUserInteraction() {
        userInteracting = false
    }
}

@Composable
fun AutoScrollDetector(
    controller: AutoScrollController,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(controller) {
                detectDragGestures(
                    onDragStart = { controller.pauseForUserInteraction() },
                    onDragEnd = { controller.resumeAfterUserInteraction() }
                ) { _, _ -> }
            }
            .pointerInput(controller) {
                detectTapGestures(
                    onPress = {
                        controller.pauseForUserInteraction()
                        try {
                            tryAwaitRelease()
                        } finally {
                            controller.resumeAfterUserInteraction()
                        }
                    }
                )
            }
    ) {
        content()
    }
}

@Composable
fun ViewModeSelector(
    currentMode: ViewMode,
    hasLyrics: Boolean,
    hasChords: Boolean,
    onModeChange: (ViewMode) -> Unit
) {
    SingleChoiceSegmentedButtonRow {
        SegmentedButton(
            selected = currentMode == ViewMode.LYRICS,
            onClick = { onModeChange(ViewMode.LYRICS) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
            enabled = hasLyrics
        ) {
            Text("Lyrics")
        }
        SegmentedButton(
            selected = currentMode == ViewMode.CHORDS,
            onClick = { onModeChange(ViewMode.CHORDS) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
            enabled = hasChords
        ) {
            Text("Chords")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LyricsView(
    songText: SongText,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    ) {
        songText.sections.forEach { section ->
            section.tag?.let { tag ->
                stickyHeader(key = "header_$tag") {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }
            }
            items(section.lines, key = { it.raw.hashCode() }) { line ->
                if (line.cleanLyrics.isNotBlank()) {
                    Text(
                        text = line.cleanLyrics,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * lyricLineHeightFactor
                        ),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
        if (!songText.hasLyrics()) {
            item(key = "empty_lyrics") {
                EmptyStateMessage(
                    message = "No lyrics available for this song",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                )
            }
        }
    }
}

@Composable
fun ChordsView(
    songText: SongText,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    ) {
        songText.sections.forEach { section ->
            section.tag?.let { tag ->
                item(key = "header_$tag") {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }
            }
            items(section.lines, key = { it.raw.hashCode() }) { line ->
                ChordLyricLine(line = line, modifier = Modifier.padding(vertical = 6.dp))
            }
        }
        if (!songText.hasChords()) {
            item(key = "empty_chords") {
                EmptyStateMessage(
                    message = "No chords available for this song",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                )
            }
        }
    }
}

@Composable
fun ChordLyricLine(
    line: LyricLine,
    modifier: Modifier = Modifier
) {
    val lyricStyle = MaterialTheme.typography.bodyLarge.copy(
        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * lyricLineHeightFactor
    )
    val chordStyle = MaterialTheme.typography.labelLarge.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp
    )
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val chordOffsets by remember(line, lyricStyle) {
        derivedStateOf {
            calculateChordOffsets(
                line = line,
                lyricStyle = lyricStyle,
                density = density,
                textMeasurer = textMeasurer
            )
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (line.chordSpans.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxWidth()) {
                line.chordSpans.forEachIndexed { index, span ->
                    val offset = chordOffsets.getOrNull(index) ?: 0.dp
                    Text(
                        text = span.chord.displayName,
                        style = chordStyle,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(start = offset)
                    )
                }
            }
        }
        if (line.cleanLyrics.isNotEmpty()) {
            Text(
                text = line.cleanLyrics,
                style = lyricStyle,
                modifier = Modifier.padding(top = if (line.chordSpans.isNotEmpty()) 6.dp else 0.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

private fun calculateChordOffsets(
    line: LyricLine,
    lyricStyle: TextStyle,
    density: Density,
    textMeasurer: TextMeasurer
): List<Dp> {
    if (line.chordSpans.isEmpty()) return emptyList()
    return line.chordSpans.map { span ->
        val index = span.startColumn.coerceIn(0, line.cleanLyrics.length)
        val prefix = line.cleanLyrics.take(index)
        val widthPx = textMeasurer.measure(prefix, lyricStyle).size.width
        with(density) { widthPx.toDp() }
    }
}

@Composable
fun EmptyStateMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
        textAlign = TextAlign.Center
    )
}

@Composable
fun SpeedControlBottomSheet(
    currentSpeed: Float,
    isEnabled: Boolean,
    onSpeedChange: (Float) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Auto-Scroll",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Enable Auto-Scroll")
                Switch(checked = isEnabled, onCheckedChange = onToggleEnabled)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Speed: ${(currentSpeed * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = currentSpeed,
                onValueChange = onSpeedChange,
                valueRange = 0.1f..1f,
                enabled = isEnabled
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SpeedPresetButton(label = "Slow", speed = 0.2f, onSpeedChange = onSpeedChange, enabled = isEnabled)
                SpeedPresetButton(label = "Medium", speed = 0.4f, onSpeedChange = onSpeedChange, enabled = isEnabled)
                SpeedPresetButton(label = "Fast", speed = 0.7f, onSpeedChange = onSpeedChange, enabled = isEnabled)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun SpeedPresetButton(
    label: String,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    enabled: Boolean
) {
    androidx.compose.material3.OutlinedButton(onClick = { onSpeedChange(speed) }, enabled = enabled) {
        Text(label)
    }
}

private object LyricsChordsPlayerDefaults {
    val baseAutoScrollSpeed = 40.dp
    const val lyricLineHeightFactor = 1.3f
}
