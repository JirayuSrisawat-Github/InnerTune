package com.zionhuang.music.ui.player.text

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameNanos

class AutoScrollController(
    private val listState: LazyListState,
    private val coroutineScope: CoroutineScope
) {
    private var scrollJob: Job? = null
    @Volatile
    private var isUserInteracting: Boolean = false

    fun startAutoScroll(speedFactor: Float, density: Density) {
        val normalizedSpeed = speedFactor.coerceIn(0f, 1f)
        stopAutoScroll()
        if (normalizedSpeed <= 0f) return
        scrollJob = coroutineScope.launch {
            val baseSpeed = 40.dp
            var lastFrameTime: Long? = null
            while (isActive) {
                if (isUserInteracting) {
                    lastFrameTime = null
                    delay(100)
                    continue
                }
                val frameTime = withFrameNanos { it }
                val previous = lastFrameTime
                lastFrameTime = frameTime
                if (previous != null) {
                    val deltaSeconds = (frameTime - previous) / 1_000_000_000f
                    if (deltaSeconds > 0f) {
                        val speedPixelsPerSecond = with(density) { baseSpeed.toPx() } * normalizedSpeed
                        val scrollDistance = speedPixelsPerSecond * deltaSeconds
                        if (scrollDistance != 0f) {
                            listState.scrollBySafely(scrollDistance)
                        }
                    }
                }
                delay(16)
            }
        }
    }

    fun stopAutoScroll() {
        scrollJob?.cancel()
        scrollJob = null
    }

    fun pauseForUserInteraction() {
        isUserInteracting = true
    }

    fun resumeAfterUserInteraction() {
        isUserInteracting = false
    }

    private suspend fun LazyListState.scrollBySafely(distance: Float) {
        if (distance == 0f) return
        runCatching {
            scrollBy(distance)
        }
    }
}

@Composable
fun rememberAutoScrollController(listState: LazyListState, coroutineScope: CoroutineScope): AutoScrollController {
    return remember(listState, coroutineScope) {
        AutoScrollController(listState, coroutineScope)
    }
}

@Composable
fun AutoScrollDetector(
    controller: AutoScrollController,
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
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
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false }
                ) { _, _ -> }
            }
            .pointerInput(Unit) {
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
