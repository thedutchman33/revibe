package com.revibe.youtube

import com.revibe.data.JdbcLibraryStore
import com.revibe.library.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YouTubePlaylistTest {
    @Test
    fun searchPlaylistsAndOpenDetails() = runBlocking {
        val playlists = YouTubeTestHarness.source.searchPlaylists("arijit singh", "IN", 6)
        assertTrue(playlists.isNotEmpty(), "YouTube Music returned no playlists: ${YouTubeTestHarness.source.diagnostic.value}")
        val playlist = playlists.first()
        assertTrue(playlist.isPlaylist, "Playlist result was not marked as a playlist")
        assertTrue(playlist.id.startsWith("ytm:playlist:"), "Unexpected playlist id: ${playlist.id}")
        println("[playlist] ${playlist.title} by ${playlist.artist} count=${playlist.trackCount} id=${playlist.id}")

        val detail = YouTubeTestHarness.source.album(playlist.id, "IN")
        assertTrue(detail.album.isPlaylist, "Playlist detail lost its playlist marker")
        assertTrue(detail.tracks.isNotEmpty(), "Playlist detail returned no tracks")
        assertTrue(detail.tracks.all { it.source == com.revibe.model.MusicSourceId.YOUTUBE_MUSIC })
        println("[playlist] detail=${detail.album.title} tracks=${detail.tracks.size} cover=${detail.album.artworkUrl != null}")
    }

    @Test
    fun importsPlaylistIntoSqliteLibrary() = runBlocking {
        val playlists = YouTubeTestHarness.source.searchPlaylists("arijit singh", "IN", 2)
        val detail = YouTubeTestHarness.source.album(playlists.first().id, "IN")
        val dir = File(System.getProperty("java.io.tmpdir"), "revibe-playlist-import-${System.nanoTime()}").apply { mkdirs() }
        try {
            val library = LibraryRepository(JdbcLibraryStore(File(dir, "library.db"), Dispatchers.IO))
            val local = library.createPlaylist("Imported YouTube playlist")
            var added = 0
            detail.tracks.forEach { track ->
                if (library.addToPlaylist(local.id, track)) added++
            }
            val saved = library.playlistTracks(local.id)
            assertEquals(detail.tracks.size, added)
            assertEquals(detail.tracks.size, saved.size)
            assertEquals(detail.tracks.first().id, saved.first().id)
            println("[playlist] imported=$added local=${saved.size} playlist=${local.name}")
        } finally {
            dir.deleteRecursively()
        }
    }
}
