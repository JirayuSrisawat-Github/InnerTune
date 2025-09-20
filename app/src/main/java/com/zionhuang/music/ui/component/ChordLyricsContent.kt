package com.zionhuang.music.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zionhuang.music.lyrics.chords.LyricLine
import com.zionhuang.music.lyrics.chords.SongText
import com.zionhuang.music.lyrics.chords.hasChords
import com.zionhuang.music.lyrics.chords.hasLyrics
import com.zionhuang.music.ui.player.text.AutoScrollDetector
import com.zionhuang.music.ui.player.text.PlayerAction
import com.zionhuang.music.ui.player.text.PlayerViewModel
import com.zionhuang.music.ui.player.text.ViewMode
import com.zionhuang.music.ui.player.text.rememberAutoScrollController
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChordLyricsContent(
    songText: SongText,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = viewModel(key = "ChordLyricsContent_${songText.id}")
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(songText.id) {
        viewModel.handleAction(PlayerAction.LoadSong(songText))
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val autoScrollController = rememberAutoScrollController(listState, coroutineScope)

    var showSpeedControl by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.autoScrollEnabled, uiState.autoScrollSpeed, uiState.autoScrollPaused) {
        if (uiState.autoScrollEnabled && !uiState.autoScrollPaused) {
            autoScrollController.startAutoScroll(uiState.autoScrollSpeed, density)
        } else {
            autoScrollController.stopAutoScroll()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (songText.hasLyrics() && songText.hasChords()) {
                ViewModeSelector(
                    currentMode = uiState.viewMode,
                    hasLyrics = songText.hasLyrics(),
                    hasChords = songText.hasChords(),
                    onModeChange = { viewModel.handleAction(PlayerAction.SwitchView(it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }

            Box(modifier = Modifier.weight(1f, fill = true)) {
                AutoScrollDetector(
                    controller = autoScrollController,
                    onPause = { viewModel.handleAction(PlayerAction.PauseAutoScroll) },
                    onResume = { viewModel.handleAction(PlayerAction.ResumeAutoScroll) }
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
        }

        FloatingActionButton(
            onClick = { showSpeedControl = true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            val isRunning = uiState.autoScrollEnabled && !uiState.autoScrollPaused
            Text(if (isRunning) "Pause" else "Scroll")
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
                .padding(24.dp)
        ) {
            Text(
                text = "Auto-Scroll Speed",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            SwitchRow(
                label = "Enable Auto-Scroll",
                checked = isEnabled,
                onCheckedChange = onToggleEnabled
            )

            val percentage = (currentSpeed.coerceIn(0f, 1f) * 100).roundToInt()
            Text(
                text = "Speed: ${percentage}%",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Slider(
                value = currentSpeed.coerceIn(0f, 1f),
                onValueChange = onSpeedChange,
                enabled = isEnabled
            )

            Spacer(modifier = Modifier.height(8.dp))
            PresetButtons(isEnabled = isEnabled, onSpeedChange = onSpeedChange)
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PresetButtons(
    isEnabled: Boolean,
    onSpeedChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PresetButton(
            label = "Slow",
            speed = 0.2f,
            enabled = isEnabled,
            onSpeedChange = onSpeedChange,
            modifier = Modifier.weight(1f)
        )
        PresetButton(
            label = "Medium",
            speed = 0.4f,
            enabled = isEnabled,
            onSpeedChange = onSpeedChange,
            modifier = Modifier.weight(1f)
        )
        PresetButton(
            label = "Fast",
            speed = 0.7f,
            enabled = isEnabled,
            onSpeedChange = onSpeedChange,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PresetButton(
    label: String,
    speed: Float,
    enabled: Boolean,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = { onSpeedChange(speed) },
        enabled = enabled,
        modifier = modifier
    ) {
        Text(label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewModeSelector(
    currentMode: ViewMode,
    hasLyrics: Boolean,
    hasChords: Boolean,
    onModeChange: (ViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.SingleChoiceSegmentedButtonRow(
        modifier = modifier
    ) {
        androidx.compose.material3.SegmentedButton(
            selected = currentMode == ViewMode.LYRICS,
            onClick = { onModeChange(ViewMode.LYRICS) },
            enabled = hasLyrics
        ) {
            Text("Lyrics")
        }
        androidx.compose.material3.SegmentedButton(
            selected = currentMode == ViewMode.CHORDS,
            onClick = { onModeChange(ViewMode.CHORDS) },
            enabled = hasChords
        ) {
            Text("Chords")
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
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        modifier = modifier.fillMaxSize()
    ) {
        songText.sections.forEach { section ->
            section.tag?.let { tag ->
                item(key = "section_${tag}") {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
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
                    message = "No lyrics available",
                    modifier = Modifier.padding(vertical = 24.dp)
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
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        modifier = modifier.fillMaxSize()
    ) {
        songText.sections.forEach { section ->
            section.tag?.let { tag ->
                item(key = "section_${tag}") {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            items(
                items = section.lines,
                key = { it.raw.hashCode() }
            ) { line ->
                ChordLyricLine(
                    line = line,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
        if (!songText.hasChords()) {
            item {
                EmptyStateMessage(
                    message = "No chords available",
                    modifier = Modifier.padding(vertical = 24.dp)
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
    val textMeasurer = rememberTextMeasurer()
    val chordStyle = MaterialTheme.typography.labelLarge.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp
    )
    val density = LocalDensity.current
    val charWidthPx = remember(line.chordSpans, chordStyle) {
        val measurement = textMeasurer.measure("M", chordStyle)
        measurement.size.width.toFloat().coerceAtLeast(1f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                val chordDescription = if (line.chordSpans.isNotEmpty()) {
                    "Chords: " + line.chordSpans.joinToString(", ") { it.chord.displayName }
                } else ""
                contentDescription = buildString {
                    if (chordDescription.isNotEmpty()) {
                        append(chordDescription)
                        append(". ")
                    }
                    append("Lyrics: ${line.cleanLyrics}")
                }
            }
    ) {
        if (line.chordSpans.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            ) {
                line.chordSpans.forEach { span ->
                    val xOffset = with(density) { (charWidthPx * span.startColumn).toDp() }
                    Text(
                        text = span.chord.displayName,
                        style = chordStyle,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.offset(x = xOffset)
                    )
                }
            }
        }

        Text(
            text = line.cleanLyrics,
            style = MaterialTheme.typography.bodyLarge.copy(
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.3f
            ),
            modifier = Modifier.padding(top = if (line.chordSpans.isNotEmpty()) 4.dp else 0.dp)
        )
    }
}

@Composable
private fun EmptyStateMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    )
}
