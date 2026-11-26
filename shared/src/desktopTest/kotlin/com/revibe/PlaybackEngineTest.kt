package com.revibe

import com.revibe.catalog.ITunesCatalogSource
import com.revibe.playback.JavaFxPlaybackEngine
import com.revibe.playback.PlaybackEngine
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Plays a real catalog preview through the desktop engine and waits for decoder
 * progress. This is the check that separates real playback from a mocked one.
 */
class PlaybackEngineTest {

    @Test
    fun `engine decodes a real preview stream`() = runBlocking {
        val client = HttpClient(CIO) {
            expectSuccess = false
            install(ContentNegotiation) {
                val format = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
                json(format)
                json(format, contentType = ContentType.Text.JavaScript)
            }
            install(HttpTimeout) { requestTimeoutMillis = 20_000; connectTimeoutMillis = 10_000 }
        }
        val track = ITunesCatalogSource(client)
            .searchTracks("arijit singh", "IN", 12)
            .firstOrNull { !it.streamUrl.isNullOrBlank() }
        assertNotNull(track, "catalog returned no preview URL to play")

        val engine = JavaFxPlaybackEngine()
        val firstProgress = CompletableDeferred<Long>()
        var lastError: String? = null

        engine.setListener(
            object : PlaybackEngine.Listener {
                override fun onReady(durationMs: Long) {
                    println("[audio] ready durationMs=$durationMs")
                }

                override fun onProgress(positionMs: Long) {
                    if (positionMs > 250 && !firstProgress.isCompleted) firstProgress.complete(positionMs)
                }

                override fun onPlayingChanged(isPlaying: Boolean) {
                    println("[audio] playing=$isPlaying")
                }

                override fun onBuffering(isBuffering: Boolean) {}

                override fun onEnded() {}

                override fun onError(message: String) {
                    lastError = message
                    if (!firstProgress.isCompleted) {
                        firstProgress.completeExceptionally(IllegalStateException(message))
                    }
                }
            },
        )

        engine.load(track!!.streamUrl!!, autoPlay = true)
        val position = runCatching { withTimeout(30_000) { firstProgress.await() } }
        println("[audio] position=${position.getOrDefault(-1)} error=$lastError")
        engine.release()
        // The toolkit keeps a non-daemon thread alive; shut it down so the test worker exits.
        javafx.application.Platform.runLater { javafx.application.Platform.exit() }
        kotlinx.coroutines.delay(300)

        assertTrue(position.isSuccess, "engine did not advance: ${lastError ?: position.exceptionOrNull()?.message}")
        assertTrue(position.getOrDefault(0) > 0)
    }
}
