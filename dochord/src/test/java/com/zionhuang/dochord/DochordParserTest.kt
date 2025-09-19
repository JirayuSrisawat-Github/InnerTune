package com.zionhuang.dochord

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.junit.Test

class DochordParserTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun parseChordHtmlProducesChordSheet() {
        val html = fixture("chord_response.html")
        val parsed = ChordParser.parse(html)

        assertTrue(parsed.contains("[Em]"), "Expected parsed chords to include bracketed chord markers")
        val lines = parsed.lines()
        assertTrue(lines.first().contains("INTRO"), "Expected first line to start with INTRO section")
        assertTrue(lines.size > 10, "Expected multiple chord lines to be extracted")
    }

    @Test
    fun parseSearchPayloadExtractsFormattedUrl() {
        val raw = fixture("chord_search.js")
        val payload = raw.substringAfter('(').substringBeforeLast(")")
        val response = json.decodeFromString(Dochord.SearchResponse.serializer(), payload)
        val firstUrl = response.results?.firstOrNull()?.formattedUrl

        assertEquals("https://www.dochord.com/364942/", firstUrl)
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource(name)) {
            "Fixture $name not found"
        }.readText()
}
