package com.zionhuang.music.lyrics.chords

import androidx.compose.runtime.Immutable

/**
 * Models describing a parsed song text that may contain inline chords.
 */
@Immutable
data class SongText(
    val id: String,
    val title: String,
    val artist: String?,
    val sections: List<Section>
)

@Immutable
data class Section(
    val tag: String?,
    val lines: List<LyricLine>
)

@Immutable
data class LyricLine(
    val raw: String,
    val cleanLyrics: String,
    val tokens: List<Token>,
    val chordSpans: List<ChordSpan>
)

sealed interface Token {
    @Immutable
    data class Word(val text: String) : Token

    @Immutable
    data class Space(val length: Int = 1) : Token

    @Immutable
    data class ChordAnchor(val chord: Chord, val charIndex: Int) : Token
}

@Immutable
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

@Immutable
data class ChordSpan(
    val startColumn: Int,
    val chord: Chord,
    val width: Int
)

fun SongText.hasLyrics(): Boolean = sections.any { section ->
    section.lines.any { line -> line.cleanLyrics.isNotBlank() }
}

fun SongText.hasChords(): Boolean = sections.any { section ->
    section.lines.any { line -> line.chordSpans.isNotEmpty() }
}
