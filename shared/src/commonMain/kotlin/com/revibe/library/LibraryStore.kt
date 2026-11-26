package com.revibe.library

import com.revibe.model.Album
import com.revibe.model.Artist
import com.revibe.model.CachedLyrics
import com.revibe.model.Playlist
import com.revibe.model.Track

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
    suspend fun deletePlaylist(playlistId: String)
    suspend fun playlistTracks(playlistId: String): List<Track>
    suspend fun addToPlaylist(playlistId: String, track: Track): Boolean
    suspend fun removeFromPlaylist(playlistId: String, trackId: String)
    suspend fun movePlaylistTrack(playlistId: String, fromIndex: Int, toIndex: Int)

    suspend fun recordPlay(track: Track)
    suspend fun recentlyPlayed(limit: Int): List<Track>
    suspend fun recentlyPlayedIds(limit: Int): List<String>

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
