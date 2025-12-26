package com.zionhuang.dochord

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the regex patterns used in Dochord.
 */
class DochordRegexTest {

    // Sample CSE script content that matches the actual Google CSE format
    private val sampleCseScript = """
        (function(){
            var b = window.google || {};
            google.search = {};
            google.search.cse = google.search.cse || {};
            var config = {
                "cse_token": "AKciHJkxIzF2DQZ_abc123",
                "cselibVersion": "d2b5e1f8c4a3",
                "exp": ["25c", "13c", "s2f"]
            };
            // more script content...
        })();
    """.trimIndent()

    private val TOKEN_REGEX = "\"cse_token\": \"([^\"]+)\"".toRegex()
    private val CSE_LIB_REGEX = "\"cselibVersion\": \"([^\"]+)\"".toRegex()
    private val EXP_REGEX = "\"exp\": \\[(.*?)\\]".toRegex()

    @Test
    fun extractCseToken() {
        val match = TOKEN_REGEX.find(sampleCseScript)
        assertNotNull(match, "Should find cse_token")
        assertEquals("AKciHJkxIzF2DQZ_abc123", match.groupValues[1])
    }

    @Test
    fun extractCselibVersion() {
        val match = CSE_LIB_REGEX.find(sampleCseScript)
        assertNotNull(match, "Should find cselibVersion")
        assertEquals("d2b5e1f8c4a3", match.groupValues[1])
    }

    @Test
    fun extractExperiments() {
        val match = EXP_REGEX.find(sampleCseScript)
        assertNotNull(match, "Should find experiments")
        val rawExp = match.groupValues[1]
        val experiments = rawExp
            .split(',')
            .map { it.trim().trim('"') }
            .filter { it.isNotEmpty() }
            .joinToString(",")
        assertEquals("25c,13c,s2f", experiments)
    }

    @Test
    fun handleMissingToken() {
        val scriptWithoutToken = """
            var config = {
                "cselibVersion": "abc123"
            };
        """.trimIndent()
        val match = TOKEN_REGEX.find(scriptWithoutToken)
        assertNull(match, "Should not find cse_token in script without it")
    }

    @Test
    fun handleEmptyExperiments() {
        val scriptWithEmptyExp = """
            {
                "cse_token": "token123",
                "cselibVersion": "version123",
                "exp": []
            }
        """.trimIndent()
        val match = EXP_REGEX.find(scriptWithEmptyExp)
        assertNotNull(match)
        val rawExp = match.groupValues[1]
        val experiments = rawExp
            .split(',')
            .map { it.trim().trim('"') }
            .filter { it.isNotEmpty() }
            .joinToString(",")
        assertTrue(experiments.isEmpty(), "Empty experiments array should result in empty string")
    }

    @Test
    fun queryBuildingWithThaiCharacters() {
        val title = "แค่บางคำ"
        val artist = "Musketeers"
        val query = buildQuery(title, artist)
        assertEquals("แค่บางคำ - Musketeers", query)
    }

    @Test
    fun queryBuildingWithEmptyArtist() {
        val query = buildQuery("Song Title", "")
        assertEquals("Song Title", query)
    }

    @Test
    fun queryBuildingWithEmptyTitle() {
        val query = buildQuery("", "Artist Name")
        assertEquals("Artist Name", query)
    }

    private fun buildQuery(title: String, artist: String): String {
        val normalizedTitle = title.trim()
        val normalizedArtist = artist.trim()
        return when {
            normalizedTitle.isEmpty() -> normalizedArtist
            normalizedArtist.isEmpty() -> normalizedTitle
            else -> "$normalizedTitle - $normalizedArtist"
        }
    }
}
