package com.revibe.personalization

import com.revibe.catalog.ArtistRecommendationSeed
import com.revibe.catalog.CatalogRepository
import com.revibe.library.RecommendationCacheRow
import com.revibe.library.RecommendationCacheStore
import com.revibe.model.Album
import com.revibe.model.PlayedTrack
import com.revibe.model.UserActivitySnapshot
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * The Made for You recommendation pipeline.
 *
 * - Only YouTube Music provider responses are used (artist pages, featured
 *   playlists, home-feed mixes). Nothing is hardcoded or sourced elsewhere.
 * - A successful set is cached in SQLite for six hours. Opening Home, restarting
 *   or navigating within the app returns the cached set without provider calls.
 * - A failed refresh keeps the previous set and does not move the timestamp.
 * - Albums are verified by fetching their real track list, so one-track releases
 *   can never be recommended as albums.
 */
class MadeForYouService(
    private val catalog: CatalogRepository,
    private val cacheStore: RecommendationCacheStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val mutex = Mutex()

    suspend fun recommendations(
        activity: UserActivitySnapshot,
        recentHistory: List<PlayedTrack>,
        market: String,
    ): List<RecommendedAlbum> = mutex.withLock {
        val now = clock()
        val cached = loadCache()
        if (cached != null &&
            now >= cached.refreshedAt &&
            now - cached.refreshedAt < MADE_FOR_YOU_REFRESH_INTERVAL_MS
        ) {
            return cached.items
        }

        val previousIds = cached?.items?.map { it.id }?.toSet().orEmpty()
        val built = try {
            build(activity, recentHistory, market, previousIds, now)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            emptyList()
        }

        // A provider failure (or a successful response with no valid items) keeps
        // the previous shelf and, importantly, does not restart the six-hour timer.
        if (built.isEmpty()) return cached?.items.orEmpty()

        runCatching {
            cacheStore.saveRecommendationCache(
                RecommendationCacheRow(now, RecommendationCacheCodec.encode(built)),
            )
        }
        built
    }

    private suspend fun loadCache(): Cached? {
        val row = try {
            cacheStore.recommendationCache()
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            null
        } ?: return null
        val items = RecommendationCacheCodec.decode(row.payload) ?: return null
        return Cached(row.refreshedAt, items)
    }

    private data class Cached(val refreshedAt: Long, val items: List<RecommendedAlbum>)

    private data class Seed(val name: String, val artistId: String, val weight: Int)

    private class MutableSeed(val name: String, var weight: Int = 0, var artistId: String? = null)

    private suspend fun build(
        activity: UserActivitySnapshot,
        recentHistory: List<PlayedTrack>,
        market: String,
        previousIds: Set<String>,
        now: Long,
    ): List<RecommendedAlbum> = coroutineScope {
        val affinity = buildAffinity(activity)
        val recentArtists = recentHistory
            .map { normalizeArtist(it.track.artist) }
            .filter { it.isNotBlank() }
            .toSet()
        val recentAlbumIds = recentHistory.mapNotNull { it.track.albumId }.toSet()
        val playedAlbumIds = activity.topAlbums.map { it.albumId }.toSet() + recentAlbumIds
        val mixes = relevantMixes(activity, recentHistory, market)
        val seedArtists = seedArtists(activity, recentHistory, market)

        if (seedArtists.isEmpty()) {
            // No artist history yet: use only the provider's own home-feed mixes.
            // The homepage still applies its normal maturity gate for the section.
            return@coroutineScope MadeForYouSelector.select(
                familiarAlbums = emptyList(),
                discoveryAlbums = emptyList(),
                playlists = emptyList(),
                mixes = mixes.values.map { it.first },
                affinity = affinity,
                recentArtists = recentArtists,
                recentlyPlayedAlbumIds = playedAlbumIds,
                previousIds = previousIds,
                mixRelevance = mixes.mapValues { it.value.second },
                seed = now / MADE_FOR_YOU_REFRESH_INTERVAL_MS,
            )
        }

        val seedPages = loadSeedPages(seedArtists.take(3), market).toMutableList()
        if (seedPages.all { page ->
                page.second.albums.isEmpty() &&
                    page.second.playlists.isEmpty() &&
                    page.second.relatedArtists.isEmpty()
            } && seedArtists.size > 3
        ) {
            seedPages += loadSeedPages(seedArtists.drop(3), market)
        }

        val familiarNames = seedPages.map { normalizeArtist(it.first.name) }.toSet()
        val relatedArtists = seedPages
            .flatMap { it.second.relatedArtists }
            .filter { normalizeArtist(it.name) !in familiarNames }
            .distinctBy { it.id }
        // Provider order is relevance; alternate the first slot across refresh
        // windows so the same neighbours are not recommended indefinitely. Small
        // provider lists keep every neighbour instead of rotating one away.
        val relatedOffset = ((now / MADE_FOR_YOU_REFRESH_INTERVAL_MS) % 2).toInt()
        val relatedSeeds = if (relatedArtists.size <= 2) {
            relatedArtists
        } else {
            relatedArtists.drop(relatedOffset).take(2)
        }
        val relatedPages = loadSeedPages(relatedSeeds.map { Seed(it.name, it.id, 0) }, market)

        val familiarShortlist = seedPages.flatMap { (seed, page) ->
            page.albums
                .filter { artistMatches(it.artist, seed.name) }
                .sortedWith(albumPreference(playedAlbumIds))
                .take(3)
        }.distinctBy { it.id }

        val discoveryShortlist = relatedPages.flatMap { (seed, page) ->
            page.albums
                .filter { artistMatches(it.artist, seed.name) }
                .sortedWith(albumPreference(playedAlbumIds))
                .take(2)
        }.distinctBy { it.id }

        val allPlaylists = (seedPages + relatedPages).flatMap { it.second.playlists }
        val playlistOccurrences = allPlaylists.groupingBy { it.id }.eachCount()
        val playlistShortlist = allPlaylists
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<Album> { playlistOccurrences[it.id] ?: 0 }
                    .thenByDescending { it.year ?: 0 }
                    .thenBy { it.title },
            )
            .take(4)

        val verifiedFamiliar = verifyAlbums(familiarShortlist, RecommendedContentType.ALBUM, market)
        val verifiedDiscovery = verifyAlbums(discoveryShortlist, RecommendedContentType.ALBUM, market)
        val verifiedPlaylists = verifyAlbums(playlistShortlist, RecommendedContentType.PLAYLIST, market)

        MadeForYouSelector.select(
            familiarAlbums = verifiedFamiliar,
            discoveryAlbums = verifiedDiscovery,
            playlists = verifiedPlaylists,
            mixes = mixes.values.map { it.first },
            affinity = affinity,
            recentArtists = recentArtists,
            recentlyPlayedAlbumIds = playedAlbumIds,
            previousIds = previousIds,
            playlistOccurrences = playlistOccurrences,
            mixRelevance = mixes.mapValues { it.value.second },
            seed = now / MADE_FOR_YOU_REFRESH_INTERVAL_MS,
        )
    }

    private suspend fun loadSeedPages(
        seeds: List<Seed>,
        market: String,
    ): List<Pair<Seed, ArtistRecommendationSeed>> = coroutineScope {
        seeds.map { seed ->
            async {
                seed to (provider { catalog.recommendationSeed(seed.artistId, market) }
                    ?: ArtistRecommendationSeed())
            }
        }.awaitAll()
    }

    /**
     * Artist affinity seeds come from real play counts, recently played artists and
     * frequently played albums. Artist ids come from the local history rows; a
     * provider artist search is only a fallback for older/local rows.
     */
    private suspend fun seedArtists(
        activity: UserActivitySnapshot,
        recentHistory: List<PlayedTrack>,
        market: String,
    ): List<Seed> = coroutineScope {
        val merged = linkedMapOf<String, MutableSeed>()
        fun merge(name: String, weight: Int, artistId: String?) {
            if (name.isBlank()) return
            val key = normalizeArtist(name)
            if (key.isBlank()) return
            val entry = merged.getOrPut(key) { MutableSeed(name.trim()) }
            entry.weight += weight
            if (entry.artistId == null) entry.artistId = artistId
        }

        activity.topArtists.forEach { merge(it.artist, it.plays * 3, it.artistId) }
        activity.topAlbums.forEach { merge(it.artist, it.plays, it.artistId) }
        recentHistory.take(20).forEachIndexed { index, played ->
            merge(played.track.artist, maxOf(14 - index, 2), played.track.artistId)
        }

        val unresolved = merged.values.filter { it.artistId == null }.take(3)
        val resolved = unresolved.map { seed ->
            async {
                seed to provider {
                    catalog.search(seed.name, market).artists
                        .firstOrNull { normalizeArtist(it.name) == normalizeArtist(seed.name) }
                }
            }
        }.awaitAll()
        resolved.forEach { (seed, artist) ->
            if (seed.artistId == null) seed.artistId = artist?.id
        }

        merged.values
            .filter { it.artistId != null }
            .sortedWith(compareByDescending<MutableSeed> { it.weight }.thenBy { it.name })
            .take(5)
            .map { Seed(it.name, it.artistId!!, it.weight) }
    }

    private suspend fun relevantMixes(
        activity: UserActivitySnapshot,
        recentHistory: List<PlayedTrack>,
        market: String,
    ): Map<String, Pair<Album, Int>> {
        val mixes = provider { catalog.officialMixes(market) } ?: return emptyMap()
        val names = (activity.topArtists.map { it.artist } +
            activity.topAlbums.map { it.artist } +
            recentHistory.map { it.track.artist })
            .flatMap(::splitArtistNames)
            .map(::normalizeArtist)
            .filter { it.length >= 3 }
            .distinct()
        return mixes.mapNotNull { mix ->
            val haystack = "${mix.title} ${mix.artist}".lowercase()
            val relevance = names.count { haystack.contains(it) }
            if (relevance == 0) return@mapNotNull null
            mix.id to (
                mix.copy(
                    artist = "YouTube Music",
                    genre = "Official YouTube Music mix",
                    isPlaylist = true,
                ) to relevance
            )
        }.toMap()
    }

    /**
     * Fetches the real track list of every shortlisted album/playlist. Anything
     * with fewer than two tracks is dropped, so a single release can never be
     * shown as an album or playlist recommendation.
     */
    private suspend fun verifyAlbums(
        candidates: List<Album>,
        type: RecommendedContentType,
        market: String,
    ): List<Album> = coroutineScope {
        val permits = Semaphore(4)
        candidates
            .distinctBy { it.id }
            .map { candidate ->
                async {
                    permits.withPermit {
                        val detail = provider { catalog.album(candidate.id, market) } ?: return@withPermit null
                        if (detail.tracks.size <= 1) return@withPermit null
                        if (type == RecommendedContentType.ALBUM && detail.album.isPlaylist) return@withPermit null
                        if (type != RecommendedContentType.ALBUM && !detail.album.isPlaylist) return@withPermit null
                        if (type == RecommendedContentType.ALBUM) {
                            // Keep the artist name from the artist page: album detail
                            // headers often omit it and would show "Unknown artist".
                            detail.album.copy(
                                title = candidate.title,
                                artist = candidate.artist,
                                artistId = candidate.artistId ?: detail.album.artistId,
                                artworkUrl = candidate.artworkUrl ?: detail.album.artworkUrl,
                                year = candidate.year ?: detail.album.year,
                                trackCount = detail.tracks.size,
                            )
                        } else {
                            // Preserve the editorial playlist owner ("YouTube Music")
                            // returned by the Featured-on shelf.
                            candidate.copy(trackCount = detail.tracks.size)
                        }
                    }
                }
            }
            .awaitAll()
            .filterNotNull()
    }

    private fun albumPreference(playedAlbumIds: Set<String>): Comparator<Album> =
        compareBy<Album> { it.id in playedAlbumIds }
            .thenByDescending { it.year ?: 0 }
            .thenBy { it.title }

    private fun buildAffinity(activity: UserActivitySnapshot): Map<String, Int> {
        val affinity = mutableMapOf<String, Int>()
        activity.topArtists.forEach { affinity.merge(normalizeArtist(it.artist), it.plays, ::maxOf) }
        activity.topAlbums.forEach { affinity.merge(normalizeArtist(it.artist), it.plays, ::maxOf) }
        return affinity
    }

    private fun artistMatches(albumArtist: String, seedName: String): Boolean {
        val album = normalizeArtist(albumArtist)
        val seed = normalizeArtist(seedName)
        if (album.isBlank() || seed.isBlank()) return false
        return album == seed || album.contains(seed) || seed.contains(album)
    }

    private fun splitArtistNames(value: String): List<String> =
        value.split(ARTIST_SEPARATORS).map(String::trim).filter { it.isNotBlank() }

    private fun normalizeArtist(value: String): String = value.trim().lowercase()

    private suspend fun <T> provider(block: suspend () -> T): T? = try {
        block()
    } catch (error: kotlinx.coroutines.CancellationException) {
        throw error
    } catch (error: Exception) {
        null
    }

    private companion object {
        val ARTIST_SEPARATORS = Regex(
            """\s*(?:,|&|\bfeat\.?|\bft\.?|\s+-\s+)\s*""",
            RegexOption.IGNORE_CASE,
        )
    }
}
