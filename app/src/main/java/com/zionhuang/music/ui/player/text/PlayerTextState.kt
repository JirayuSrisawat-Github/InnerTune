package com.zionhuang.music.ui.player.text

import androidx.lifecycle.ViewModel
import com.zionhuang.music.lyrics.chords.SongText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ViewMode { LYRICS, CHORDS }

data class PlayerUiState(
    val currentSong: SongText? = null,
    val viewMode: ViewMode = ViewMode.LYRICS,
    val autoScrollEnabled: Boolean = false,
    val autoScrollSpeed: Float = 0.4f,
    val autoScrollPaused: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface PlayerAction {
    data class LoadSong(val song: SongText) : PlayerAction
    data class SwitchView(val mode: ViewMode) : PlayerAction
    data class SetAutoScrollEnabled(val enabled: Boolean) : PlayerAction
    data class SetAutoScrollSpeed(val speed: Float) : PlayerAction
    object PauseAutoScroll : PlayerAction
    object ResumeAutoScroll : PlayerAction
}

class PlayerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState = _uiState.asStateFlow()

    fun handleAction(action: PlayerAction) {
        when (action) {
            is PlayerAction.LoadSong -> setSong(action.song)
            is PlayerAction.SwitchView -> updateViewMode(action.mode)
            is PlayerAction.SetAutoScrollEnabled -> toggleAutoScroll(action.enabled)
            is PlayerAction.SetAutoScrollSpeed -> updateScrollSpeed(action.speed)
            PlayerAction.PauseAutoScroll -> updateAutoScrollPaused(true)
            PlayerAction.ResumeAutoScroll -> updateAutoScrollPaused(false)
        }
    }

    fun setSong(songText: SongText?) {
        if (songText == null) {
            _uiState.update { it.copy(currentSong = null, isLoading = false, error = null) }
            return
        }
        _uiState.update {
            it.copy(
                currentSong = songText,
                isLoading = false,
                error = null,
                viewMode = determineInitialView(songText)
            )
        }
    }

    private fun updateViewMode(mode: ViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    private fun toggleAutoScroll(enabled: Boolean) {
        _uiState.update {
            it.copy(autoScrollEnabled = enabled, autoScrollPaused = false)
        }
    }

    private fun updateScrollSpeed(speed: Float) {
        val normalized = speed.coerceIn(0f, 1f)
        _uiState.update { it.copy(autoScrollSpeed = normalized) }
    }

    private fun updateAutoScrollPaused(paused: Boolean) {
        _uiState.update { current ->
            if (current.autoScrollPaused == paused) current
            else current.copy(autoScrollPaused = paused)
        }
    }

    private fun determineInitialView(song: SongText): ViewMode {
        val hasChords = song.sections.any { section ->
            section.lines.any { it.chordSpans.isNotEmpty() }
        }
        val hasLyrics = song.sections.any { section ->
            section.lines.any { it.cleanLyrics.isNotBlank() }
        }
        return when {
            hasChords -> ViewMode.CHORDS
            hasLyrics -> ViewMode.LYRICS
            else -> ViewMode.LYRICS
        }
    }
}
