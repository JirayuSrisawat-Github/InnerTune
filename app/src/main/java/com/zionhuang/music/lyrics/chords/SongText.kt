package com.zionhuang.music.lyrics.chords

/**
 * Represents a parsed chord chart or lyric sheet that may contain multiple sections.
 */
data class SongText(
    val id: String,
    val title: String,
    val artist: String?,
    val sections: List<Section>
)

/**
 * Logical grouping inside a song such as a verse or chorus.
 */
data class Section(
    val tag: String?,
    val lines: List<LyricLine>
)

/**
 * Individual lyric line with optional chord anchors embedded.
 */
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

/**
 * Parsed representation of a chord symbol.
 */
data class Chord(
    val root: String,
    val quality: String?,
    val bass: String?
) {
    val displayName: String
        get() = buildString {
            append(root)
            quality?.takeIf { it.isNotEmpty() }?.let { append(it) }
            bass?.takeIf { it.isNotEmpty() }?.let { append("/$it") }
        }
}

/**
 * Describes the on-screen positioning information for a rendered chord label.
 */
data class ChordSpan(
    val startColumn: Int,
    val chord: Chord,
    val width: Int
)

fun SongText.hasLyrics(): Boolean = sections.any { section ->
    section.lines.any { it.cleanLyrics.isNotBlank() }
}

fun SongText.hasChords(): Boolean = sections.any { section ->
    section.lines.any { it.chordSpans.isNotEmpty() }
}
