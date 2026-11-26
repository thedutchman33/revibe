package com.revibe.catalog

import com.revibe.model.Album
import com.revibe.model.AlbumDetail
import com.revibe.model.Artist
import com.revibe.model.ArtistDetail
import com.revibe.model.SearchResults
import com.revibe.model.Track
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Orchestrates the active catalog source and the artist knowledge source.
 * Results are cached in memory for the session to keep browsing snappy and to
 * stay within the polite request budgets of the public APIs.
 */
class CatalogRepository(
    private val source: CatalogSource,
    private val knowledge: ArtistKnowledgeSource,
) {
    private val albumCache = mutableMapOf<String, AlbumDetail>()
    private val artistCache = mutableMapOf<String, ArtistDetail>()
    private val searchCache = mutableMapOf<String, SearchResults>()
    private val missCache = mutableSetOf<String>()

    suspend fun search(query: String, market: String): SearchResults {
        val trimmed = query.trim()
        if (trimmed.length < 2) return SearchResults(trimmed)
        val key = "$market|${trimmed.lowercase()}"
        searchCache[key]?.let { return it }
        val results = coroutineScope {
            val tracks = async { source.searchTracks(trimmed, market, 24) }
            val albums = async { source.searchAlbums(trimmed, market, 16) }
            val artists = async { source.searchArtists(trimmed, market, 8) }
            SearchResults(
                query = trimmed,
                tracks = tracks.await(),
                albums = albums.await(),
                artists = artists.await(),
            )
        }
        if (searchCache.size > 40) searchCache.clear()
        searchCache[key] = results
        return results
    }

    suspend fun album(albumId: String, market: String): AlbumDetail {
        albumCache[albumId]?.let { return it }
        val detail = source.album(numericId(albumId), market)
        albumCache[albumId] = detail
        return detail
    }

    suspend fun artist(artistId: String, market: String): ArtistDetail {
        artistCache[artistId]?.let { return it }
        val base = source.artist(numericId(artistId), market)

        val bioKey = base.artist.name.lowercase()
        val bio = if (bioKey in missCache) {
            null
        } else {
            knowledge.biography(base.artist.name).also { if (it == null) missCache += bioKey }
        }
        val enriched = base.copy(
            artist = base.artist.copy(
                name = base.artist.name.takeIf { it != "Unknown artist" } ?: "Artist",
                imageUrl = base.artist.imageUrl ?: bio?.imageUrl,
            ),
            bio = bio?.summary,
        )
        artistCache[artistId] = enriched
        return enriched
    }

    suspend fun artistKnowledge(artistName: String): ArtistKnowledge? =
        knowledge.biography(artistName)

    suspend fun albumsForArtist(artistId: String, market: String, limit: Int = 40): List<Album> =
        source.albumsForArtist(numericId(artistId), market, limit)

    suspend fun tracksForArtist(artistId: String, artistName: String, market: String, limit: Int = 20): List<Track> =
        source.tracksForArtist(numericId(artistId), artistName, market, limit)

    suspend fun shelfAlbums(query: String, market: String, limit: Int = 12): List<Album> =
        source.searchAlbums(query, market, limit)

    suspend fun shelfTracks(query: String, market: String, limit: Int = 20): List<Track> =
        source.searchTracks(query, market, limit)

    private fun numericId(prefixed: String): String = prefixed.substringAfterLast(':')
}
