package com.zionhuang.music.lyrics.chords

import kotlin.math.max

private val chordRegex = Regex("""\\[([^\\]]+)]""")
private val chordPattern = Regex("""^([A-Ga-g][#♯♭b]?)([^/]*)?(?:/([A-Ga-g][#♯♭b]?))?$""")

/**
 * Parser that extracts chords from text based lyrics.
 */
open class ChordParser {
    fun parseSong(
        songId: String,
        title: String,
        artist: String?,
        rawLyrics: String
    ): SongText {
        val sections = mutableListOf<Section>()
        var currentTag: String? = null
        val currentLines = mutableListOf<LyricLine>()

        fun flushSection() {
            if (currentLines.isNotEmpty()) {
                sections += Section(tag = currentTag, lines = currentLines.toList())
                currentLines.clear()
            }
        }

        rawLyrics.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trimEnd()
            if (trimmed.isBlank()) {
                flushSection()
                currentTag = null
                return@forEach
            }

            val potentialTag = trimmed.removePrefix("[").removeSuffix("]")
            val isTag = trimmed.startsWith("[") && trimmed.endsWith("]") &&
                trimmed.count { it == '[' } == 1 &&
                trimmed.count { it == ']' } == 1 &&
                trimmed.length <= 64 &&
                chordPattern.matchEntire(potentialTag) == null
            if (isTag) {
                flushSection()
                currentTag = potentialTag
                return@forEach
            }

            currentLines += parseLine(rawLine)
        }

        flushSection()

        return SongText(
            id = songId,
            title = title,
            artist = artist,
            sections = sections.ifEmpty { listOf(Section(tag = null, lines = emptyList())) }
        )
    }

    open fun parseLine(input: String): LyricLine {
        val tokens = mutableListOf<Token>()
        val chordAnchors = mutableListOf<Pair<Chord, Int>>()
        val cleanLyricsBuilder = StringBuilder()
        var lastIndex = 0

        chordRegex.findAll(input).forEach { match ->
            val chordText = match.groupValues[1]
            val chord = parseChord(chordText)
            val segment = input.substring(lastIndex, match.range.first)
            cleanLyricsBuilder.append(segment)
            val currentIndex = cleanLyricsBuilder.length
            if (chord != null) {
                chordAnchors += chord to currentIndex
            }
            lastIndex = match.range.last + 1
        }
        if (lastIndex < input.length) {
            cleanLyricsBuilder.append(input.substring(lastIndex))
        }

        val cleanText = cleanLyricsBuilder.toString()
        val textTokens = tokenizeText(cleanText, chordAnchors)
        tokens += textTokens
        val chordSpans = computeChordSpans(cleanText, chordAnchors)

        return LyricLine(
            raw = input,
            cleanLyrics = cleanText,
            tokens = tokens,
            chordSpans = chordSpans
        )
    }

    protected open fun parseChord(chordText: String): Chord? {
        val normalized = chordText.replace("♯", "#").replace("♭", "b")
        val match = chordPattern.matchEntire(normalized.trim()) ?: return null
        val root = match.groupValues[1].replaceFirstChar { it.uppercase() }
        return Chord(
            root = root,
            quality = match.groupValues[2].takeIf { it.isNotEmpty() },
            bass = match.groupValues[3].takeIf { it.isNotEmpty() }?.replaceFirstChar { it.uppercase() }
        )
    }

    private fun tokenizeText(text: String, anchors: List<Pair<Chord, Int>>): List<Token> {
        if (text.isEmpty()) return emptyList()
        val tokens = mutableListOf<Token>()
        val anchorIterator = anchors.sortedBy { it.second }.iterator()
        var nextAnchor = if (anchorIterator.hasNext()) anchorIterator.next() else null
        val buffer = StringBuilder()
        var spaceCount = 0

        text.forEachIndexed { index, char ->
            if (nextAnchor != null && index == nextAnchor!!.second) {
                if (buffer.isNotEmpty()) {
                    tokens += Token.Word(buffer.toString())
                    buffer.clear()
                }
                if (spaceCount > 0) {
                    tokens += Token.Space(spaceCount)
                    spaceCount = 0
                }
                tokens += Token.ChordAnchor(nextAnchor!!.first, index)
                nextAnchor = if (anchorIterator.hasNext()) anchorIterator.next() else null
            }

            if (char.isWhitespace()) {
                if (buffer.isNotEmpty()) {
                    tokens += Token.Word(buffer.toString())
                    buffer.clear()
                }
                if (char == '\t') {
                    spaceCount += 4
                } else {
                    spaceCount += 1
                }
            } else {
                if (spaceCount > 0) {
                    tokens += Token.Space(spaceCount)
                    spaceCount = 0
                }
                buffer.append(char)
            }
        }

        if (buffer.isNotEmpty()) {
            tokens += Token.Word(buffer.toString())
        }
        if (spaceCount > 0) {
            tokens += Token.Space(spaceCount)
        }
        if (nextAnchor != null) {
            tokens += Token.ChordAnchor(nextAnchor!!.first, text.length)
        }

        return tokens
    }

    private fun computeChordSpans(
        text: String,
        anchors: List<Pair<Chord, Int>>
    ): List<ChordSpan> {
        if (anchors.isEmpty()) return emptyList()
        val sorted = anchors.sortedBy { it.second }
        val spans = sorted.map { (chord, charIndex) ->
            ChordSpan(
                startColumn = charIndex.coerceAtLeast(0),
                chord = chord,
                width = max(1, chord.displayName.length)
            )
        }
        return resolveCollisions(spans)
    }

    private fun resolveCollisions(spans: List<ChordSpan>): List<ChordSpan> {
        if (spans.isEmpty()) return emptyList()
        val resolved = mutableListOf<ChordSpan>()
        spans.forEach { span ->
            val last = resolved.lastOrNull()
            val adjusted = if (last != null && span.startColumn < last.startColumn + last.width + 1) {
                span.copy(startColumn = last.startColumn + last.width + 1)
            } else {
                span
            }
            resolved += adjusted
        }
        return resolved
    }
}

class RobustChordParser : ChordParser() {
    override fun parseLine(input: String): LyricLine = try {
        super.parseLine(input)
    } catch (error: Exception) {
        LyricLine(
            raw = input,
            cleanLyrics = input.replace(chordRegex, ""),
            tokens = emptyList(),
            chordSpans = emptyList()
        )
    }
}
