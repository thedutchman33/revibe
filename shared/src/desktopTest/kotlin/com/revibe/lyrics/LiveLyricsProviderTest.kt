package com.revibe.lyrics

import com.revibe.model.MusicSourceId
import com.revibe.model.Track
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LiveLyricsProviderTest {
    private val client = HttpClient(CIO) {
        expectSuccess = false
        followRedirects = true
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
        install(HttpTimeout) { requestTimeoutMillis = 25_000; connectTimeoutMillis = 10_000 }
    }

    @Test
    fun `unison returns word-synced English lyrics for an exact video id`() = runBlocking {
        val track = Track(
            id = "ytm:track:Ugr4KKK_SqY",
            title = "Explorer",
            artist = "Justice",
            album = "Hyperdrama",
            durationMs = 249_000,
            source = MusicSourceId.YOUTUBE_MUSIC,
        )
        val lyrics = UnisonLyricsSource(client).fetch(track)
        println("[lyrics] Unison: ${lyrics?.lines?.size} lines, word=${lyrics?.wordSynced}, source=${lyrics?.source}")
        assertNotNull(lyrics, "Unison returned no lyrics for Ugr4KKK_SqY")
        assertEquals(150_140, lyrics.lines.first().timeMs)
        assertTrue(lyrics.lines.size > 10)
        assertTrue(lyrics.wordSynced)
    }

    @Test
    fun `lrc red returns word-synced Hindi lyrics with a verified match`() = runBlocking {
        val track = Track(
            id = "ytm:track:BddP6PYo2gs",
            title = "Kesariya",
            artist = "Arijit Singh",
            album = "Brahmastra",
            durationMs = 268_000,
            source = MusicSourceId.YOUTUBE_MUSIC,
        )
        val lyrics = LrcRedLyricsSource(client).fetch(track)
        println(
            "[lyrics] lrc.red: ${lyrics?.lines?.size} lines, word=${lyrics?.wordSynced}, " +
                "first=${lyrics?.lines?.firstOrNull()?.text}",
        )
        assertNotNull(lyrics, "lrc.red returned no lyrics for Kesariya")
        assertEquals("lrc.red", lyrics.source)
        assertTrue(lyrics.lines.size > 10)
        assertTrue(lyrics.wordSynced)
    }
}
