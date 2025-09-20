package com.zionhuang.music.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pointerInput
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
import com.zionhuang.music.BuildConfig
import com.zionhuang.music.LocalPlayerConnection
import com.zionhuang.music.R
import com.zionhuang.music.chords.ChordSheetParser
import com.zionhuang.music.chords.LyricLine
import com.zionhuang.music.chords.SongText
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withFrameNanos
import kotlin.math.roundToInt
import kotlin.text.StringBuilder
import kotlin.time.Duration.Companion.seconds

private enum class PlayerTextViewMode { Lyrics, Chords }

private data class SpeedPreset(val labelRes: Int, val value: Float)

private val AutoScrollBaseSpeedDp = 100.dp
private const val AutoScrollDefaultPercent = 40f
private val AutoScrollSpeedPresets = listOf(
    SpeedPreset(R.string.auto_scroll_preset_slow, 25f),
    SpeedPreset(R.string.auto_scroll_preset_medium, 40f),
    SpeedPreset(R.string.auto_scroll_preset_fast, 60f),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Lyrics(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val density = LocalDensity.current
    val view = LocalView.current

    val playerTextAlignment by rememberEnumPreference(PlayerTextAlignmentKey, PlayerTextAlignment.CENTER)
    var translationEnabled by rememberPreference(TranslateLyricsKey, false)

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val translating by playerConnection.translating.collectAsState()
    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    val chordsEntity by playerConnection.currentChords.collectAsState(initial = null)
    var showChordsPreference by rememberPreference(ShowChordsKey, false)

    val chordsText = chordsEntity?.chords
    val lyrics = remember(lyricsEntity, translating) {
        if (translating) null else lyricsEntity?.lyrics
    }

    val lines = remember(lyrics) {
        when {
            lyrics.isNullOrEmpty() -> emptyList()
            lyrics == LYRICS_NOT_FOUND -> emptyList()
            lyrics.startsWith("[") -> listOf(HEAD_LYRICS_ENTRY) + parseLyrics(lyrics)
            else -> lyrics.lines().mapIndexed { index, line -> LyricsEntry(index * 100L, line) }
        }
    }
    val isSynced = remember(lyrics) {
        !lyrics.isNullOrEmpty() && lyrics.startsWith("[")
    }

    var currentLineIndex by remember { mutableIntStateOf(-1) }
    var deferredCurrentLineIndex by rememberSaveable { mutableIntStateOf(0) }
    var lastPreviewTime by rememberSaveable { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }

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

    val chordSongText by produceState<SongText?>(initialValue = null, key1 = mediaMetadata?.id, key2 = chordsText) {
        val metadata = mediaMetadata
        val chords = chordsText
        value = if (metadata != null && chords != null && chords != CHORDS_NOT_FOUND) {
            withContext(Dispatchers.Default) {
                val artist = metadata.artists.joinToString(" / ") { it.name }.takeIf { it.isNotBlank() }
                ChordSheetParser.parse(
                    id = metadata.id,
                    title = metadata.title,
                    artist = artist,
                    source = chords,
                )
            }
        } else {
            null
        }
    }

    val hasChordContent = chordSongText?.sections?.any { section -> section.lines.isNotEmpty() } == true
    val hasLyricsContent = !lyrics.isNullOrEmpty() && lyrics != LYRICS_NOT_FOUND && lines.isNotEmpty()

    var viewMode by rememberSaveable {
        mutableStateOf(
            when {
                showChordsPreference && hasChordContent -> PlayerTextViewMode.Chords
                else -> PlayerTextViewMode.Lyrics
            }
        )
    }

    LaunchedEffect(showChordsPreference, hasChordContent, hasLyricsContent) {
        viewMode = when {
            showChordsPreference && hasChordContent -> PlayerTextViewMode.Chords
            !hasLyricsContent && hasChordContent -> PlayerTextViewMode.Chords
            else -> PlayerTextViewMode.Lyrics
        }
    }

    LaunchedEffect(viewMode) {
        showChordsPreference = viewMode == PlayerTextViewMode.Chords
        val announcement = when (viewMode) {
            PlayerTextViewMode.Lyrics -> stringResource(R.string.lyrics_tab)
            PlayerTextViewMode.Chords -> stringResource(R.string.chords_tab)
        }
        view.announceForAccessibility(announcement)
    }

    LaunchedEffect(currentLineIndex, lastPreviewTime, viewMode) {
        if (viewMode != PlayerTextViewMode.Lyrics || !isSynced) return@LaunchedEffect
        if (currentLineIndex != -1) {
            deferredCurrentLineIndex = currentLineIndex
            val offset = with(density) { 36.dp.toPx().toInt() }
            if (lastPreviewTime == 0L && isSeeking) {
                lyricsListState.scrollToItem(currentLineIndex, offset)
            } else if (lastPreviewTime == 0L) {
                lyricsListState.animateScrollToItem(currentLineIndex, offset)
            }
        }
    }

    var autoScrollEnabled by rememberSaveable { mutableStateOf(false) }
    var autoScrollPausedByHold by remember { mutableStateOf(false) }
    var autoScrollDriving by remember { mutableStateOf(false) }
    var autoScrollSpeedPercent by rememberSaveable { mutableFloatStateOf(AutoScrollDefaultPercent) }
    var showSpeedSheet by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val pressToPauseModifier = remember(autoScrollEnabled) {
        if (!autoScrollEnabled) {
            Modifier
        } else {
            Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    if (!autoScrollEnabled) return@awaitEachGesture
                    autoScrollPausedByHold = true
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        autoScrollPausedByHold = false
                    }
                }
            }
        }
    }

    val isAutoScrollRunning = autoScrollEnabled && !autoScrollPausedByHold && autoScrollSpeedPercent > 0f

    LaunchedEffect(viewMode, isAutoScrollRunning, autoScrollSpeedPercent) {
        if (!isAutoScrollRunning) return@LaunchedEffect
        val listState = when (viewMode) {
            PlayerTextViewMode.Lyrics -> lyricsListState
            PlayerTextViewMode.Chords -> chordsListState
        }
        val basePxPerSecond = with(density) { AutoScrollBaseSpeedDp.toPx() }
        val pxPerSecond = basePxPerSecond * (autoScrollSpeedPercent / 100f)
        if (pxPerSecond <= 0f) return@LaunchedEffect
        var lastFrameTime = 0L
        while (isActive && autoScrollEnabled && !autoScrollPausedByHold) {
            val frameTime = withFrameNanos { it }
            if (lastFrameTime != 0L) {
                val deltaSeconds = (frameTime - lastFrameTime) / 1_000_000_000f
                val distance = pxPerSecond * deltaSeconds
                if (distance > 0f) {
                    autoScrollDriving = true
                    val remainder = listState.scrollBy(distance)
                    autoScrollDriving = false
                    if (remainder != 0f) {
                        autoScrollEnabled = false
                        break
                    }
                }
            }
            lastFrameTime = frameTime
        }
        autoScrollDriving = false
    }

    LaunchedEffect(viewMode, autoScrollEnabled) {
        val listState = when (viewMode) {
            PlayerTextViewMode.Lyrics -> lyricsListState
            PlayerTextViewMode.Chords -> chordsListState
        }
        snapshotFlow { listState.isScrollInProgress }
            .collectLatest { inProgress ->
                if (inProgress && autoScrollEnabled && !autoScrollDriving) {
                    autoScrollEnabled = false
                    autoScrollPausedByHold = false
                    val result = snackbarHostState.showSnackbar(
                        message = stringResource(R.string.auto_scroll_paused),
                        actionLabel = stringResource(R.string.auto_scroll_resume),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        autoScrollEnabled = true
                    }
                }
            }
    }

    val contentPadding = rememberBoxPadding()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        SegmentedButton(
                            selected = viewMode == PlayerTextViewMode.Lyrics,
                            onClick = { viewMode = PlayerTextViewMode.Lyrics },
                            enabled = hasLyricsContent || lyrics == null || lyrics == LYRICS_NOT_FOUND,
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            label = { Text(text = stringResource(R.string.lyrics_tab)) },
                        )
                        SegmentedButton(
                            selected = viewMode == PlayerTextViewMode.Chords,
                            onClick = { viewMode = PlayerTextViewMode.Chords },
                            enabled = hasChordContent || chordsText == null || chordsText == CHORDS_NOT_FOUND,
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            label = { Text(text = stringResource(R.string.chords_tab)) },
                        )
                    }
                },
                actions = {
                    if (BuildConfig.FLAVOR != "foss" && viewMode == PlayerTextViewMode.Lyrics) {
                        IconButton(onClick = { translationEnabled = !translationEnabled }) {
                            Icon(
                                painter = painterResource(id = R.drawable.translate),
                                contentDescription = stringResource(R.string.toggle_translation),
                                tint = LocalContentColor.current.copy(alpha = if (translationEnabled) 1f else 0.3f),
                            )
                        }
                    }
                    mediaMetadata?.let { metadata ->
                        IconButton(
                            onClick = {
                                menuState.show {
                                    LyricsMenu(
                                        lyricsProvider = { lyricsEntity },
                                        mediaMetadataProvider = { metadata },
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.more_horiz),
                                contentDescription = stringResource(R.string.lyrics_more_options),
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showSpeedSheet = true },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.speed),
                    contentDescription = stringResource(R.string.auto_scroll_speed),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val halfHeight = maxHeight / 2
            val verticalPadding = WindowInsets.systemBars
                .only(WindowInsetsSides.Top)
                .add(WindowInsets(top = halfHeight, bottom = halfHeight))
                .asPaddingValues()

            when (viewMode) {
                PlayerTextViewMode.Lyrics -> {
                    LyricsContent(
                        lines = lines,
                        lyrics = lyrics,
                        translating = translating,
                        isSynced = isSynced,
                        listState = lyricsListState,
                        pressModifier = pressToPauseModifier,
                        currentLineIndex = if (isSeeking) deferredCurrentLineIndex else currentLineIndex,
                        onLineClick = { time ->
                            playerConnection.player.seekTo(time)
                            lastPreviewTime = 0L
                        },
                        alignment = playerTextAlignment,
                        verticalPadding = verticalPadding,
                        hasChords = hasChordContent,
                        contentPadding = contentPadding,
                        onUserScroll = {
                            lastPreviewTime = System.currentTimeMillis()
                        },
                    )
                    if (lyrics == LYRICS_NOT_FOUND) {
                        MissingContentMessage(
                            text = stringResource(R.string.lyrics_not_found),
                            alignment = playerTextAlignment,
                        )
                    } else if (!hasChordContent && chordsText == CHORDS_NOT_FOUND) {
                        MissingContentMessage(
                            text = stringResource(R.string.chords_hint_unavailable),
                            alignment = playerTextAlignment,
                            alpha = 0.7f,
                        )
                    }
                }
                PlayerTextViewMode.Chords -> {
                    when {
                        chordsEntity == null -> {
                            LoadingChords()
                        }
                        chordsText == CHORDS_NOT_FOUND -> {
                            MissingContentMessage(
                                text = stringResource(R.string.chords_not_found),
                                alignment = playerTextAlignment,
                            )
                        }
                        hasChordContent -> {
                            ChordsContent(
                                songText = chordSongText,
                                listState = chordsListState,
                                pressModifier = pressToPauseModifier,
                                verticalPadding = verticalPadding,
                                contentPadding = contentPadding,
                                lyricAlignment = playerTextAlignment,
                                showLyricsHint = !hasLyricsContent,
                            )
                        }
                        else -> {
                            MissingContentMessage(
                                text = stringResource(R.string.chords_hint_unavailable),
                                alignment = playerTextAlignment,
                            )
                        }
                    }
                }
            }
        }

        if (showSpeedSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showSpeedSheet = false },
                sheetState = sheetState,
            ) {
                SpeedControlSheet(
                    autoScrollEnabled = autoScrollEnabled,
                    onToggle = {
                        autoScrollEnabled = !autoScrollEnabled
                        if (autoScrollEnabled) {
                            autoScrollPausedByHold = false
                        }
                    },
                    speedPercent = autoScrollSpeedPercent,
                    onSpeedChange = { autoScrollSpeedPercent = it },
                )
            }
        }
    }
}
@Composable
private fun rememberBoxPadding(): PaddingValues = PaddingValues(horizontal = 16.dp)

