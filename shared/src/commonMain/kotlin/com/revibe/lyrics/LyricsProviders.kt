package com.revibe.lyrics

import com.revibe.model.MusicSourceId
import com.revibe.model.Track
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlin.math.abs
import kotlinx.serialization.Serializable

/** A lyrics backend that returns the shared domain model, never UI state. */
interface LyricsSource {
    val id: String
    suspend fun fetch(track: Track): Lyrics?
}

/**
 * LRCLIB is a free, community lyrics database with an open API and no key.
 * Docs: https://lrclib.net/docs
 */
class LrclibLyricsSource(private val client: HttpClient) : LyricsSource {
    override val id = "LRCLIB"

    override suspend fun fetch(track: Track): Lyrics? {
        val direct = runCatching {
            val response: HttpResponse = client.get("https://lrclib.net/api/get") {
                parameter("artist_name", track.artist)
                parameter("track_name", track.title)
                parameter("album_name", track.album)
                if (track.durationMs > 0) parameter("duration", track.durationMs / 1000)
            }
            if (!response.status.isSuccess()) return@runCatching null
            response.body<LrclibLyrics>()
        }.getOrNull()

        direct?.let { return build(track, it.syncedLyrics, it.plainLyrics, id) }

        val results = runCatching {
            val response: HttpResponse = client.get("https://lrclib.net/api/search") {
                parameter("q", "${track.artist} ${track.title}")
            }
            if (!response.status.isSuccess()) return@runCatching emptyList()
            response.body<List<LrclibLyrics>>()
        }.getOrDefault(emptyList())

        val best = results
            .filter { !it.instrumental && (!it.plainLyrics.isNullOrBlank() || !it.syncedLyrics.isNullOrBlank()) }
            .minByOrNull { candidate ->
                if (track.durationMs > 0 && candidate.duration != null) {
                    kotlin.math.abs(candidate.duration - track.durationMs / 1000.0)
                } else {
                    60.0
                }
            }
            ?: return null

        return build(track, best.syncedLyrics, best.plainLyrics, id)
    }

    private fun build(track: Track, synced: String?, plain: String?, source: String): Lyrics? {
        val lines = LrcParser.parse(synced)
        if (lines.isEmpty() && plain.isNullOrBlank()) return null
        return Lyrics(
            lines = lines,
            plain = plain,
            hasTimestamps = lines.isNotEmpty(),
            wordSynced = lines.any { it.words.isNotEmpty() },
            source = source,
            syncedText = synced,
        )
    }
}

/**
 * Unison is a crowdsourced, video-id-keyed lyric database. Reads need no key or
 * sign-in, but the licence requires credit to Unison (shown as the source in
 * the lyrics panel). Requests are limited to 600/min per IP.
 *
 * A response is only accepted when both its title and an artist token match the
 * playing track; a video-id hit alone is not enough to risk showing the wrong
 * lyrics.
 */
class UnisonLyricsSource(private val client: HttpClient) : LyricsSource {
    override val id = "Unison"

    override suspend fun fetch(track: Track): Lyrics? {
        if (track.source != MusicSourceId.YOUTUBE_MUSIC) return null
        val videoId = track.id.removePrefix("ytm:track:")
            .takeIf { it != track.id && it.isNotBlank() }
            ?: return null

        val payload = runCatching {
            val response: HttpResponse = client.get("https://unison.betterlyrics.org/lyrics") {
                parameter("v", videoId)
                header("Accept", "application/json")
            }
            if (!response.status.isSuccess()) return@runCatching null
            response.body<UnisonEnvelope>()
        }.getOrNull() ?: return null

        if (!payload.success) return null
        val data = payload.data ?: return null
        if (!matchesTrack(track.title, track.artist, data.song, data.artist)) return null
        val ttml = data.lyrics?.takeIf { it.isNotBlank() } ?: return null
        val lines = TtmlParser.parse(ttml)
        if (lines.isEmpty()) return null

        // Unison's licence requires credit to the submitter as well as the service.
        val credit = data.submitter?.displayName?.takeIf { it.isNotBlank() }
            ?.let { "Unison · $it" } ?: id
        return Lyrics(
            lines = lines,
            plain = null,
            hasTimestamps = true,
            wordSynced = lines.any { it.words.isNotEmpty() },
            source = credit,
            syncedText = ttml,
        )
    }
}

/**
 * lrc.red is a keyless public lyric database with word-synced TTML and strong
 * Indian-language coverage. Its API terms require a descriptive User-Agent that
 * links to the app and names a contact address, plus visible credit wherever
 * its data is shown. The contact address is supplied by the distributor via
 * REVIBE_CONTACT_EMAIL; without it the app still identifies itself by repo URL.
 *
 * Docs: https://lrc.red/docs
 */
