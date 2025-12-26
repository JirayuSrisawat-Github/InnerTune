package com.zionhuang.dochord

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Integration tests for Dochord module.
 * These tests make actual network calls to verify the chord fetching flow.
 */
class DochordIntegrationTest {

    /**
     * Test fetching chords for an English song.
     * This should work as a baseline.
     */
    @Test
    fun fetchChordSheetForEnglishSong() = runTest {
        val result = Dochord.fetchChordSheet(
            title = "Hotel California",
            artist = "Eagles"
        )

        result.fold(
            onSuccess = { chordSheet ->
                println("SUCCESS: English song chord sheet fetched")
                println("Content length: ${chordSheet.text.length}")
                println("First 500 chars:\n${chordSheet.text.take(500)}")
                assertTrue(chordSheet.text.isNotBlank(), "Chord sheet should not be blank")
            },
            onFailure = { error ->
                println("ERROR for English song: ${error.javaClass.simpleName}: ${error.message}")
                error.printStackTrace()
                // Don't fail the test - we're investigating, not asserting
            }
        )
    }

    /**
     * Test fetching chords for a Thai song - the problematic case.
     * This is the specific case reported by the user.
     */
    @Test
    fun fetchChordSheetForThaiSong() = runTest {
        val result = Dochord.fetchChordSheet(
            title = "แค่บางคำ",
            artist = "Musketeers"
        )

        result.fold(
            onSuccess = { chordSheet ->
                println("SUCCESS: Thai song chord sheet fetched")
                println("Content length: ${chordSheet.text.length}")
                println("First 500 chars:\n${chordSheet.text.take(500)}")
                assertTrue(chordSheet.text.isNotBlank(), "Chord sheet should not be blank")
            },
            onFailure = { error ->
                println("ERROR for Thai song: ${error.javaClass.simpleName}: ${error.message}")
                error.printStackTrace()
                // Log the stack trace for debugging
            }
        )
    }

    /**
     * Test fetching chords for another Thai song to verify pattern.
     * This test may trigger rate limiting and retry mechanism.
     */
    @Test
    fun fetchChordSheetForAnotherThaiSong() = runTest {
        val startTime = System.currentTimeMillis()
        val result = Dochord.fetchChordSheet(
            title = "คำตอบ",
            artist = "Bodyslam"
        )

        val elapsed = System.currentTimeMillis() - startTime
        result.fold(
            onSuccess = { chordSheet ->
                println("SUCCESS: Thai song (Bodyslam) chord sheet fetched in ${elapsed}ms")
                println("Content length: ${chordSheet.text.length}")
            },
            onFailure = { error ->
                println("ERROR for Bodyslam song after ${elapsed}ms: ${error.javaClass.simpleName}: ${error.message}")
                if (error is Dochord.RateLimitException) {
                    println("  -> Rate limit error (retries may have occurred)")
                }
            }
        )
    }

    /**
     * Test that empty queries are handled gracefully.
     */
    @Test
    fun handleEmptyQuery() = runTest {
        val result = Dochord.fetchChordSheet(
            title = "",
            artist = ""
        )

        result.fold(
            onSuccess = { 
                println("Unexpected success for empty query")
            },
            onFailure = { error ->
                println("Expected failure for empty query: ${error.message}")
            }
        )
    }

    /**
     * Test with special characters in the query.
     */
    @Test
    fun fetchChordSheetWithSpecialCharacters() = runTest {
        val result = Dochord.fetchChordSheet(
            title = "ปล่อย (Let Go)",
            artist = "ป๊อบ ปองกูล"
        )

        result.fold(
            onSuccess = { chordSheet ->
                println("SUCCESS: Special chars song fetched")
                println("Content length: ${chordSheet.text.length}")
            },
            onFailure = { error ->
                println("ERROR for special chars: ${error.javaClass.simpleName}: ${error.message}")
            }
        )
    }
}
