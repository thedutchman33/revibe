package com.revibe.playback

import com.revibe.youtube.YouTubeTestHarness
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * FFmpeg is the only desktop engine. Decoding, resampling and seeking are real
 * native FFmpeg work; the output sink is a paced in-memory line so the
 * deterministic suite does not need an audio device. The live test uses the
 * real Windows Java Sound line.
 */
class FfmpegPlaybackEngineTest {
    @Test
    fun `FFmpeg decodes every supported audio format`() {
        listOf(
            "tone.m4a" to "MP4/AAC",
            "tone.aac" to "AAC/ADTS",
            "tone.mp3" to "MP3",
            "tone.wav" to "WAV",
            "tone.flac" to "FLAC",
            "tone.ogg" to "Ogg/Vorbis",
            "tone.webm" to "WebM/Opus",
        ).forEach { (name, label) ->
            val engine = newEngine(frameDelayMs = 0)
            try {
                val probe = Probe()
                engine.setListener(probe)
                engine.load(resource(name).absolutePath, autoPlay = true)
                assertTrue(
                    probe.playing.await(30, TimeUnit.SECONDS),
                    "$label did not start; errors=${probe.errors}",
                )
                assertTrue(probe.progress.await(10, TimeUnit.SECONDS), "$label produced no position")
                assertTrue(probe.durationMs.get() > 1_000, "$label reported no duration")
                assertTrue(probe.errors.isEmpty(), "$label raised ${probe.errors}")
            } finally {
                release(engine)
            }
        }
    }

    @Test
    fun `seeking moves forward and backward`() {
        val engine = newEngine(frameDelayMs = 20)
        try {
            val probe = Probe()
            engine.setListener(probe)
            engine.load(resource("long.mp3").absolutePath, autoPlay = true)
            assertTrue(probe.playing.await(20, TimeUnit.SECONDS), "long MP3 did not start; ${probe.errors}")
            assertTrue(probe.progress.await(10, TimeUnit.SECONDS), "long MP3 produced no position")

            engine.pause()
            Thread.sleep(250)
            engine.seekTo(12_000)
            assertTrue(probe.awaitPositionIn(11_000, 14_000, 5_000), "forward seek landed at ${probe.positionMs.get()}")
            engine.play()
            assertTrue(probe.awaitPositionAtLeast(12_500, 6_000), "forward seek did not play")

            engine.pause()
            Thread.sleep(250)
            engine.seekTo(2_000)
            assertTrue(probe.awaitPositionIn(1_000, 4_500, 5_000), "backward seek landed at ${probe.positionMs.get()}")
            assertTrue(probe.errors.isEmpty(), "seeking raised ${probe.errors}")
        } finally {
            release(engine)
        }
    }

    @Test
    fun `seeking to zero restarts the track`() {
        val engine = newEngine(frameDelayMs = 20)
        try {
            val probe = Probe()
            engine.setListener(probe)
            engine.load(resource("long.mp3").absolutePath, autoPlay = true)
            assertTrue(probe.playing.await(20, TimeUnit.SECONDS), "long MP3 did not start; ${probe.errors}")
            assertTrue(probe.awaitPositionAtLeast(2_000, 8_000), "did not reach 2s")

            engine.seekTo(0)
            assertTrue(
                probe.awaitPositionIn(0, 1_200, 8_000),
                "restart landed at ${probe.positionMs.get()}",
            )
            assertTrue(probe.awaitPositionAtLeast(1_500, 8_000), "restart did not advance")
            assertTrue(probe.errors.isEmpty(), "restart raised ${probe.errors}")
        } finally {
            release(engine)
        }
    }

    @Test
    fun `MP4 seeking resumes inside the track`() {
        val engine = newEngine(frameDelayMs = 20)
        try {
            val probe = Probe()
            engine.setListener(probe)
            engine.load(resource("tone.m4a").absolutePath, autoPlay = false)
            engine.seekTo(2_000)
            engine.play()
            assertTrue(
                probe.awaitPositionIn(2_400, 3_800, 5_000),
                "MP4 seek did not resume near 2s; position=${probe.positionMs.get()} errors=${probe.errors}",
            )
            assertTrue(probe.errors.isEmpty(), "MP4 seek raised ${probe.errors}")
        } finally {
            release(engine)
        }
    }

    @Test
    fun `pause holds position and resume advances it`() {
        val engine = newEngine(frameDelayMs = 20)
        try {
            val probe = Probe()
            engine.setListener(probe)
            engine.load(resource("long.mp3").absolutePath, autoPlay = true)
            assertTrue(probe.playing.await(20, TimeUnit.SECONDS), "long MP3 did not start; ${probe.errors}")
            assertTrue(probe.progress.await(10, TimeUnit.SECONDS), "long MP3 produced no position")

            engine.pause()
            Thread.sleep(400)
            val pausedAt = probe.positionMs.get()
            Thread.sleep(900)
            val stillAt = probe.positionMs.get()
            assertTrue(abs(stillAt - pausedAt) < 400, "position moved while paused: $pausedAt -> $stillAt")

            engine.play()
            assertTrue(probe.awaitPositionAtLeast(pausedAt + 250, 8_000), "resume did not advance")
            assertTrue(probe.errors.isEmpty(), "pause/resume raised ${probe.errors}")
        } finally {
            release(engine)
        }
    }

