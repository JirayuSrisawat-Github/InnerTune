package com.zionhuang.music.chords

import kotlin.math.max
import kotlin.math.min
import timber.log.Timber

object ChordSheetParser {
    private val sectionHeaderRegex = Regex(
        pattern = "^(?:\\[(?<bracket>[^\\]]+)\\]|(?<label>(?i)(verse|chorus|bridge|intro|outro|hook|tag|solo|pre-chorus|prechorus|refrain)(?:\\s*\\d+)?[:.]?))$",
        options = setOf(RegexOption.IGNORE_CASE),
    )
    private val rootRegex = Regex("^[A-G](?:#|b)?", RegexOption.IGNORE_CASE)
    private val separatorTokens = setOf("|", "||", "|:", ":|", "·", "-", "~")

    fun parse(id: String, title: String, artist: String?, source: String): SongText {
        val sanitizedSource = source.replace("\r\n", "\n")
        val lines = sanitizedSource.lines()
        val sections = mutableListOf<Section>()

        var currentTag: String? = null
        var currentLines = mutableListOf<LyricLine>()

        fun flushSection() {
            if (currentLines.isNotEmpty()) {
                sections += Section(currentTag, currentLines)
                currentLines = mutableListOf()
            }
            currentTag = null
        }

        var index = 0
        while (index < lines.size) {
            val rawLine = lines[index]
            val trimmedLine = rawLine.trimEnd()
            val collapsed = trimmedLine.trim()

            if (collapsed.isEmpty()) {
                flushSection()
                index++
                continue
            }

            val headerMatch = sectionHeaderRegex.matchEntire(collapsed)
            if (headerMatch != null && currentLines.isEmpty()) {
                currentTag = headerMatch.groups["bracket"]?.value ?: headerMatch.groups["label"]?.value?.replaceFirstChar { it.uppercase() }
                index++
                continue
            }

            if (isChordLine(trimmedLine)) {
                val nextLine = lines.getOrNull(index + 1)
                if (nextLine != null && !isChordLine(nextLine.trimEnd())) {
                    parseChordAndLyricLines(trimmedLine, nextLine)?.let(currentLines::add)
                    index += 2
                    continue
                }
            }

            parseInlineLine(trimmedLine)?.let(currentLines::add)
            index++
        }

        flushSection()

        return SongText(
            id = id,
            title = title,
            artist = artist,
            sections = sections.filter { section -> section.lines.isNotEmpty() },
        )
    }

    private data class Anchor(
        val chord: Chord,
        val label: String,
        val column: Int,
    )

    private fun Anchor.toChordSpan(): ChordSpan {
        val normalizedLabel = buildString {
            append(chord.root)
            chord.quality?.let(::append)
            chord.bass?.let { bass ->
                append('/')
                append(bass)
            }
        }
        val labelText = if (normalizedLabel.isNotEmpty()) normalizedLabel else label
        return ChordSpan(
            startColumn = column,
            label = labelText,
        )
    }

    private fun parseChordAndLyricLines(chordLine: String, lyricLine: String): LyricLine? {
        val anchors = mutableListOf<Anchor>()
        var position = 0
        val normalizedChordLine = chordLine.replace('\t', ' ')
        while (position < normalizedChordLine.length) {
            if (normalizedChordLine[position].isWhitespace()) {
                position++
                continue
            }
            val start = position
            while (position < normalizedChordLine.length && !normalizedChordLine[position].isWhitespace()) {
                position++
            }
            val label = normalizedChordLine.substring(start, position)
            val normalizedLabel = normalizeChordLabel(label)
            val chord = parseChord(normalizedLabel)
            if (chord != null) {
                anchors += Anchor(
                    chord = chord,
                    label = normalizedLabel,
                    column = start,
                )
            } else {
                Timber.w("Dropping malformed chord token in chord line: %s", label)
            }
        }

        val lyricText = lyricLine.replace('\t', ' ')
        val adjustedAnchors = adjustAnchors(anchors, lyricText.length)
        return createLyricLine(
            raw = lyricLine,
            lyricText = lyricText,
            anchors = adjustedAnchors,
        )
    }

    private fun parseInlineLine(line: String): LyricLine? {
        var index = 0
        var column = 0
        val lyricBuilder = StringBuilder()
        val anchors = mutableListOf<Anchor>()
        val sanitizedLine = line.replace('\t', ' ')

        while (index < sanitizedLine.length) {
            val char = sanitizedLine[index]
            if (char == '[') {
                val closingIndex = sanitizedLine.indexOf(']', startIndex = index + 1)
                if (closingIndex == -1) {
                    lyricBuilder.append(char)
                    column++
                    index++
                    continue
                }
                val label = sanitizedLine.substring(index + 1, closingIndex)
                val normalizedLabel = normalizeChordLabel(label)
                val chord = parseChord(normalizedLabel)
                if (chord != null) {
                    anchors += Anchor(
                        chord = chord,
                        label = normalizedLabel,
                        column = column,
                    )
                } else {
                    Timber.w("Dropping malformed inline chord token: %s", label)
                }
                index = closingIndex + 1
            } else {
                lyricBuilder.append(char)
                column++
                index++
            }
        }

        val lyricText = lyricBuilder.toString()
        val adjustedAnchors = adjustAnchors(anchors, lyricText.length)
        return createLyricLine(
            raw = line,
            lyricText = lyricText,
            anchors = adjustedAnchors,
        )
    }

