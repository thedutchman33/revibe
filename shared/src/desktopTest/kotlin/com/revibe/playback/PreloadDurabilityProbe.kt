package com.revibe.playback

import com.revibe.youtube.YouTubeTestHarness
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Manual probe for the "prefetch sits warm while the current track plays" case.
 *
 *   REVIBE_LATENCY_PROBE=1 ./gradlew :shared:desktopTest \
 *     --tests 'com.revibe.playback.PreloadDurabilityProbe' -PrevibeLiveTests=true --rerun-tasks
 */
class PreloadDurabilityProbe {
    private val lastProgress = AtomicLong(0)

    @Test
    fun measureWarmPrefetchAfterWaiting() = runBlocking {
        if (System.getenv("REVIBE_LATENCY_PROBE") != "1") return@runBlocking
        val tracks = YouTubeTestHarness.tracks.take(2)
        assertTrue(tracks.size >= 2, "Need two live tracks")
        val resolver = DesktopTrackStreamResolver(YouTubeTestHarness.source)
        val engine = FfmpegPlaybackEngine().also { it.setVolume(0.01f) }
        try {
            val first = resolver.resolve(tracks[0])
            val coldStart = System.nanoTime()
            val coldAudio = engine.loadAndAwaitAudio(first.uri!!, "cold")
            println("[durability] cold audio in ${ms(coldStart, coldAudio)}ms")

            val next = resolver.prefetch(tracks[1])!!
            val preloadStart = System.nanoTime()
            engine.preload(next.uri!!)
            println("[durability] preload returned in ${ms(preloadStart, System.nanoTime())}ms")
            val waitSeconds = System.getenv("REVIBE_PRELOAD_WAIT_SEC")?.toIntOrNull() ?: 15
            for (second in 1..waitSeconds) {
                Thread.sleep(1_000)
                if (second % 5 == 0) println("[durability] spare has been warm for ${second}s")
            }

            val nextStart = System.nanoTime()
            val audio = engine.loadAndAwaitAudio(next.uri!!, "next")
            println(
                "[durability] next loadToAudio=${ms(nextStart, audio)}ms",
            )
            Thread.sleep(1_500)
            println("[durability] next position after 1.5s=${lastProgress.get()}ms")
            assertTrue(
                lastProgress.get() >= 1_000,
                "Preloaded playback stalled at ${lastProgress.get()}ms",
            )

            lastProgress.set(0)
            val seekStart = System.nanoTime()
            engine.seekTo(45_000)
            // The engine reports the target immediately; require progress inside a
            // window after it so neither an echoed target nor a jump to EOF passes.
            val reached = awaitProgressIn(30_000, 75_000, 30_000)
            val landed = lastProgress.get()
            val advanced = awaitProgressIn(landed + 2_000, landed + 15_000, 20_000)
            println(
                "[durability] seek45 landed=$reached advanced=$advanced " +
                    "in ${ms(seekStart, System.nanoTime())}ms (last=${lastProgress.get()})",
            )

            // Control: seek the same stream opened cold, without a preload.
            engine.loadAndAwaitAudio(next.uri!!, "cold-seek")
            Thread.sleep(500)
            lastProgress.set(0)
            val coldSeekStart = System.nanoTime()
            engine.seekTo(45_000)
            val coldReached = awaitProgressIn(30_000, 75_000, 30_000)
            val coldLanded = lastProgress.get()
            val coldAdvanced = awaitProgressIn(coldLanded + 2_000, coldLanded + 15_000, 20_000)
            println(
                "[durability] cold seek45 landed=$coldReached advanced=$coldAdvanced " +
                    "in ${ms(coldSeekStart, System.nanoTime())}ms (last=${lastProgress.get()})",
            )

            assertTrue(
                reached && advanced,
                "Seeking after a preloaded swap did not resume playing near 45s " +
                    "(landed=$reached advanced=$advanced last=${lastProgress.get()})",
            )
            assertTrue(
                coldReached && coldAdvanced,
                "A cold seek to 45s did not resume playing " +
                    "(landed=$coldReached advanced=$coldAdvanced last=${lastProgress.get()})",
            )
        } finally {
            engine.release()
            resolver.close()
        }
    }

    private fun awaitProgressIn(min: Long, max: Long, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (lastProgress.get() in min..max) return true
            Thread.sleep(50)
        }
        return false
    }

    private fun ms(a: Long, b: Long) = "%.0f".format((b - a) / 1_000_000.0)

    private fun FfmpegPlaybackEngine.loadAndAwaitAudio(url: String, label: String): Long {
        val latch = CountDownLatch(1)
        val progress = CountDownLatch(1)
        val audioAt = AtomicLong(0)
        val errors = mutableListOf<String>()
        setListener(object : PlaybackEngine.Listener {
            override fun onReady(durationMs: Long) = Unit
            override fun onProgress(positionMs: Long) {
                lastProgress.set(positionMs)
                if (positionMs > 0) progress.countDown()
            }

            override fun onPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && latch.count > 0) {
                    audioAt.set(System.nanoTime())
                    latch.countDown()
                }
            }

            override fun onBuffering(isBuffering: Boolean) = Unit
            override fun onEnded() = Unit
            override fun onError(message: String) {
                synchronized(errors) { errors += message }
                latch.countDown()
            }
        })
        load(url, autoPlay = true)
        val started = latch.await(35, TimeUnit.SECONDS)
        assertTrue(started && errors.isEmpty(), "No audio for $label: $errors")
        assertTrue(progress.await(10, TimeUnit.SECONDS), "No progress for $label")
        return audioAt.get()
    }
}
