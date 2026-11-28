package com.revibe.personalization

import com.revibe.model.Album
import com.revibe.model.MusicSourceId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Recommendations are intentionally refreshed in six-hour windows, not per screen visit. */
const val MADE_FOR_YOU_REFRESH_INTERVAL_MS = 6L * 60L * 60L * 1_000L

/** The existing Made for You row shows at most eight cards. */
const val MADE_FOR_YOU_LIMIT = 8

enum class RecommendedContentType(val label: String) {
    ALBUM("Album"),
    PLAYLIST("Playlist"),
    MIX("Official mix"),
}

/** One provider item plus the type label the existing card design already expects. */
data class RecommendedAlbum(
    val album: Album,
    val type: RecommendedContentType,
) {
    val id: String get() = album.id
}

/** SQLite payload codec. The timestamp lives in its own column, not in the JSON. */
object RecommendationCacheCodec {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class StoredItem(
        val id: String,
        val title: String,
        val artist: String,
        val artworkUrl: String? = null,
        val year: Int? = null,
        val trackCount: Int = 0,
        val artistId: String? = null,
        val type: String,
    )

    @Serializable
    private data class StoredSet(val items: List<StoredItem>)

    fun encode(items: List<RecommendedAlbum>): String = json.encodeToString(
        StoredSet.serializer(),
        StoredSet(
            items = items.map { recommended ->
                StoredItem(
                    id = recommended.album.id,
                    title = recommended.album.title,
                    artist = recommended.album.artist,
                    artworkUrl = recommended.album.artworkUrl,
                    year = recommended.album.year,
                    trackCount = recommended.album.trackCount,
                    artistId = recommended.album.artistId,
                    type = recommended.type.name,
                )
            },
        ),
    )

    fun decode(payload: String): List<RecommendedAlbum>? = runCatching {
        json.decodeFromString(StoredSet.serializer(), payload).items.mapNotNull { item ->
            val type = RecommendedContentType.entries.firstOrNull { it.name == item.type } ?: return@mapNotNull null
            RecommendedAlbum(
                album = Album(
                    id = item.id,
                    title = item.title,
                    artist = item.artist,
                    artworkUrl = item.artworkUrl,
                    year = item.year,
                    trackCount = item.trackCount,
                    genre = when (type) {
                        RecommendedContentType.ALBUM -> "Online catalog"
                        RecommendedContentType.PLAYLIST -> "YouTube Music playlist"
                        RecommendedContentType.MIX -> "Official YouTube Music mix"
                    },
                    artistId = item.artistId,
                    source = MusicSourceId.YOUTUBE_MUSIC,
                    isPlaylist = type != RecommendedContentType.ALBUM,
                ),
                type = type,
            )
        }
    }.getOrNull()
}

/**
 * Pure selection over provider candidates. It does not call the catalog, so the
 * diversity and rotation rules can be tested without the network.
 */
object MadeForYouSelector {

    private data class Scored(
        val album: Album,
        val type: RecommendedContentType,
        val score: Double,
    )

