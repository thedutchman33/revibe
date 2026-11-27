package com.revibe.data

import com.revibe.library.RecentAlbums
import com.revibe.model.MusicSourceId
import com.revibe.model.PlayedTrack
import com.revibe.model.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecentAlbumsTest {

    private fun track(
        id: String,
        album: String,
        albumId: String?,
        artworkUrl: String? = null,
        artist: String = "Artist",
    ) = Track(
        id = id,
        title = "Track $id",
        artist = artist,
        album = album,
        durationMs = 180_000,
        artworkUrl = artworkUrl,
        albumId = albumId,
        source = MusicSourceId.YOUTUBE_MUSIC,
    )

    @Test
    fun `repeated plays collapse to one album with the newest artwork and order`() {
        val history = listOf(
            PlayedTrack(track("one", "First", "album:first", "old.jpg"), playedAt = 10),
            PlayedTrack(track("two", "Second", "album:second", "second.jpg"), playedAt = 20),
            PlayedTrack(track("three", "First", "album:first", "new.jpg"), playedAt = 30),
        )

        val albums = RecentAlbums.from(history)

        assertEquals(listOf("album:first", "album:second"), albums.map { it.album.id })
        assertEquals("new.jpg", albums.first().album.artworkUrl)
        assertEquals(30, albums.first().playedAt)
    }

    @Test
    fun `albums without a catalog id or name are not offered`() {
        val history = listOf(
            PlayedTrack(track("no-id", "No id", null), playedAt = 10),
            PlayedTrack(track("blank", "", "album:blank"), playedAt = 20),
            PlayedTrack(track("valid", "Valid", "album:valid"), playedAt = 5),
        )

        val albums = RecentAlbums.from(history)

        assertEquals(listOf("album:valid"), albums.map { it.album.id })
        assertEquals("Valid", albums.single().album.title)
    }

    @Test
    fun `limit is applied after ordering`() {
        val history = (1..5).map { index ->
            PlayedTrack(
                track("$index", "Album $index", "album:$index"),
                playedAt = index.toLong(),
            )
        }

        val albums = RecentAlbums.from(history, limit = 3)

        assertEquals(3, albums.size)
        assertEquals(listOf("album:5", "album:4", "album:3"), albums.map { it.album.id })
        assertTrue(albums.all { it.playedAt > 0 })
    }
}
