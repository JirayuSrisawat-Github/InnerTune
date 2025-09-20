package com.zionhuang.music.lyrics.chords

import kotlin.math.max

/**
 * Parser that converts chord sheet style text (ChordPro style [C] markers) into structured lines.
 */
open class ChordParser {
    private val chordPattern = Regex("""^([A-G][#♯♭b]?)([^/]*)?(?:/([A-G][#♯♭b]?))?$""")

    fun parseSong(
        id: String,
        title: String,
        artist: String?,
        rawText: String
    ): SongText {
        val sections = mutableListOf<Section>()
        var currentSectionTag: String? = null
        val currentLines = mutableListOf<LyricLine>()

        fun commitSection() {
            if (currentLines.isNotEmpty()) {
                sections += Section(currentSectionTag, currentLines.toList())
                currentLines.clear()
            }
        }

        rawText.lines().forEach { rawLine ->
            when {
                rawLine.isBlank() -> {
                    commitSection()
                    currentSectionTag = null
                }

                isSectionHeader(rawLine.trim()) -> {
                    commitSection()
                    currentSectionTag = rawLine.trim().removeSurrounding("[", "]")
                }

                else -> {
                    currentLines += parseLine(rawLine)
                }
            }
        }
        commitSection()

        return SongText(
            id = id,
            title = title,
            artist = artist,
            sections = sections.ifEmpty { listOf(Section(null, listOf())) }
        )
    }

    open fun parseLine(input: String): LyricLine {
        if (input.isEmpty()) {
            return LyricLine(
                raw = input,
                cleanLyrics = "",
                tokens = emptyList(),
                chordSpans = emptyList()
            )
        }

        val chordAnchors = mutableListOf<Pair<Chord, Int>>()
        val cleanText = StringBuilder()
        var index = 0

        while (index < input.length) {
            val current = input[index]
            if (current == '[') {
                val endIndex = input.indexOf(']', startIndex = index + 1)
                if (endIndex > index) {
                    val chordText = input.substring(index + 1, endIndex)
                    val chord = parseChord(chordText)
                    if (chord != null) {
                        chordAnchors += chord to cleanText.length
                    }
                    index = endIndex + 1
                    continue
                }
            }
            cleanText.append(current)
            index++
        }

        val tokens = buildTokens(cleanText.toString(), chordAnchors)
        val chordSpans = computeChordSpans(cleanText.toString(), chordAnchors)

        return LyricLine(
            raw = input,
            cleanLyrics = cleanText.toString(),
            tokens = tokens,
            chordSpans = chordSpans
        )
    }

    protected open fun parseChord(chordText: String): Chord? {
        val normalized = chordText.replace("♯", "#").replace("♭", "b")
        val match = chordPattern.matchEntire(normalized) ?: return null
        return Chord(
            root = match.groupValues[1],
            quality = match.groupValues[2].takeIf { it.isNotEmpty() },
            bass = match.groupValues[3].takeIf { it.isNotEmpty() }
        )
    }

    private fun buildTokens(
        cleanText: String,
        anchors: List<Pair<Chord, Int>>
    ): List<Token> {
        if (cleanText.isEmpty()) return anchors.map { Token.ChordAnchor(it.first, it.second) }

        val tokens = mutableListOf<Token>()
        val anchorMap = anchors.groupBy({ it.second }, { it.first })
        val currentWord = StringBuilder()
        var pendingSpaces = 0

        fun flushWord() {
            if (currentWord.isNotEmpty()) {
                tokens += Token.Word(currentWord.toString())
                currentWord.clear()
            }
        }

        fun flushSpaces() {
            if (pendingSpaces > 0) {
                tokens += Token.Space(pendingSpaces)
                pendingSpaces = 0
            }
        }

        cleanText.forEachIndexed { index, c ->
            anchorMap[index]?.forEach { chord ->
                flushWord()
                flushSpaces()
                tokens += Token.ChordAnchor(chord, index)
            }

            if (c.isWhitespace()) {
                flushWord()
                pendingSpaces++
            } else {
                flushSpaces()
                currentWord.append(c)
            }
        }
        flushWord()
        flushSpaces()

        anchorMap[cleanText.length]?.forEach { chord ->
            tokens += Token.ChordAnchor(chord, cleanText.length)
        }

        return tokens
    }

    private fun computeChordSpans(
        text: String,
        anchors: List<Pair<Chord, Int>>
    ): List<ChordSpan> {
        if (anchors.isEmpty()) return emptyList()
        val spans = anchors.map { (chord, charIndex) ->
            val index = charIndex.coerceIn(0, text.length)
            ChordSpan(
                startColumn = index,
                chord = chord,
                width = chord.displayName.length
            )
        }.sortedBy { it.startColumn }

        return resolveCollisions(spans)
    }

    private fun resolveCollisions(spans: List<ChordSpan>): List<ChordSpan> {
        if (spans.isEmpty()) return emptyList()
        val resolved = ArrayList<ChordSpan>(spans.size)
        var lastEnd = -1
        spans.forEach { span ->
            val adjustedStart = if (lastEnd >= 0) {
                max(span.startColumn, lastEnd + 1)
            } else {
                span.startColumn
            }
            val resolvedSpan = span.copy(startColumn = adjustedStart)
            lastEnd = adjustedStart + span.width
            resolved += resolvedSpan
        }
        return resolved
    }

    private fun isSectionHeader(line: String): Boolean {
        if (!line.startsWith('[') || !line.endsWith(']')) return false
        val content = line.substring(1, line.length - 1)
        return parseChord(content) == null
    }
}
