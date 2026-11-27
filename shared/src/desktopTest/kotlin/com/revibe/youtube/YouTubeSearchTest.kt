package com.revibe.youtube

import kotlin.test.Test
import kotlin.test.assertTrue

class YouTubeSearchTest {
    @Test
    fun tracksAlbumsAndArtistsReturnResults() {
        val tracks = YouTubeTestHarness.tracks
        val albums = YouTubeTestHarness.albums
        val artists = YouTubeTestHarness.artists
        println("[search] tracks=${tracks.size} albums=${albums.size} artists=${artists.size}")
        assertTrue(tracks.isNotEmpty(), "No YouTube Music tracks")
        assertTrue(albums.isNotEmpty(), "No YouTube Music albums")
        assertTrue(artists.isNotEmpty(), "No YouTube Music artists")

        tracks.take(3).forEach { println("[search] track=${it.title} / ${it.artist} / ${it.album} / ${it.durationMs}ms / ${it.id}") }
        albums.take(2).forEach { println("[search] album=${it.title} / ${it.artist} / ${it.id}") }
        artists.take(2).forEach { println("[search] artist=${it.name} / ${it.id}") }
    }
}
