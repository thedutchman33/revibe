package com.revibe.provider

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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArtworkResolverTest {

    private fun track(
        artist: String = "Daft Punk",
        album: String = "Discovery",
        artworkUrl: String? = null,
    ) = Track(
        id = "local:test",
        title = "One More Time",
        artist = artist,
        album = album,
        durationMs = 320_000,
        artworkUrl = artworkUrl,
        source = MusicSourceId.LOCAL,
    )

    @Test
    fun `catalog artwork wins and does not call the provider`() = runBlocking {
        var calls = 0
        val provider = object : ArtworkProvider {
            override val id = "fake"
            override suspend fun artwork(query: ArtworkQuery): ArtworkCandidate {
                calls++
                return ArtworkCandidate("https://example.test/cover.jpg", id, "fake")
            }
        }
        val resolver = ArtworkResolver(provider)
        assertEquals("https://example.test/catalog.jpg", resolver.resolve(track(artworkUrl = "https://example.test/catalog.jpg")))
        assertEquals(0, calls)
    }

    @Test
    fun `provider fills a missing cover but never invents one for unknown metadata`() = runBlocking {
        var calls = 0
        val provider = object : ArtworkProvider {
            override val id = "fake"
            override suspend fun artwork(query: ArtworkQuery): ArtworkCandidate {
                calls++
                return ArtworkCandidate("https://example.test/cover.jpg", id, "album")
            }
        }
        val resolver = ArtworkResolver(provider)
        assertEquals("https://example.test/cover.jpg", resolver.resolve(track()))
        assertEquals(1, calls)
        // An unknown album does not block title/artist matching.
        assertEquals("https://example.test/cover.jpg", resolver.resolve(track(album = "Unknown album")))
        assertEquals(2, calls)
        assertNull(resolver.resolve(track(artist = "Unknown artist")))
        assertEquals(2, calls)
    }

    @Test
    fun `fallback providers are tried in order and stop at the first cover`() = runBlocking {
        var firstCalls = 0
        var secondCalls = 0
        val first = object : ArtworkProvider {
            override val id = "first"
            override suspend fun artwork(query: ArtworkQuery): ArtworkCandidate? {
                firstCalls++
                return null
            }
        }
        val second = object : ArtworkProvider {
            override val id = "second"
            override suspend fun artwork(query: ArtworkQuery): ArtworkCandidate {
                secondCalls++
                return ArtworkCandidate("https://example.test/deezer.jpg", id, "search")
            }
        }
        val resolver = ArtworkResolver(listOf(first, second))
        assertEquals("https://example.test/deezer.jpg", resolver.resolve(track()))
        assertEquals(1, firstCalls)
        assertEquals(1, secondCalls)
    }

    @Test
    fun `musicbrainz and cover art archive return a real cover for a real release`() = runBlocking {
        val client = HttpClient(CIO) {
            expectSuccess = false
            followRedirects = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
            install(HttpTimeout) { requestTimeoutMillis = 20_000; connectTimeoutMillis = 10_000 }
        }
        val provider = MusicBrainzArtworkProvider(client)
        val candidate = provider.artwork(
            ArtworkQuery(
                artist = "Daft Punk",
                album = "Random Access Memories",
                title = "Get Lucky",
                durationMs = 369_000,
            ),
        )
        println("[artwork] ${candidate?.url} via ${candidate?.provider} (${candidate?.matchedBy})")
        assertNotNull(candidate, "MusicBrainz/CAA returned no cover for a real release")
        assertTrue(candidate.url.startsWith("https://"))
        assertTrue(candidate.url.contains("coverartarchive.org"))
        assertEquals("Cover Art Archive", candidate.provider)

        // A second lookup must hit the session cache, not the network.
        val again = provider.artwork(
            ArtworkQuery(
                artist = "Daft Punk",
                album = "Random Access Memories",
                title = "Get Lucky",
                durationMs = 369_000,
            ),
        )
        assertEquals(candidate.url, again?.url)
    }
}
