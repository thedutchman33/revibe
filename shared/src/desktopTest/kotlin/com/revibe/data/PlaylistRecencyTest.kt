package com.revibe.data

import com.revibe.model.MusicSourceId
import com.revibe.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaylistRecencyTest {

    private fun newStore(name: String): JdbcLibraryStore {
        val dir = Files.createTempDirectory("revibe-$name").toFile()
        return JdbcLibraryStore(File(dir, "library.db"), Dispatchers.IO)
    }

    private fun track(
        id: String,
        artworkUrl: String? = "https://example.invalid/cover.jpg",
        localPath: String? = null,
    ) = Track(
        id = id,
        title = "Track $id",
        artist = "Artist",
        album = "Album",
        durationMs = 180_000,
        artworkUrl = artworkUrl,
        source = if (localPath != null) MusicSourceId.LOCAL else MusicSourceId.YOUTUBE_MUSIC,
        localPath = localPath,
    )

    @Test
    fun `playlist grid stays empty until a playlist is actually played`() = runBlocking {
        val store = newStore("playlist-recency-empty")
        val playlist = store.createPlaylist("Never played")

        assertTrue(store.recentlyPlayedPlaylists(8).isEmpty())

        store.recordPlaylistPlay(playlist.id)

        assertEquals(listOf(playlist.id), store.recentlyPlayedPlaylists(8).map { it.playlist.id })
    }

    @Test
    fun `playlist plays are newest first and replaying a row moves it to the top`() = runBlocking {
        val store = newStore("playlist-recency-order")
        val first = store.createPlaylist("First")
        val second = store.createPlaylist("Second")
        val third = store.createPlaylist("Third")

        store.recordPlaylistPlay(first.id)
        store.recordPlaylistPlay(second.id)
        store.recordPlaylistPlay(third.id)

        assertEquals(
            listOf(third.id, second.id, first.id),
            store.recentlyPlayedPlaylists(8).map { it.playlist.id },
        )

        store.recordPlaylistPlay(first.id)

        assertEquals(
            listOf(first.id, third.id, second.id),
            store.recentlyPlayedPlaylists(8).map { it.playlist.id },
        )
        assertEquals(
            listOf(first.id, third.id),
            store.recentlyPlayedPlaylists(2).map { it.playlist.id },
        )
    }

    @Test
    fun `recent playlist carries its first available cover and real track count`() = runBlocking {
        val store = newStore("playlist-recency-cover")
        val playlist = store.createPlaylist("Covered")
        store.addToPlaylist(playlist.id, track("one", artworkUrl = null))
        store.addToPlaylist(playlist.id, track("two", artworkUrl = "https://example.invalid/second.jpg"))

        store.recordPlaylistPlay(playlist.id)

        val played = store.recentlyPlayedPlaylists(8).single()
        assertEquals("https://example.invalid/second.jpg", played.artworkUrl)
        assertEquals(2, played.playlist.trackCount)
    }

    @Test
    fun `local cover path is used when a playlist has no remote artwork`() = runBlocking {
        val store = newStore("playlist-recency-local-cover")
        val playlist = store.createPlaylist("Local")
        store.addToPlaylist(playlist.id, track("local", artworkUrl = null, localPath = "C:/Music/cover.jpg"))

        store.recordPlaylistPlay(playlist.id)

        assertEquals("C:/Music/cover.jpg", store.recentlyPlayedPlaylists(8).single().artworkUrl)
    }

    @Test
    fun `deleting a playlist removes its recent row`() = runBlocking {
        val store = newStore("playlist-recency-delete")
        val playlist = store.createPlaylist("Delete me")
        store.recordPlaylistPlay(playlist.id)

        store.deletePlaylist(playlist.id)

        assertTrue(store.recentlyPlayedPlaylists(8).isEmpty())
    }

    @Test
    fun `a playlist without any cover reports null`() = runBlocking {
        val store = newStore("playlist-recency-no-cover")
        val playlist = store.createPlaylist("No cover")
        store.addToPlaylist(playlist.id, track("plain", artworkUrl = null))
        store.recordPlaylistPlay(playlist.id)

        assertNull(store.recentlyPlayedPlaylists(8).single().artworkUrl)
    }

    @Test
    fun `library playlist rows use the first available track cover`() = runBlocking {
        val store = newStore("playlist-library-cover")
        val playlist = store.createPlaylist("Covered")
        store.addToPlaylist(playlist.id, track("one", artworkUrl = null))
        store.addToPlaylist(playlist.id, track("two", artworkUrl = "https://example.invalid/second.jpg"))

        val row = store.playlists().single { it.id == playlist.id }
        assertEquals("https://example.invalid/second.jpg", row.artworkUrl)
        assertEquals(2, row.trackCount)
        assertEquals(row.artworkUrl, store.playlist(playlist.id)?.artworkUrl)
    }

    @Test
    fun `library playlist row has no artwork when no track has a cover`() = runBlocking {
        val store = newStore("playlist-library-no-cover")
        val playlist = store.createPlaylist("No cover")
        store.addToPlaylist(playlist.id, track("plain", artworkUrl = null))

        assertNull(store.playlists().single { it.id == playlist.id }.artworkUrl)
    }
}
