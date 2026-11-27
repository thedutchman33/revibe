package com.revibe

import com.revibe.playback.JavaFxPlaybackEngine
import com.revibe.playback.PlaybackEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Decodes the bundled FFmpeg-generated tone through the desktop engine and
 * waits for real position progress. This is the check that separates actual
 * playback from a mocked one, without depending on a network catalog.
 */
class PlaybackEngineTest {

    @Test
    fun `engine decodes a local media file`() = runBlocking {
        val resource = requireNotNull(javaClass.getResource("/media/tone.mp3")) {
            "bundled test media is missing"
        }
        val media = File(resource.toURI())

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

        engine.load(media.toURI().toString(), autoPlay = true)
        val position = runCatching { withTimeout(30_000) { firstProgress.await() } }
        println("[audio] position=${position.getOrDefault(-1)} error=$lastError")
        engine.release()
        // Keep the JavaFX toolkit alive: the YouTube playback test shares it.
        delay(300)

        assertTrue(position.isSuccess, "engine did not advance: ${lastError ?: position.exceptionOrNull()?.message}")
        assertTrue(position.getOrDefault(0) > 0)
    }
}
