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
                "codecs=${stream.codecs} client=${stream.clientName} note=${stream.note} " +
                "expires=${stream.expiresAtEpochMs}",
        )
        assertTrue(stream.url.isNotBlank())
        assertTrue(stream.url.startsWith("https://"))
        assertTrue(isJavaFxCompatible(stream.mimeType, stream.codecs, false, stream.url))
        assertTrue(stream.mimeType?.contains("mp4") == true)
        assertTrue(stream.codecs?.contains("mp4a") == true, "Expected AAC audio, got ${stream.codecs}")
        assertTrue(stream.itag > 0)
    }
}
