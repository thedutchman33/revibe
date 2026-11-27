package com.revibe.provider

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Keyless Deezer artwork fallback. Deezer's public search returns a 1000 px
 * album cover, which is a useful square fallback when the primary catalogue
 * supplied no artwork. Results are accepted only after title and artist
 * matching; the album name is a ranking signal, not a requirement, because
 * Revibe may only know the playlist a track was played from.
 */
class DeezerArtworkProvider(
    private val client: HttpClient,
    private val timeoutMs: Long = 8_000,
) : ArtworkProvider {

    override val id: String = "deezer"

    private val cache = mutableMapOf<String, ArtworkCandidate?>()
    private val cacheLock = Mutex()

    override suspend fun artwork(query: ArtworkQuery): ArtworkCandidate? {
        if (!hasUsableArtworkMetadata(query)) return null
        val key = cacheKey(query)
        val cached = cacheLock.withLock { cache[key] }
        if (cached != null) return cached

        val candidate = withTimeoutOrNull(timeoutMs) {
            runCatching { fetch(query) }.getOrNull()
        }
        if (candidate != null) cacheLock.withLock { cache[key] = candidate }
        return candidate
    }

    private suspend fun fetch(query: ArtworkQuery): ArtworkCandidate? {
        // Deezer's search is plain full text; `track:`/`artist:` filters that
        // work on MusicBrainz return an empty result set here. Matching is
        // done locally after the response.
        val response = client.get("https://api.deezer.com/search") {
            parameter("q", "${escape(query.title)} ${escape(query.artist)}".trim())
            parameter("limit", 10)
        }
        if (!response.status.isSuccess()) return null
        val tracks = response.body<DeezerSearch>().data

        val best = tracks
            .filter { artworkTitlesMatch(query.title, it.title) }
            .filter { artworkArtistsMatch(query.artist, it.artist?.name.orEmpty()) }
            .maxByOrNull { track ->
                var score = track.rank.coerceAtMost(1_000_000)
                if (artworkAlbumsMatch(query.album, track.album?.title)) score += 5_000_000
                if (track.album?.coverXl != null) score += 1_000_000
                score
            } ?: return null

        val album = best.album ?: return null
        val url = album.coverXl ?: album.coverBig ?: album.coverMedium ?: return null
        return ArtworkCandidate(
            url = url.replaceFirst("http://", "https://"),
            provider = "Deezer",
            matchedBy = "deezer-track-search",
            width = 1000,
            height = 1000,
        )
    }

    private fun cacheKey(query: ArtworkQuery): String =
        "${normalizeArtworkTitle(query.title)}|${normalizeArtworkText(query.artist)}|${normalizeArtworkText(query.album)}"

    private fun escape(value: String): String = value.replace("\"", " ").trim()

    @Serializable
    private data class DeezerSearch(
        val data: List<DeezerTrack> = emptyList(),
    )

    @Serializable
    private data class DeezerTrack(
        val title: String = "",
        val rank: Long = 0,
        val artist: DeezerArtist? = null,
        val album: DeezerAlbum? = null,
    )

    @Serializable
    private data class DeezerArtist(
        val name: String = "",
    )

    @Serializable
    private data class DeezerAlbum(
        val title: String = "",
        @SerialName("cover_xl") val coverXl: String? = null,
        @SerialName("cover_big") val coverBig: String? = null,
        @SerialName("cover_medium") val coverMedium: String? = null,
    )
}
