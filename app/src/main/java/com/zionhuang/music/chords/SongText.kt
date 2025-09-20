package com.zionhuang.music.chords

import androidx.compose.runtime.Immutable

@Immutable
data class SongText(
    val id: String,
    val title: String,
    val artist: String?,
    val sections: List<Section>,
)

@Immutable
data class Section(
    val tag: String?,
    val lines: List<LyricLine>,
)

@Immutable
data class LyricLine(
    val raw: String,
    val tokens: List<Token>,
    val chordSpans: List<ChordSpan>,
) {
    val lyricText: String = buildString {
        tokens.forEach { token ->
            when (token) {
                is Token.Word -> append(token.text)
                is Token.Space -> repeat(token.length) { append(' ') }
                is Token.ChordAnchor -> Unit
            }
        }
    }

    val hasChords: Boolean
        get() = chordSpans.isNotEmpty()
}

sealed interface Token {
    data class Word(val text: String) : Token
    data class Space(val length: Int = 1) : Token
    data class ChordAnchor(val chord: Chord, val charIndex: Int) : Token
}

@Immutable
data class Chord(
    val root: String,
    val quality: String?,
    val bass: String?,
)

@Immutable
data class ChordSpan(
    val startColumn: Int,
    val label: String,
)
