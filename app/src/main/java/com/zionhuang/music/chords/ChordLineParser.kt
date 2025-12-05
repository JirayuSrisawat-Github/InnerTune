package com.zionhuang.music.chords

/**
 * Represents a chord placement within a line of text.
 *
 * @property index Character position in the display text where the chord should appear
 * @property chord The chord name (e.g., "Em7", "Cadd9", "Bm7")
 */
data class ChordPlacement(
    val index: Int,
    val chord: String
)

/**
 * Represents a parsed line containing both the display text (with brackets removed)
 * and the positions of chords within that text.
 *
 * @property displayText The text with chord brackets removed
 * @property placements List of chord placements indicating where chords should be displayed
 */
data class ChordLine(
    val displayText: String,
    val placements: List<ChordPlacement>
)

/**
 * Parser for extracting chord information from text with inline chord annotations.
 *
 * Chord format: [ChordName] - chords are embedded in brackets within the text
 * Example: "ลัคคี[Em7]งเท่าไร" -> displayText: "ลัคคีงเท่าไร", placement at index 5 for "Em7"
 */
object ChordLineParser {
    /**
     * Parses a single line of text containing chord annotations in [ChordName] format.
     *
     * Algorithm:
     * 1. Iterate through each character
     * 2. When '[' is found, extract text until ']'
     * 3. Store chord name and current display text position
     * 4. Remove brackets from display text
     * 5. Handle edge cases: empty brackets, unclosed brackets
     *
     * @param rawLine The input line with chord annotations (e.g., "Hello[Em7] world[C]")
     * @return ChordLine with displayText and chord placements
     */
    fun parse(rawLine: String): ChordLine {
        val displayBuilder = StringBuilder()
        val placements = mutableListOf<ChordPlacement>()

        var i = 0
        while (i < rawLine.length) {
            if (rawLine[i] == '[') {
                val endIndex = rawLine.indexOf(']', i + 1)
                if (endIndex > i) {
                    val chord = rawLine.substring(i + 1, endIndex).trim()
                    if (chord.isNotEmpty()) {
                        // Store chord at current display text position
                        placements.add(ChordPlacement(displayBuilder.length, chord))
                    }
                    i = endIndex + 1
                    continue
                }
            }
            displayBuilder.append(rawLine[i])
            i++
        }

        return ChordLine(
            displayText = displayBuilder.toString(),
            placements = placements
        )
    }

    /**
     * Parses multiple lines of chord text.
     *
     * @param rawChords Multi-line string with chord annotations
     * @return List of parsed ChordLine objects
     */
    fun parseAll(rawChords: String): List<ChordLine> {
        return rawChords.lines().map { parse(it) }
    }
}
