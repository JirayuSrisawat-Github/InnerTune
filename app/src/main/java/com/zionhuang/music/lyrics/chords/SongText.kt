package com.zionhuang.music.lyrics.chords

/**
 * Represents parsed chord/lyric content that can be rendered in the player text screen.
 */
data class SongText(
    val id: String,
    val title: String,
    val artist: String? = null,
    val sections: List<Section>
)

data class Section(
    val tag: String?,
    val lines: List<LyricLine>
)

data class LyricLine(
    val raw: String,
    val cleanLyrics: String,
    val tokens: List<Token>,
    val chordSpans: List<ChordSpan>
)

sealed interface Token {
    data class Word(val text: String) : Token
    data class Space(val length: Int = 1) : Token
    data class ChordAnchor(val chord: Chord, val charIndex: Int) : Token
}

data class Chord(
    val root: String,
    val quality: String? = null,
    val bass: String? = null
) {
    val displayName: String
        get() = buildString {
            append(root)
            quality?.let { append(it) }
            bass?.let { append("/$it") }
        }
}

data class ChordSpan(
    val startColumn: Int,
    val chord: Chord,
    val width: Int
)
