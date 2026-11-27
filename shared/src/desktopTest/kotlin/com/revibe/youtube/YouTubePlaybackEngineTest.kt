package com.revibe.youtube

import com.revibe.playback.LocalStreamProxy
import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertTrue

class YouTubePlaybackEngineTest {
    @Test
    fun javaFxPlaysResolvedStream() = runBlocking {
        val source = YouTubeTestHarness.source
        val track = YouTubeTestHarness.firstTrack()
        val stream = source.resolveStream(track)
        val proxy = LocalStreamProxy()
        val playableUrl = proxy.expose(stream.url, stream.headers, stream.mimeType)
        var url: String? = null
        Platform.setImplicitExit(false)
        runCatching { Platform.startup {} }
        val ready = CountDownLatch(1)
        val failure = AtomicReference<String?>(null)
        val playerRef = AtomicReference<MediaPlayer?>(null)
        Platform.runLater {
            try {
                val player = MediaPlayer(Media(playableUrl))
                playerRef.set(player)
                url = playableUrl
                player.setOnReady { ready.countDown() }
                player.setOnError {
                    failure.set(player.error?.message ?: "unknown")
                    ready.countDown()
                }
                player.play()
            } catch (t: Throwable) {
                failure.set("${t::class.simpleName}: ${t.message}")
                ready.countDown()
            }
        }
        assertTrue(ready.await(30, TimeUnit.SECONDS), "JavaFX never became ready")
        Thread.sleep(5_000)
        var position = 0L
        var playing = false
        Platform.runLater {
            val player = playerRef.get()
            position = player?.currentTime?.takeIf { it != Duration.UNKNOWN }?.toMillis()?.toLong() ?: 0L
            playing = player?.status == MediaPlayer.Status.PLAYING ||
                player?.status == MediaPlayer.Status.PAUSED ||
                player?.status == MediaPlayer.Status.STOPPED
            player?.dispose()
        }
        Thread.sleep(300)
        println("[playback] urlResolved=${url != null} statusReady=true positionMs=$position failure=${failure.get()}")
        proxy.close()
        assertTrue(failure.get() == null, "Playback failed: ${failure.get()}")
        assertTrue(position > 750, "Playback position did not advance: ${position}ms")
    }
}
