package com.zionhuang.music.ui.component.playertext

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AutoScrollController(
    private val listState: LazyListState,
    private val coroutineScope: CoroutineScope
) {
    private var scrollJob: Job? = null
    private var isUserInteracting: Boolean = false

    fun startAutoScroll(speedFactor: Float, density: Density, baseSpeed: Dp = 40.dp) {
        stopAutoScroll()
        val clampedSpeed = speedFactor.coerceIn(0f, 1f)
        scrollJob = coroutineScope.launch {
            var lastFrameTime = 0L
            val baseSpeedPxPerSecond = with(density) { (baseSpeed * clampedSpeed).toPx() }
            while (isActive) {
                if (isUserInteracting || baseSpeedPxPerSecond == 0f) {
                    lastFrameTime = 0L
                    delay(32)
                    continue
                }
                var scrollDistance = 0f
                withFrameNanos { frameTime ->
                    if (lastFrameTime != 0L) {
                        val deltaSeconds = (frameTime - lastFrameTime) / 1_000_000_000f
                        scrollDistance = baseSpeedPxPerSecond * deltaSeconds
                    }
                    lastFrameTime = frameTime
                }
                if (scrollDistance != 0f) {
                    listState.scrollBy(scrollDistance)
                } else {
                    delay(16)
                }
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
}
