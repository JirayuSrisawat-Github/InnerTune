package com.zionhuang.music.lyrics.chords

import kotlin.math.max

open class ChordParser {
    private val chordRegex = Regex("""\\[([^]]+)\\]""")
    private val chordPattern = Regex("""^([A-G][#♯♭b]?)([^/]*)?(?:/([A-G][#♯♭b]?))?$""")

    open fun parseLine(input: String): LyricLine {
        if (input.isEmpty()) {
            return LyricLine(
                raw = input,
                cleanLyrics = "",
                tokens = emptyList(),
                chordSpans = emptyList()
            )
        }

        val anchors = mutableListOf<Pair<Chord, Int>>()
        val cleanBuilder = StringBuilder()
        var lastIndex = 0

        chordRegex.findAll(input).forEach { match ->
            val chordText = match.groupValues[1]
            val normalizedChord = parseChord(chordText)

            val fragment = input.substring(lastIndex, match.range.first)
            cleanBuilder.append(fragment)

            normalizedChord?.let { chord ->
                anchors += chord to cleanBuilder.codePointCount()
            }

            lastIndex = match.range.last + 1
        }

        if (lastIndex < input.length) {
            cleanBuilder.append(input.substring(lastIndex))
        }

        val cleanText = cleanBuilder.toString()
        val sortedAnchors = anchors.sortedBy { it.second }
        val tokens = tokenizeText(cleanText, sortedAnchors)
        val chordSpans = computeChordSpans(cleanText, sortedAnchors)

        return LyricLine(
            raw = input,
            cleanLyrics = cleanText,
            tokens = tokens,
            chordSpans = chordSpans
        )
    }

    fun parseSongText(id: String, title: String, artist: String?, rawText: String): SongText {
        val sections = mutableListOf<Section>()
        val currentLines = mutableListOf<LyricLine>()
        var currentTag: String? = null

        fun commitSection() {
            if (currentLines.isNotEmpty()) {
                sections += Section(
                    tag = currentTag?.ifBlank { null },
                    lines = currentLines.toList()
                )
                currentLines.clear()
            }
            currentTag = null
        }

        rawText.lines().forEach { rawLine ->
            val normalizedLine = rawLine.trimEnd('\r')
            val trimmedLine = normalizedLine.trim()
            if (trimmedLine.isEmpty()) {
                commitSection()
            } else if (trimmedLine.endsWith(":") && currentLines.isEmpty()) {
                currentTag = trimmedLine.dropLast(1).trim().ifBlank { null }
            } else {
                currentLines += parseLine(normalizedLine)
            }
        }
        commitSection()

        val resolvedSections = if (sections.isEmpty() && currentLines.isEmpty()) {
            val fallbackLines = rawText.lines()
                .filter { it.isNotEmpty() }
                .map { parseLine(it.trimEnd('\r')) }
            if (fallbackLines.isEmpty()) emptyList() else listOf(Section(null, fallbackLines))
        } else sections

        return SongText(
            id = id,
            title = title,
            artist = artist,
            sections = resolvedSections
        )
    }

    fun containsChordNotation(text: String): Boolean {
        return chordRegex.findAll(text).any { match ->
            parseChord(match.groupValues[1]) != null
        }
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

    protected open fun computeChordSpans(
        text: String,
        anchors: List<Pair<Chord, Int>>
    ): List<ChordSpan> {
        if (anchors.isEmpty()) return emptyList()

        val spans = anchors.map { (chord, charIndex) ->
            val prefix = text.takeCharCount(charIndex)
            val column = measureMonospaceColumns(prefix)
            ChordSpan(
                startColumn = column,
                chord = chord,
                width = chord.displayName.length
            )
        }.sortedBy { it.startColumn }

        return resolveCollisions(spans)
    }

    protected open fun resolveCollisions(spans: List<ChordSpan>): List<ChordSpan> {
        if (spans.size <= 1) return spans
        val resolved = mutableListOf<ChordSpan>()
        spans.forEach { span ->
            val adjustedColumn = when (val last = resolved.lastOrNull()) {
                null -> span.startColumn
                else -> max(span.startColumn, last.startColumn + last.width + 1)
            }
            resolved += span.copy(startColumn = adjustedColumn)
        }
        return resolved
    }

    private fun tokenizeText(
        text: String,
        anchors: List<Pair<Chord, Int>>
    ): List<Token> {
        if (text.isEmpty() && anchors.isEmpty()) return emptyList()
        val tokens = mutableListOf<Token>()
        var currentIndex = 0
        var anchorIndex = 0
        val sortedAnchors = anchors.sortedBy { it.second }

        while (currentIndex < text.length || anchorIndex < sortedAnchors.size) {
            val nextAnchor = sortedAnchors.getOrNull(anchorIndex)
            val anchorPosition = nextAnchor?.second ?: Int.MAX_VALUE

            if (currentIndex < anchorPosition && currentIndex < text.length) {
                val end = minOf(anchorPosition, text.length)
                appendSegmentTokens(
                    segment = text.substring(currentIndex, end),
                    tokens = tokens
                )
                currentIndex = end
            }

            if (nextAnchor != null && currentIndex >= anchorPosition) {
                tokens += Token.ChordAnchor(nextAnchor.first, nextAnchor.second)
                anchorIndex++
            }

            if (nextAnchor == null && currentIndex >= text.length) {
                break
            }
        }

        if (currentIndex < text.length) {
            appendSegmentTokens(text.substring(currentIndex), tokens)
        }

        return tokens
    }

    private fun appendSegmentTokens(segment: String, tokens: MutableList<Token>) {
        if (segment.isEmpty()) return
        var index = 0
        while (index < segment.length) {
            val start = index
            val isSpace = segment[index].isWhitespace()
            while (index < segment.length && segment[index].isWhitespace() == isSpace) {
                index++
            }
            val tokenText = segment.substring(start, index)
            if (isSpace) {
                tokens += Token.Space(tokenText.length)
            } else {
                tokens += Token.Word(tokenText)
            }
        }
    }

    private fun measureMonospaceColumns(text: String): Int {
        return text.codePointCount(0, text.length)
    }

    private fun StringBuilder.codePointCount(): Int = this.toString().codePointCount(0, length)

    private fun String.takeCharCount(count: Int): String {
        if (count <= 0) return ""
        if (count >= length) return this
        var endIndex = 0
        var remaining = count
        while (endIndex < length && remaining > 0) {
            val char = this[endIndex]
            endIndex += if (Character.isHighSurrogate(char) && endIndex + 1 < length && Character.isLowSurrogate(this[endIndex + 1])) {
                2
            } else {
                1
            }
            remaining--
        }
        return substring(0, endIndex)
    }
}
