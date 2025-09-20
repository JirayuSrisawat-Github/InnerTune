package com.zionhuang.music.lyrics.chords

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordParserTest {
    private val parser = ChordParser()

    @Test
    fun parseSimpleChordInEnglishLyrics() {
        val result = parser.parseLine("Twinkle [Am]twinkle, little [C]star")

        assertEquals("Twinkle twinkle, little star", result.cleanLyrics)
        assertEquals(2, result.chordSpans.size)
        assertEquals("Am", result.chordSpans[0].chord.displayName)
        assertEquals("C", result.chordSpans[1].chord.displayName)
    }

    @Test
    fun parseChordInThaiLyricsWithCorrectPositioning() {
        val result = parser.parseLine("ทะเ[Em7]ลแสนไกลไม่มีสิ้นสุด")

        assertEquals("ทะเลแสนไกลไม่มีสิ้นสุด", result.cleanLyrics)
        assertEquals(1, result.chordSpans.size)
        assertEquals("Em7", result.chordSpans[0].chord.displayName)
        assertTrue(result.chordSpans[0].startColumn >= 0)
    }

    @Test
    fun handleAdjacentChordsWithoutCollision() {
        val result = parser.parseLine("Start [C][G] end")

        assertEquals(2, result.chordSpans.size)
        assertTrue(result.chordSpans[1].startColumn >= result.chordSpans[0].startColumn)
    }

    @Test
    fun handleMalformedChordsGracefully() {
        val result = parser.parseLine("Text [INVALID_CHORD] more text")

        assertEquals("Text  more text", result.cleanLyrics)
        assertTrue(result.chordSpans.isEmpty())
    }
}
