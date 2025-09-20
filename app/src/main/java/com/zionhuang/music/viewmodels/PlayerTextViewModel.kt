package com.zionhuang.music.viewmodels

import androidx.lifecycle.ViewModel
import com.zionhuang.music.lyrics.chords.SongText
import com.zionhuang.music.lyrics.chords.hasChords
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.coerceIn

enum class ViewMode { LYRICS, CHORDS }

data class PlayerUiState(
    val currentSong: SongText? = null,
    val viewMode: ViewMode = ViewMode.LYRICS,
    val autoScrollEnabled: Boolean = false,
    val autoScrollSpeed: Float = 0.4f,
    val isLoading: Boolean = false,
    val error: String? = null
)

class PlayerTextViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    fun setSong(song: SongText, initialMode: ViewMode) {
        val targetMode = if (song.hasChords()) initialMode else ViewMode.LYRICS
        _uiState.update { current ->
            current.copy(
                currentSong = song,
                viewMode = targetMode,
                autoScrollEnabled = false,
                isLoading = false,
                error = null
            )
        }
    }

    fun handleAction(action: Action) {
        when (action) {
            is Action.SwitchView -> switchView(action.mode)
            is Action.SetAutoScrollEnabled -> setAutoScrollEnabled(action.enabled)
            is Action.SetAutoScrollSpeed -> setAutoScrollSpeed(action.speed)
        }
    }

    private fun switchView(mode: ViewMode) {
        _uiState.update { state ->
            if (state.viewMode == mode) state else state.copy(viewMode = mode)
        }
    }

    private fun setAutoScrollEnabled(enabled: Boolean) {
        _uiState.update { it.copy(autoScrollEnabled = enabled) }
    }

    private fun setAutoScrollSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.1f, 1f)
        _uiState.update { it.copy(autoScrollSpeed = clamped) }
    }

    sealed interface Action {
        data class SwitchView(val mode: ViewMode) : Action
        data class SetAutoScrollEnabled(val enabled: Boolean) : Action
        data class SetAutoScrollSpeed(val speed: Float) : Action
    }
}
