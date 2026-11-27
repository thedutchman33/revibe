package com.revibe.playback

import com.revibe.youtube.YouTubeTestHarness
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Manual, real-network seek matrix through the real Windows Java Sound line.
 * Timing starts at the seek request and ends at the decoder's onSeeked
 * callback, so it excludes UI target echoes and normal playback afterwards.
 *
 *   REVIBE_LATENCY_PROBE=1 ./gradlew :shared:desktopTest \
 *     --tests 'com.revibe.playback.LiveSeekMatrixProbe' -PrevibeLiveTests=true --rerun-tasks
 */
class LiveSeekMatrixProbe {
    private val lastProgress = AtomicLong(0)
    private val errors = mutableListOf<String>()
    private var seekLatch = CountDownLatch(1)
    private val seekLanded = AtomicLong(-1)

    @Test
    fun measuresForwardAndBackwardSeeks() = runBlocking {
        if (System.getenv("REVIBE_LATENCY_PROBE") != "1") return@runBlocking
        // Live extraction can be temporarily rate limited. Try a few real rows
        // so the seek matrix reports the playback path, not a catalog blip.
        val resolved = YouTubeTestHarness.tracks.take(4).firstNotNullOfOrNull { candidate ->
            runCatching { candidate to YouTubeTestHarness.source.resolveStream(candidate) }.getOrNull()
        }
        assertTrue(resolved != null, "No live track could be resolved")
        val (track, stream) = resolved!!
        DesktopStreamHeaders.put(stream.url, stream.headers)
        val player = FfmpegAudioPlayer(stream.url, { JavaSoundSink() }, initialVolume = 0.01f)
        try {
            player.listener = object : FfmpegAudioPlayer.Listener {
                override fun onAudioStarted() = Unit
                override fun onPaused() = Unit
                override fun onSeeked(positionMs: Long) {
                    seekLanded.set(positionMs)
                    seekLatch.countDown()
                }

                override fun onEnded() = Unit
                override fun onError(message: String) {
                    synchronized(errors) { errors += message }
                }
            }
            player.play()
            val deadline = System.currentTimeMillis() + 35_000
            while (player.currentPositionMs() < 500 && System.currentTimeMillis() < deadline && errors.isEmpty()) {
                Thread.sleep(25)
            }
            assertTrue(player.currentPositionMs() >= 500, "stream did not start: $errors")
            println(
                "[seek-matrix] track=\"${track.title}\" itag=${stream.itag} " +
                    "codec=${stream.codecs} bitrate=${stream.bitrate}",
            )

            // Multiple forward positions and one backward jump.
            val targets = listOf(10_000L, 45_000L, 90_000L, 25_000L, 150_000L, 0L)
            for (target in targets) {
                seekLanded.set(-1)
                seekLatch = CountDownLatch(1)
                val startedAt = System.nanoTime()
                player.seekTo(target)
                assertTrue(
                    seekLatch.await(20, TimeUnit.SECONDS),
                    "seek to $target did not land; errors=$errors",
                )
                val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
                val landedAt = seekLanded.get()
                println(
                    "[seek-matrix] target=${target}ms landed=${landedAt}ms " +
                        "elapsed=${elapsedMs}ms error=${kotlin.math.abs(landedAt - target)}ms",
                )
                assertTrue(
                    kotlin.math.abs(landedAt - target) <= 2_000,
                    "seek to $target landed at $landedAt",
                )
                assertTrue(elapsedMs <= 5_000, "seek to $target took ${elapsedMs}ms")
                lastProgress.set(player.currentPositionMs())
                val advanceDeadline = System.currentTimeMillis() + 5_000
                while (player.currentPositionMs() <= landedAt + 500 &&
                    System.currentTimeMillis() < advanceDeadline
                ) {
                    Thread.sleep(20)
                }
                assertTrue(
                    player.currentPositionMs() > landedAt + 250,
                    "playback did not advance after seek to $target",
                )
            }

            val pausedAt = player.currentPositionMs()
            player.pause()
            Thread.sleep(700)
            val stillAt = player.currentPositionMs()
            assertTrue(kotlin.math.abs(stillAt - pausedAt) < 300, "position moved while paused: $pausedAt -> $stillAt")
            player.play()
            val resumeDeadline = System.currentTimeMillis() + 5_000
            while (player.currentPositionMs() <= stillAt + 250 && System.currentTimeMillis() < resumeDeadline) {
                Thread.sleep(20)
            }
            assertTrue(player.currentPositionMs() > stillAt + 150, "resume did not advance from $stillAt")
            println("[seek-matrix] pause/resume ok at ${stillAt}ms")
            assertTrue(errors.isEmpty(), "engine errors: $errors")
        } finally {
            player.close()
            DesktopStreamHeaders.clear()
        }
    }
}
