package com.zionhuang.music.chords

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordSheetParserTest {
    @Test
    fun `parse inline chord anchors with thai text`() {
        val song = ChordSheetParser.parse(
            id = "id",
            title = "ทะเล",
            artist = null,
            source = "ทะเ[Em7]ลแสนไกลไม่มีสิ้นสุด",
        )

        val section = song.sections.single()
        val line = section.lines.single()
        assertEquals("ทะเลแสนไกลไม่มีสิ้นสุด", line.lyricText)
        assertEquals(1, line.chordSpans.size)
        val span = line.chordSpans.first()
        assertEquals("Em7", span.label)
        assertEquals(2, span.startColumn)
        val anchorToken = line.tokens.filterIsInstance<Token.ChordAnchor>().single()
        assertEquals(2, anchorToken.charIndex)
        assertEquals("Em7", anchorToken.chord.root + (anchorToken.chord.quality ?: ""))
    }

    @Test
    fun `supports slash chords and sharps`() {
        val song = ChordSheetParser.parse(
            id = "id",
            title = "Song",
            artist = "Artist",
            source = "[G/B]Line one\n[F#m]Line two",
        )

        val firstLine = song.sections.first().lines.first()
        assertEquals("G/B", firstLine.chordSpans.first().label)

        val secondLine = song.sections.first().lines[1]
        assertEquals("F#m", secondLine.chordSpans.first().label)
    }

    @Test
    fun `adjusts adjacent chord columns`() {
        val song = ChordSheetParser.parse(
            id = "id",
            title = "Song",
            artist = null,
            source = "[C][G]Hello",
        )

        val line = song.sections.single().lines.single()
        assertEquals(2, line.chordSpans.size)
        val columns = line.chordSpans.map { it.startColumn }
        assertTrue(columns[0] < columns[1])
        assertEquals(0, columns[0])
        assertEquals(1, columns[1])
    }
}
