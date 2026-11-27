package com.revibe.catalog

import com.revibe.model.Album
import com.revibe.model.Artist
import com.revibe.model.MusicSourceId
import com.revibe.model.SearchResults
import com.revibe.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Revibe's catalog sources. YouTube Music is the primary and only catalog; the
 * manager stays multi-source so a local or enrichment catalog can be added
 * without touching the UI. Sources are queried in parallel and merged in
 * priority order, so a failure of one provider never removes the others.
 */
class MusicSourceManager(private val sources: List<CatalogSource>) {

    init {
        require(sources.isNotEmpty()) { "At least one catalog source is required" }
    }

    val primary: CatalogSource get() = sources.first()

    fun sourceFor(trackOrAlbumId: String): CatalogSource? =
        sources.firstOrNull { trackOrAlbumId.startsWith(it.idPrefix) }

    fun source(id: MusicSourceId): CatalogSource? = sources.firstOrNull { it.id == id }

    val availableSourceIds: List<MusicSourceId> get() = sources.map { it.id }

    suspend fun search(query: String, market: String, limit: Int = 24): SearchResults = coroutineScope {
        val perSource = sources.map { source ->
            async {
                try {
                    source.searchAll(query, market, limit)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    println("[sources] ${source.displayName} search failed: ${error::class.simpleName}")
                    null
                }
            }
        }.map { it.await() }.filterNotNull()

        val tracks = perSource.flatMap { it.tracks }.distinctBy { "${it.title.lowercase()}|${it.artist.lowercase()}" }.take(limit)
        val albums = perSource.flatMap { it.albums }.distinctBy { "${it.title.lowercase()}|${it.artist.lowercase()}" }.take((limit * 2 / 3).coerceAtLeast(6))
        val artists = perSource.flatMap { it.artists }.distinctBy { it.name.lowercase() }.take((limit / 3).coerceAtLeast(4))
        val playlists = perSource.flatMap { it.playlists }
            .distinctBy { it.id }
            .take((limit / 3).coerceAtLeast(4))
        SearchResults(query, tracks, albums, artists, playlists)
    }

    suspend fun album(albumId: String, market: String) =
        (sourceFor(albumId) ?: guarded(albumId)).album(albumId, market)

    suspend fun artist(artistId: String, market: String) =
        (sourceFor(artistId) ?: guarded(artistId)).artist(artistId, market)

    suspend fun albumsForArtist(artistId: String, market: String, limit: Int) =
        (sourceFor(artistId) ?: guarded(artistId)).albumsForArtist(artistId, market, limit)

    suspend fun tracksForArtist(artistId: String, artistName: String, market: String, limit: Int) =
        (sourceFor(artistId) ?: guarded(artistId)).tracksForArtist(artistId, artistName, market, limit)

    private fun guarded(id: String): CatalogSource = primary

    /** Home shelves use the first source that returns anything. */
    suspend fun shelfAlbums(query: String, market: String, limit: Int): List<Album> {
        for (source in sources) {
            val albums = try {
                source.searchAlbums(query, market, limit)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                emptyList()
            }
            if (albums.isNotEmpty()) return albums
        }
        return emptyList()
    }

    suspend fun officialMixes(market: String): List<Album> {
        val mixes = mutableListOf<Album>()
        for (source in sources) {
            try {
                mixes += source.officialMixes(market)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // A provider without a home feed simply contributes no mixes.
            }
        }
        return mixes.distinctBy { it.id }
    }

    suspend fun shelfTracks(query: String, market: String, limit: Int): List<Track> {
        for (source in sources) {
            val tracks = try {
                source.searchTracks(query, market, limit)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                emptyList()
            }
            if (tracks.isNotEmpty()) return tracks
        }
        return emptyList()
    }
}
