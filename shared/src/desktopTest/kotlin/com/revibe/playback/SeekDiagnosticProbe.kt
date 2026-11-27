package com.revibe.playback

import com.revibe.youtube.YouTubeTestHarness
import kotlinx.coroutines.runBlocking
import org.bytedeco.ffmpeg.global.avutil.AV_LOG_DEBUG
import org.bytedeco.ffmpeg.global.avutil.av_log_set_level
import org.bytedeco.javacv.FFmpegLogCallback
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Manual, real-network probe for the seek path. Gated like the other live
 * probes so the deterministic suite never opens a signed stream.
 *
 *   REVIBE_LATENCY_PROBE=1 ./gradlew :shared:desktopTest \
 *     --tests 'com.revibe.playback.SeekDiagnosticProbe' -PrevibeLiveTests=true --rerun-tasks
 */
class SeekDiagnosticProbe {
    @Test
    fun diagnoseNetworkSeek() = runBlocking {
        if (System.getenv("REVIBE_LATENCY_PROBE") != "1") return@runBlocking
        val track = YouTubeTestHarness.firstTrack()
        val stream = YouTubeTestHarness.source.resolveStream(track)
        DesktopStreamHeaders.put(stream.url, stream.headers)
        val queryKeys = URI(stream.url).rawQuery
            ?.split('&')
            ?.map { it.substringBefore('=') }
            ?.filter { it in setOf("range", "rn", "rbuf", "ump", "sabr", "mime", "itag") }
            .orEmpty()
        println(
            "[seek-diag] itag=${stream.itag} mime=${stream.mimeType} codecs=${stream.codecs} " +
                "client=${stream.clientName} urlKeys=$queryKeys",
        )
        FFmpegLogCallback.set()
        av_log_set_level(AV_LOG_DEBUG)
        val grabber = FfmpegAudioPlayer.openGrabber(stream.url)
        try {
            println(
                "[seek-diag] opened duration=${grabber.lengthInTime / 1000}ms " +
                    "rate=${grabber.sampleRate} channels=${grabber.audioChannels}",
            )
            readFrames(grabber, "before")
            for (targetMs in listOf(45_000L, 120_000L)) {
                for (checkFrame in listOf(false, true)) {
                    val startedAt = System.nanoTime()
                    val error = runCatching {
                        grabber.setTimestamp(targetMs * 1_000L, checkFrame)
                    }.exceptionOrNull()
                    val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
                    println(
                        "[seek-diag] target=${targetMs}ms checkFrame=$checkFrame " +
                            "took=${elapsedMs}ms error=${error?.message}",
                    )
                    readFrames(grabber, "target=$targetMs check=$checkFrame")
                    // Reopen for the next variant so one failed seek cannot
                    // contaminate the following measurement.
                    break
                }
            }
        } finally {
            runCatching { grabber.stop() }
            runCatching { grabber.release() }
            DesktopStreamHeaders.clear()
        }
    }

    private fun readFrames(grabber: org.bytedeco.javacv.FFmpegFrameGrabber, label: String) {
        var frames = 0
        repeat(12) { index ->
            val frame = runCatching { grabber.grabSamples() }.getOrElse {
                println("[seek-diag] $label frame=$index threw ${it.message}")
                return
            }
            if (frame == null) {
                println("[seek-diag] $label frame=$index null timestamp=${grabber.timestamp / 1000}ms")
                return
            }
            frames++
            println(
                "[seek-diag] $label frame=$index ts=${frame.timestamp / 1000}ms " +
                    "samples=${frame.samples?.firstOrNull()?.remaining() ?: 0}",
            )
        }
        assertTrue(frames > 0, "$label produced no audio frames")
    }
}
