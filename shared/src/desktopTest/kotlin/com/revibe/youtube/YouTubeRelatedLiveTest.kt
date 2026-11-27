package com.revibe.youtube

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Opt-in live diagnostic for YouTube Music's native Up next queue and its radio
 * continuation. Run with REVIBE_LIVE=1.
 */
class YouTubeRelatedLiveTest {
    @Test
    fun nativeQueueReturnsOrderedTracksAndContinuation() = runBlocking {
        if (System.getenv("REVIBE_LIVE") != "1") {
            println("NATIVE_QUEUE skipped (set REVIBE_LIVE=1 to run)")
            return@runBlocking
        }
        val source = YouTubeTestHarness.source
        val seed = source.searchTracks("Aarzu Noor Khan Madhurxo", "IN", 1).firstOrNull()
        assertTrue(seed != null, "Live search returned no seed track")

        val first = source.nativeQueue(seed!!, limit = 40)
        println(
            "NATIVE_QUEUE count=${first.tracks.size} continuation=${first.continuation != null} " +
                "first=${first.tracks.take(5).joinToString(" | ") { "${it.title} / ${it.artist}" }}",
        )
        assertTrue(first.tracks.isNotEmpty(), "Native Up next returned no tracks")
        assertTrue(first.continuation != null, "Native queue returned no continuation token")

        val second = source.nativeQueue(seed, continuation = first.continuation, limit = 40)
        println(
            "NATIVE_QUEUE_CONT count=${second.tracks.size} continuation=${second.continuation != null} " +
                "first=${second.tracks.take(5).joinToString(" | ") { "${it.title} / ${it.artist}" }}",
        )
        assertTrue(second.tracks.isNotEmpty(), "Native queue continuation returned no tracks")
        assertTrue(
            second.tracks.map { it.id }.distinct().size == second.tracks.size,
            "Continuation contained duplicate video ids",
        )
        Unit
    }
}
