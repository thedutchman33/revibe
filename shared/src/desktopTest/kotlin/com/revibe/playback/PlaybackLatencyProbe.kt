package com.revibe.playback

import com.revibe.youtube.YouTubeTestHarness
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Manual, real-network latency probe. It is skipped unless REVIBE_LATENCY_PROBE=1
 * so the deterministic suite never depends on YouTube or an audio device.
 *
 * Run with:
 *   REVIBE_LATENCY_PROBE=1 ./gradlew :shared:desktopTest \
 *     --tests 'com.revibe.playback.PlaybackLatencyProbe' -PrevibeLiveTests=true --rerun-tasks
 */
class PlaybackLatencyProbe {
    @Test
    fun measureColdWarmAndNext() = runBlocking {
        if (System.getenv("REVIBE_LATENCY_PROBE") != "1") return@runBlocking
        val tracks = YouTubeTestHarness.tracks.take(2)
        assertTrue(tracks.size >= 2, "Need two live tracks for the transition probe")

        val resolver = DesktopTrackStreamResolver(YouTubeTestHarness.source)
        val engine = FfmpegPlaybackEngine().also { it.setVolume(0.01f) }
        try {
            val coldStart = System.nanoTime()
            val first = resolver.resolve(tracks[0])
            val coldResolvedAt = System.nanoTime()
            val coldAudio = engine.loadAndAwaitAudio(first.uri!!)
            val coldEnd = System.nanoTime()
            report("cold", coldStart, coldResolvedAt, coldEnd, coldAudio)
            engine.stop()
            Thread.sleep(300)

            val warmStart = System.nanoTime()
            val warmStream = resolver.resolve(tracks[0])
            val warmResolvedAt = System.nanoTime()
            val warmAudio = engine.loadAndAwaitAudio(warmStream.uri!!)
            val warmEnd = System.nanoTime()
            report("warm", warmStart, warmResolvedAt, warmEnd, warmAudio)
            engine.stop()
            Thread.sleep(300)

            val prefetchStart = System.nanoTime()
            val next = resolver.prefetch(tracks[1])
            val prefetchEnd = System.nanoTime()
            engine.preload(next!!.uri!!)
            println("[latency] next prefetch=${(prefetchEnd - prefetchStart) / 1_000_000.0}ms")
            // Let the prefetch settle as it would while the previous song plays.
            Thread.sleep(1_500)

            val nextStart = System.nanoTime()
            val nextStream = resolver.resolve(tracks[1])
            val nextResolvedAt = System.nanoTime()
            val nextAudio = engine.loadAndAwaitAudio(nextStream.uri!!)
            val nextEnd = System.nanoTime()
            report("next", nextStart, nextResolvedAt, nextEnd, nextAudio)
        } finally {
            engine.release()
            resolver.close()
        }
    }

    private fun report(label: String, start: Long, resolved: Long, end: Long, audioAt: Long) {
        fun ms(a: Long, b: Long) = "%.0f".format((b - a) / 1_000_000.0)
        println(
            "[latency] $label resolve=${ms(start, resolved)}ms " +
                "loadToAudio=${ms(resolved, audioAt)}ms total=${ms(start, audioAt)}ms",
        )
    }

    private fun FfmpegPlaybackEngine.loadAndAwaitAudio(url: String): Long {
        val latch = CountDownLatch(1)
        val progress = CountDownLatch(1)
        val audioAt = java.util.concurrent.atomic.AtomicLong(0)
        val errors = mutableListOf<String>()
        setListener(object : PlaybackEngine.Listener {
            override fun onReady(durationMs: Long) = Unit
            override fun onProgress(positionMs: Long) {
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
        assertTrue(started && errors.isEmpty(), "No audio for probe stream: $errors")
        assertTrue(progress.await(8, TimeUnit.SECONDS), "Audio callback fired but playback did not advance")
        return audioAt.get()
    }
}
