package com.revibe.playback

import com.revibe.youtube.FREE_AUDIO_ITAGS
import com.revibe.youtube.YouTubeTestHarness
import com.revibe.youtube.isFreeAudioStream
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Manual, real-network quality report for free-account streams.
 *
 *   REVIBE_LATENCY_PROBE=1 ./gradlew :shared:desktopTest \
 *     --tests 'com.revibe.playback.FreeAudioQualityProbe' -PrevibeLiveTests=true --rerun-tasks
 */
class FreeAudioQualityProbe {
    @Test
    fun reportSelectedFormats() = runBlocking {
        if (System.getenv("REVIBE_LATENCY_PROBE") != "1") return@runBlocking
        val source = YouTubeTestHarness.source
        val tracks = YouTubeTestHarness.tracks.take(6)
        assertTrue(tracks.size >= 3, "Need at least three live tracks, got ${tracks.size}")
        var checked = 0
        for (track in tracks) {
            val stream = runCatching { source.resolveStream(track) }.getOrElse { error ->
                println("[quality] ${track.title}: resolve failed: ${error.message}")
                continue
            }
            DesktopStreamHeaders.put(stream.url, stream.headers)
            val grabber = runCatching { FfmpegAudioPlayer.openGrabber(stream.url) }.getOrElse { error ->
                println("[quality] ${track.title}: open failed: ${error.message}")
                DesktopStreamHeaders.remove(stream.url)
                continue
            }
            try {
                println(
                    "[quality] title=\"${track.title}\" itag=${stream.itag} " +
                        "mime=${stream.mimeType} codec=${stream.codecs} " +
                        "bitrate=${stream.bitrate}bps advertisedRate=${stream.sampleRate}Hz " +
                        "decodedRate=${grabber.sampleRate}Hz channels=${grabber.audioChannels} " +
                        "client=${stream.clientName}",
                )
                assertTrue(stream.itag in FREE_AUDIO_ITAGS, "Selected non-free itag ${stream.itag}")
                assertTrue(stream.itag != 141, "Selected premium-only itag 141")
                assertTrue(
                    isFreeAudioStream(stream.itag, stream.mimeType, stream.codecs),
                    "Selected incompatible free format ${stream.mimeType}/${stream.codecs}",
                )
                checked++
            } finally {
                runCatching { grabber.stop() }
                runCatching { grabber.release() }
                DesktopStreamHeaders.remove(stream.url)
            }
        }
        assertTrue(checked >= 3, "Only $checked of ${tracks.size} tracks resolved to a free stream")
    }
}