    private fun createLyricLine(
        raw: String,
        lyricText: String,
        anchors: List<Anchor>,
    ): LyricLine {
        val chordSpans = anchors.map { anchor ->
            anchor.toChordSpan()
        }
        val tokens = buildTokens(lyricText, anchors)
        return LyricLine(
            raw = raw,
            tokens = tokens,
            chordSpans = chordSpans,
        )
    }

    private fun buildTokens(
        lyricText: String,
        anchors: List<Anchor>,
    ): List<Token> {
        if (lyricText.isEmpty()) return anchors.map { Token.ChordAnchor(it.chord, it.column) }

        val tokens = mutableListOf<Token>()
        var anchorIndex = 0
        var wordBuilder: StringBuilder? = null
        var spaceCount = 0

        fun flushWord() {
            val word = wordBuilder?.toString()
            if (!word.isNullOrEmpty()) {
                tokens += Token.Word(word)
            }
            wordBuilder = null
        }

        fun flushSpace() {
            if (spaceCount > 0) {
                tokens += Token.Space(spaceCount)
            }
            spaceCount = 0
        }

        var charIndex = 0
        val sortedAnchors = anchors.sortedBy { it.column }
        while (charIndex <= lyricText.length) {
            while (anchorIndex < sortedAnchors.size && sortedAnchors[anchorIndex].column == charIndex) {
                flushWord()
                flushSpace()
                val anchor = sortedAnchors[anchorIndex]
                tokens += Token.ChordAnchor(anchor.chord, anchor.column)
                anchorIndex++
            }
            if (charIndex == lyricText.length) break
            val character = lyricText[charIndex]
            if (character.isWhitespace()) {
                flushWord()
                spaceCount++
            } else {
                if (spaceCount > 0) {
                    flushSpace()
                }
                if (wordBuilder == null) {
                    wordBuilder = StringBuilder()
                }
                wordBuilder!!.append(character)
            }
            charIndex++
        }

        flushWord()
        flushSpace()

        while (anchorIndex < sortedAnchors.size) {
            val anchor = sortedAnchors[anchorIndex]
            tokens += Token.ChordAnchor(anchor.chord, max(anchor.column, lyricText.length))
            anchorIndex++
        }

        return tokens
    }

    private fun adjustAnchors(
        anchors: List<Anchor>,
        lyricLength: Int,
    ): List<Anchor> {
        if (anchors.isEmpty()) return anchors
        val adjusted = mutableListOf<Anchor>()
        var lastColumn = Int.MIN_VALUE
        anchors.sortedBy { it.column }.forEach { anchor ->
            var column = anchor.column
            if (lastColumn != Int.MIN_VALUE) {
                if (column <= lastColumn) {
                    column = lastColumn + 1
                }
            }
            column = min(column, max(lyricLength, 0))
            adjusted += anchor.copy(column = column)
            lastColumn = column
        }
        return adjusted
    }

    private fun normalizeChordLabel(raw: String): String = buildString {
        raw.trim().forEach { char ->
            append(
                when (char) {
                    '♯' -> '#'
                    '♭' -> 'b'
                    else -> char
                }
            )
        }
    }

    private fun parseChord(label: String): Chord? {
        if (label.isBlank()) return null
        val normalized = label.trim().trimEnd(':')
        val main = normalized.substringBefore('/')
        val rootMatch = rootRegex.find(main) ?: return null
        val rootToken = rootMatch.value
        val root = rootToken.replaceFirstChar { it.uppercase() }
        val quality = main.removePrefix(rootToken).takeIf { it.isNotBlank() }
        val bassPart = normalized.substringAfter('/', missingDelimiterValue = "")
        val bass = if (bassPart.isNotBlank()) {
            rootRegex.find(bassPart)?.value?.replaceFirstChar { it.uppercase() }
        } else {
            null
        }
        return Chord(
            root = root,
            quality = quality,
            bass = bass,
        )
    }

    private fun isChordLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return false
        var chordCount = 0
        tokens.forEach { token ->
            val trimmedToken = token.trim().trimEnd(':')
            val cleaned = trimmedToken.trimStart('(').trimEnd(')').trimEnd('.')
            if (cleaned.isEmpty()) return@forEach
            if (separatorTokens.contains(cleaned)) return@forEach
            if (cleaned.equals("x2", ignoreCase = true) || cleaned.equals("x3", ignoreCase = true)) return@forEach
            val normalized = normalizeChordLabel(cleaned)
            val normalizedForCheck = normalized.removeSuffix(".")
            val chord = parseChord(normalized)
            if (chord != null || normalizedForCheck.equals("N.C", ignoreCase = true) || normalizedForCheck.equals("NC", ignoreCase = true)) {
                chordCount++
            } else {
                return false
            }
        }
        return chordCount > 0
    }
}