@Composable
private fun LyricsContent(
    lines: List<LyricsEntry>,
    lyrics: String?,
    translating: Boolean,
    isSynced: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    pressModifier: Modifier,
    currentLineIndex: Int,
    onLineClick: (Long) -> Unit,
    alignment: PlayerTextAlignment,
    verticalPadding: PaddingValues,
    hasChords: Boolean,
    contentPadding: PaddingValues,
    onUserScroll: () -> Unit,
) {
    val displayedAlignment = when (alignment) {
        PlayerTextAlignment.SIDED -> TextAlign.Start
        PlayerTextAlignment.CENTER -> TextAlign.Center
    }
    val lyricStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 1.3f * MaterialTheme.typography.bodyLarge.fontSize)
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            top = verticalPadding.calculateTopPadding(),
            bottom = verticalPadding.calculateBottomPadding(),
        ),
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .then(pressModifier)
            .fadingEdge(vertical = 64.dp)
            .nestedScroll(remember {
                object : NestedScrollConnection {
                    override fun onPostScroll(consumed: androidx.compose.ui.unit.IntOffset, available: androidx.compose.ui.unit.IntOffset, source: NestedScrollSource): androidx.compose.ui.unit.IntOffset {
                        onUserScroll()
                        return super.onPostScroll(consumed, available, source)
                    }

                    override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                        onUserScroll()
                        return super.onPostFling(consumed, available)
                    }
                }
            }),
    ) {
        if (lyrics == null || translating) {
            item {
                ShimmerHost {
                    repeat(10) {
                        Box(
                            contentAlignment = when (alignment) {
                                PlayerTextAlignment.SIDED -> Alignment.CenterStart
                                PlayerTextAlignment.CENTER -> Alignment.Center
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        ) {
                            TextPlaceholder()
                        }
                    }
                }
            }
        } else {
            if (!hasChords) {
                item {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(stringResource(R.string.chords_hint_unavailable)) },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .fillMaxWidth(),
                    )
                }
            }
            itemsIndexed(lines) { index, item ->
                Text(
                    text = item.text,
                    style = lyricStyle,
                    color = if (index == currentLineIndex && isSynced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    textAlign = displayedAlignment,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (!isSynced || index == currentLineIndex) 1f else 0.5f)
                        .padding(vertical = 8.dp)
                        .clickable(enabled = isSynced) { onLineClick(item.time) },
                )
            }
        }
    }
}