    @Test
    fun `finished event fires at end of track`() {
        val engine = newEngine(frameDelayMs = 0)
        try {
            val probe = Probe()
            engine.setListener(probe)
            engine.load(resource("tone.m4a").absolutePath, autoPlay = true)
            assertTrue(probe.ended.await(20, TimeUnit.SECONDS), "end-of-track did not fire; ${probe.errors}")
            assertTrue(probe.errors.isEmpty(), "end-of-track raised ${probe.errors}")
        } finally {
            release(engine)
        }
    }

    @Test
    fun `seeking near the end still reports completion`() {
        val engine = newEngine(frameDelayMs = 0)
        try {
            val probe = Probe()
            engine.setListener(probe)
            engine.load(resource("long.mp3").absolutePath, autoPlay = true)
            assertTrue(probe.playing.await(20, TimeUnit.SECONDS), "long MP3 did not start; ${probe.errors}")
            assertTrue(probe.progress.await(10, TimeUnit.SECONDS), "long MP3 produced no position")
            val duration = probe.durationMs.get()
            assertTrue(duration > 5_000, "unexpected test duration $duration")
            engine.seekTo(duration - 1_200)
            assertTrue(
                probe.ended.await(15, TimeUnit.SECONDS),
                "near-end seek never completed; position=${probe.positionMs.get()}",
            )
            assertTrue(probe.errors.isEmpty(), "near-end seek raised ${probe.errors}")
        } finally {
            release(engine)
        }
    }

    @Test
    fun `a prefetched next row is already decoded`() {
        val engine = newEngine(frameDelayMs = 0)
        try {
            val first = Probe()
            engine.setListener(first)
            engine.load(resource("tone.m4a").absolutePath, autoPlay = true)
            assertTrue(first.playing.await(20, TimeUnit.SECONDS), "first row did not start; ${first.errors}")

            engine.preload(resource("tone.flac").absolutePath)
            Thread.sleep(500)
            val start = System.nanoTime()
            val next = Probe()
            engine.setListener(next)
            engine.load(resource("tone.flac").absolutePath, autoPlay = true)
            assertTrue(next.playing.await(20, TimeUnit.SECONDS), "prefetched row did not start; ${next.errors}")
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            assertTrue(elapsedMs < 2_000, "prefetched row took ${elapsedMs}ms")
            assertTrue(next.errors.isEmpty(), "prefetch raised ${next.errors}")
        } finally {
            release(engine)
        }
    }

    @Test
    fun `seeking a prefetched track resumes inside it`() {
        val engine = newEngine(frameDelayMs = 20)
        try {
            val probe = Probe()
            engine.setListener(probe)
            engine.load(resource("tone.m4a").absolutePath, autoPlay = true)
            assertTrue(probe.playing.await(20, TimeUnit.SECONDS), "first row did not start; ${probe.errors}")
            engine.preload(resource("long.mp3").absolutePath)
            Thread.sleep(400)
            probe.resetProgress()
            engine.load(resource("long.mp3").absolutePath, autoPlay = true)
            assertTrue(probe.progress.await(20, TimeUnit.SECONDS), "prefetched row did not start; ${probe.errors}")
            engine.seekTo(12_000)
            engine.play()
            assertTrue(
                probe.awaitPositionIn(12_500, 18_000, 8_000),
                "seek on a prefetched row landed at ${probe.positionMs.get()}; errors=${probe.errors}",
            )
            assertTrue(probe.errors.isEmpty(), "prefetched seek raised ${probe.errors}")
        } finally {
            release(engine)
        }
    }

    @Test
    fun `rapid switching plays only the final row`() {
        val engine = newEngine(frameDelayMs = 0)
        try {
            val probe = Probe()
            engine.setListener(probe)
            engine.load(resource("tone.m4a").absolutePath, autoPlay = true)
            engine.load(resource("tone.ogg").absolutePath, autoPlay = true)
            engine.load(resource("long.mp3").absolutePath, autoPlay = true)
            assertTrue(probe.playing.await(30, TimeUnit.SECONDS), "final row did not start; ${probe.errors}")
            assertTrue(probe.durationMs.get() >= 19_000, "final row duration was ${probe.durationMs.get()}")
            assertTrue(probe.errors.isEmpty(), "rapid switching raised ${probe.errors}")
        } finally {
            release(engine)
        }
    }

    @Test
    fun `a long load and seek session stays healthy`() {
        val engine = newEngine(frameDelayMs = 0)
        try {
            val probe = Probe()
            engine.setListener(probe)
            repeat(10) { index ->
                val track = if (index % 2 == 0) "tone.m4a" else "long.mp3"
                engine.load(resource(track).absolutePath, autoPlay = true)
                assertTrue(probe.progress.await(15, TimeUnit.SECONDS), "load $index produced no position; ${probe.errors}")
                engine.seekTo(if (track == "long.mp3") 5_000 else 500)
                Thread.sleep(80)
                engine.stop()
                probe.resetProgress()
            }
            assertTrue(probe.errors.isEmpty(), "long session raised ${probe.errors}")
        } finally {
            release(engine)
        }
    }

