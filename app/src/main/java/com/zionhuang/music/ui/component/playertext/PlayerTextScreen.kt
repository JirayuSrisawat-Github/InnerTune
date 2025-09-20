package com.zionhuang.music.ui.component.playertext

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.mergeDescendants
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zionhuang.music.lyrics.chords.ChordSpan
import com.zionhuang.music.lyrics.chords.LyricLine
import com.zionhuang.music.lyrics.chords.SongText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun PlayerTextScreen(
    songText: SongText,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val autoScrollController = rememberAutoScrollController(listState)
    val density = LocalDensity.current

    var showSpeedControl by remember { mutableStateOf(false) }

    val hasLyrics by remember(songText) { derivedStateOf { songText.hasLyrics() } }
    val hasChords by remember(songText) { derivedStateOf { songText.hasChords() } }

    LaunchedEffect(songText.id) {
        viewModel.submitSong(songText)
    }

    DisposableEffect(Unit) {
        onDispose { autoScrollController.stopAutoScroll() }
    }

    LaunchedEffect(uiState.autoScrollEnabled, uiState.autoScrollSpeed, uiState.autoScrollPaused, density) {
        if (uiState.autoScrollEnabled && !uiState.autoScrollPaused) {
            autoScrollController.startAutoScroll(uiState.autoScrollSpeed, density)
        } else {
            autoScrollController.stopAutoScroll()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(songText.title) },
                actions = {
                    ViewModeSelector(
                        currentMode = uiState.viewMode,
                        hasLyrics = hasLyrics,
                        hasChords = hasChords,
                        onModeChange = { viewModel.handleAction(PlayerAction.SwitchView(it)) }
                    )
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showSpeedControl = true }
            ) {
                Icon(
                    imageVector = if (uiState.autoScrollEnabled && !uiState.autoScrollPaused) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null
                )
            }
        },
        modifier = modifier
    ) { paddingValues ->
        if (uiState.currentSong == null && uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = uiState.error ?: "")
            }
        } else {
            uiState.currentSong?.let { currentSong ->
                AutoScrollDetector(
                    controller = autoScrollController,
                    onPause = { viewModel.handleAction(PlayerAction.PauseAutoScroll) },
                    onResume = { viewModel.handleAction(PlayerAction.ResumeAutoScroll) }
                ) {
                    when (uiState.viewMode) {
                        ViewMode.LYRICS -> LyricsView(
                            songText = currentSong,
                            listState = listState,
                            modifier = Modifier.padding(paddingValues)
                        )

                        ViewMode.CHORDS -> ChordsView(
                            songText = currentSong,
                            listState = listState,
                            modifier = Modifier.padding(paddingValues)
                        )
                    }
                }
            }
        }

        if (showSpeedControl) {
            SpeedControlBottomSheet(
                currentSpeed = uiState.autoScrollSpeed,
                isEnabled = uiState.autoScrollEnabled,
                onSpeedChange = { speed ->
                    viewModel.handleAction(PlayerAction.SetAutoScrollSpeed(speed))
                    if (uiState.autoScrollEnabled && !uiState.autoScrollPaused) {
                        autoScrollController.startAutoScroll(speed, density)
                    }
                },
                onToggleEnabled = { enabled ->
                    viewModel.handleAction(PlayerAction.SetAutoScrollEnabled(enabled))
                    if (!enabled) {
                        autoScrollController.stopAutoScroll()
                    } else {
                        autoScrollController.startAutoScroll(uiState.autoScrollSpeed, density)
                    }
                },
                onDismiss = { showSpeedControl = false }
            )
        }
    }
}

private fun SongText.hasLyrics(): Boolean =
    sections.any { section -> section.lines.any { line -> line.cleanLyrics.isNotBlank() } }

private fun SongText.hasChords(): Boolean =
    sections.any { section -> section.lines.any { line -> line.chordSpans.isNotEmpty() } }