@Composable
private fun MissingContentMessage(
    text: String,
    alignment: PlayerTextAlignment,
    alpha: Float = 1f,
) {
    val textAlign = when (alignment) {
        PlayerTextAlignment.SIDED -> TextAlign.Start
        PlayerTextAlignment.CENTER -> TextAlign.Center
    }
    Text(
        text = text,
        fontSize = 20.sp,
        color = MaterialTheme.colorScheme.secondary,
        textAlign = textAlign,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(alpha),
    )
}

@Composable
private fun LoadingChords() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.chords_loading),
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun ChordsContent(
    songText: SongText?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    pressModifier: Modifier,
    verticalPadding: PaddingValues,
    contentPadding: PaddingValues,
    lyricAlignment: PlayerTextAlignment,
    showLyricsHint: Boolean,
) {
    val lyricTextAlign = when (lyricAlignment) {
        PlayerTextAlignment.SIDED -> TextAlign.Start
        PlayerTextAlignment.CENTER -> TextAlign.Center
    }
    val lyricStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 1.3f * MaterialTheme.typography.bodyLarge.fontSize)
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            top = verticalPadding.calculateTopPadding(),
            bottom = verticalPadding.calculateBottomPadding(),
        ),
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .then(pressModifier)
            .fadingEdge(vertical = 64.dp),
    ) {
        if (songText != null) {
            songText.sections.forEachIndexed { sectionIndex, section ->
                if (!section.tag.isNullOrBlank()) {
                    item("section-${sectionIndex}-header") {
                        Text(
                            text = section.tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 4.dp),
                        )
                    }
                }
                items(section.lines) { line ->
                    ChordLine(
                        line = line,
                        lyricStyle = lyricStyle,
                        lyricTextAlign = lyricTextAlign,
                    )
                }
            }
        }
        if (showLyricsHint) {
            item("lyrics-hint") {
                MissingContentMessage(
                    text = stringResource(R.string.lyrics_hint_unavailable),
                    alignment = lyricAlignment,
                    alpha = 0.7f,
                )
            }
        }
    }
}