    @Test
    fun `missing file reports an engine error`() {
        val engine = newEngine(frameDelayMs = 0)
        try {
            val probe = Probe()
            engine.setListener(probe)
            engine.load(
                File(System.getProperty("java.io.tmpdir"), "revibe-missing-${System.nanoTime()}.mp3").absolutePath,
                autoPlay = true,
            )
            assertTrue(probe.errorLatch.await(15, TimeUnit.SECONDS), "missing file did not produce an error")
            assertTrue(probe.errors.isNotEmpty())
        } finally {
            release(engine)
        }
    }

    @Test
    fun `YouTube Music stream decodes and seeks through FFmpeg`() = runBlocking {
        val track = YouTubeTestHarness.firstTrack()
        val stream = YouTubeTestHarness.source.resolveStream(track)
        DesktopStreamHeaders.put(stream.url, stream.headers)
        // Real Windows Java Sound output for this live check.
        val engine = FfmpegPlaybackEngine()
        try {
            val probe = Probe()
            engine.setListener(probe)
            engine.load(stream.url, autoPlay = true)
            assertTrue(probe.playing.await(35, TimeUnit.SECONDS), "YouTube stream did not start; ${probe.errors}")
            assertTrue(probe.progress.await(15, TimeUnit.SECONDS), "YouTube stream produced no position")
            assertTrue(probe.durationMs.get() > 30_000, "YouTube duration was ${probe.durationMs.get()}")
            assertTrue(probe.errors.isEmpty(), "YouTube stream raised ${probe.errors}")

            val target = minOf(30_000L, probe.durationMs.get() / 2)
            engine.pause()
            Thread.sleep(250)
            engine.seekTo(target)
            engine.play()
            assertTrue(
                probe.awaitPositionIn(target + 500, target + 15_000, 15_000),
                "YouTube seek did not resume near ${target}ms; position=${probe.positionMs.get()} errors=${probe.errors}",
            )
            println(
                "[ffmpeg-youtube] ${track.title} itag=${stream.itag} mime=${stream.mimeType} " +
                    "duration=${probe.durationMs.get()}",
            )
        } finally {
            release(engine)
            DesktopStreamHeaders.clear()
        }
    }

    private fun newEngine(frameDelayMs: Long) = FfmpegPlaybackEngine { FakeSink(frameDelayMs) }

    private fun release(engine: FfmpegPlaybackEngine) {
        engine.release()
        Thread.sleep(150)
    }

    private fun resource(name: String): File {
        val url = javaClass.getResource("/media/$name")
        assertNotNull(url, "missing test media: $name")
        return File(url.toURI())
    }

    private class FakeSink(private val frameDelayMs: Long) : AudioSink {
        private val playedFrames = AtomicLong(0)
        private var frameSize = 4

        @Volatile
        private var closed = false

        override var bufferSize: Int = 0
            private set

        override val positionFrames: Long
            get() = playedFrames.get()

        override val hasVolumeControl: Boolean = false

        override fun open(format: javax.sound.sampled.AudioFormat, bufferSize: Int) {
            this.bufferSize = bufferSize
            this.frameSize = format.frameSize.coerceAtLeast(2)
        }

        override fun write(bytes: ByteArray, shouldContinue: () -> Boolean) {
            check(!closed) { "sink is closed" }
            if (frameDelayMs > 0) Thread.sleep(frameDelayMs)
            if (shouldContinue()) playedFrames.addAndGet((bytes.size / frameSize).toLong())
        }

        override fun start() = Unit
        override fun stop() = Unit
        override fun flush() = Unit
        override fun drain() = Unit
        override fun close() {
            closed = true
        }
    }

    private class Probe : PlaybackEngine.Listener {
        val playing = CountDownLatch(1)
        @Volatile
        var progress = CountDownLatch(1)
        val ended = CountDownLatch(1)
        val errorLatch = CountDownLatch(1)
        val durationMs = AtomicLong(0)
        val positionMs = AtomicLong(0)
        val errors = CopyOnWriteArrayList<String>()

        override fun onReady(durationMs: Long) {
            if (durationMs > 0) this.durationMs.set(durationMs)
        }

        override fun onProgress(positionMs: Long) {
            this.positionMs.set(positionMs)
            if (positionMs > 0) progress.countDown()
        }

        override fun onPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) playing.countDown()
        }

        override fun onBuffering(isBuffering: Boolean) = Unit

        override fun onEnded() {
            ended.countDown()
        }

        override fun onError(message: String) {
            errors += message
            errorLatch.countDown()
        }

        fun resetProgress() {
            progress = CountDownLatch(1)
        }

        fun awaitPositionAtLeast(target: Long, timeoutMs: Long): Boolean = awaitPositionIn(target, Long.MAX_VALUE, timeoutMs)

        fun awaitPositionIn(min: Long, max: Long, timeoutMs: Long): Boolean {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val current = positionMs.get()
                if (current in min..max) return true
                Thread.sleep(40)
            }
            return false
        }
    }
}
