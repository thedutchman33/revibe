package com.revibe.provider

import com.revibe.model.MusicSourceId
import com.revibe.model.Track
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicInteger
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
    fun `musicbrainz release match returns a high resolution cover art archive image`() = runBlocking {
        val requests = AtomicInteger()
        val client = mockClient { request ->
            requests.incrementAndGet()
            when {
                request.url.encodedPath.endsWith("/release") -> respondJson(RELEASE_RESPONSE)
                request.url.encodedPath.startsWith("/release/") -> respondJson(COVER_RESPONSE)
                else -> error("unexpected request ${request.url}")
            }
        }
        val provider = MusicBrainzArtworkProvider(client, minIntervalMs = 0)
        val query = ArtworkQuery(
            artist = "Daft Punk",
            album = "Random Access Memories",
            title = "Get Lucky",
            durationMs = 369_000,
        )

        val candidate = provider.artwork(query)
        assertNotNull(candidate)
        assertEquals("https://coverartarchive.org/release/release-mbid/front-1200.jpg", candidate.url)
        assertEquals("Cover Art Archive", candidate.provider)
        assertEquals("musicbrainz-release-mbid", candidate.matchedBy)
        assertEquals(2, requests.get())

        // The second lookup is a session-cache hit.
        assertEquals(candidate.url, provider.artwork(query)?.url)
        assertEquals(2, requests.get())
    }

    @Test
    fun `musicbrainz falls back to a recording match for playlist album names`() = runBlocking {
        val client = mockClient { request ->
            when {
                request.url.encodedPath.endsWith("/release") -> respondJson("""{"releases":[]}""")
                request.url.encodedPath.endsWith("/recording") -> respondJson(RECORDING_RESPONSE)
                request.url.encodedPath.startsWith("/release/") -> respondJson(COVER_RESPONSE)
                else -> error("unexpected request ${request.url}")
            }
        }
        val provider = MusicBrainzArtworkProvider(client, minIntervalMs = 0)
        val candidate = provider.artwork(
            ArtworkQuery(
                artist = "Daft Punk",
                album = "My Playlist",
                title = "Get Lucky",
                durationMs = 369_000,
            ),
        )
        assertNotNull(candidate)
        assertEquals("musicbrainz-release-mbid", candidate.matchedBy)
        assertTrue(candidate.url.startsWith("https://coverartarchive.org/"))
    }

    @Test
    fun `musicbrainz retries once when the public service is busy`() = runBlocking {
        val releaseCalls = AtomicInteger()
        val client = mockClient { request ->
            when {
                request.url.encodedPath.endsWith("/release") -> {
                    if (releaseCalls.incrementAndGet() == 1) {
                        respond("""{"error":"busy"}""", HttpStatusCode.ServiceUnavailable)
                    } else {
                        respondJson(RELEASE_RESPONSE)
                    }
                }
                request.url.encodedPath.startsWith("/release/") -> respondJson(COVER_RESPONSE)
                else -> error("unexpected request ${request.url}")
            }
        }
        val provider = MusicBrainzArtworkProvider(client, minIntervalMs = 0)
        val candidate = provider.artwork(
            ArtworkQuery(
                artist = "Daft Punk",
                album = "Random Access Memories",
                title = "Get Lucky",
                durationMs = 369_000,
            ),
        )
        assertNotNull(candidate)
        assertEquals(2, releaseCalls.get())
    }

    @Test
    fun `musicbrainz returns null when cover art archive has no front image`() = runBlocking {
        val client = mockClient { request ->
            when {
                request.url.encodedPath.endsWith("/release") -> respondJson(RELEASE_RESPONSE)
                request.url.encodedPath.startsWith("/release/") -> respondJson("""{"images":[]}""")
                else -> error("unexpected request ${request.url}")
            }
        }
        val provider = MusicBrainzArtworkProvider(client, minIntervalMs = 0)
        assertNull(
            provider.artwork(
                ArtworkQuery(
                    artist = "Daft Punk",
                    album = "Random Access Memories",
                    title = "Get Lucky",
                    durationMs = 369_000,
                ),
            ),
        )
    }

    private fun mockClient(
        handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData,
    ): HttpClient = HttpClient(MockEngine(handler)) {
        expectSuccess = false
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(
        content = body,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private companion object {
        const val RELEASE_RESPONSE = """
            {"releases":[{"id":"release-mbid","title":"Random Access Memories","score":100,
            "artist-credit":[{"name":"Daft Punk"}]}]}
        """
        const val RECORDING_RESPONSE = """
            {"recordings":[{"id":"recording-mbid","title":"Get Lucky","score":100,
            "artist-credit":[{"name":"Daft Punk"}],
            "releases":[{"id":"release-mbid","title":"Random Access Memories"}]}]}
        """
        const val COVER_RESPONSE = """
            {"images":[{"front":true,"approved":true,
            "image":"http://coverartarchive.org/release/release-mbid/front.jpg",
            "thumbnails":{"1200":"https://coverartarchive.org/release/release-mbid/front-1200.jpg"}}]}
        """
    }
}
