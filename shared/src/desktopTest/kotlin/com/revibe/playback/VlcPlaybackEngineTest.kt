package com.revibe.playback

import com.revibe.youtube.YouTubeTestHarness
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Proof-of-concept compatibility checks for LibVLC. These use real generated
 * audio files (not mocks) and a local VLC runtime. Set REVIBE_VLC_HOME if VLC is
 * installed in a non-standard location.
 */
class VlcPlaybackEngineTest {
    private val vlcHome: File? = VlcNativeLocator.locate()

    @Test
    fun `LibVLC runtime is locatable`() {
        assertNotNull(vlcHome, "Install VLC or set REVIBE_VLC_HOME to run the native engine tests")
        assertTrue(VlcNativeLocator.isRuntime(vlcHome!!))
    }

    @Test
    fun `MP4 M4A AAC MP3 WAV FLAC OGG and WebM Opus play`() {
        val engine = newEngine()
        try {
            listOf(
                "tone.m4a" to "MP4/AAC",
                "tone.aac" to "AAC/ADTS",
                "tone.mp3" to "MP3",
                "tone.wav" to "WAV",
                "tone.flac" to "FLAC",
                "tone.ogg" to "Ogg/Vorbis",
                "tone.webm" to "WebM/Opus",
            ).forEach { (name, label) ->
                val probe = Probe()
                engine.setListener(probe)
                engine.load(resource(name).absolutePath, autoPlay = true)
                assertTrue(probe.playing.await(20, TimeUnit.SECONDS), "$label did not start playing; errors=${probe.errors}")
                assertTrue(probe.progress.await(10, TimeUnit.SECONDS), "$label produced no position")
                assertTrue(probe.durationMs.get() > 1_000, "$label reported no usable duration")
                assertTrue(probe.errors.isEmpty(), "$label raised ${probe.errors}")
                engine.stop()
            }
        } finally {
            engine.release()
        }
    }

    @Test
    fun `file URI is normalised for local playback`() {
        val engine = newEngine()
        try {
            val probe = Probe()
            engine.setListener(probe)
            engine.load(resource("tone.m4a").toURI().toString(), autoPlay = true)
            assertTrue(probe.playing.await(20, TimeUnit.SECONDS), "file URI did not start; errors=${probe.errors}")
            assertTrue(probe.progress.await(10, TimeUnit.SECONDS), "file URI produced no position")
            assertTrue(probe.errors.isEmpty(), "file URI raised ${probe.errors}")
        } finally {
            engine.release()
        }
    }

    @Test
    fun `local HLS playlist plays through LibVLC`() {
        val engine = newEngine()
        try {
            val probe = Probe()
            engine.setListener(probe)
            engine.load(resource("hls/tone.m3u8").absolutePath, autoPlay = true)
            assertTrue(probe.playing.await(20, TimeUnit.SECONDS), "HLS did not start")
            assertTrue(probe.progress.await(10, TimeUnit.SECONDS), "HLS produced no position")
            assertTrue(probe.durationMs.get() > 1_000, "HLS duration was not reported")
            assertTrue(probe.errors.isEmpty(), "HLS raised ${probe.errors}")
        } finally {
            engine.release()
        }
    }

    @Test
    fun `seeking moves forward and backward`() {
        val engine = newEngine()
        try {
            val probe = Probe()
            engine.setListener(probe)
            engine.load(resource("long.mp3").absolutePath, autoPlay = true)
            assertTrue(probe.playing.await(20, TimeUnit.SECONDS), "long MP3 did not start")
            assertTrue(probe.progress.await(10, TimeUnit.SECONDS), "long MP3 produced no position")

            engine.seekTo(12_000)
            assertTrue(probe.awaitPositionAtLeast(10_500, 8_000), "seek forward did not reach 10.5s")

            engine.seekTo(2_000)
            assertTrue(probe.awaitPositionAtMost(8_000, 8_000), "seek backward did not leave 8s")
            assertTrue(probe.errors.isEmpty(), "seeking raised ${probe.errors}")
        } finally {
            engine.release()
        }
    }

