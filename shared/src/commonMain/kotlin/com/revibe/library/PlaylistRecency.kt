package com.revibe.library

import com.revibe.model.PlayedPlaylist
import com.revibe.model.Playlist

/**
 * Orders the playlists already present in the library for the home grid and the
 * library "Recents" sort.
 *
 * Playlists with a persisted play come first, newest play first. Playlists that
 * have never been played are still included, in the library's existing order
 * (updated_at descending), so creating or importing a playlist never makes it
 * disappear from the top grid.
 */
object PlaylistRecency {
    fun order(
        playlists: List<Playlist>,
        played: List<PlayedPlaylist>,
    ): List<PlayedPlaylist> {
        val byId = played.associateBy { it.playlist.id }
        val rank = played.mapIndexed { index, item -> item.playlist.id to index }.toMap()
        return playlists
            .map { playlist ->
                byId[playlist.id] ?: PlayedPlaylist(
                    playlist = playlist,
                    artworkUrl = playlist.artworkUrl,
                    playedAt = playlist.createdAt,
                )
            }
            .sortedBy { item -> rank[item.playlist.id] ?: Int.MAX_VALUE }
    }
}
