package com.zionhuang.music.lyrics.chords

class RobustChordParser : ChordParser() {
    override fun parseLine(input: String): LyricLine {
        return try {
            super.parseLine(input)
        } catch (error: Exception) {
            LyricLine(
                raw = input,
                cleanLyrics = input.replace(Regex("""\\[[^]]*]"""), ""),
                tokens = emptyList(),
                chordSpans = emptyList()
            )
        }
    }

    fun validateSongStructure(song: SongText): ValidationResult {
        val issues = mutableListOf<String>()
        if (song.sections.isEmpty()) {
            issues += "Song has no content sections"
        }
        val totalLines = song.sections.sumOf { it.lines.size }
        if (totalLines > 500) {
            issues += "Song is very long (${totalLines} lines)"
        }
        return if (issues.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Warning(issues)
        }
    }
}

sealed class ValidationResult {
    object Success : ValidationResult()
    data class Warning(val messages: List<String>) : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}
