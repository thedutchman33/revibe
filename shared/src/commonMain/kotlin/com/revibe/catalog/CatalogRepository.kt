package com.revibe.catalog

import com.revibe.model.Album
import com.revibe.model.AlbumDetail
import com.revibe.model.Artist
import com.revibe.model.ArtistDetail
import com.revibe.model.SearchResults
import com.revibe.model.Track

/**
 * Orchestrates the active catalog sources and the artist knowledge source.
 * YouTube Music is the streaming catalog; enrichment providers stay separate.
 * Results are cached for the session.
 */
class CatalogRepository(
    private val sources: MusicSourceManager,
    private val knowledge: ArtistKnowledgeSource,
) {
    constructor(source: CatalogSource, knowledge: ArtistKnowledgeSource) :
        this(MusicSourceManager(listOf(source)), knowledge)

    private val albumCache = mutableMapOf<String, AlbumDetail>()
    private val artistCache = mutableMapOf<String, ArtistDetail>()
    private val searchCache = mutableMapOf<String, SearchResults>()
    private val missCache = mutableSetOf<String>()

    val sourceManager: MusicSourceManager get() = sources

    suspend fun search(query: String, market: String): SearchResults {
        val trimmed = query.trim()
        if (trimmed.length < 2) return SearchResults(trimmed)
        val key = "$market|${trimmed.lowercase()}"
        searchCache[key]?.let { return it }
        val results = sources.search(trimmed, market, 24)
        if (searchCache.size > 40) searchCache.clear()
        searchCache[key] = results
        return results
    }

    suspend fun album(albumId: String, market: String): AlbumDetail {
        albumCache[albumId]?.let { return it }
        val detail = sources.album(albumId, market)
        albumCache[albumId] = detail
        return detail
    }

    suspend fun artist(artistId: String, market: String): ArtistDetail {
        artistCache[artistId]?.let { return it }
        val base = sources.artist(artistId, market)

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
        sources.albumsForArtist(artistId, market, limit)

    suspend fun tracksForArtist(artistId: String, artistName: String, market: String, limit: Int = 20): List<Track> =
        sources.tracksForArtist(artistId, artistName, market, limit)

    suspend fun shelfAlbums(query: String, market: String, limit: Int = 12): List<Album> =
        sources.shelfAlbums(query, market, limit)

    suspend fun officialMixes(market: String): List<Album> = sources.officialMixes(market)

    suspend fun shelfTracks(query: String, market: String, limit: Int = 20): List<Track> =
        sources.shelfTracks(query, market, limit)
}
