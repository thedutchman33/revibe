package com.revibe.library

import com.revibe.model.Album
import com.revibe.model.Artist
import com.revibe.model.Playlist
import com.revibe.model.Track

/**
 * Thin domain layer over [LibraryStore]. Keeps UI code free of persistence
 * details and gives one place to add cloud sync later.
 */
class LibraryRepository(private val store: LibraryStore) {

    suspend fun likedTracks(): List<Track> = store.likedTracks()
    suspend fun likedIds(): Set<String> = store.likedIds()
    suspend fun toggleLike(track: Track): Boolean {
        val liked = store.likedIds()
        return if (track.id in liked) {
            store.removeLikedTrack(track.id)
            false
        } else {
            store.addLikedTrack(track)
            true
        }
    }

    suspend fun isLiked(trackId: String): Boolean = trackId in store.likedIds()

    suspend fun likedAlbums(): List<Album> = store.likedAlbums()
    suspend fun toggleLikedAlbum(album: Album): Boolean {
        val liked = store.likedAlbums().any { it.id == album.id }
        return if (liked) {
            store.removeLikedAlbum(album.id)
            false
        } else {
            store.addLikedAlbum(album)
            true
        }
    }

    suspend fun followedArtists(): List<Artist> = store.followedArtists()
    suspend fun toggleFollowArtist(artist: Artist): Boolean {
        val followed = store.followedArtists().any { it.id == artist.id }
        return if (followed) {
            store.removeFollowedArtist(artist.id)
            false
        } else {
            store.addFollowedArtist(artist)
            true
        }
    }

    suspend fun playlists(): List<Playlist> = store.playlists()
    suspend fun createPlaylist(name: String): Playlist = store.createPlaylist(name.ifBlank { "New playlist" })
    suspend fun renamePlaylist(id: String, name: String) = store.renamePlaylist(id, name.ifBlank { "New playlist" })
    suspend fun deletePlaylist(id: String) = store.deletePlaylist(id)
    suspend fun playlistTracks(id: String): List<Track> = store.playlistTracks(id)
    suspend fun addToPlaylist(playlistId: String, track: Track): Boolean = store.addToPlaylist(playlistId, track)
    suspend fun removeFromPlaylist(playlistId: String, trackId: String) = store.removeFromPlaylist(playlistId, trackId)
    suspend fun movePlaylistTrack(playlistId: String, from: Int, to: Int) = store.movePlaylistTrack(playlistId, from, to)

    suspend fun recordPlay(track: Track) = store.recordPlay(track)
    suspend fun recentlyPlayed(limit: Int = 12): List<Track> = store.recentlyPlayed(limit)
}
