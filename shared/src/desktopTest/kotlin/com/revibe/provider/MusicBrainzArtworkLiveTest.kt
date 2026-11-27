package com.revibe.provider

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Real MusicBrainz/Cover Art Archive integration check, kept out of the
 * deterministic suite because the public service returns HTTP 503 when busy
 * or rate limited. Run explicitly:
 *
 *   REVIBE_ARTWORK_LIVE=1 ./gradlew :shared:desktopTest \
 *     --tests 'com.revibe.provider.MusicBrainzArtworkLiveTest' --rerun-tasks
 */
class MusicBrainzArtworkLiveTest {
    @Test
    fun resolvesARealCoverFromThePublicServices() = runBlocking {
        if (System.getenv("REVIBE_ARTWORK_LIVE") != "1") return@runBlocking
        val client = HttpClient(CIO) {
            expectSuccess = false
            followRedirects = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
            install(HttpTimeout) { requestTimeoutMillis = 20_000; connectTimeoutMillis = 10_000 }
        }
        try {
            val query = ArtworkQuery(
                artist = "Daft Punk",
                album = "Random Access Memories",
                title = "Get Lucky",
                durationMs = 369_000,
            )
            // MusicBrainz answers 503 while its public rate limiter is busy.
            // Retry with a fresh provider so a cached miss does not hide it.
            var candidate: ArtworkCandidate? = null
            repeat(4) { attempt ->
                candidate = MusicBrainzArtworkProvider(client, timeoutMs = 20_000).artwork(query)
                if (candidate != null) return@repeat
                if (attempt < 3) delay(2_000)
            }
            println("[artwork-live] ${candidate?.url} via ${candidate?.provider} (${candidate?.matchedBy})")
            assertNotNull(candidate, "MusicBrainz/CAA returned no cover; the public service may be busy")
            assertTrue(candidate!!.url.startsWith("https://"))
            assertTrue(candidate!!.url.contains("coverartarchive.org"))
        } finally {
            client.close()
        }
    }
}
