package com.zionhuang.music.songtext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongTextParserTest {
    @Test
    fun parseThaiInlineChordCreatesAnchor() {
        val raw = "ทะเ[Em7]ลแสนไกลไม่มีสิ้นสุด"
        val songText = SongTextParser.parse(
            id = "test",
            title = "Test",
            artist = null,
            raw = raw,
        )

        val line = songText.sections.first().lines.first()
        assertEquals("ทะเลแสนไกลไม่มีสิ้นสุด", line.text)
        assertEquals(1, line.chordSpans.size)
        val chordSpan = line.chordSpans.first()
        assertEquals("Em7", chordSpan.label)
        assertEquals(3, chordSpan.startColumn)
    }

    @Test
    fun supportsSlashAndSharpChords() {
        val raw = "Play [G/B]this and [F#m]that"
        val songText = SongTextParser.parse(
            id = "test",
            title = "Test",
            artist = null,
            raw = raw,
        )

        val line = songText.sections.first().lines.first()
        assertEquals("Play this and that", line.text)
        val labels = line.chordSpans.map { it.label }
        assertTrue(labels.contains("G/B"))
        assertTrue(labels.contains("F#m"))
    }

    @Test
    fun offsetsAdjacentChordsToAvoidOverlap() {
        val raw = "[C][G]Test"
        val songText = SongTextParser.parse(
            id = "test",
            title = "Test",
            artist = null,
            raw = raw,
        )

        val line = songText.sections.first().lines.first()
        assertEquals("Test", line.text)
        assertEquals(2, line.chordSpans.size)
        val first = line.chordSpans[0]
        val second = line.chordSpans[1]
        assertEquals("C", first.label)
        assertEquals("G", second.label)
        assertTrue(second.startColumn >= first.startColumn + 2)
    }
}
