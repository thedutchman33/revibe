package com.revibe.provider

import com.revibe.platform.currentTimeMillis
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Artwork enrichment through MusicBrainz release search and Cover Art Archive.
 *
 * Rules:
 * - MusicBrainz is called at most once per [minIntervalMs] and always with a
 *   meaningful User-Agent (their documented requirement).
 * - A cover is only returned after an identifier/name+artist match; title-only
 *   matches are rejected.
 * - Results, including misses, are cached per album for the session.
 * - All failures return null so playback is never affected.
 */
class MusicBrainzArtworkProvider(
    private val client: HttpClient,
    private val now: () -> Long = { currentTimeMillis() },
    private val minIntervalMs: Long = 1_100,
    private val timeoutMs: Long = 8_000,
) : ArtworkProvider {

    override val id: String = "musicbrainz+coverartarchive"

    private val cache = mutableMapOf<String, ArtworkCandidate?>()
    private val gate = Mutex()
    private var lastMusicBrainzCall = 0L

    override suspend fun artwork(query: ArtworkQuery): ArtworkCandidate? {
        if (query.artist.isBlank() || query.title.isBlank()) return null

        val key = query.releaseMbid ?: cacheKey(query)
        if (cache.containsKey(key)) return cache[key]

        val candidate = withTimeoutOrNull(timeoutMs) {
            runCatching { fetch(query) }.getOrNull()
        }
        cache[key] = candidate
        return candidate
    }

    private suspend fun fetch(query: ArtworkQuery): ArtworkCandidate? {
        val mbid = query.releaseMbid
            ?: if (query.album.isNotBlank() && !query.album.equals("Unknown album", ignoreCase = true)) {
                searchRelease(query)
            } else {
                null
            }
            ?: searchRecording(query)
            ?: return null
        return coverArt(mbid)
    }

    private suspend fun searchRelease(query: ArtworkQuery): String? {
        val response = musicBrainzGet(
            "release",
            "release:\"${escape(query.album)}\" AND artist:\"${escape(query.artist)}\"",
        )
        if (!response.status.isSuccess()) return null
        val releases = response.body<MusicBrainzSearch>().releases
        val best = releases
            .filter { artworkAlbumsMatch(query.album, it.title) }
            .filter { release ->
                release.artistCredit.any { artworkArtistsMatch(query.artist, it.name) }
            }
            .maxByOrNull { it.score ?: 0 }
        return best?.id
    }

    /**
     * Release search needs a real album name. Playlist playback often only
     * knows the playlist title, so fall back to a recording lookup and use the
     * first release that recording appears on. Title and artist are checked
     * again because MusicBrainz relevance is not a match guarantee.
     */
    private suspend fun searchRecording(query: ArtworkQuery): String? {
        val response = musicBrainzGet(
            "recording",
            "recording:\"${escape(query.title)}\" AND artist:\"${escape(query.artist)}\"",
        )
        if (!response.status.isSuccess()) return null
        val recordings = response.body<MusicBrainzRecordingSearch>().recordings
        val best = recordings
            .filter { artworkTitlesMatch(query.title, it.title) }
            .filter { recording ->
                recording.artistCredit.any { artworkArtistsMatch(query.artist, it.name) }
            }
            .maxByOrNull { it.score ?: 0 }
            ?: return null
        return best.releases.firstOrNull { it.id.isNotBlank() }?.id
    }

    /**
     * MusicBrainz answers 503 when its public rate limiter trips. One retry
     * after the normal interval keeps a transient error from losing the cover
     * while still respecting the one-request-per-second rule.
     */
    private suspend fun musicBrainzGet(path: String, query: String): HttpResponse {
        waitForMusicBrainzSlot()
        suspend fun call(): HttpResponse = client.get("https://musicbrainz.org/ws/2/$path") {
            header("User-Agent", USER_AGENT)
            parameter("query", query)
            parameter("fmt", "json")
            parameter("limit", 5)
        }
        var response = call()
        if (response.status.value == 503 || response.status.value == 429) {
            waitForMusicBrainzSlot()
            response = call()
        }
        return response
    }

    private suspend fun waitForMusicBrainzSlot() {
        gate.withLock {
            val wait = minIntervalMs - (now() - lastMusicBrainzCall)
            if (wait > 0) delay(wait)
            lastMusicBrainzCall = now()
        }
    }

    private suspend fun coverArt(releaseMbid: String): ArtworkCandidate? {
        val response = client.get("https://coverartarchive.org/release/$releaseMbid") {
            header("User-Agent", USER_AGENT)
        }
        if (!response.status.isSuccess()) return null
        val images = response.body<CoverArtResponse>().images
        val front = images.firstOrNull { it.front && it.approved } ?: return null
        val url = front.thumbnails["1200"]
            ?: front.thumbnails["500"]
            ?: front.thumbnails["large"]
            ?: front.image
        if (url.isBlank()) return null
        return ArtworkCandidate(
            url = url.replaceFirst("http://", "https://"),
            provider = "Cover Art Archive",
            matchedBy = "musicbrainz-release-mbid",
        )
    }

    private fun cacheKey(query: ArtworkQuery): String = if (
        query.album.isNotBlank() && !query.album.equals("Unknown album", ignoreCase = true)
    ) {
        "${normalizeArtworkText(query.artist)}|${normalizeArtworkText(query.album)}"
    } else {
        "${normalizeArtworkText(query.artist)}|${normalizeArtworkTitle(query.title)}"
    }

    private fun escape(value: String): String = value.replace("\"", " ").trim()

    private companion object {
        const val USER_AGENT = "Revibe/0.7.0 (https://github.com/revibe)"
    }
}

@Serializable
private data class MusicBrainzSearch(
    val releases: List<MusicBrainzRelease> = emptyList(),
)

@Serializable
private data class MusicBrainzRelease(
    val id: String = "",
    val title: String = "",
    val score: Int? = null,
    @SerialName("artist-credit") val artistCredit: List<MusicBrainzArtistCredit> = emptyList(),
)

@Serializable
private data class MusicBrainzArtistCredit(
    val name: String = "",
)

@Serializable
private data class MusicBrainzRecordingSearch(
    val recordings: List<MusicBrainzRecording> = emptyList(),
)

@Serializable
private data class MusicBrainzRecording(
    val id: String = "",
    val title: String = "",
    val score: Int? = null,
    @SerialName("artist-credit") val artistCredit: List<MusicBrainzArtistCredit> = emptyList(),
    val releases: List<MusicBrainzReleaseRef> = emptyList(),
)

@Serializable
private data class MusicBrainzReleaseRef(
    val id: String = "",
    val title: String = "",
)

@Serializable
private data class CoverArtResponse(
    val images: List<CoverArtImage> = emptyList(),
)

@Serializable
private data class CoverArtImage(
    val front: Boolean = false,
    val approved: Boolean = true,
    val image: String = "",
    val thumbnails: Map<String, String> = emptyMap(),
)