@Composable
fun ViewModeSelector(
    currentMode: ViewMode,
    hasLyrics: Boolean,
    hasChords: Boolean,
    onModeChange: (ViewMode) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = { onModeChange(ViewMode.LYRICS) },
            enabled = hasLyrics,
            colors = AlertDialogDefaults.textButtonColors(),
        ) {
            Text(
                text = "Lyrics",
                fontWeight = if (currentMode == ViewMode.LYRICS) FontWeight.Bold else FontWeight.Normal
            )
        }
        TextButton(
            onClick = { onModeChange(ViewMode.CHORDS) },
            enabled = hasChords,
            colors = AlertDialogDefaults.textButtonColors(),
        ) {
            Text(
                text = "Chords",
                fontWeight = if (currentMode == ViewMode.CHORDS) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun LyricsView(
    songText: SongText,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        songText.sections.forEach { section ->
            section.tag?.let { tag ->
                item(key = "section_$tag") {
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
            items(
                items = section.lines,
                key = { it.raw.hashCode() }
            ) { line ->
                Text(
                    text = line.cleanLyrics,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.3f
                    ),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
        if (!songText.hasLyrics()) {
            item {
                EmptyStateMessage(
                    message = "No lyrics available for this song",
                    modifier = Modifier.padding(32.dp)
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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        songText.sections.forEach { section ->
            section.tag?.let { tag ->
                item(key = "section_$tag") {
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
            items(
                items = section.lines,
                key = { it.raw.hashCode() }
            ) { line ->
                AccessibleChordLyricLine(
                    line = line,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
        if (!songText.hasChords()) {
            item {
                EmptyStateMessage(
                    message = "No chords available for this song",
                    modifier = Modifier.padding(32.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun ChordLyricLine(
    line: LyricLine,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val lyricStyle = MaterialTheme.typography.bodyLarge.copy(
        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.3f
    )
    val chordStyle = MaterialTheme.typography.labelLarge.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp
    )
    val density = LocalDensity.current
    val spacingPx = with(density) { 4.dp.roundToPx() }

    val positionedChords = remember(line, spacingPx, lyricStyle, chordStyle) {
        calculateChordPositions(line, textMeasurer, lyricStyle, chordStyle, spacingPx)
    }

    if (positionedChords.isEmpty()) {
        Text(
            text = line.cleanLyrics,
            style = lyricStyle,
            modifier = modifier.fillMaxWidth()
        )
        return
    }

    androidx.compose.ui.layout.Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            line.chordSpans.forEach { span ->
                Text(
                    text = span.chord.displayName,
                    style = chordStyle,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Text(
                text = line.cleanLyrics,
                style = lyricStyle,
                modifier = Modifier
                    .fillMaxWidth()
            )
        }
    ) { measurables, constraints ->
        if (measurables.isEmpty()) {
            layout(constraints.minWidth, constraints.minHeight) {}
            return@Layout
        }

        val lyricMeasurable = measurables.last()
        val lyricPlaceable = lyricMeasurable.measure(constraints)

        val chordMeasurables = measurables.dropLast(1)
        val chordPlaceables = chordMeasurables.mapIndexed { index, measurable ->
            val placeable = measurable.measure(constraints)
            val offsetPx = positionedChords.getOrNull(index)?.offsetPx ?: 0
            offsetPx to placeable
        }

        val chordHeight = chordPlaceables.maxOfOrNull { it.second.height } ?: 0
        val width = max(
            lyricPlaceable.width,
            chordPlaceables.maxOfOrNull { it.first + it.second.width } ?: lyricPlaceable.width
        )
        val height = chordHeight + spacingPx + lyricPlaceable.height

        layout(width, height) {
            chordPlaceables.forEach { (offset, placeable) ->
                placeable.place(IntOffset(offset.coerceAtLeast(0), 0))
            }
            lyricPlaceable.place(0, chordHeight + spacingPx)
        }
    }
}

@Composable
fun AccessibleChordLyricLine(
    line: LyricLine,
    modifier: Modifier = Modifier
) {
    val chordDescription = remember(line.chordSpans) {
        if (line.chordSpans.isEmpty()) null
        else line.chordSpans.joinToString(separator = ", ") { it.chord.displayName }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    chordDescription?.let {
                        append("Chords: ")
                        append(it)
                        append('.')
                        append('\n')
                    }
                    if (line.cleanLyrics.isNotEmpty()) {
                        append("Lyrics: ")
                        append(line.cleanLyrics)
                    }
                }
            }
    ) {
        ChordLyricLine(line = line)
    }
}

private data class PositionedChord(
    val offsetPx: Int,
    val widthPx: Int
)

@OptIn(ExperimentalTextApi::class)
private fun calculateChordPositions(
    line: LyricLine,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    lyricStyle: TextStyle,
    chordStyle: TextStyle,
    spacingPx: Int
): List<PositionedChord> {
    if (line.chordSpans.isEmpty()) return emptyList()
    val sortedSpans = line.chordSpans.sortedBy(ChordSpan::startColumn)
    val positioned = mutableListOf<PositionedChord>()
    var lastEnd = -spacingPx
    sortedSpans.forEach { span ->
        val prefix = line.cleanLyrics.take(span.startColumn.coerceIn(0, line.cleanLyrics.length))
        val prefixWidth = textMeasurer.measure(prefix, lyricStyle).size.width
        val chordWidth = textMeasurer.measure(span.chord.displayName, chordStyle).size.width
        var start = prefixWidth
        if (positioned.isNotEmpty()) {
            val minStart = lastEnd + spacingPx
            if (start < minStart) {
                start = minStart
            }
        }
        lastEnd = start + chordWidth
        positioned += PositionedChord(start, chordWidth)
    }
    return positioned
}

@Composable
fun EmptyStateMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth()
    )
}

class AutoScrollController(
    private val listState: LazyListState,
    private val scope: CoroutineScope
) {
    private var scrollJob: kotlinx.coroutines.Job? = null
    private var userInteracting = false

    fun startAutoScroll(speedFactor: Float, density: Density) {
        stopAutoScroll()
        if (speedFactor <= 0f) return
        scrollJob = scope.launch {
            val baseSpeedPx = with(density) { 40.dp.toPx() }
            var lastFrameTime = 0L
            while (isActive) {
                val frameTime = androidx.compose.animation.core.withFrameNanos { it }
                if (!userInteracting && lastFrameTime != 0L) {
                    val deltaSeconds = (frameTime - lastFrameTime) / 1_000_000_000f
                    val distance = baseSpeedPx * speedFactor * deltaSeconds
                    if (distance != 0f) {
                        listState.scrollBy(distance)
                    }
                }
                lastFrameTime = frameTime
            }
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
fun rememberAutoScrollController(listState: LazyListState): AutoScrollController {
    val scope = rememberCoroutineScope()
    return remember(listState) { AutoScrollController(listState, scope) }
}

@Composable
fun AutoScrollDetector(
    controller: AutoScrollController,
    onPause: () -> Unit,
    onResume: () -> Unit,
    content: @Composable () -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(isDragging) {
        if (isDragging) {
            controller.pauseForUserInteraction()
            onPause()
        } else {
            delay(500)
            controller.resumeAfterUserInteraction()
            onResume()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(controller) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                    },
                    onDragEnd = {
                        isDragging = false
                    },
                    onDragCancel = {
                        isDragging = false
                    }
                ) { _, _ -> }
            }
            .pointerInput(controller) {
                detectTapGestures(
                    onPress = {
                        controller.pauseForUserInteraction()
                        onPause()
                        try {
                            tryAwaitRelease()
                        } finally {
                            controller.resumeAfterUserInteraction()
                            onResume()
                        }
                    }
                )
            }
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedControlBottomSheet(
    currentSpeed: Float,
    isEnabled: Boolean,
    onSpeedChange: (Float) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Auto-Scroll Speed",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text("Enable Auto-Scroll")
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggleEnabled
                )
            }
            Text(
                text = "Speed: ${(currentSpeed * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Slider(
                value = currentSpeed,
                onValueChange = onSpeedChange,
                enabled = isEnabled,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                SpeedPresetButton("Slow", 0.2f, onSpeedChange, isEnabled)
                SpeedPresetButton("Medium", 0.4f, onSpeedChange, isEnabled)
                SpeedPresetButton("Fast", 0.7f, onSpeedChange, isEnabled)
            }
            Spacer(modifier = Modifier.height(16.dp))
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
    TextButton(
        onClick = { onSpeedChange(speed) },
        enabled = enabled
    ) {
        Text(label)
    }
}
