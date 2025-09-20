package com.zionhuang.music.lyrics.player

import kotlin.math.max

/**
 * Represents parsed song text with optional metadata and structured sections.
 */
data class SongText(
    val id: String,
    val title: String,
    val artist: String?,
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

/**
 * Representation of a parsed chord in the song text.
 */
data class Chord(
    val root: String,
    val quality: String?,
    val bass: String?
) {
    val displayName: String get() = buildString {
        append(root)
        quality?.takeIf { it.isNotEmpty() }?.let { append(it) }
        bass?.takeIf { it.isNotEmpty() }?.let { append("/$it") }
    }
}

/**
 * Indicates where a chord should be rendered relative to the lyric line.
 *
 * @param startColumn character-based column hint calculated from the clean lyrics.
 * @param chord the chord that should be rendered.
 * @param width the chord display length in characters, used for collision avoidance.
 */
data class ChordSpan(
    val startColumn: Int,
    val chord: Chord,
    val width: Int
)

internal fun List<ChordSpan>.resolveCollisions(): List<ChordSpan> {
    if (isEmpty()) return this
    val resolved = ArrayList<ChordSpan>(size)
    for (span in this) {
        val previous = resolved.lastOrNull()
        val adjustedColumn = if (previous != null) {
            max(span.startColumn, previous.startColumn + previous.width + 1)
        } else {
            span.startColumn
        }
        resolved += span.copy(startColumn = adjustedColumn)
    }
    return resolved
}

fun SongText.hasLyrics(): Boolean =
    sections.any { section ->
        section.lines.any { line -> line.cleanLyrics.isNotBlank() }
    }

fun SongText.hasChords(): Boolean =
    sections.any { section ->
        section.lines.any { line -> line.chordSpans.isNotEmpty() }
    }
