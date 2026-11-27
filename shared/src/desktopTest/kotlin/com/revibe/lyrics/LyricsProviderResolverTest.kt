package com.revibe.lyrics

import com.revibe.model.MusicSourceId
import com.revibe.model.Track
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LyricsProviderResolverTest {

    private val track = Track(
        id = "ytm:track:kwjp52YYlwQ",
        title = "Explorer",
        artist = "Justice",
        album = "Hyperdrama",
        durationMs = 249_000,
        source = MusicSourceId.YOUTUBE_MUSIC,
    )

    private fun lyrics(source: String, synced: Boolean, word: Boolean): Lyrics = Lyrics(
        lines = listOf(
            LyricLine(
                timeMs = 1000,
                text = "line",
                words = if (word) listOf(LyricWord(1000, "line")) else emptyList(),
            ),
        ),
        plain = if (!synced) "plain" else null,
        hasTimestamps = synced,
        wordSynced = word,
        source = source,
        syncedText = if (synced) "[00:01.00]line" else null,
    )

    private class FakeSource(
        override val id: String,
        private val result: Lyrics?,
        private val failure: Boolean = false,
    ) : LyricsSource {
        var calls = 0
        override suspend fun fetch(track: Track): Lyrics? {
            calls++
            if (failure) error("provider down")
            return result
        }
    }

    @Test
    fun `timed primary wins and the fallback is not called`() = runBlocking {
        val primary = FakeSource("LRCLIB", lyrics("LRCLIB", synced = true, word = false))
        val fallback = FakeSource("Unison", lyrics("Unison", synced = true, word = true))
        val result = LyricsProviderResolver(primary, fallback).resolve(track)
        assertEquals("LRCLIB", result?.source)
        assertEquals(0, fallback.calls)
    }

    @Test
    fun `plain primary is replaced by a word-synced fallback`() = runBlocking {
        val primary = FakeSource("LRCLIB", lyrics("LRCLIB", synced = false, word = false))
        val fallback = FakeSource("Unison", lyrics("Unison", synced = true, word = true))
        val result = LyricsProviderResolver(primary, fallback).resolve(track)
        assertEquals("Unison", result?.source)
        assertTrue(result?.wordSynced == true)
        assertEquals(1, fallback.calls)
    }

    @Test
    fun `missing primary falls back and provider errors do not throw`() = runBlocking {
        val primary = FakeSource("LRCLIB", null, failure = true)
        val fallback = FakeSource("Unison", lyrics("Unison", synced = true, word = false))
        val result = LyricsProviderResolver(primary, fallback).resolve(track)
        assertEquals("Unison", result?.source)

        val bothFail = LyricsProviderResolver(
            FakeSource("LRCLIB", null, failure = true),
            FakeSource("Unison", null, failure = true),
        ).resolve(track)
        assertNull(bothFail)
    }

    @Test
    fun `wrong title or artist is rejected`() {
        assertTrue(matchesTrack("Explorer", "Justice", "Explorer", "Justice, Connan Mockasin"))
        assertTrue(matchesTrack("Get Lucky (feat. Pharrell)", "Daft Punk", "Get Lucky", "Daft Punk, Pharrell Williams"))
        assertFalse(matchesTrack("Explorer", "Justice", "Another Song", "Justice"))
        assertFalse(matchesTrack("Explorer", "Justice", "Explorer", "A Different Artist"))
        assertFalse(matchesTrack("Explorer", "Justice", null, "Justice"))
    }
}
