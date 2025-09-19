package com.zionhuang.music.chords

import kotlin.collections.ArrayDeque

import kotlin.math.max

object ChordSheetParser {
    data class Placement(val index: Int, val chord: String)
    data class Line(
        val rawLyric: String,
        val normalizedLyric: String,
        val placements: List<Placement>,
    )

    private val whitespaceRegex = "\\s+".toRegex()

    fun parse(sheet: String?): List<Line> {
        if (sheet.isNullOrBlank()) return emptyList()
        return sheet.lines().mapNotNull(::parseLine)
    }

    private fun parseLine(line: String): Line? {
        var index = 0
        val lyricBuilder = StringBuilder()
        val placements = mutableListOf<Placement>()
        while (index < line.length) {
            val char = line[index]
            if (char == '[') {
                val end = line.indexOf(']', startIndex = index + 1)
                if (end > index) {
                    val chord = line.substring(index + 1, end).trim()
                    if (chord.isNotEmpty()) {
                        placements += Placement(lyricBuilder.length, chord)
                    }
                    index = end + 1
                    continue
                }
            }
            lyricBuilder.append(char)
            index++
        }

        val lyric = lyricBuilder.toString().trimEnd()
        if (lyric.isBlank() && placements.isEmpty()) return null
        return Line(
            rawLyric = lyric,
            normalizedLyric = normalize(lyric),
            placements = placements,
        )
    }

    fun renderChords(line: Line, displayText: String): String? {
        if (line.placements.isEmpty()) return null
        if (!line.rawLyric.trimEnd().equals(displayText.trimEnd(), ignoreCase = false)) return null
        if (displayText.isEmpty()) return null

        val chordChars = CharArray(displayText.length) { ' ' }
        val limit = max(displayText.length, line.rawLyric.length)
        for (placement in line.placements) {
            var position = placement.index
            if (position >= limit) position = limit - placement.chord.length
            if (position < 0 || position >= limit) continue
            placement.chord.forEachIndexed { offset, character ->
                val targetIndex = position + offset
                if (targetIndex in chordChars.indices) {
                    chordChars[targetIndex] = character
                }
            }
        }
        val rendered = String(chordChars).trimEnd()
        return rendered.takeIf { it.isNotBlank() }
    }

    fun matchLines(lyrics: List<String>, chordLines: List<Line>): Map<Int, Line> {
        if (chordLines.isEmpty()) return emptyMap()
        val matches = mutableMapOf<Int, Line>()
        val queue = ArrayDeque(chordLines)
        lyrics.forEachIndexed { index, rawText ->
            val text = rawText.trimEnd()
            if (text.isBlank()) return@forEachIndexed
            val normalized = normalize(text)
            while (queue.isNotEmpty()) {
                val candidate = queue.removeFirst()
                if (candidate.rawLyric.trimEnd().equals(text.trimEnd(), ignoreCase = false)) {
                    matches[index] = candidate
                    break
                }
                if (candidate.normalizedLyric == normalized) {
                    matches[index] = candidate
                    break
                }
            }
        }
        return matches
    }

    private fun normalize(text: String): String =
        text.replace(whitespaceRegex, "").lowercase()
}