    fun select(
        familiarAlbums: List<Album>,
        discoveryAlbums: List<Album>,
        playlists: List<Album>,
        mixes: List<Album>,
        affinity: Map<String, Int>,
        recentArtists: Set<String>,
        recentlyPlayedAlbumIds: Set<String>,
        previousIds: Set<String> = emptySet(),
        playlistOccurrences: Map<String, Int> = emptyMap(),
        mixRelevance: Map<String, Int> = emptyMap(),
        limit: Int = MADE_FOR_YOU_LIMIT,
        seed: Long = 0L,
    ): List<RecommendedAlbum> {
        val familiar = score(
            albums = familiarAlbums,
            type = RecommendedContentType.ALBUM,
            base = 52.0,
        ) { album ->
            val key = normalizeArtist(album.artist)
            val plays = affinity[key] ?: 0
            val recency = if (key in recentArtists) 10.0 else 0.0
            val alreadySpinning = if (album.id in recentlyPlayedAlbumIds) -16.0 else 4.0
            minOf(plays, 25) * 2.0 + recency + alreadySpinning + jitter(album.id, seed)
        }
        val discovery = score(
            albums = discoveryAlbums,
            type = RecommendedContentType.ALBUM,
            base = 34.0,
        ) { album -> jitter(album.id, seed) }
        val playlist = score(
            albums = playlists,
            type = RecommendedContentType.PLAYLIST,
            base = 46.0,
        ) { album ->
            (playlistOccurrences[album.id] ?: 1) * 7.0 + jitter(album.id, seed)
        }
        val mix = score(
            albums = mixes,
            type = RecommendedContentType.MIX,
            base = 44.0,
        ) { album ->
            (mixRelevance[album.id] ?: 1) * 9.0 + jitter(album.id, seed)
        }

        val buckets = listOf(
            Bucket(familiar, quota = 3, type = RecommendedContentType.ALBUM),
            Bucket(playlist, quota = 3, type = RecommendedContentType.PLAYLIST),
            Bucket(discovery, quota = 2, type = RecommendedContentType.ALBUM),
            Bucket(mix, quota = 2, type = RecommendedContentType.MIX),
        )
        val selected = mutableListOf<Scored>()
        val usedIds = mutableSetOf<String>()
        val usedArtists = mutableSetOf<String>()
        val usedAlbumTitles = mutableSetOf<String>()
        val usedCollectionTitles = mutableSetOf<String>()
        val usedPerBucket = IntArray(buckets.size)

        fun canAdd(candidate: Scored, allowPrevious: Boolean): Boolean {
            if (candidate.album.source != MusicSourceId.YOUTUBE_MUSIC) return false
            if (candidate.album.id.isBlank() || candidate.album.title.isBlank()) return false
            if (candidate.album.id in usedIds) return false
            if (!allowPrevious && candidate.album.id in previousIds) return false
            if (candidate.type == RecommendedContentType.ALBUM) {
                if (candidate.album.trackCount <= 1) return false
                if (normalizeArtist(candidate.album.artist) in usedArtists) return false
                if (normalizeTitle(candidate.album.title) in usedAlbumTitles) return false
            } else {
                if (candidate.album.trackCount == 1) return false
                if (normalizeTitle(candidate.album.title) in usedCollectionTitles) return false
            }
            return true
        }

        fun add(candidate: Scored) {
            selected += candidate
            usedIds += candidate.album.id
            if (candidate.type == RecommendedContentType.ALBUM) {
                usedArtists += normalizeArtist(candidate.album.artist)
                usedAlbumTitles += normalizeTitle(candidate.album.title)
            } else {
                usedCollectionTitles += normalizeTitle(candidate.album.title)
            }
        }

        // Round-robin keeps the row from becoming all albums or all collections.
        var addedAny = true
        while (selected.size < limit && addedAny) {
            addedAny = false
            buckets.forEachIndexed { index, bucket ->
                if (selected.size >= limit) return@forEachIndexed
                if (usedPerBucket[index] >= bucket.quota) return@forEachIndexed
                val next = bucket.items.firstOrNull { canAdd(it, allowPrevious = false) }
                if (next != null) {
                    add(next)
                    usedPerBucket[index] += 1
                    addedAny = true
                }
            }
        }

        // If every fresh candidate was filtered out, prefer last refresh's items
        // over an empty shelf. A partial fresh set is shown as-is; it is not
        // padded with repeats.
        if (selected.isEmpty()) {
            for (bucket in buckets) {
                for (candidate in bucket.items) {
                    if (selected.size >= limit) break
                    if (candidate.album.id !in previousIds) continue
                    if (canAdd(candidate, allowPrevious = true)) add(candidate)
                }
            }
        }

        return selected.map { RecommendedAlbum(it.album, it.type) }
    }

    private data class Bucket(
        val items: List<Scored>,
        val quota: Int,
        val type: RecommendedContentType,
    )

    private fun score(
        albums: List<Album>,
        type: RecommendedContentType,
        base: Double,
        adjust: (Album) -> Double,
    ): List<Scored> = albums
        .asSequence()
        .filter { it.id.isNotBlank() && it.title.isNotBlank() }
        .distinctBy { it.id }
        .map { album -> Scored(album, type, base + adjust(album)) }
        .sortedWith(
            compareByDescending<Scored> { it.score }
                .thenBy { it.album.id },
        )
        .toList()

    private fun normalizeArtist(value: String): String = value.trim().lowercase()

    private fun normalizeTitle(value: String): String =
        value.trim().lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    /** Stable, refresh-window-scoped jitter so equal-quality items rotate over time. */
    private fun jitter(id: String, seed: Long): Double {
        val mixed = id.hashCode().toLong() * 31L + seed
        return ((mixed and 0x7FFF) % 7L).toDouble()
    }
}