class LrcRedLyricsSource(
    private val client: HttpClient,
    private val contactEmail: String? = null,
) : LyricsSource {
    override val id = "lrc.red"

    private val userAgent: String = buildString {
        append("Revibe/0.8.0 (https://github.com/revibe")
        contactEmail?.takeIf { it.isNotBlank() }?.let { append("; mailto:$it") }
        append(")")
    }

    override suspend fun fetch(track: Track): Lyrics? {
        val hits = runCatching {
            val response: HttpResponse = client.get("https://lrc.red/search.json") {
                parameter("q", "${track.title} ${track.artist}")
                header("User-Agent", userAgent)
                header("Accept", "application/json")
            }
            if (!response.status.isSuccess()) return@runCatching emptyList()
            response.body<LrcRedSearch>().hits
        }.getOrDefault(emptyList())

        val best = hits
            .asSequence()
            .filter { hit ->
                val isrc = hit.isrc.orEmpty()
                isrc.length == 12 &&
                    hit.title != null &&
                    hit.artist != null &&
                    matchesTrack(track.title, track.artist, hit.title, hit.artist) &&
                    versionCompatible(track.title, hit.title) &&
                    durationCompatible(track.durationMs, hit.duration)
            }
            .minByOrNull { hit ->
                when {
                    track.durationMs <= 0 -> 0.0
                    hit.duration == null -> Double.MAX_VALUE
                    else -> abs(track.durationMs / 1000.0 - hit.duration)
                }
            }
            ?: return null

        val isrc = best.isrc ?: return null
        val ttml = getText("https://lrc.red/s/$isrc.ttml")
        val parsedTtml = TtmlParser.parse(ttml)
        if (parsedTtml.isNotEmpty()) {
            return Lyrics(
                lines = parsedTtml,
                plain = null,
                hasTimestamps = true,
                wordSynced = parsedTtml.any { it.words.isNotEmpty() },
                source = id,
                syncedText = ttml,
            )
        }

        val lrc = getText("https://lrc.red/s/$isrc.lrc")
        val parsedLrc = LrcParser.parse(lrc)
        if (parsedLrc.isEmpty()) return null
        return Lyrics(
            lines = parsedLrc,
            plain = null,
            hasTimestamps = true,
            wordSynced = parsedLrc.any { it.words.isNotEmpty() },
            source = id,
            syncedText = lrc,
        )
    }

    private suspend fun getText(url: String): String? = runCatching {
        val response: HttpResponse = client.get(url) {
            header("User-Agent", userAgent)
            header("Accept", "text/plain, application/xml, text/xml, */*")
        }
        if (!response.status.isSuccess()) return@runCatching null
        response.bodyAsText().takeIf { it.isNotBlank() }
    }.getOrNull()
}

private val versionMarkers = listOf(
    "remix", "teaser", "trailer", "lofi", "lo-fi", "slowed", "reverb",
    "cover", "karaoke", "instrumental", "live", "acoustic", "remaster", "sped up",
)

private fun versionCompatible(trackTitle: String, candidateTitle: String): Boolean {
    val track = trackTitle.lowercase()
    val candidate = candidateTitle.lowercase()
    return versionMarkers.none { it in candidate && it !in track }
}

private fun durationCompatible(trackMs: Long, hitSeconds: Double?): Boolean {
    if (trackMs <= 0 || hitSeconds == null) return true
    return abs(trackMs / 1000.0 - hitSeconds) <= 12.0
}

/**
 * Resolution is quality-gated but preserves LRCLIB's primary status:
 * - a timed LRCLIB result is used immediately;
 * - a plain or missing LRCLIB result asks Unison for a better one;
 * - provider failures and mismatches become "no lyrics", never an exception.
 */
class LyricsProviderResolver(private val sources: List<LyricsSource>) {
    constructor(vararg sources: LyricsSource) : this(sources.toList())

    suspend fun resolve(track: Track): Lyrics? {
        var plain: Lyrics? = null
        for (source in sources) {
            val result = runCatching { source.fetch(track) }.getOrNull() ?: continue
            if (result.hasTimestamps) return result
            if (plain == null) plain = result
        }
        return plain
    }
}

private val bracketed = Regex("""[\(\[\{].*?[\)\]\}]""")
private val nonWord = Regex("""[^\p{L}\p{N}]+""")

/** Title/artist matching used by Unison; exposed for tests. */
internal fun matchesTrack(
    trackTitle: String,
    trackArtist: String,
    candidateTitle: String?,
    candidateArtist: String?,
): Boolean {
    if (candidateTitle.isNullOrBlank() || candidateArtist.isNullOrBlank()) return false
    val title = normalizeTitle(trackTitle)
    val candidate = normalizeTitle(candidateTitle)
    if (title.isEmpty() || candidate.isEmpty()) return false
    val titleMatches = title == candidate ||
        (title.length >= 3 && candidate.startsWith("$title ")) ||
        (candidate.length >= 3 && title.startsWith("$candidate "))
    if (!titleMatches) return false

    val wanted = artistTokens(trackArtist)
    val offered = artistTokens(candidateArtist)
    if (wanted.isEmpty() || offered.isEmpty()) return false
    return wanted.any { it in offered }
}

private fun normalizeTitle(value: String): String = value
    .lowercase()
    .replace(bracketed, " ")
    .replace(Regex("""\b(official|lyrics?|video|audio|visuali[sz]er|remaster(ed)?)\b"""), " ")
    .replace(nonWord, " ")
    .trim()
    .replace(Regex("""\s+"""), " ")

private fun artistTokens(value: String): Set<String> = value
    .lowercase()
    .split(Regex("""\s*(?:,|&|/|;|\bfeat\.?\b|\bft\.?\b|\bx\b)\s*"""))
    .map { it.replace(nonWord, " ").trim().replace(Regex("""\s+"""), " ") }
    .filter { it.isNotEmpty() }
    .toSet()

@Serializable
private data class LrclibLyrics(
    val id: Long? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)

@Serializable
private data class UnisonEnvelope(
    val success: Boolean = false,
    val data: UnisonData? = null,
)

@Serializable
private data class UnisonData(
    val song: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val lyrics: String? = null,
    val format: String? = null,
    val syncType: String? = null,
    val submitter: UnisonSubmitter? = null,
)

@Serializable
private data class UnisonSubmitter(val displayName: String? = null)

@Serializable
private data class LrcRedSearch(val hits: List<LrcRedHit> = emptyList())

@Serializable
private data class LrcRedHit(
    val isrc: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val duration: Double? = null,
)
