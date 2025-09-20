package com.zionhuang.music.lyrics.chords

class RobustChordParser : ChordParser() {
    override fun parseLine(input: String): LyricLine =
        try {
            super.parseLine(input)
        } catch (error: Exception) {
            LyricLine(
                raw = input,
                cleanLyrics = input.replace(Regex("""\\[[^]]*]"""), ""),
                tokens = emptyList(),
                chordSpans = emptyList()
            )
        }

    fun validateSongStructure(song: SongText): ValidationResult {
        val issues = buildList {
            if (song.sections.isEmpty()) {
                add("Song has no content sections")
            }
            val totalLines = song.sections.sumOf { it.lines.size }
            if (totalLines > 500) {
                add("Song is very long ($totalLines lines) - performance may be affected")
            }
        }
        return if (issues.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Warning(issues)
        }
    }
}

sealed class ValidationResult {
    data object Success : ValidationResult()
    data class Warning(val messages: List<String>) : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}
