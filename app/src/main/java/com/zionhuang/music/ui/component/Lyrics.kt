package com.zionhuang.music.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
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
import com.zionhuang.music.songtext.SongText
import com.zionhuang.music.songtext.SongTextParser
import com.zionhuang.music.songtext.chordLine
import com.zionhuang.music.songtext.text
import com.zionhuang.music.ui.menu.LyricsMenu
import com.zionhuang.music.ui.screens.settings.PlayerTextAlignment
import com.zionhuang.music.utils.rememberEnumPreference
import com.zionhuang.music.utils.rememberPreference
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

private enum class ViewMode { Lyrics, Chords }

const val animateScrollDuration = 300L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Lyrics(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val view = LocalView.current

    val snackbarHostState = remember { SnackbarHostState() }
    var showSpeedSheet by rememberSaveable { mutableStateOf(false) }
    var autoScrollSpeed by rememberSaveable { mutableStateOf(0.4f) }
    var autoScrollActive by rememberSaveable { mutableStateOf(false) }
    var autoScrollPausedByPress by remember { mutableStateOf(false) }
    var hasAnnouncedInitial by remember { mutableStateOf(false) }

    val showChordsState = rememberPreference(ShowChordsKey, false)
    val playerTextAlignment by rememberEnumPreference(PlayerTextAlignmentKey, PlayerTextAlignment.CENTER)
    var translationEnabled by rememberPreference(TranslateLyricsKey, false)

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val translating by playerConnection.translating.collectAsState()
    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    val chordsEntity by playerConnection.currentChords.collectAsState(initial = null)

    val lyrics = remember(lyricsEntity, translating) {
        if (translating) null else lyricsEntity?.lyrics
    }
    val lines = remember(lyrics) {
        when {
            lyrics == null || lyrics == LYRICS_NOT_FOUND -> emptyList()
            lyrics.startsWith("[") -> listOf(HEAD_LYRICS_ENTRY) + parseLyrics(lyrics)
            else -> lyrics.lines().mapIndexed { index, text -> LyricsEntry(index * 100L, text) }
        }
    }
    val isSynced = remember(lyrics) { !lyrics.isNullOrEmpty() && lyrics.startsWith("[") }

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
            val position = sliderPosition ?: playerConnection.player.currentPosition
            currentLineIndex = findCurrentLineIndex(lines, position)
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

    val chords = chordsEntity?.chords
    var songText by remember { mutableStateOf<SongText?>(null) }
    LaunchedEffect(chords, mediaMetadata) {
        val metadata = mediaMetadata
        val chordText = chords
        songText = if (metadata == null || chordText.isNullOrBlank() || chordText == CHORDS_NOT_FOUND) {
            null
        } else {
            withContext(Dispatchers.Default) {
                runCatching {
                    SongTextParser.parse(
                        id = metadata.id,
                        title = metadata.title,
                        artist = metadata.artists.joinToString(separator = ", ") { it.name }.takeIf { it.isNotBlank() },
                        raw = chordText,
                    )
                }.getOrNull()
            }
        }
    }
    val hasChordContent = remember(songText) {
        songText?.sections?.any { section -> section.lines.any { it.chordSpans.isNotEmpty() } } == true
    }
    val chordsNotFound = chords == CHORDS_NOT_FOUND
    val chordsLoading = chordsEntity == null && !chordsNotFound
    val lyricsAvailable = !lyrics.isNullOrBlank() && lyrics != LYRICS_NOT_FOUND

    LaunchedEffect(chordsNotFound) {
        if (chordsNotFound) {
            showChordsState.value = false
        }
    }
    LaunchedEffect(lyricsAvailable, hasChordContent) {
        if (!lyricsAvailable && hasChordContent) {
            showChordsState.value = true
        }
    }

    val viewMode = if (showChordsState.value) ViewMode.Chords else ViewMode.Lyrics

    LaunchedEffect(viewMode) {
        if (hasAnnouncedInitial) {
            val announcementRes = when (viewMode) {
                ViewMode.Lyrics -> R.string.lyrics_tab
                ViewMode.Chords -> R.string.chords_tab
            }
            view.announceForAccessibility(context.getString(announcementRes))
        } else {
            hasAnnouncedInitial = true
        }
    }
    val lyricsListState = rememberLazyListState()
    val chordsListState = rememberLazyListState()
    val chordsHasScrollableContent = remember(songText) {
        songText?.sections?.any { it.lines.isNotEmpty() } == true
    }
    val canAutoScroll = when (viewMode) {
        ViewMode.Lyrics -> lines.isNotEmpty()
        ViewMode.Chords -> chordsHasScrollableContent
    }

    LaunchedEffect(currentLineIndex, lastPreviewTime, viewMode, isSynced, autoScrollActive) {
        if (viewMode != ViewMode.Lyrics || !isSynced || autoScrollActive) return@LaunchedEffect
        if (currentLineIndex != -1 && lastPreviewTime == 0L) {
            deferredCurrentLineIndex = currentLineIndex
            val offset = with(density) { 36.dp.toPx().toInt() }
            if (isSeeking) {
                lyricsListState.scrollToItem(currentLineIndex, offset)
            } else {
                lyricsListState.animateScrollToItem(currentLineIndex, offset)
            }
        }
    }

    LaunchedEffect(canAutoScroll) {
        if (!canAutoScroll) {
            autoScrollActive = false
        }
    }

    var userScrollInterrupt by remember { mutableStateOf(false) }
    val pauseMessage = stringResource(R.string.auto_scroll_paused)
    val resumeLabel = stringResource(R.string.auto_scroll_resume)

    LaunchedEffect(userScrollInterrupt) {
        if (userScrollInterrupt) {
            autoScrollActive = false
            val result = snackbarHostState.showSnackbar(
                message = pauseMessage,
                actionLabel = resumeLabel,
            )
            if (result == SnackbarResult.ActionPerformed) {
                autoScrollActive = true
            }
            userScrollInterrupt = false
        }
    }
    LaunchedEffect(viewMode, autoScrollActive, autoScrollSpeed, autoScrollPausedByPress) {
        if (!autoScrollActive || autoScrollPausedByPress) return@LaunchedEffect
        val targetState = when (viewMode) {
            ViewMode.Lyrics -> lyricsListState
            ViewMode.Chords -> chordsListState
        }
        val baseSpeedPx = with(density) { 100.dp.toPx() }
        val pxPerSecond = baseSpeedPx * clamp01(autoScrollSpeed)
        if (pxPerSecond <= 0f) return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        while (true) {
            val frameTime = withFrameNanos { it }
            val deltaSeconds = (frameTime - previousFrame) / 1_000_000_000f
            previousFrame = frameTime
            val distance = (pxPerSecond * deltaSeconds).toFloat()
            if (distance <= 0f) continue
            val consumed = targetState.scrollByPixels(distance)
            if (consumed < distance) {
                autoScrollActive = false
                break
            }
        }
    }

    DisposableEffect(autoScrollActive) {
        if (!autoScrollActive) {
            autoScrollPausedByPress = false
        }
        onDispose { autoScrollPausedByPress = false }
    }

    DisposableEffect(autoScrollActive, view) {
        val previousKeepScreenOn = view.keepScreenOn
        if (autoScrollActive) {
            view.keepScreenOn = true
        }
        onDispose {
            view.keepScreenOn = previousKeepScreenOn
        }
    }

    val pointerModifier = if (autoScrollActive) {
        Modifier.pointerInput(autoScrollActive) {
            detectTapGestures(
                onPress = {
                    autoScrollPausedByPress = true
                    try {
                        tryAwaitRelease()
                    } finally {
                        autoScrollPausedByPress = false
                    }
                },
            )
        }
    } else {
        Modifier
    }

    val showTranslationToggle = BuildConfig.FLAVOR != "foss" && viewMode == ViewMode.Lyrics
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            PlayerTopBar(
                viewMode = viewMode,
                chordsEnabled = !chordsNotFound,
                lyricsEnabled = lyricsAvailable || lines.isNotEmpty(),
                translationEnabled = translationEnabled,
                showTranslationToggle = showTranslationToggle,
                onModeChange = { mode ->
                    when (mode) {
                        ViewMode.Lyrics -> showChordsState.value = false
                        ViewMode.Chords -> if (!chordsNotFound) showChordsState.value = true
                    }
                },
                onTranslationToggle = { translationEnabled = !translationEnabled },
                onMenuClick = {
                    mediaMetadata?.let { metadata ->
                        menuState.show {
                            LyricsMenu(
                                lyricsProvider = { lyricsEntity },
                                mediaMetadataProvider = { metadata },
                                onDismiss = menuState::dismiss,
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (canAutoScroll) {
                FloatingActionButton(onClick = { showSpeedSheet = true }) {
                    Icon(
                        painter = painterResource(id = R.drawable.speed),
                        contentDescription = stringResource(R.string.auto_scroll),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .then(pointerModifier),
        ) {
            when (viewMode) {
                ViewMode.Lyrics -> {
                    val displayedLineIndex = if (isSeeking) deferredCurrentLineIndex else currentLineIndex
                    LyricsContent(
                        lines = lines,
                        listState = lyricsListState,
                        currentLineIndex = displayedLineIndex,
                        lyrics = lyrics,
                        translating = translating,
                        isSynced = isSynced,
                        playerTextAlignment = playerTextAlignment,
                        showChordsHint = chordsNotFound || (songText != null && !hasChordContent),
                        onLineClick = { entry ->
                            playerConnection.player.seekTo(entry.time)
                            autoScrollActive = false
                            lastPreviewTime = 0L
                        },
                        onUserScroll = {
                            lastPreviewTime = System.currentTimeMillis()
                            if (autoScrollActive && !userScrollInterrupt) {
                                userScrollInterrupt = true
                            }
                        },
                        autoScrollActive = autoScrollActive,
                    )
                }

                ViewMode.Chords -> ChordsContent(
                    songText = songText,
                    listState = chordsListState,
                    chordsLoading = chordsLoading,
                    chordsNotFound = chordsNotFound,
                    hasChordContent = hasChordContent,
                    lyricsAvailable = lyricsAvailable,
                    onUserScroll = {
                        lastPreviewTime = System.currentTimeMillis()
                        if (autoScrollActive && !userScrollInterrupt) {
                            userScrollInterrupt = true
                        }
                    },
                )
            }
        }

        if (showSpeedSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSpeedSheet = false },
                sheetState = sheetState,
            ) {
                AutoScrollSpeedSheet(
                    autoScrollSpeed = autoScrollSpeed,
                    onSpeedChange = { autoScrollSpeed = clamp01(it) },
                    autoScrollActive = autoScrollActive,
                    onToggleAutoScroll = {
                        autoScrollActive = !autoScrollActive
                        if (!autoScrollActive) {
                            autoScrollPausedByPress = false
                        }
                    },
                )
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerTopBar(
    viewMode: ViewMode,
    chordsEnabled: Boolean,
    lyricsEnabled: Boolean,
    translationEnabled: Boolean,
    showTranslationToggle: Boolean,
    onModeChange: (ViewMode) -> Unit,
    onTranslationToggle: () -> Unit,
    onMenuClick: () -> Unit,
) {
    val segmentedColors = SegmentedButtonDefaults.colors(
        activeContainerColor = Color.Transparent,
        activeContentColor = MaterialTheme.colorScheme.primary,
        activeBorderColor = MaterialTheme.colorScheme.primary,
        inactiveContainerColor = Color.Transparent,
        inactiveContentColor = MaterialTheme.colorScheme.secondary,
        inactiveBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
    )
    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        title = {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                SegmentedButton(
                    selected = viewMode == ViewMode.Lyrics,
                    onClick = { onModeChange(ViewMode.Lyrics) },
                    enabled = lyricsEnabled,
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    colors = segmentedColors,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.lyrics_tab))
                }
                SegmentedButton(
                    selected = viewMode == ViewMode.Chords,
                    onClick = { onModeChange(ViewMode.Chords) },
                    enabled = chordsEnabled,
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    colors = segmentedColors,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.chords_tab))
                }
            }
        },
        actions = {
            if (showTranslationToggle) {
                IconButton(onClick = onTranslationToggle) {
                    Icon(
                        painter = painterResource(id = R.drawable.translate),
                        contentDescription = null,
                        tint = LocalContentColor.current.copy(alpha = if (translationEnabled) 1f else 0.3f),
                    )
                }
            }
            IconButton(onClick = onMenuClick) {
                Icon(
                    painter = painterResource(id = R.drawable.more_horiz),
                    contentDescription = null,
                )
            }
        },
    )
}

@Composable
private fun LyricsContent(
    lines: List<LyricsEntry>,
    listState: LazyListState,
    currentLineIndex: Int,
    lyrics: String?,
    translating: Boolean,
    isSynced: Boolean,
    playerTextAlignment: PlayerTextAlignment,
    showChordsHint: Boolean,
    onLineClick: (LyricsEntry) -> Unit,
    onUserScroll: () -> Unit,
    autoScrollActive: Boolean,
) {
    val textAlign = when (playerTextAlignment) {
        PlayerTextAlignment.SIDED -> TextAlign.Start
        PlayerTextAlignment.CENTER -> TextAlign.Center
    }
    val bodyStyle = minLineHeightStyle(MaterialTheme.typography.bodyLarge)

    when {
        translating || lyrics == null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        lyrics == LYRICS_NOT_FOUND -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.lyrics_not_found),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = textAlign,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }

        else -> {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
            ) {
                val halfHeight = maxHeight / 2
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = halfHeight, bottom = halfHeight),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(remember(autoScrollActive) {
                            object : NestedScrollConnection {
                                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                    if (source == NestedScrollSource.UserInput) {
                                        onUserScroll()
                                    }
                                    return Offset.Zero
                                }

                                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                                    if (consumed != Velocity.Zero || available != Velocity.Zero) {
                                        onUserScroll()
                                    }
                                    return Velocity.Zero
                                }
                            }
                        }),
                ) {
                    if (showChordsHint) {
                        item {
                            Text(
                                text = stringResource(R.string.chords_unavailable_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 8.dp),
                            )
                        }
                    }
                    itemsIndexed(lines) { index, entry ->
                        Text(
                            text = entry.text,
                            style = bodyStyle,
                            color = if (index == currentLineIndex) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            },
                            textAlign = textAlign,
                            fontWeight = if (index == currentLineIndex) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                                .clickable(enabled = isSynced) { onLineClick(entry) }
                                .alpha(
                                    if (!isSynced || index == currentLineIndex) {
                                        1f
                                    } else {
                                        0.5f
                                    }
                                ),
                        )
                    }
                }
            }
        }
    }
}
@Composable
private fun ChordsContent(
    songText: SongText?,
    listState: LazyListState,
    chordsLoading: Boolean,
    chordsNotFound: Boolean,
    hasChordContent: Boolean,
    lyricsAvailable: Boolean,
    onUserScroll: () -> Unit,
) {
    when {
        chordsLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        chordsNotFound -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.chords_not_found),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }

        songText == null || !hasChordContent -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.chords_unavailable_hint),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }

        else -> {
            val lyricStyle = minLineHeightStyle(MaterialTheme.typography.bodyLarge)
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(remember {
                        object : NestedScrollConnection {
                            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                if (source == NestedScrollSource.UserInput) {
                                    onUserScroll()
                                }
                                return Offset.Zero
                            }

                            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                                if (consumed != Velocity.Zero || available != Velocity.Zero) {
                                    onUserScroll()
                                }
                                return Velocity.Zero
                            }
                        }
                    }),
            ) {
                if (!lyricsAvailable) {
                    item {
                        Text(
                            text = stringResource(R.string.lyrics_unavailable_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                    }
                }
                songText.sections.forEachIndexed { sectionIndex, section ->
                    if (!section.tag.isNullOrBlank()) {
                        item(key = "section-$sectionIndex") {
                            Text(
                                text = section.tag!!,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 4.dp),
                            )
                        }
                    }
                    itemsIndexed(section.lines, key = { index, _ -> "$sectionIndex-$index" }) { _, line ->
                        val chordText = remember(line.chordSpans) { line.chordLine() }
                        val lyricText = remember(line.tokens) { line.text }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 6.dp),
                        ) {
                            if (chordText.isNotEmpty()) {
                                Text(
                                    text = chordText,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (lyricText.isNotEmpty()) {
                                Text(
                                    text = lyricText,
                                    style = lyricStyle,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(top = if (chordText.isNotEmpty()) 6.dp else 0.dp),
                                )
                            } else if (chordText.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoScrollSpeedSheet(
    autoScrollSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    autoScrollActive: Boolean,
    onToggleAutoScroll: () -> Unit,
) {
    val clampedSpeed = clamp01(autoScrollSpeed)
    val percent = (clampedSpeed * 100f).roundToInt()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.auto_scroll),
            style = MaterialTheme.typography.titleLarge,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.auto_scroll_speed),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.auto_scroll_speed_value, percent),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Slider(
                value = clampedSpeed,
                onValueChange = { onSpeedChange(clamp01(it)) },
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(16.dp))
            val iconRes = if (autoScrollActive) R.drawable.pause else R.drawable.play
            val description = if (autoScrollActive) R.string.auto_scroll_pause else R.string.auto_scroll_play
            FilledTonalIconButton(onClick = onToggleAutoScroll) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = stringResource(description),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            AutoScrollPresetChip(
                label = stringResource(R.string.auto_scroll_preset_slow),
                value = 0.2f,
                onClick = onSpeedChange,
            )
            AutoScrollPresetChip(
                label = stringResource(R.string.auto_scroll_preset_medium),
                value = 0.4f,
                onClick = onSpeedChange,
            )
            AutoScrollPresetChip(
                label = stringResource(R.string.auto_scroll_preset_fast),
                value = 0.7f,
                onClick = onSpeedChange,
            )
        }
        Text(
            text = stringResource(R.string.auto_scroll_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun AutoScrollPresetChip(
    label: String,
    value: Float,
    onClick: (Float) -> Unit,
) {
    AssistChip(
        onClick = { onClick(clamp01(value)) },
        label = { Text(text = label) },
    )
}

private suspend fun LazyListState.scrollByPixels(distance: Float): Float {
    if (distance == 0f) return 0f
    return scrollBy(distance)
}

private fun clamp01(value: Float): Float = max(0f, min(1f, value))

private fun minLineHeightStyle(style: TextStyle): TextStyle {
    val fontSize = if (style.fontSize != TextUnit.Unspecified) style.fontSize else 16.sp
    val desired = (fontSize.value * 1.3f).sp
    val current = style.lineHeight
    val lineHeight = if (current == TextUnit.Unspecified || current.value < desired.value) desired else current
    return style.copy(lineHeight = lineHeight)
}

val LyricsPreviewTime = 4.seconds
