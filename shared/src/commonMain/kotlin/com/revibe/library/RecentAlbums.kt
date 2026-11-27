package com.revibe.library

import com.revibe.model.Album
import com.revibe.model.PlayedTrack

/** An album inferred from real play history; [playedAt] is the newest play in that album. */
data class RecentAlbum(
    val album: Album,
    val playedAt: Long,
)

/**
 * Builds the Recently played > Albums row from persisted track plays.
 *
 * Only albums with a real catalog album id are returned, because the card opens
 * the album page and must not invent an id that the catalog cannot resolve. The
 * newest play supplies the artwork and sort position; repeated plays of the same
 * album collapse into one card.
 */
object RecentAlbums {
    fun from(history: List<PlayedTrack>, limit: Int = 12): List<RecentAlbum> {
        if (limit <= 0) return emptyList()
        return history
            .mapNotNull { played ->
                val albumId = played.track.albumId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                if (played.track.album.isBlank()) return@mapNotNull null
                albumId to played
            }
            .groupBy({ it.first }, { it.second })
            .map { (albumId, plays) ->
                val newest = plays.maxBy { it.playedAt }
                val track = newest.track
                RecentAlbum(
                    album = Album(
                        id = albumId,
                        title = track.album,
                        artist = track.artist,
                        artworkUrl = track.artworkUrl,
                        year = track.year,
                        artistId = track.artistId,
                        source = track.source,
                    ),
                    playedAt = newest.playedAt,
                )
            }
            .sortedByDescending { it.playedAt }
            .take(limit)
    }
}
