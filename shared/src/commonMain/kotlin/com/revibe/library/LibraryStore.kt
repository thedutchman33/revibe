package com.revibe.library

import com.revibe.model.Album
import com.revibe.model.Artist
import com.revibe.model.CachedLyrics
import com.revibe.model.PlayedPlaylist
import com.revibe.model.PlayedTrack
import com.revibe.model.Playlist
import com.revibe.model.PlaylistTrackEntry
import com.revibe.model.Track
import com.revibe.model.UserActivitySnapshot
import com.revibe.sync.SearchEntry

/**
 * Platform-neutral persistence contract. The desktop target implements this with
 * SQLite (JDBC); Android/iOS can implement the same interface with Room/SQLDelight
 * without touching repositories, controllers or UI.
 */
interface LibraryStore {
    suspend fun likedTracks(): List<Track>
    suspend fun likedIds(): Set<String>
    suspend fun addLikedTrack(track: Track)
    suspend fun removeLikedTrack(trackId: String)

    suspend fun likedAlbums(): List<Album>
    suspend fun addLikedAlbum(album: Album)
    suspend fun removeLikedAlbum(albumId: String)

    suspend fun followedArtists(): List<Artist>
    suspend fun addFollowedArtist(artist: Artist)
    suspend fun removeFollowedArtist(artistId: String)

    suspend fun playlists(): List<Playlist>
    suspend fun playlist(playlistId: String): Playlist?
    suspend fun createPlaylist(name: String): Playlist
    suspend fun renamePlaylist(playlistId: String, name: String)
    suspend fun setPlaylistDescription(playlistId: String, description: String)
    suspend fun deletePlaylist(playlistId: String)
    suspend fun playlistTracks(playlistId: String): List<Track>
    suspend fun playlistTrackEntries(playlistId: String): List<PlaylistTrackEntry>
    suspend fun addToPlaylist(playlistId: String, track: Track): Boolean
    suspend fun removeFromPlaylist(playlistId: String, trackId: String)
    suspend fun removePlaylistEntry(playlistId: String, entryId: String)
    suspend fun movePlaylistTrack(playlistId: String, fromIndex: Int, toIndex: Int)
    suspend fun recordPlaylistPlay(playlistId: String)
    suspend fun recentlyPlayedPlaylists(limit: Int): List<PlayedPlaylist>

    suspend fun recordPlay(track: Track)
    suspend fun recordSkip(track: Track, playedMs: Long)
    suspend fun recordCompletion(track: Track, playedMs: Long)
    suspend fun skippedTrackIds(limit: Int): List<String>
    suspend fun recentlyPlayed(limit: Int): List<Track>
    suspend fun recentlyPlayedIds(limit: Int): List<String>
    suspend fun recentHistory(limit: Int): List<PlayedTrack>
    suspend fun userActivity(): UserActivitySnapshot

    /** Persisted, de-duplicated search terms. Newest first. */
    suspend fun searchHistory(limit: Int): List<SearchEntry>
    suspend fun recordSearch(query: String): SearchEntry

    suspend fun cachedLyrics(trackId: String): CachedLyrics?
    suspend fun putLyrics(trackId: String, plain: String?, synced: String?, source: String)

    suspend fun getSetting(key: String): String?
    suspend fun setSetting(key: String, value: String)

    suspend fun scanFolders(): List<String>
    suspend fun addScanFolder(path: String)
    suspend fun removeScanFolder(path: String)

    suspend fun localTracks(): List<Track>
    suspend fun localFileState(): Map<String, Long>
    suspend fun upsertLocalTracks(tracks: List<Track>, mtimes: Map<String, Long>)
    suspend fun removeLocalTracks(paths: List<String>)
    suspend fun upsertTracks(tracks: List<Track>)
}
