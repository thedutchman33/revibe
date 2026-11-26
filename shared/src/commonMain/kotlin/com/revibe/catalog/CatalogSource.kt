package com.revibe.catalog

import com.revibe.model.Album
import com.revibe.model.AlbumDetail
import com.revibe.model.Artist
import com.revibe.model.ArtistDetail
import com.revibe.model.MusicSourceId
import com.revibe.model.Track

/**
 * A music source is the only thing the rest of the app knows about a catalog.
 * Adding a new provider means implementing this interface, not touching the UI.
 *
 * Sources that require authentication or that cannot be used within a provider's
 * terms of service are represented in [com.revibe.model.MusicSourceId] but are
 * deliberately not implemented in this milestone.
 */
interface CatalogSource {
    val id: MusicSourceId
    val displayName: String

    suspend fun searchTracks(query: String, market: String, limit: Int): List<Track>
    suspend fun searchAlbums(query: String, market: String, limit: Int): List<Album>
    suspend fun searchArtists(query: String, market: String, limit: Int): List<Artist>
    suspend fun album(albumId: String, market: String): AlbumDetail
    suspend fun artist(artistId: String, market: String): ArtistDetail
    suspend fun albumsForArtist(artistId: String, market: String, limit: Int): List<Album>
    suspend fun tracksForArtist(artistId: String, artistName: String, market: String, limit: Int): List<Track>
}

/** Long-form artist information from a separate, attributed knowledge source. */
interface ArtistKnowledgeSource {
    suspend fun biography(artistName: String): ArtistKnowledge?
}

data class ArtistKnowledge(
    val summary: String,
    val imageUrl: String?,
    val pageUrl: String,
    val sourceLabel: String,
)
