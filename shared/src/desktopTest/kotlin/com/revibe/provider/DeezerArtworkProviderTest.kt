package com.revibe.provider

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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeezerArtworkProviderTest {

    private val body = """
        {
          "data": [
            {
              "title": "Different Song",
              "rank": 900000,
              "artist": { "name": "Arijit Singh" },
              "album": { "title": "Uncut Bollywood", "cover_xl": "https://cdn.test/wrong.jpg" }
            },
            {
              "title": "Gehra Hua",
              "rank": 500000,
              "artist": { "name": "Arijit Singh" },
              "album": {
                "title": "Uncut Bollywood",
                "cover_xl": "https://cdn.test/gehra-1000.jpg",
                "cover_big": "https://cdn.test/gehra-500.jpg"
              }
            }
          ]
        }
    """.trimIndent()

    private fun provider(engine: MockEngine = MockEngine {
        respond(
            content = body,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }): DeezerArtworkProvider {
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return DeezerArtworkProvider(client)
    }

    @Test
    fun `accepts only the exact title and artist and returns the 1000px cover`() = runBlocking {
        var searchText = ""
        val engine = MockEngine { request ->
            searchText = request.url.parameters["q"].orEmpty()
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val candidate = provider(engine).artwork(
            ArtworkQuery(
                artist = "Arijit Singh",
                album = "Uncut Bollywood",
                title = "Gehra Hua",
                durationMs = 231_000,
            ),
        )
        assertEquals("Gehra Hua Arijit Singh", searchText)
        assertEquals("https://cdn.test/gehra-1000.jpg", candidate?.url)
        assertEquals("Deezer", candidate?.provider)
        assertEquals(1000, candidate?.width)
        assertTrue(candidate?.url?.startsWith("https://") == true)
    }

    @Test
    fun `rejects a result by another artist`() = runBlocking {
        assertNull(
            provider().artwork(
                ArtworkQuery(
                    artist = "Anuv Jain",
                    album = "Uncut Bollywood",
                    title = "Gehra Hua",
                    durationMs = 231_000,
                ),
            ),
        )
    }

    @Test
    fun `returns null for unknown metadata without making a request`() = runBlocking {
        var requests = 0
        val engine = MockEngine {
            requests++
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        assertNull(
            provider(engine).artwork(
                ArtworkQuery(
                    artist = "Unknown artist",
                    album = "Unknown album",
                    title = "",
                    durationMs = 0,
                ),
            ),
        )
        assertEquals(0, requests)
    }
}
