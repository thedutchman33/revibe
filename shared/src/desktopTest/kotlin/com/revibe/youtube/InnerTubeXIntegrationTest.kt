package com.revibe.youtube

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InnerTubeXIntegrationTest {
    @Test
    fun initializesAndSearches() = runBlocking {
        val source = YouTubeTestHarness.source
        val results = source.searchTracks("arijit singh", "IN", 5)
        println("[integration] status=${source.status.value} diagnostic=${source.diagnostic.value} results=${results.size}")
        assertTrue(results.isNotEmpty(), "Search returned no tracks")
        assertEquals(YouTubeAvailability.AVAILABLE, source.status.value)
        assertTrue(source.diagnostic.value.isNotBlank())
    }
}
