package com.revibe.youtube

import com.revibe.data.JdbcLibraryStore
import com.revibe.lyrics.LyricsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class YouTubeLyricsTest {
    @Test
    fun lrclibMatchesMappedYouTubeTrack() = runBlocking {
        val source = YouTubeTestHarness.source
        val candidates = source.searchTracks("kesariya arijit singh", "IN", 10)
        val track = candidates.firstOrNull { it.title.contains("Kesariya", ignoreCase = true) }
            ?: candidates.first()
        val dir = File(System.getProperty("java.io.tmpdir"), "revibe-yt-lyrics-${System.nanoTime()}").apply { mkdirs() }
        val store = JdbcLibraryStore(File(dir, "lyrics.db"), Dispatchers.IO)
        val repository = LyricsRepository(YouTubeTestHarness.http, store)
        val lyrics = repository.lyricsFor(track)
        println(
            "[lyrics] ${track.title} / ${track.artist} / ${track.album} / ${track.durationMs}ms -> " +
                "source=${lyrics?.source} lines=${lyrics?.lines?.size} synced=${lyrics?.hasTimestamps}",
        )
        assertNotNull(lyrics, "LRCLIB returned no lyrics for the mapped track")
        assertTrue(lyrics.lines.isNotEmpty() || !lyrics.plain.isNullOrBlank())
        dir.deleteRecursively()
        Unit
    }
}
