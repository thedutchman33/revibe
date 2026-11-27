package com.revibe.youtube

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YouTubeStreamCompatibilityTest {
    @Test
    fun acceptsOnlyTheFreeAudioItagLadder() {
        assertTrue(isFreeAudioStream(251, "audio/webm", "opus"))
        assertTrue(isFreeAudioStream(140, "audio/mp4", "mp4a.40.2"))
        assertTrue(isFreeAudioStream(250, "audio/webm", "opus"))
        assertTrue(isFreeAudioStream(249, "audio/webm", "opus"))
        assertTrue(isFreeAudioStream(139, "audio/mp4", "mp4a.40.5"))

        // Premium-only or unknown formats are never selected.
        assertFalse(isFreeAudioStream(141, "audio/mp4", "mp4a.40.2"))
        assertFalse(isFreeAudioStream(774, "audio/webm", "opus"))
        // Codec and container must agree with the itag.
        assertFalse(isFreeAudioStream(251, "audio/mp4", "mp4a.40.2"))
        assertFalse(isFreeAudioStream(140, "audio/webm", "opus"))
        assertFalse(isFreeAudioStream(251, "audio/webm", "vorbis"))
        assertFalse(isFreeAudioStream(140, "application/vnd.apple.mpegurl", null))
    }

    @Test
    fun progressiveMuxedFallbackRemainsAvailable() {
        assertTrue(isProgressiveAacCompatible("audio/mp4", "mp4a.40.2", false, "https://example.test/audio"))
        assertTrue(isProgressiveAacCompatible("video/mp4", "avc1.42001E, mp4a.40.2", false, "https://example.test/muxed"))
        assertFalse(isProgressiveAacCompatible("audio/mp4", "mp4a.40.2", true, "https://example.test/sabr"))
        assertFalse(isProgressiveAacCompatible("application/vnd.apple.mpegurl", null, false, "https://example.test/a.m3u8"))
        assertFalse(isProgressiveAacCompatible("audio/mp4", "mp4a.40.2", false, ""))
    }
}
