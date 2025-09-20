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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zionhuang.music.lyrics.player.LyricLine
import com.zionhuang.music.lyrics.player.SongText
import com.zionhuang.music.lyrics.player.hasChords
import com.zionhuang.music.lyrics.player.hasLyrics
import com.zionhuang.music.viewmodels.PlayerAction
import com.zionhuang.music.viewmodels.PlayerTextViewModel
import com.zionhuang.music.viewmodels.ViewMode
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerTextScreen(
    songText: SongText,
    modifier: Modifier = Modifier,
    viewModel: PlayerTextViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(songText) {
        viewModel.setSong(songText)
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val autoScrollController = remember(listState, coroutineScope) {
        AutoScrollController(listState, coroutineScope)
    }
    val density = LocalDensity.current

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

    var showSpeedControl by rememberSaveable { mutableStateOf(false) }

    val hasLyrics = uiState.currentSong?.hasLyrics() == true
    val hasChords = uiState.currentSong?.hasChords() == true

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (hasLyrics || hasChords) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    ViewModeSelector(
                        currentMode = uiState.viewMode,
                        hasLyrics = hasLyrics,
                        hasChords = hasChords,
                        onModeChange = { mode ->
                            viewModel.handleAction(PlayerAction.SwitchView(mode))
                        }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { showSpeedControl = true }) {
                        Icon(
                            imageVector = if (uiState.autoScrollEnabled) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null
                        )
                    }
                }
            }

            AutoScrollDetector(
                controller = autoScrollController,
                modifier = Modifier.weight(1f)
            ) {
                when (uiState.viewMode) {
                    ViewMode.LYRICS -> LyricsView(
                        songText = songText,
                        listState = listState,
                        modifier = Modifier.fillMaxSize()
                    )
                    ViewMode.CHORDS -> ChordsView(
                        songText = songText,
                        listState = listState,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { showSpeedControl = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = if (uiState.autoScrollEnabled) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null
            )
        }

        if (showSpeedControl) {
            SpeedControlBottomSheet(
                currentSpeed = uiState.autoScrollSpeed,
                isEnabled = uiState.autoScrollEnabled,
                onSpeedChange = { speed ->
                    viewModel.handleAction(PlayerAction.SetAutoScrollSpeed(speed))
                    if (uiState.autoScrollEnabled) {
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

@Composable
fun AutoScrollDetector(
    controller: AutoScrollController,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(isDragging) {
        if (isDragging) {
            controller.pauseForUserInteraction()
        } else {
            delay(500)
            controller.resumeAfterUserInteraction()
        }
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false }
                ) { _, _ -> }
            }
            .pointerInput(Unit) {
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
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            enabled = hasLyrics
        ) {
            Text(text = "Lyrics")
        }
        SegmentedButton(
            selected = currentMode == ViewMode.CHORDS,
            onClick = { onModeChange(ViewMode.CHORDS) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            enabled = hasChords
        ) {
            Text(text = "Chords")
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
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        songText.sections.forEach { section ->
            if (!section.tag.isNullOrEmpty()) {
                item(key = "section_${section.tag}") {
                    Text(
                        text = section.tag,
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
                key = { line -> line.raw.hashCode() }
            ) { line ->
                Text(
                    text = line.cleanLyrics,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.3f
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
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
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        songText.sections.forEach { section ->
            if (!section.tag.isNullOrEmpty()) {
                item(key = "section_${section.tag}") {
                    Text(
                        text = section.tag,
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
                key = { line -> line.raw.hashCode() }
            ) { line ->
                ChordLyricLine(
                    line = line,
                    modifier = Modifier.fillMaxWidth()
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

@Composable
fun ChordLyricLine(
    line: LyricLine,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }

    Column(modifier = modifier.padding(vertical = 4.dp)) {
        if (line.chordSpans.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxWidth()) {
                line.chordSpans.forEach { span ->
                    val offsetX = layoutResult?.let { result ->
                        val index = span.startColumn.coerceIn(0, line.cleanLyrics.length)
                        val cursorRect = result.getCursorRect(index)
                        with(density) { cursorRect.left.toDp() }
                    } ?: 0.dp
                    Text(
                        text = span.chord.displayName,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .padding(bottom = 2.dp)
                            .offset(x = offsetX)
                    )
                }
            }
        }

        Text(
            text = line.cleanLyrics,
            style = MaterialTheme.typography.bodyLarge.copy(
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.3f
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (line.chordSpans.isNotEmpty()) 4.dp else 0.dp),
            onTextLayout = { layoutResult = it }
        )
    }
}

@Composable
fun EmptyStateMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
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
                valueRange = 0f..1f,
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
    OutlinedButton(
        onClick = { onSpeedChange(speed) },
        enabled = enabled
    ) {
        Text(label)
    }
}
