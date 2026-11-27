package com.revibe.data

import com.revibe.library.PlaylistRecency
import com.revibe.model.PlayedPlaylist
import com.revibe.model.Playlist
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaylistRecencyOrderingTest {

    private fun playlist(id: String, name: String, createdAt: Long, artworkUrl: String? = null) =
        Playlist(
            id = id,
            name = name,
            createdAt = createdAt,
            artworkUrl = artworkUrl,
        )

    @Test
    fun `unplayed playlists stay in the grid and played playlists come first`() {
        val first = playlist("first", "First", createdAt = 1)
        val second = playlist("second", "Second", createdAt = 2)
        val third = playlist("third", "Third", createdAt = 3)

        val ordered = PlaylistRecency.order(
            playlists = listOf(first, second, third),
            played = listOf(
                PlayedPlaylist(third, artworkUrl = "third.jpg", playedAt = 30),
                PlayedPlaylist(first, artworkUrl = "first.jpg", playedAt = 10),
            ),
        )

        assertEquals(listOf("third", "first", "second"), ordered.map { it.playlist.id })
    }

    @Test
    fun `unplayed playlists keep their library order`() {
        val first = playlist("first", "First", createdAt = 1)
        val second = playlist("second", "Second", createdAt = 2)
        val third = playlist("third", "Third", createdAt = 3)

        val ordered = PlaylistRecency.order(
            playlists = listOf(first, second, third),
            played = emptyList(),
        )

        assertEquals(listOf("first", "second", "third"), ordered.map { it.playlist.id })
    }

    @Test
    fun `an unplayed playlist uses its own cover and creation time`() {
        val playlist = playlist("one", "One", createdAt = 42, artworkUrl = "cover.jpg")

        val ordered = PlaylistRecency.order(
            playlists = listOf(playlist),
            played = emptyList(),
        ).single()

        assertEquals("cover.jpg", ordered.artworkUrl)
        assertEquals(42, ordered.playedAt)
    }
}
