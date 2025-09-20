package com.zionhuang.music.songtext

import android.util.Log

private const val TAG = "SongTextParser"

/**
 * Represents a parsed chord sheet or lyric document with sections.
 */
data class SongText(
    val id: String,
    val title: String,
    val artist: String?,
    val sections: List<Section>,
)

data class Section(
    val tag: String?,
    val lines: List<LyricLine>,
)

data class LyricLine(
    val raw: String,
    val tokens: List<Token>,
    val chordSpans: List<ChordSpan>,
)

sealed interface Token {
    data class Word(val text: String) : Token
    data class Space(val length: Int = 1) : Token
    data class ChordAnchor(val chord: Chord, val charIndex: Int) : Token
}

data class Chord(
    val root: String,
    val quality: String?,
    val bass: String?,
) {
    val label: String
        get() = buildString {
            append(root)
            quality?.takeIf { it.isNotBlank() }?.let { append(it) }
            bass?.takeIf { it.isNotBlank() }?.let { append("/").append(it) }
        }
}

data class ChordSpan(
    val startColumn: Int,
    val label: String,
)

private data class CacheEntry(
    val hash: Int,
    val songText: SongText,
)

private object SongTextCache {
    private val entries = mutableMapOf<String, CacheEntry>()

    fun get(id: String, hash: Int): SongText? = entries[id]?.takeIf { it.hash == hash }?.songText

    fun put(id: String, hash: Int, songText: SongText) {
        entries[id] = CacheEntry(hash, songText)
    }
}

object SongTextParser {
    fun parse(id: String, title: String, artist: String?, raw: String): SongText {
        val normalizedRaw = raw.replace("\r\n", "\n").replace('\r', '\n')
        val hash = normalizedRaw.hashCode()
        SongTextCache.get(id, hash)?.let { return it }

        val sections = mutableListOf<Section>()
        var currentTag: String? = null
        var pendingChordLine: String? = null
        var currentLines = mutableListOf<LyricLine>()

        fun flushPendingChord() {
            val chordLine = pendingChordLine ?: return
            val anchors = parseChordLine(chordLine)
            if (anchors.isNotEmpty()) {
                val tokens = buildTokens("", anchors)
                val chordSpans = buildChordSpans(anchors)
                currentLines.add(
                    LyricLine(
                        raw = chordLine,
                        tokens = tokens,
                        chordSpans = chordSpans,
                    )
                )
            }
            pendingChordLine = null
        }

        fun flushSection() {
            if (currentLines.isNotEmpty()) {
                sections.add(Section(tag = currentTag, lines = currentLines))
                currentLines = mutableListOf()
            }
            currentTag = null
        }

        normalizedRaw.lines().forEach { rawLine ->
            val line = rawLine.trimEnd('\u2028', '\u2029')
            if (line.isBlank()) {
                flushPendingChord()
                flushSection()
                return@forEach
            }

            if (pendingChordLine != null) {
                currentLines.add(buildLineFromChordRow(pendingChordLine!!, line))
                pendingChordLine = null
                return@forEach
            }

            val trimmed = line.trim()
            if (isSectionHeader(trimmed)) {
                flushPendingChord()
                flushSection()
                currentTag = trimmed.removeSuffix(":").trim()
                return@forEach
            }

            if (isChordOnlyLine(line)) {
                pendingChordLine = line
                return@forEach
            }

            currentLines.add(parseInlineLine(line))
        }

        flushPendingChord()
        flushSection()

        val songText = SongText(
            id = id,
            title = title,
            artist = artist,
            sections = sections,
        )
        SongTextCache.put(id, hash, songText)
        return songText
    }

    private fun parseInlineLine(line: String): LyricLine {
        val lyricBuilder = StringBuilder()
        val anchors = mutableListOf<Token.ChordAnchor>()
        var index = 0
        while (index < line.length) {
            val char = line[index]
            if (char == '[') {
                val closeIndex = line.indexOf(']', startIndex = index + 1)
                if (closeIndex != -1) {
                    val chordLabel = line.substring(index + 1, closeIndex)
                    parseChord(chordLabel)?.let { chord ->
                        anchors.add(Token.ChordAnchor(chord, lyricBuilder.length))
                    } ?: Log.w(TAG, "Skipping malformed chord token: $chordLabel")
                    index = closeIndex + 1
                    continue
                }
            }
            lyricBuilder.append(char)
            index++
        }
        val lyric = lyricBuilder.toString()
        val sortedAnchors = anchors.sortedBy { it.charIndex }
        val tokens = buildTokens(lyric, sortedAnchors)
        val chordSpans = buildChordSpans(sortedAnchors)
        return LyricLine(
            raw = line,
            tokens = tokens,
            chordSpans = chordSpans,
        )
    }

    private fun buildLineFromChordRow(chordRow: String, lyricRow: String): LyricLine {
        val anchors = parseChordLine(chordRow)
        val tokens = buildTokens(lyricRow, anchors)
        val chordSpans = buildChordSpans(anchors)
        return LyricLine(
            raw = lyricRow,
            tokens = tokens,
            chordSpans = chordSpans,
        )
    }

    private fun parseChordLine(line: String): List<Token.ChordAnchor> {
        val anchors = mutableListOf<Token.ChordAnchor>()
        var index = 0
        while (index < line.length) {
            val char = line[index]
            if (char.isWhitespace()) {
                index++
                continue
            }
            val start = index
            while (index < line.length && !line[index].isWhitespace()) {
                index++
            }
            val token = line.substring(start, index)
            val sanitized = sanitizeChordToken(token) ?: continue
            val chord = parseChord(sanitized)
            if (chord != null) {
                anchors.add(Token.ChordAnchor(chord, start))
            } else {
                Log.w(TAG, "Skipping malformed chord token: $token")
            }
        }
        return anchors.sortedBy { it.charIndex }
    }

