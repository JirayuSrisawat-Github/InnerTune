package com.zionhuang.music.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zionhuang.music.BuildConfig
import com.zionhuang.music.LocalPlayerConnection
import com.zionhuang.music.R
import com.zionhuang.music.constants.PlayerTextAlignmentKey
import com.zionhuang.music.constants.ShowChordsKey
import com.zionhuang.music.constants.TranslateLyricsKey
import com.zionhuang.music.db.entities.ChordsEntity.Companion.CHORDS_NOT_FOUND
import com.zionhuang.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.zionhuang.music.lyrics.LyricsEntry
import com.zionhuang.music.lyrics.LyricsEntry.Companion.HEAD_LYRICS_ENTRY
import com.zionhuang.music.lyrics.LyricsUtils.findCurrentLineIndex
import com.zionhuang.music.lyrics.LyricsUtils.parseLyrics
import com.zionhuang.music.ui.component.shimmer.ShimmerHost
import com.zionhuang.music.ui.component.shimmer.TextPlaceholder
import com.zionhuang.music.ui.menu.LyricsMenu
import com.zionhuang.music.ui.screens.settings.PlayerTextAlignment
import com.zionhuang.music.ui.utils.fadingEdge
import com.zionhuang.music.utils.rememberEnumPreference
import com.zionhuang.music.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.seconds
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import com.zionhuang.music.chords.ChordLineParser
import com.zionhuang.music.chords.ChordLine

/**
 * Displays a line of lyrics with guitar chords positioned above as floating chips.
 *
 * Uses text measurement to calculate precise horizontal positions for chord chips,
 * ensuring proper alignment with the corresponding lyrics text.
 *
 * @param chordLine Parsed chord line containing display text and chord placements
 * @param modifier Optional modifier for the composable
 */
