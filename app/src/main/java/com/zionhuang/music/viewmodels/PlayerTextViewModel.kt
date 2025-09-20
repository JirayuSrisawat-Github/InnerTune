package com.zionhuang.music.viewmodels

import androidx.lifecycle.ViewModel
import com.zionhuang.music.lyrics.player.SongText
import com.zionhuang.music.lyrics.player.hasChords
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ViewMode { LYRICS, CHORDS }

data class PlayerUiState(
    val currentSong: SongText? = null,
    val viewMode: ViewMode = ViewMode.LYRICS,
    val autoScrollEnabled: Boolean = false,
    val autoScrollSpeed: Float = 0.4f,
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface PlayerAction {
    data class SwitchView(val mode: ViewMode) : PlayerAction
    data class SetAutoScrollEnabled(val enabled: Boolean) : PlayerAction
    data class SetAutoScrollSpeed(val speed: Float) : PlayerAction
    object PauseAutoScroll : PlayerAction
    object ResumeAutoScroll : PlayerAction
}

class PlayerTextViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    fun setSong(songText: SongText?) {
        _uiState.update { state ->
            val newMode = if (songText?.hasChords() == true) ViewMode.CHORDS else ViewMode.LYRICS
            state.copy(
                currentSong = songText,
                viewMode = newMode,
                isLoading = false,
                error = null
            )
        }
    }

    fun handleAction(action: PlayerAction) {
        when (action) {
            is PlayerAction.SwitchView -> updateViewMode(action.mode)
            is PlayerAction.SetAutoScrollEnabled -> setAutoScrollEnabled(action.enabled)
            is PlayerAction.SetAutoScrollSpeed -> setAutoScrollSpeed(action.speed)
            PlayerAction.PauseAutoScroll -> setAutoScrollEnabled(false)
            PlayerAction.ResumeAutoScroll -> setAutoScrollEnabled(true)
        }
    }

    private fun updateViewMode(mode: ViewMode) {
        _uiState.update { state ->
            if (state.currentSong?.hasChords() == true || mode == ViewMode.LYRICS) {
                state.copy(viewMode = mode)
            } else {
                state
            }
        }
    }

    private fun setAutoScrollEnabled(enabled: Boolean) {
        _uiState.update { it.copy(autoScrollEnabled = enabled) }
    }

    private fun setAutoScrollSpeed(speed: Float) {
        val clamped = speed.coerceIn(0f, 1f)
        _uiState.update { it.copy(autoScrollSpeed = clamped) }
    }

    fun showLoading() {
        _uiState.update { it.copy(isLoading = true, error = null) }
    }

    fun postError(message: String?) {
        _uiState.update { it.copy(error = message, isLoading = false) }
    }

    fun resetAutoScroll() {
        _uiState.update { it.copy(autoScrollEnabled = false) }
    }
}
