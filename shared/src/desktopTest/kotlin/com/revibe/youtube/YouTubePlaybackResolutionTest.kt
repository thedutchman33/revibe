package com.revibe.youtube

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class YouTubePlaybackResolutionTest {
    @Test
    fun resolvesCompatibleProgressiveStream() = runBlocking {
        val source = YouTubeTestHarness.source
        val track = YouTubeTestHarness.firstTrack()
        val stream = source.resolveStream(track)
        println(
            "[resolution] ${track.title} -> itag=${stream.itag} mime=${stream.mimeType} " +
                "codecs=${stream.codecs} bitrate=${stream.bitrate} sampleRate=${stream.sampleRate} " +
                "client=${stream.clientName} note=${stream.note} expires=${stream.expiresAtEpochMs}",
        )
        assertTrue(stream.url.isNotBlank())
        assertTrue(stream.url.startsWith("https://"))
        assertTrue(
            isFreeAudioStream(stream.itag, stream.mimeType, stream.codecs),
            "Expected a free audio-only stream, got itag=${stream.itag} ${stream.mimeType} ${stream.codecs}",
        )
        assertTrue(FREE_AUDIO_ITAGS.contains(stream.itag))
        assertTrue(stream.itag != 141, "Premium-only itag 141 must never be selected")
        assertTrue((stream.bitrate ?: 0) > 0)
        assertTrue((stream.sampleRate ?: 0) > 0)
    }
}