@Composable
private fun ChordLine(
    line: LyricLine,
    lyricStyle: androidx.compose.ui.text.TextStyle,
    lyricTextAlign: TextAlign,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        val chordRow = remember(line.chordSpans) { buildChordRow(line) }
        if (line.chordSpans.isNotEmpty()) {
            Text(
                text = chordRow,
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            )
        }
        Text(
            text = line.lyricText,
            style = lyricStyle,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = lyricTextAlign,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (line.hasChords) 4.dp else 0.dp),
        )
    }
}

@Composable
private fun SpeedControlSheet(
    autoScrollEnabled: Boolean,
    onToggle: () -> Unit,
    speedPercent: Float,
    onSpeedChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.auto_scroll_speed),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconButton(onClick = onToggle) {
                Icon(
                    painter = painterResource(id = if (autoScrollEnabled) R.drawable.pause else R.drawable.play),
                    contentDescription = stringResource(if (autoScrollEnabled) R.string.pause_auto_scroll else R.string.play_auto_scroll),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Slider(
                    value = speedPercent,
                    onValueChange = onSpeedChange,
                    valueRange = 0f..100f,
                )
                Text(
                    text = stringResource(R.string.auto_scroll_speed_value, speedPercent.roundToInt()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AutoScrollSpeedPresets.forEach { preset ->
                AssistChip(
                    onClick = { onSpeedChange(preset.value) },
                    label = { Text(stringResource(preset.labelRes)) },
                    shape = MaterialTheme.shapes.small,
                )
            }
        }
    }
}

private fun buildChordRow(line: LyricLine): String {
    if (line.chordSpans.isEmpty()) return ""
    val builder = StringBuilder()
    var currentColumn = 0
    line.chordSpans.forEach { span ->
        val spaces = (span.startColumn - currentColumn).coerceAtLeast(0)
        repeat(spaces) { builder.append(' ') }
        builder.append(span.label)
        currentColumn = span.startColumn + span.label.length
    }
    return builder.toString().trimEnd()
}

const val animateScrollDuration = 300L
val LyricsPreviewTime = 4.seconds
