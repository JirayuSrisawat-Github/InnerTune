package com.zionhuang.music.ui.component.playertext

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zionhuang.music.lyrics.chords.ChordParser
import com.zionhuang.music.lyrics.chords.SongText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ViewMode { LYRICS, CHORDS }

data class PlayerUiState(
    val currentSong: SongText? = null,
    val viewMode: ViewMode = ViewMode.LYRICS,
    val autoScrollEnabled: Boolean = false,
    val autoScrollPaused: Boolean = false,
    val autoScrollSpeed: Float = 0.4f,
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface PlayerAction {
    data class LoadSong(val songId: String) : PlayerAction
    data class SwitchView(val mode: ViewMode) : PlayerAction
    data class SetAutoScrollEnabled(val enabled: Boolean) : PlayerAction
    data class SetAutoScrollSpeed(val speed: Float) : PlayerAction
    object PauseAutoScroll : PlayerAction
    object ResumeAutoScroll : PlayerAction
}

class PlayerViewModel(
    private val chordParser: ChordParser = ChordParser()
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState = _uiState.asStateFlow()

    fun submitSong(song: SongText) {
        _uiState.update {
            it.copy(
                currentSong = song,
                isLoading = false,
                error = null,
                viewMode = determineInitialView(song)
            )
        }
    }

    fun parseRawSong(
        id: String,
        title: String,
        artist: String?,
        body: String
    ) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val parsedSong = chordParser.parseSong(id, title, artist, body)
                _uiState.update {
                    it.copy(
                        currentSong = parsedSong,
                        isLoading = false,
                        error = null,
                        viewMode = determineInitialView(parsedSong)
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to parse song: ${error.message}",
                        currentSong = null
                    )
                }
            }
        }
    }

    fun handleAction(action: PlayerAction) {
        when (action) {
            is PlayerAction.LoadSong -> loadSong(action.songId)
            is PlayerAction.SwitchView -> updateViewMode(action.mode)
            is PlayerAction.SetAutoScrollEnabled -> toggleAutoScroll(action.enabled)
            is PlayerAction.SetAutoScrollSpeed -> updateScrollSpeed(action.speed)
            PlayerAction.PauseAutoScroll -> pauseAutoScroll()
            PlayerAction.ResumeAutoScroll -> resumeAutoScroll()
        }
    }

    private fun loadSong(songId: String) {
        _uiState.update {
            it.copy(
                isLoading = false,
                error = "Song repository not configured for PlayerViewModel ($songId)",
                currentSong = null
            )
        }
    }

    private fun updateViewMode(mode: ViewMode) {
        _uiState.update {
            it.copy(viewMode = mode)
        }
    }

    private fun toggleAutoScroll(enabled: Boolean) {
        _uiState.update {
            it.copy(
                autoScrollEnabled = enabled,
                autoScrollPaused = if (enabled) it.autoScrollPaused else false
            )
        }
    }

    private fun updateScrollSpeed(speed: Float) {
        val coercedSpeed = speed.coerceIn(0f, 1f)
        _uiState.update {
            it.copy(autoScrollSpeed = coercedSpeed)
        }
    }

    private fun pauseAutoScroll() {
        _uiState.update {
            if (it.autoScrollEnabled) {
                it.copy(autoScrollPaused = true)
            } else {
                it
            }
        }
    }

    private fun resumeAutoScroll() {
        _uiState.update {
            if (it.autoScrollEnabled) {
                it.copy(autoScrollPaused = false)
            } else {
                it
            }
        }
    }

    private fun determineInitialView(song: SongText): ViewMode {
        val hasChords = song.sections.any { section ->
            section.lines.any { line -> line.chordSpans.isNotEmpty() }
        }
        return if (hasChords) ViewMode.CHORDS else ViewMode.LYRICS
    }
}
