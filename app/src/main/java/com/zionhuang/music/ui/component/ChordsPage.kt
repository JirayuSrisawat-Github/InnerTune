package com.zionhuang.music.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.times
import com.zionhuang.music.R
import com.zionhuang.music.songtext.LyricLine
import com.zionhuang.music.songtext.SongText
import com.zionhuang.music.songtext.ChordSpan
import com.zionhuang.music.songtext.text
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun ChordsPage(
    songText: SongText?,
    hint: String?,
    listState: LazyListState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val sections = songText?.sections.orEmpty()
    if (sections.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
        ) {
            Text(
                text = hint ?: stringResource(R.string.chords_not_found),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
    ) {
        sections.forEach { section ->
            if (!section.tag.isNullOrBlank()) {
                item(key = "${section.tag}-header") {
                    Text(
                        text = section.tag,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    )
                }
            }
            items(section.lines, key = { it.raw }) { line ->
                ChordLyricLine(line = line)
            }
        }
        if (hint != null) {
            item {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun ChordLyricLine(line: LyricLine) {
    val lyricBaseStyle = MaterialTheme.typography.bodyLarge
    val lyricLineHeight = if (lyricBaseStyle.lineHeight.isUnspecified) {
        lyricBaseStyle.fontSize * 1.3f
    } else {
        lyricBaseStyle.lineHeight
    }
    val lyricStyle = lyricBaseStyle.copy(lineHeight = lyricLineHeight)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        if (line.chordSpans.isNotEmpty()) {
            ChordRow(spans = line.chordSpans)
        }
        val lyric = line.text.trimEnd()
        if (lyric.isNotEmpty()) {
            Text(
                text = lyric,
                style = lyricStyle,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(top = if (line.chordSpans.isNotEmpty()) 4.dp else 0.dp)
            )
        }
    }
}

@Composable
private fun ChordRow(spans: List<ChordSpan>, modifier: Modifier = Modifier) {
    if (spans.isEmpty()) return
    val textStyle = MaterialTheme.typography.labelLarge.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.tertiary
    )
    val textMeasurer = rememberTextMeasurer()
    val charWidthPx = remember(textStyle) {
        val width = textMeasurer.measure("M", style = textStyle).size.width
        if (width <= 0) 1f else width.toFloat()
    }
    Layout(
        content = {
            spans.forEach { span ->
                Text(text = span.label, style = textStyle)
            }
        },
        modifier = modifier.fillMaxWidth()
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        var requiredWidth = constraints.minWidth
        var requiredHeight = constraints.minHeight
        placeables.forEachIndexed { index, placeable ->
            val span = spans[index]
            val startPx = (span.startColumn * charWidthPx).roundToInt()
            requiredWidth = max(requiredWidth, startPx + placeable.width)
            requiredHeight = max(requiredHeight, placeable.height)
        }
        val layoutWidth = constraints.constrainWidth(requiredWidth)
        val layoutHeight = constraints.constrainHeight(requiredHeight)
        layout(layoutWidth, layoutHeight) {
            placeables.forEachIndexed { index, placeable ->
                val span = spans[index]
                val startPx = (span.startColumn * charWidthPx).roundToInt()
                val clampedX = startPx.coerceIn(0, layoutWidth - placeable.width)
                placeable.placeRelative(clampedX, 0)
            }
        }
    }
}