@Composable
private fun ChordLineDisplay(
    chordLine: ChordLine,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Detect if this is an instrumental section or just separators (pipes/colons)
    val trimmedText = chordLine.displayText.trim()
    val isInstrumental = trimmedText.startsWith("INSTRU", ignoreCase = true) ||
                         (trimmedText.all { it in ":||-" } && chordLine.placements.isNotEmpty())

    val textStyle = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal
    )

    // Calculate horizontal positions for each chord based on character index
    val chordPositions = remember(chordLine) {
        chordLine.placements.map { placement ->
            val textBeforeChord = chordLine.displayText.substring(0, placement.index)
            val width = textMeasurer.measure(
                text = textBeforeChord,
                style = textStyle
            ).size.width

            with(density) { width.toDp() } to placement.chord
        }
    }

    if (isInstrumental) {
        // Special styling for instrumental sections
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "INSTRU",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.alpha(0.7f)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.alpha(0.9f)
            ) {
                chordLine.placements.forEach { placement ->
                    ChordChip(chord = placement.chord)
                }
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            // Chord chips positioned above
            if (chordPositions.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .padding(top = 2.dp)
                ) {
                    chordPositions.forEach { (offsetDp, chord) ->
                        ChordChip(
                            chord = chord,
                            modifier = Modifier.offset(x = offsetDp)
                        )
                    }
                }
            }

            // Lyric text below
            Text(
                text = chordLine.displayText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
fun Lyrics(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val density = LocalDensity.current

    val playerTextAlignment by rememberEnumPreference(PlayerTextAlignmentKey, PlayerTextAlignment.CENTER)
    var translationEnabled by rememberPreference(TranslateLyricsKey, false)

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val translating by playerConnection.translating.collectAsState()
    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    val chordsEntity by playerConnection.currentChords.collectAsState(initial = null)
    var showChords by rememberPreference(ShowChordsKey, false)
    val chords = chordsEntity?.chords
    val lyrics = remember(lyricsEntity, translating) {
        if (translating) null
        else lyricsEntity?.lyrics
    }

    val lines = remember(lyrics) {
        if (lyrics == null || lyrics == LYRICS_NOT_FOUND) emptyList()
        else if (lyrics.startsWith("[")) listOf(HEAD_LYRICS_ENTRY) + parseLyrics(lyrics)
        else lyrics.lines().mapIndexed { index, line -> LyricsEntry(index * 100L, line) }
    }
    val isSynced = remember(lyrics) {
        !lyrics.isNullOrEmpty() && lyrics.startsWith("[")
    }

    var currentLineIndex by remember {
        mutableIntStateOf(-1)
    }
    // Because LaunchedEffect has delay, which leads to inconsistent with current line color and scroll animation,
    // we use deferredCurrentLineIndex when user is scrolling
    var deferredCurrentLineIndex by rememberSaveable {
        mutableIntStateOf(0)
    }

    var lastPreviewTime by rememberSaveable {
        mutableLongStateOf(0L)
    }
    var isSeeking by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(lyrics) {
        if (lyrics.isNullOrEmpty() || !lyrics.startsWith("[")) {
            currentLineIndex = -1
            return@LaunchedEffect
        }
        while (isActive) {
            delay(50)
            val sliderPosition = sliderPositionProvider()
            isSeeking = sliderPosition != null
            currentLineIndex = findCurrentLineIndex(lines, sliderPosition ?: playerConnection.player.currentPosition)
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

    val lyricsListState = rememberLazyListState()
    val chordsListState = rememberLazyListState()

    LaunchedEffect(currentLineIndex, lastPreviewTime, showChords) {
        if (showChords || !isSynced) return@LaunchedEffect
        if (currentLineIndex != -1) {
            deferredCurrentLineIndex = currentLineIndex
            if (lastPreviewTime == 0L) {
                if (isSeeking) {
                    lyricsListState.scrollToItem(currentLineIndex, with(density) { 36.dp.toPx().toInt() })
                } else {
                    lyricsListState.animateScrollToItem(currentLineIndex, with(density) { 36.dp.toPx().toInt() })
                }
            }
        }
    }


BoxWithConstraints(
    contentAlignment = Alignment.Center,
    modifier = modifier
        .fillMaxSize()
        .padding(bottom = 12.dp)
) {
    val halfHeight = maxHeight / 2

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TabRow(
            selectedTabIndex = if (showChords) 1 else 0,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = !showChords,
                onClick = { showChords = false },
                text = { Text(stringResource(R.string.lyrics_tab)) }
            )
            Tab(
                selected = showChords,
                onClick = { showChords = true },
                text = { Text(stringResource(R.string.chords_tab)) }
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (showChords) {
                when {
                    chordsEntity == null -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = stringResource(R.string.chords_loading),
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                    }
                    chords == CHORDS_NOT_FOUND -> {
                        Text(
                            text = stringResource(R.string.chords_not_found),
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }
                    else -> {
                        val chordLines = remember(chords) {
                            chords?.let { ChordLineParser.parseAll(it) } ?: emptyList()
                        }
                        LazyColumn(
                            state = chordsListState,
                            contentPadding = WindowInsets.systemBars
                                .only(WindowInsetsSides.Top)
                                .add(WindowInsets(top = halfHeight, bottom = halfHeight))
                                .asPaddingValues(),
                            modifier = Modifier
                                .fadingEdge(vertical = 64.dp)
                        ) {
                            itemsIndexed(chordLines) { _, chordLine ->
                                ChordLineDisplay(
                                    chordLine = chordLine,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = lyricsListState,
                    contentPadding = WindowInsets.systemBars
                        .only(WindowInsetsSides.Top)
                        .add(WindowInsets(top = halfHeight, bottom = halfHeight))
                        .asPaddingValues(),
                    modifier = Modifier
                        .fadingEdge(vertical = 64.dp)
                        .nestedScroll(remember {
                            object : NestedScrollConnection {
                                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                                    lastPreviewTime = System.currentTimeMillis()
                                    return super.onPostScroll(consumed, available, source)
                                }

                                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                                    lastPreviewTime = System.currentTimeMillis()
                                    return super.onPostFling(consumed, available)
                                }
                            }
                        })
                ) {
                    val displayedCurrentLineIndex = if (isSeeking) deferredCurrentLineIndex else currentLineIndex

                    if (lyrics == null || translating) {
                        item {
                            ShimmerHost {
                                repeat(10) {
                                    Box(
                                        contentAlignment = when (playerTextAlignment) {
                                            PlayerTextAlignment.SIDED -> Alignment.CenterStart
                                            PlayerTextAlignment.CENTER -> Alignment.Center
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 4.dp)
                                    ) {
                                        TextPlaceholder()
                                    }
                                }
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = lines
                        ) { index, item ->
                            Text(
                                text = item.text,
                                fontSize = 20.sp,
                                color = if (index == displayedCurrentLineIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                textAlign = when (playerTextAlignment) {
                                    PlayerTextAlignment.SIDED -> TextAlign.Start
                                    PlayerTextAlignment.CENTER -> TextAlign.Center
                                },
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = isSynced) {
                                        playerConnection.player.seekTo(item.time)
                                        lastPreviewTime = 0L
                                    }
                                    .padding(horizontal = 24.dp, vertical = 8.dp)
                                    .alpha(if (!isSynced || index == displayedCurrentLineIndex) 1f else 0.5f)
                            )
                        }
                    }
                }

                if (lyrics == LYRICS_NOT_FOUND) {
                    Text(
                        text = stringResource(R.string.lyrics_not_found),
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = when (playerTextAlignment) {
                            PlayerTextAlignment.SIDED -> TextAlign.Start
                            PlayerTextAlignment.CENTER -> TextAlign.Center
                        },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                            .alpha(0.5f)
                    )
                }
            }
        }
    }
        mediaMetadata?.let { mediaMetadata ->
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp)
            ) {
                if (BuildConfig.FLAVOR != "foss" && !showChords) {
                    IconButton(
                        onClick = {
                            translationEnabled = !translationEnabled
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.translate),
                            contentDescription = null,
                            tint = LocalContentColor.current.copy(alpha = if (translationEnabled) 1f else 0.3f)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        menuState.show {
                            LyricsMenu(
                                lyricsProvider = { lyricsEntity },
                                mediaMetadataProvider = { mediaMetadata },
                                onDismiss = menuState::dismiss
                            )
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.more_horiz),
                        contentDescription = null
                    )
                }
            }
        }
    }
}

const val animateScrollDuration = 300L
val LyricsPreviewTime = 4.seconds
