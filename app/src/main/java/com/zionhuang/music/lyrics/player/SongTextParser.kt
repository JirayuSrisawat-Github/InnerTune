package com.zionhuang.music.lyrics.player

/**
 * Splits raw song text into structured sections and lines while parsing chord annotations.
 */
class SongTextParser(
    private val chordParser: ChordParser = ChordParser()
) {
    private val sectionBracketPattern = Regex("""^\[(.+)]$""")
    private val sectionSuffixPattern = Regex("""^([A-Za-z].*?):$""")
    private val chordOnlyPattern = Regex("""^([A-G][#♯♭b]?)([^/]*)?(?:/([A-G][#♯♭b]?))?$""")

    fun parse(
        id: String,
        title: String,
        artist: String?,
        rawText: String
    ): SongText {
        val sections = mutableListOf<Section>()
        var currentTag: String? = null
        var currentLines = mutableListOf<LyricLine>()

        fun commitSection() {
            if (currentLines.isEmpty()) return
            sections += Section(
                tag = currentTag?.takeIf { it.isNotBlank() },
                lines = currentLines.toList()
            )
            currentLines = mutableListOf()
        }

        rawText.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim()
            val bracketMatch = sectionBracketPattern.matchEntire(trimmed)
            val suffixMatch = sectionSuffixPattern.matchEntire(trimmed)

            when {
                trimmed.isEmpty() -> {
                    currentLines += chordParser.parseLine("")
                }
                bracketMatch != null && !chordOnlyPattern.matches(bracketMatch.groupValues[1].trim()) -> {
                    commitSection()
                    currentTag = bracketMatch.groupValues[1].trim()
                }
                suffixMatch != null && !chordOnlyPattern.matches(suffixMatch.groupValues[1].trim()) -> {
                    commitSection()
                    currentTag = suffixMatch.groupValues[1].trim()
                }
                else -> {
                    currentLines += chordParser.parseLine(rawLine)
                }
            }
        }

        commitSection()

        val nonEmptySections = sections.filter { section ->
            section.lines.any { it.cleanLyrics.isNotEmpty() || it.chordSpans.isNotEmpty() }
        }

        val finalSections = if (nonEmptySections.isEmpty()) {
            listOf(
                Section(
                    tag = null,
                    lines = listOf(
                        LyricLine(
                            raw = "",
                            cleanLyrics = "",
                            tokens = emptyList(),
                            chordSpans = emptyList()
                        )
                    )
                )
            )
        } else {
            nonEmptySections
        }

        return SongText(
            id = id,
            title = title,
            artist = artist,
            sections = finalSections
        )
    }
}
