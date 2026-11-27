package com.revibe.youtube

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YouTubeStreamCompatibilityTest {
    @Test
    fun acceptsProgressiveAacAndRejectsOtherTransports() {
        assertTrue(isJavaFxCompatible("audio/mp4", "mp4a.40.2", false, "https://example.test/audio"))
        assertTrue(isJavaFxCompatible("video/mp4", "avc1.42001E, mp4a.40.2", false, "https://example.test/muxed"))
        assertFalse(isJavaFxCompatible("audio/webm", "opus", false, "https://example.test/webm"))
        assertFalse(isJavaFxCompatible("audio/mp4", "mp4a.40.2", true, "https://example.test/sabr"))
        assertFalse(isJavaFxCompatible("application/vnd.apple.mpegurl", null, false, "https://example.test/a.m3u8"))
        assertFalse(isJavaFxCompatible("audio/mp4", "mp4a.40.2", false, ""))
    }
}