    private fun buildTokens(line: String, anchors: List<Token.ChordAnchor>): List<Token> {
        if (line.isEmpty() && anchors.isEmpty()) return emptyList()
        val tokens = mutableListOf<Token>()
        var lineIndex = 0
        var anchorIndex = 0
        val sortedAnchors = anchors.sortedBy { it.charIndex }
        while (lineIndex < line.length) {
            while (anchorIndex < sortedAnchors.size && sortedAnchors[anchorIndex].charIndex == lineIndex) {
                tokens.add(sortedAnchors[anchorIndex])
                anchorIndex++
            }
            val char = line[lineIndex]
            if (char.isWhitespace()) {
                val start = lineIndex
                while (lineIndex < line.length && line[lineIndex].isWhitespace()) {
                    lineIndex++
                }
                tokens.add(Token.Space(lineIndex - start))
            } else {
                val start = lineIndex
                while (lineIndex < line.length && !line[lineIndex].isWhitespace()) {
                    lineIndex++
                }
                tokens.add(Token.Word(line.substring(start, lineIndex)))
            }
        }
        while (anchorIndex < sortedAnchors.size) {
            tokens.add(sortedAnchors[anchorIndex])
            anchorIndex++
        }
        return tokens
    }

    private fun buildChordSpans(anchors: List<Token.ChordAnchor>): List<ChordSpan> {
        if (anchors.isEmpty()) return emptyList()
        val spans = mutableListOf<ChordSpan>()
        anchors.sortedBy { it.charIndex }.forEach { anchor ->
            var column = anchor.charIndex.coerceAtLeast(0)
            if (spans.isNotEmpty()) {
                val previousIndex = spans.lastIndex
                var previous = spans[previousIndex]
                if (column <= previous.startColumn && previous.startColumn > 0) {
                    previous = previous.copy(startColumn = previous.startColumn - 1)
                    spans[previousIndex] = previous
                }
                val minimumStart = previous.startColumn + previous.label.length + 1
                if (column < minimumStart) {
                    column = minimumStart
                }
            }
            spans.add(ChordSpan(startColumn = column, label = anchor.chord.label))
        }
        return spans
    }

    private fun isChordOnlyLine(line: String): Boolean {
        val anchors = parseChordLine(line)
        if (anchors.isEmpty()) return false
        val nonSpaceCharacters = line.any { !it.isWhitespace() }
        val hasLetters = line.any { it.isLetter() }
        if (!nonSpaceCharacters || !hasLetters) return false
        return anchors.isNotEmpty()
    }

    private fun isSectionHeader(line: String): Boolean {
        if (line.isEmpty()) return false
        if (line.endsWith(":")) return true
        val normalized = line.lowercase()
        return normalized.startsWith("verse") ||
            normalized.startsWith("chorus") ||
            normalized.startsWith("bridge") ||
            normalized.startsWith("intro") ||
            normalized.startsWith("outro") ||
            normalized.startsWith("pre-") ||
            normalized.startsWith("hook") ||
            normalized.startsWith("tag") ||
            normalized.startsWith("interlude") ||
            normalized.startsWith("solo")
    }

    private fun sanitizeChordToken(token: String): String? {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.all { it == '|' || it == '-' || it == '–' || it == '—' }) return null
        val withoutBars = trimmed.trim('|')
        val withoutDashes = withoutBars.trim('-', '–', '—')
        if (withoutDashes.isEmpty()) return null
        val stripped = if (withoutDashes.startsWith("(") && withoutDashes.endsWith(")") && withoutDashes.count { it == '(' } == 1 && withoutDashes.count { it == ')' } == 1) {
            withoutDashes.substring(1, withoutDashes.lastIndex)
        } else {
            withoutDashes
        }
        val cleaned = stripped.trimEnd('.', ',', ';', ':')
        return cleaned.replace("♯", "#").replace("♭", "b").takeIf { it.isNotEmpty() }
    }

    private val chordRegex = Regex("^([A-G](?:#|b)?)([^/]*)?(?:/([A-G](?:#|b)?))?$")

    private fun parseChord(label: String): Chord? {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return null
        val match = chordRegex.matchEntire(trimmed)
        val groups = match?.groupValues ?: return null
        val root = groups.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        val quality = groups.getOrNull(2)?.takeIf { it.isNotBlank() }
        val bass = groups.getOrNull(3)?.takeIf { it.isNotBlank() }
        return Chord(root = root, quality = quality, bass = bass)
    }
}

val LyricLine.text: String
    get() = buildString {
        tokens.forEach { token ->
            when (token) {
                is Token.Word -> append(token.text)
                is Token.Space -> repeat(token.length) { append(' ') }
                is Token.ChordAnchor -> Unit
            }
        }
    }

fun LyricLine.chordLine(): String {
    if (chordSpans.isEmpty()) return ""
    val endColumn = chordSpans.maxOf { it.startColumn + it.label.length }
    if (endColumn == 0) return ""
    val chars = MutableList(endColumn) { ' ' }
    chordSpans.forEach { span ->
        span.label.forEachIndexed { index, c ->
            val position = span.startColumn + index
            if (position in chars.indices) {
                chars[position] = c
            }
        }
    }
    return chars.joinToString("").trimEnd()
}