    @Test
    fun `pause holds position and resume advances it`() {
        val engine = newEngine()
        try {
            val probe = Probe()
            engine.setListener(probe)
            engine.load(resource("long.mp3").absolutePath, autoPlay = true)
            assertTrue(probe.playing.await(20, TimeUnit.SECONDS), "long MP3 did not start")
            assertTrue(probe.progress.await(10, TimeUnit.SECONDS), "long MP3 produced no position")

            engine.pause()
            Thread.sleep(500)
            val pausedAt = probe.positionMs.get()
            Thread.sleep(700)
            val stillAt = probe.positionMs.get()
            assertTrue(abs(stillAt - pausedAt) < 700, "position moved while paused: $pausedAt -> $stillAt")

            engine.play()
            assertTrue(probe.awaitPositionAtLeast(pausedAt + 250, 8_000), "resume did not advance")
            assertTrue(probe.errors.isEmpty(), "pause/resume raised ${probe.errors}")
        } finally {
            engine.release()
        }
    }

    @Test
    fun `LibVLC plays a live resolved YouTube Music stream`() = runBlocking {
        val track = YouTubeTestHarness.firstTrack()
        val stream = YouTubeTestHarness.source.resolveStream(track)
        val proxy = LocalStreamProxy()
        val engine = newEngine()
        try {
            val probe = Probe()
            engine.setListener(probe)
            val url = proxy.expose(stream.url, stream.headers, stream.mimeType)
            engine.load(url, autoPlay = true)
            assertTrue(probe.playing.await(30, TimeUnit.SECONDS), "YouTube stream did not start; errors=${probe.errors}")
            assertTrue(probe.progress.await(12, TimeUnit.SECONDS), "YouTube stream produced no position")
            assertTrue(probe.awaitPositionAtLeast(1_000, 10_000), "YouTube position did not advance past 1s")
            assertTrue(probe.durationMs.get() > 30_000, "YouTube duration looked implausible: ${probe.durationMs.get()}")
            assertTrue(probe.errors.isEmpty(), "YouTube stream raised ${probe.errors}")
            println("[vlc-youtube] ${track.title} itag=${stream.itag} mime=${stream.mimeType} duration=${probe.durationMs.get()} position=${probe.positionMs.get()}")
        } finally {
            engine.release()
            proxy.close()
        }
    }

    @Test
    fun `finished event fires at end of track`() {
        val engine = newEngine()
        try {
            val probe = Probe()
            engine.setListener(probe)
            engine.load(resource("tone.m4a").absolutePath, autoPlay = true)
            assertTrue(probe.ended.await(20, TimeUnit.SECONDS), "end-of-track event did not fire; errors=${probe.errors}")
            assertTrue(probe.errors.isEmpty(), "end-of-track raised ${probe.errors}")
        } finally {
            engine.release()
        }
    }

    @Test
    fun `missing file reports an engine error`() {
        val engine = newEngine()
        try {
            val probe = Probe()
            engine.setListener(probe)
            engine.load(File(System.getProperty("java.io.tmpdir"), "revibe-does-not-exist-${System.nanoTime()}.mp3").toURI().toString(), autoPlay = true)
            assertTrue(probe.errorLatch.await(12, TimeUnit.SECONDS), "missing file did not produce an error")
            assertTrue(probe.errors.isNotEmpty())
        } finally {
            engine.release()
        }
    }

    private fun newEngine(): VlcPlaybackEngine {
        val home = assertNotNull(vlcHome, "Install VLC or set REVIBE_VLC_HOME to run the native engine tests")
        return VlcPlaybackEngine(home).also { it.setVolume(0f) }
    }

    private fun resource(name: String): File {
        val url = javaClass.getResource("/media/$name")
        assertNotNull(url, "missing test media: $name")
        return File(url.toURI())
    }

    private class Probe : PlaybackEngine.Listener {
        val playing = CountDownLatch(1)
        val progress = CountDownLatch(1)
        val ended = CountDownLatch(1)
        val errorLatch = CountDownLatch(1)
        val durationMs = AtomicLong(0)
        val positionMs = AtomicLong(0)
        val buffering = AtomicBoolean(false)
        val errors = mutableListOf<String>()

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

        override fun onBuffering(isBuffering: Boolean) {
            buffering.set(isBuffering)
        }

        override fun onEnded() {
            ended.countDown()
        }

        override fun onError(message: String) {
            synchronized(errors) { errors += message }
            errorLatch.countDown()
        }

        fun awaitPositionAtLeast(target: Long, timeoutMs: Long): Boolean {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (positionMs.get() >= target) return true
                Thread.sleep(50)
            }
            return false
        }

        fun awaitPositionAtMost(target: Long, timeoutMs: Long): Boolean {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (positionMs.get() <= target) return true
                Thread.sleep(50)
            }
            return false
        }
    }
}
