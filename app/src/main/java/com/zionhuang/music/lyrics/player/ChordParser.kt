package com.zionhuang.music.lyrics.player

/**
 * Parser that extracts chord information from lyric lines with bracket notation.
 */
class ChordParser {
    private val chordRegex = Regex("""\\[([^\\]]+)\\]""")
    private val chordPattern = Regex("""^([A-G][#♯♭b]?)([^/]*)?(?:/([A-G][#♯♭b]?))?$""")

    fun parseLine(input: String): LyricLine {
        if (input.isEmpty()) {
            return LyricLine(
                raw = input,
                cleanLyrics = "",
                tokens = emptyList(),
                chordSpans = emptyList()
            )
        }

        val tokens = mutableListOf<Token>()
        val chordAnchors = mutableListOf<Pair<Chord, Int>>()
        var cleanText = input
        var offset = 0

        chordRegex.findAll(input).forEach { match ->
            val chordText = match.groupValues[1]
            val originalPosition = (match.range.first - offset).coerceAtLeast(0)
            val chord = parseChord(chordText)

            if (chord != null) {
                chordAnchors.add(chord to originalPosition.coerceAtMost(cleanText.length))
            }

            cleanText = cleanText.replaceFirst(match.value, "")
            offset += match.value.length
        }

        tokens += tokenizeText(cleanText, chordAnchors)

        val chordSpans = computeChordSpans(cleanText, chordAnchors)

        return LyricLine(
            raw = input,
            cleanLyrics = cleanText,
            tokens = tokens,
            chordSpans = chordSpans
        )
    }

    fun parseChord(chordText: String): Chord? {
        val normalized = chordText.replace("♯", "#").replace("♭", "b").trim()
        val match = chordPattern.matchEntire(normalized) ?: return null

        return Chord(
            root = match.groupValues[1],
            quality = match.groupValues[2].takeIf { it.isNotEmpty() },
            bass = match.groupValues[3].takeIf { it.isNotEmpty() }
        )
    }

    private fun computeChordSpans(text: String, anchors: List<Pair<Chord, Int>>): List<ChordSpan> {
        if (anchors.isEmpty()) return emptyList()
        val spans = anchors.map { (chord, charIndex) ->
            val prefixLength = text.take(charIndex.coerceIn(0, text.length))
            val column = prefixLength.codePointCount()
            ChordSpan(
                startColumn = column,
                chord = chord,
                width = chord.displayName.codePointCount().coerceAtLeast(1)
            )
        }.sortedBy { it.startColumn }
        return spans.resolveCollisions()
    }

    private fun tokenizeText(text: String, anchors: List<Pair<Chord, Int>>): List<Token> {
        if (text.isEmpty()) return emptyList()
        val sortedAnchors = anchors.sortedBy { it.second }
        val tokens = mutableListOf<Token>()
        var index = 0
        var anchorIndex = 0

        fun emitAnchors(untilIndex: Int) {
            while (anchorIndex < sortedAnchors.size && sortedAnchors[anchorIndex].second <= untilIndex) {
                val (anchorChord, anchorPosition) = sortedAnchors[anchorIndex]
                tokens += Token.ChordAnchor(anchorChord, anchorPosition.coerceIn(0, text.length))
                anchorIndex++
            }
        }

        while (index < text.length) {
            emitAnchors(index)
            val currentChar = text[index]
            if (currentChar.isWhitespace()) {
                val start = index
                while (index < text.length && text[index].isWhitespace()) {
                    index++
                }
                tokens += Token.Space(maxOf(1, index - start))
            } else {
                val start = index
                while (index < text.length && !text[index].isWhitespace()) {
                    index++
                }
                tokens += Token.Word(text.substring(start, index))
            }
        }

        emitAnchors(text.length)
        return tokens
    }
}

private fun String.codePointCount(): Int =
    if (isEmpty()) 0 else this.codePointCount(0, length)

private fun Int.coerceAtLeast(min: Int) = if (this < min) min else this
