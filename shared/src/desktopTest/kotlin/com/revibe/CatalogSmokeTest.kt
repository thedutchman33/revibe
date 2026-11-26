package com.revibe

import com.revibe.catalog.ITunesCatalogSource
import com.revibe.lyrics.LrcParser
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogSmokeTest {

    private fun client() = HttpClient(CIO) {
        expectSuccess = false
        install(ContentNegotiation) {
            val format = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
            json(format)
            json(format, contentType = ContentType.Text.JavaScript)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
        }
    }

    @Test
    fun `raw itunes request is reachable`() = runBlocking {
        val response = client().get("https://itunes.apple.com/search") {
            parameter("term", "arijit singh")
            parameter("entity", "song")
            parameter("limit", 3)
            parameter("country", "IN")
        }
        println("[smoke] status=${response.status}")
        val body = response.bodyAsText()
        println("[smoke] body=${body.take(200)}")
        assertTrue(response.status.value in 200..299, "iTunes returned ${response.status}")
    }

    @Test
    fun `itunes source maps real tracks`() = runBlocking {
        val source = ITunesCatalogSource(client())
        val tracks = source.searchTracks("arijit singh", "IN", 5)
        println("[smoke] mapped tracks=${tracks.size}")
        tracks.take(3).forEach { println("[smoke] ${it.title} — ${it.artist} (${it.durationMs}ms) ${it.streamUrl != null}") }
        assertTrue(tracks.isNotEmpty(), "iTunes source returned no tracks")
        assertTrue(tracks.all { it.title.isNotBlank() })
        assertTrue(tracks.any { it.artworkUrl != null })
    }

    @Test
    fun `lrc parser handles line and word sync`() {
        val synced = """
            [00:01.50]First line
            [00:03.20]<00:03.20>Hello <00:03.80>world
            [00:05.00]
        """.trimIndent()
        val lines = LrcParser.parse(synced)
        assertEquals(3, lines.size)
        assertEquals(1500, lines[0].timeMs)
        assertEquals("Hello world", lines[1].text)
        assertEquals(2, lines[1].words.size)
        assertTrue(LrcParser.parse(null).isEmpty())
    }
}
