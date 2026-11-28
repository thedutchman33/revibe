package com.revibe.catalog

import com.revibe.model.Album
import com.revibe.model.AlbumDetail
import com.revibe.model.Artist
import com.revibe.model.ArtistDetail
import com.revibe.model.MusicSourceId
import com.revibe.model.SearchResults
import com.revibe.model.Track
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

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
    /** Namespaced ids owned by this source, for example `ytm:` or `local:`. */
    val idPrefix: String

    suspend fun searchTracks(query: String, market: String, limit: Int): List<Track>
    suspend fun searchAlbums(query: String, market: String, limit: Int): List<Album>
    suspend fun searchArtists(query: String, market: String, limit: Int): List<Artist>

    /** Playlist-backed sources override this; providers without playlists return nothing. */
    suspend fun searchPlaylists(query: String, market: String, limit: Int): List<Album> = emptyList()

    /** Provider-curated mixes from a home feed; sources without one return nothing. */
    suspend fun officialMixes(market: String): List<Album> = emptyList()

    /**
     * Provider shelves used to recommend albums, playlists and adjacent artists for
     * one artist the user actually listens to. Sources without those shelves return
     * an empty seed; nothing is synthesised locally.
     */
    suspend fun recommendationSeed(artistId: String, market: String): ArtistRecommendationSeed =
        ArtistRecommendationSeed()

    suspend fun album(albumId: String, market: String): AlbumDetail
    suspend fun artist(artistId: String, market: String): ArtistDetail
    suspend fun albumsForArtist(artistId: String, market: String, limit: Int): List<Album>
    suspend fun tracksForArtist(artistId: String, artistName: String, market: String, limit: Int): List<Track>

    /** One search entry point used by the multi-source manager. */
    suspend fun searchAll(query: String, market: String, limit: Int): SearchResults = coroutineScope {
        val tracks = async { searchTracks(query, market, limit) }
        val albums = async { searchAlbums(query, market, (limit * 2 / 3).coerceAtLeast(4)) }
        val artists = async { searchArtists(query, market, (limit / 3).coerceAtLeast(4)) }
        val playlists = async { searchPlaylists(query, market, (limit / 3).coerceAtLeast(4)) }
        SearchResults(query, tracks.await(), albums.await(), artists.await(), playlists.await())
    }
}

/**
 * Real provider shelves for one artist: multi-track albums from the artist's
 * "Albums" shelf, YouTube Music's "Featured on" playlists and the artist's
 * "Fans might also like" neighbours. Singles and user uploads are excluded by
 * the provider parser itself, not by a hardcoded deny-list.
 */
data class ArtistRecommendationSeed(
    val albums: List<Album> = emptyList(),
    val playlists: List<Album> = emptyList(),
    val relatedArtists: List<Artist> = emptyList(),
)

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
