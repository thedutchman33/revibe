package com.revibe.library

import com.revibe.model.Album
import com.revibe.model.Artist
import com.revibe.model.PlayedPlaylist
import com.revibe.model.PlayedTrack
import com.revibe.model.Playlist
import com.revibe.model.PlaylistTrackEntry
import com.revibe.model.Track
import com.revibe.model.UserActivitySnapshot
import com.revibe.sync.SearchEntry

/**
 * Thin domain layer over [LibraryStore]. Keeps UI code free of persistence
 * details and gives one place to add cloud sync later.
 */
class LibraryRepository(private val store: LibraryStore) : RecommendationCacheStore {

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
    suspend fun setPlaylistDescription(id: String, description: String) = store.setPlaylistDescription(id, description)
    suspend fun deletePlaylist(id: String) = store.deletePlaylist(id)
    suspend fun playlistTracks(id: String): List<Track> = store.playlistTracks(id)
    suspend fun playlistTrackEntries(id: String): List<PlaylistTrackEntry> = store.playlistTrackEntries(id)
    suspend fun addToPlaylist(playlistId: String, track: Track): Boolean = store.addToPlaylist(playlistId, track)
    suspend fun removeFromPlaylist(playlistId: String, trackId: String) = store.removeFromPlaylist(playlistId, trackId)
    suspend fun removePlaylistEntry(playlistId: String, entryId: String) = store.removePlaylistEntry(playlistId, entryId)
    suspend fun movePlaylistTrack(playlistId: String, from: Int, to: Int) = store.movePlaylistTrack(playlistId, from, to)
    suspend fun recordPlaylistPlay(id: String) = store.recordPlaylistPlay(id)
    suspend fun recentlyPlayedPlaylists(limit: Int = 8): List<PlayedPlaylist> = store.recentlyPlayedPlaylists(limit)

    suspend fun recordPlay(track: Track) = store.recordPlay(track)
    suspend fun recordSkip(track: Track, playedMs: Long) = store.recordSkip(track, playedMs)
    suspend fun recordCompletion(track: Track, playedMs: Long) = store.recordCompletion(track, playedMs)
    suspend fun skippedTrackIds(limit: Int = 24): List<String> = store.skippedTrackIds(limit)
    suspend fun recentlyPlayed(limit: Int = 12): List<Track> = store.recentlyPlayed(limit)
    suspend fun recentHistory(limit: Int = 40): List<PlayedTrack> = store.recentHistory(limit)
    suspend fun activity(): UserActivitySnapshot = store.userActivity()
    suspend fun searchHistory(limit: Int = 8): List<SearchEntry> = store.searchHistory(limit)
    suspend fun recordSearch(query: String): SearchEntry = store.recordSearch(query)

    override suspend fun recommendationCache(): RecommendationCacheRow? = store.recommendationCache()
    override suspend fun saveRecommendationCache(row: RecommendationCacheRow) = store.saveRecommendationCache(row)
}
