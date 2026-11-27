package com.revibe.youtube

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.InnerTubeHttpException
import com.metrolist.innertubex.bodyAsTextLimited
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.ExtractedStream
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.StreamResolveException
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.models.YouTubeClient
import com.revibe.catalog.CatalogSource
import com.revibe.model.Album
import com.revibe.model.AlbumDetail
import com.revibe.model.Artist
import com.revibe.model.ArtistDetail
import com.revibe.model.MusicSourceId
import com.revibe.image.highResolutionArtworkUrl
import com.revibe.model.Track
import io.ktor.client.HttpClient
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.sequences.SequenceScope

enum class YouTubeAvailability(val label: String) {
    AVAILABLE("Available"),
    INITIALIZING("Initializing"),
    AUTHENTICATION_REQUIRED("Sign-in required"),
    STREAM_UNAVAILABLE("Stream unavailable"),
    UNSUPPORTED_TRANSPORT("Unsupported transport"),
    NETWORK_ERROR("Network error"),
    REGION_UNAVAILABLE("Region unavailable"),
    API_ERROR("API error"),
}

data class NativeQueuePage(
    val seedVideoId: String,
    val tracks: List<Track>,
    val continuation: String?,
)

data class YouTubeStream(
    val url: String,
    val headers: Map<String, String>,
    val mimeType: String?,
    val codecs: String?,
    val itag: Int,
    val expiresAtEpochMs: Long?,
    val clientName: String,
    val note: String,
) {
    override fun toString(): String = "YouTubeStream(itag=$itag, mime=$mimeType, client=$clientName)"
}

class YouTubeSourceException(
    val status: YouTubeAvailability,
    override val message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/**
 * YouTube Music through InnerTubeX. Search/browse calls use the public
 * WEB_REMIX client; playback uses InnerTubeX's extraction stack and accepts
 * only progressive MP4/AAC, which the existing JavaFX engine can decode.
 * SABR/UMP and WebM/Opus are reported as UNSUPPORTED_TRANSPORT, not silently
 * dropped, and local files remain independently playable.
 */
class YouTubeMusicSource(
    private val http: HttpClient,
) : CatalogSource, AutoCloseable {
    override val id: MusicSourceId = MusicSourceId.YOUTUBE_MUSIC
    override val displayName: String = "Online catalog"
    override val idPrefix: String = "ytm:"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val initMutex = Mutex()
    private val cacheMutex = Mutex()
    private var tube: InnerTube? = null
    private var extractor: InnerTubeExtractor? = null
    private val streamCache = mutableMapOf<String, CachedStream>()

    private val _status = MutableStateFlow(YouTubeAvailability.INITIALIZING)
    val status: StateFlow<YouTubeAvailability> = _status.asStateFlow()

    private val _diagnostic = MutableStateFlow("Waiting for the first request")
    val diagnostic: StateFlow<String> = _diagnostic.asStateFlow()


    private data class CachedStream(val stream: YouTubeStream, val cachedAtMs: Long, val ttlMs: Long)

    // ------------------------------------------------------------------ search

    override suspend fun searchTracks(query: String, market: String, limit: Int): List<Track> =
        searchRendererItems(query, SONG_PARAMS)
            .mapNotNull { parseSong(it) }
            .distinctBy { it.id }
            .take(limit)

    override suspend fun searchAlbums(query: String, market: String, limit: Int): List<Album> =
        searchRendererItems(query, ALBUM_PARAMS)
            .mapNotNull { parseAlbum(it) }
            .distinctBy { it.id }
            .take(limit)

    override suspend fun searchArtists(query: String, market: String, limit: Int): List<Artist> =
        searchRendererItems(query, ARTIST_PARAMS)
            .mapNotNull { parseArtist(it) }
            .distinctBy { it.id }
            .take(limit)

    override suspend fun searchPlaylists(query: String, market: String, limit: Int): List<Album> =
        searchRendererItems(query, PLAYLIST_PARAMS)
            .mapNotNull { parsePlaylist(it) }
            .distinctBy { it.id }
            .take(limit)

    /**
     * The official YouTube Music home "Song mixes" shelf. Radio/mix ids (RD...)
     * identify provider-generated mixes; user-created PL... playlists are
     * deliberately rejected.
     */
    @Suppress("UNUSED_PARAMETER")
    override suspend fun officialMixes(market: String): List<Album> {
        val response = apiRequest("official mixes") { client ->
            client.browse(YouTubeClient.WEB_REMIX, "FEmusic_home")
        }
        val root = parseJson(response)
        val shelves = root.findAll("musicCarouselShelfRenderer").toList()
        val mixShelves = shelves.filter { shelf ->
            shelf.obj("header")
                ?.obj("musicCarouselShelfBasicHeaderRenderer")
                ?.obj("title")
                ?.firstText()
                ?.contains("mix", ignoreCase = true) == true
        }
        fun officialItems(from: List<JsonObject>): List<Album> = from
            .flatMap { shelf ->
                shelf.findAll("musicTwoRowItemRenderer")
                    .mapNotNull { renderer -> parseOfficialMix(renderer) }
                    .toList()
            }
            .distinctBy { it.id }

        return officialItems(mixShelves).ifEmpty { officialItems(shelves) }.take(20)
    }

    private suspend fun searchRendererItems(query: String, params: String): List<JsonObject> {
        if (query.isBlank()) return emptyList()
        val response = apiRequest("search") { client ->
            client.search(YouTubeClient.WEB_REMIX, query, params = params)
        }
        val root = parseJson(response)
        val tabbed = root.obj("contents")?.obj("tabbedSearchResultsRenderer") ?: root
        return tabbed.findAll("musicResponsiveListItemRenderer").toList()
    }

    /**
     * The native YouTube Music "Up next" queue. InnerTubeX's `next()` returns a
     * `musicQueueRenderer` playlist panel in provider order, plus a radio
     * continuation token. We never rank or reorder these rows; the only extra
     * work is parsing metadata and deduplicating against the local queue.
     */
    suspend fun nativeQueue(
        track: Track,
        continuation: String? = null,
        limit: Int = 50,
    ): NativeQueuePage {
        val videoId = track.id.removePrefix("${idPrefix}track:").substringAfter("track:")
        if (videoId.isBlank()) return NativeQueuePage("", emptyList(), null)
        val response = apiRequest(if (continuation == null) "native queue" else "native queue continuation") { client ->
            if (continuation == null) {
                client.next(YouTubeClient.WEB_REMIX, videoId = videoId, playlistId = "RDAMVM$videoId")
            } else {
                client.next(YouTubeClient.WEB_REMIX, continuation = continuation)
            }
        }
        val root = parseJson(response)
        val queueRoot = root.findAll("musicQueueRenderer").firstOrNull()?.obj("content")?.obj("playlistPanelRenderer")
            ?: root.findAll("playlistPanelRenderer").firstOrNull()
            ?: root.findAll("playlistPanelContinuation").firstOrNull()
            ?: root
        val tracks = queueRoot.findAll("playlistPanelVideoRenderer")
            .mapNotNull { parsePlaylistPanelVideo(it) }
            .toList()
        val token = queueContinuation(queueRoot) ?: if (queueRoot !== root) queueContinuation(root) else null
        return NativeQueuePage(
            seedVideoId = videoId,
            tracks = tracks.take(limit),
            continuation = token,
        )
    }

    private fun queueContinuation(root: JsonObject): String? {
        for (key in listOf("nextRadioContinuationData", "nextContinuationData", "reloadContinuationData")) {
            root.findAll(key).firstNotNullOfOrNull { it.str("continuation") }?.let { return it }
        }
        return root.findAll("continuationCommand").firstNotNullOfOrNull { it.str("token") }
    }

    /** Pagination is supported by the provider even though the current shelf UI does not need it yet. */
    suspend fun searchContinuation(query: String, continuation: String, limit: Int = 24): Pair<List<Track>, String?> {
        val response = apiRequest("search continuation") { client ->
            client.search(YouTubeClient.WEB_REMIX, query, continuation = continuation)
        }
        val root = parseJson(response)
        val section = root.obj("continuationContents")?.obj("musicShelfContinuation") ?: root
        val tracks = section.findAll("musicResponsiveListItemRenderer")
            .mapNotNull { parseSong(it) }
            .distinctBy { it.id }
            .take(limit)
            .toList()
        val token = root.findAll("continuationCommand").firstNotNullOfOrNull { it.str("token") }
        return tracks to token
    }

    // ------------------------------------------------------------------- album

    override suspend fun album(albumId: String, market: String): AlbumDetail {
        val playlistPrefix = "${idPrefix}playlist:"
        val isPlaylist = albumId.startsWith(playlistPrefix)
        val rawId = when {
            isPlaylist -> albumId.removePrefix(playlistPrefix)
            else -> albumId.substringAfter("${idPrefix}album:").ifBlank { albumId.removePrefix(idPrefix) }
        }
        val browseId = when {
            rawId.startsWith("MPRE") -> rawId
            rawId.startsWith("VL") -> rawId
            isPlaylist -> "VL$rawId"
            rawId.startsWith("OLAK5uy_") || rawId.startsWith("RD") -> "VL$rawId"
            else -> rawId
        }
        val response = apiRequest("album") { client ->
            client.browse(YouTubeClient.WEB_REMIX, browseId)
        }
        val root = parseJson(response)
        val header = findHeader(root)
        val title = header?.title ?: "Unknown album"
        val artist = header?.artist ?: "Unknown artist"
        val artwork = header?.artwork
        val year = header?.year
        val tracks = root.findAll("musicResponsiveListItemRenderer")
            .mapNotNull { renderer ->
                parseSong(
                    renderer = renderer,
                    albumFallback = title,
                    albumIdFallback = albumId,
                    artworkFallback = artwork,
                    artistFallback = artist,
                )
            }
            .distinctBy { it.id }
            .toList()
        val album = Album(
            id = albumId,
            title = title,
            artist = artist,
            artworkUrl = artwork ?: tracks.firstOrNull()?.artworkUrl,
            year = year,
            trackCount = tracks.size,
            genre = if (isPlaylist) "Online playlist" else "Online catalog",
            artistId = header?.artistId,
            source = MusicSourceId.YOUTUBE_MUSIC,
            isPlaylist = isPlaylist,
        )
        return AlbumDetail(album, tracks)
    }

    // ------------------------------------------------------------------ artist

    override suspend fun artist(artistId: String, market: String): ArtistDetail {
        val rawId = artistId.substringAfter("${idPrefix}artist:")
            .ifBlank { artistId.removePrefix(idPrefix) }
        val response = apiRequest("artist") { client ->
            client.browse(YouTubeClient.WEB_REMIX, rawId)
        }
        val root = parseJson(response)
        val header = findHeader(root)
        val name = header?.title ?: "Unknown artist"
        val topTracks = root.findAll("musicResponsiveListItemRenderer")
            .mapNotNull { parseSong(it) }
            .distinctBy { it.id }
            .take(10)
            .toList()
        val albums = root.findAll("musicTwoRowItemRenderer")
            .mapNotNull { parseAlbumCard(it, artistFallback = name) }
            .distinctBy { it.id }
            .take(40)
            .toList()
        val tracks = if (topTracks.isNotEmpty()) topTracks else searchTracks(name, market, 10)
        val artist = Artist(
            id = artistId,
            name = name,
            imageUrl = header?.artwork ?: albums.firstOrNull()?.artworkUrl,
            genre = "Online catalog",
            source = MusicSourceId.YOUTUBE_MUSIC,
        )
        return ArtistDetail(artist = artist, topTracks = tracks, albums = albums)
    }

    override suspend fun albumsForArtist(artistId: String, market: String, limit: Int): List<Album> =
        artist(artistId, market).albums.take(limit)

    override suspend fun tracksForArtist(artistId: String, artistName: String, market: String, limit: Int): List<Track> =
        artist(artistId, market).topTracks.take(limit)

    // --------------------------------------------------------------- playback

    /**
     * Resolves a track and returns only a stream the desktop engine can decode.
     * The first attempt asks InnerTubeX for MP4/AAC; if YouTube only offers
     * WebM/Opus or SABR, the documented progressive client is tried. If neither
     * is compatible the caller gets UNSUPPORTED_TRANSPORT with the real codec.
     */
    suspend fun resolveStream(track: Track, forceRefresh: Boolean = false): YouTubeStream {
        val videoId = track.id.removePrefix("${idPrefix}track:")
        require(videoId.isNotBlank()) { "Catalog track has no video id" }

        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            cacheMutex.withLock {
                streamCache[videoId]?.takeIf { now - it.cachedAtMs < it.ttlMs }?.let { return it.stream }
            }
        }

        val extraction = ensureExtractor()
        val autoHints = ContentHints(
            isExplicit = track.explicit,
            wantVideo = false,
        ).withStreamCapabilities(
            allowHls = false,
            allowSabr = false,
            allowBoundedRange = true,
            allowProgressive = true,
        )
        val progressiveHints = ContentHints(
            isExplicit = track.explicit,
            wantVideo = false,
            playbackClientOverrideId = "IOS_PRODUCER",
        ).withStreamCapabilities(
            allowHls = false,
            allowSabr = false,
            allowBoundedRange = true,
            allowProgressive = true,
        )

        data class Attempt(val label: String, val hints: ContentHints, val quality: AudioQuality)
        val attempts = listOf(
            Attempt("mp4", autoHints, AudioQuality.MP4),
            Attempt("high", autoHints, AudioQuality.HIGH),
            Attempt("progressive", progressiveHints, AudioQuality.HIGH),
        )

        var incompatible: ExtractedStream? = null
        var lastFailure: StreamResolveException? = null
        for (attempt in attempts) {
            try {
                val stream = extraction.extract(videoId, attempt.hints, audioQuality = attempt.quality) ?: continue
                if (isJavaFxCompatible(stream)) {
                    val resolved = YouTubeStream(
                        url = stream.audioUrl,
                        headers = stream.headers,
                        mimeType = stream.mimeType,
                        codecs = stream.codecs,
                        itag = stream.itag,
                        expiresAtEpochMs = stream.expiresAt?.toEpochMilliseconds(),
                        clientName = stream.clientName,
                        note = if (stream.mimeType?.startsWith("audio/") == true) {
                            "Online catalog · AAC"
                        } else {
                            "Online catalog · progressive MP4/AAC"
                        },
                    )
                    val ttl = (resolved.expiresAtEpochMs ?: (now + 240_000))
                        .minus(now)
                        .minus(45_000)
                        .coerceIn(30_000, 240_000)
                    cacheMutex.withLock {
                        if (streamCache.size > 24) streamCache.clear()
                        streamCache[videoId] = CachedStream(resolved, now, ttl)
                    }
                    setStatus(YouTubeAvailability.AVAILABLE, "${attempt.label} stream ready · itag ${stream.itag}")
                    return resolved
                }
                incompatible = stream
                setStatus(
                    YouTubeAvailability.UNSUPPORTED_TRANSPORT,
                    "Received ${stream.mimeType ?: "unknown"} (${stream.codecs ?: "unknown codec"})",
                )
            } catch (error: StreamResolveException) {
                lastFailure = error
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                lastFailure = StreamResolveException(
                    StreamResolveException.Reason.UNKNOWN,
                    error.message ?: "Stream extraction failed",
                    error,
                )
            }
        }

        if (incompatible != null) {
            throw YouTubeSourceException(
                YouTubeAvailability.UNSUPPORTED_TRANSPORT,
                "The catalog only returned ${incompatible.mimeType ?: "an unknown format"}; " +
                    "Revibe's resolver currently requires progressive MP4/AAC; SABR/UMP needs a dedicated transport.",
                lastFailure,
            )
        }
        val reason = lastFailure?.reason
        val status = when (reason) {
            StreamResolveException.Reason.AGE_RESTRICTED,
            StreamResolveException.Reason.EXPLICIT_UNSUPPORTED,
            -> YouTubeAvailability.AUTHENTICATION_REQUIRED
            StreamResolveException.Reason.UNAVAILABLE -> YouTubeAvailability.STREAM_UNAVAILABLE
            StreamResolveException.Reason.NETWORK -> YouTubeAvailability.NETWORK_ERROR
            StreamResolveException.Reason.NO_PLAYABLE_STREAM -> YouTubeAvailability.UNSUPPORTED_TRANSPORT
            else -> YouTubeAvailability.API_ERROR
        }
        setStatus(status, "Extraction failed: ${reason?.name ?: "UNKNOWN"}")
        throw YouTubeSourceException(
            status,
            when (reason) {
                StreamResolveException.Reason.AGE_RESTRICTED,
                StreamResolveException.Reason.EXPLICIT_UNSUPPORTED,
                -> "This track requires a signed-in session."
                StreamResolveException.Reason.NETWORK -> "Could not reach the catalog. Check your connection."
                StreamResolveException.Reason.UNAVAILABLE -> "This track is unavailable in the catalog."
                StreamResolveException.Reason.NO_PLAYABLE_STREAM ->
                    "The catalog returned no transport the desktop engine can play."
                else -> "The catalog could not resolve this track."
            },
            lastFailure,
        )
    }

    // ------------------------------------------------------------------- infra

    private suspend fun ensureTube(): InnerTube {
        tube?.let { return it }
        return initMutex.withLock {
            tube ?: run {
                setStatus(YouTubeAvailability.INITIALIZING, "Starting InnerTubeX")
                val client = InnerTube(http)
                val store = RemotePlayerConfigStore(
                    httpClient = http,
                    repository = PlayerConfigRepository.disabled(),
                )
                val cipher = YouTubeCipherService(http, store)
                extractor = InnerTubeExtractor(
                    configParser = YtConfigParserImpl(http, client, store, cipherService = cipher),
                    cipherService = cipher,
                    innerTube = client,
                )
                tube = client
                setStatus(YouTubeAvailability.AVAILABLE, "InnerTubeX initialized")
                client
            }
        }
    }

    private suspend fun ensureExtractor(): InnerTubeExtractor {
        ensureTube()
        return extractor ?: error("InnerTubeX extractor unavailable")
    }

    private suspend fun apiRequest(label: String, block: suspend (InnerTube) -> HttpResponse): HttpResponse {
        val client = ensureTube()
        return try {
            val response = block(client)
            if (!response.status.isSuccess()) {
                val status = when (response.status.value) {
                    401 -> YouTubeAvailability.AUTHENTICATION_REQUIRED
                    403 -> YouTubeAvailability.REGION_UNAVAILABLE
                    else -> YouTubeAvailability.API_ERROR
                }
                setStatus(status, "$label returned HTTP ${response.status.value}")
                throw YouTubeSourceException(status, "$label returned HTTP ${response.status.value}")
            }
            response
        } catch (error: CancellationException) {
            throw error
        } catch (error: YouTubeSourceException) {
            throw error
        } catch (error: InnerTubeHttpException) {
            val status = when (error.status.value) {
                401 -> YouTubeAvailability.AUTHENTICATION_REQUIRED
                403 -> YouTubeAvailability.REGION_UNAVAILABLE
                429 -> YouTubeAvailability.API_ERROR
                in 500..599 -> YouTubeAvailability.API_ERROR
                else -> YouTubeAvailability.NETWORK_ERROR
            }
            setStatus(status, "$label HTTP ${error.status.value}")
            throw YouTubeSourceException(status, "$label failed (HTTP ${error.status.value})", error)
        } catch (error: Exception) {
            setStatus(YouTubeAvailability.NETWORK_ERROR, "$label network failure")
            throw YouTubeSourceException(
                YouTubeAvailability.NETWORK_ERROR,
                "$label failed: ${error::class.simpleName ?: "network error"}",
                error,
            )
        }
    }

    private suspend fun parseJson(response: HttpResponse): JsonObject {
        val text = response.bodyAsTextLimited(6_000_000)
        return json.parseToJsonElement(text).jsonObject
    }

    private fun setStatus(status: YouTubeAvailability, detail: String) {
        _status.value = status
        _diagnostic.value = sanitize(detail)
    }

    private fun sanitize(text: String): String =
        text.replace(Regex("https?://\\S+"), "[url]").take(220)

    override fun close() {
        runCatching { tube?.close() }
        tube = null
        extractor = null
    }

    // -------------------------------------------------------------- JSON model

    private data class Header(
        val title: String,
        val artist: String?,
        val artistId: String?,
        val artwork: String?,
        val year: Int?,
    )

    private fun findHeader(root: JsonObject): Header? {
        val candidates = listOf(
            "musicResponsiveHeaderRenderer",
            "musicDetailHeaderRenderer",
            "musicImmersiveHeaderRenderer",
        ).flatMap { key -> root.findAll(key).toList() }
        for (header in candidates) {
            val title = header.obj("title")?.firstText() ?: continue
            if (title.equals("Save this for later", ignoreCase = true)) continue
            // Album headers carry "Album • 2023" in subtitle and the artist in straplineTextOne.
            val subtitleRuns = header.obj("subtitle")?.textRuns().orEmpty()
            val straplineRuns = header.obj("straplineTextOne")?.textRuns().orEmpty()
            val metadataRuns = subtitleRuns + straplineRuns
            val artwork = header.obj("thumbnail")?.thumbnailUrl()
                ?: header.obj("thumbnailRenderer")?.thumbnailUrl()
                ?: header.obj("foregroundThumbnail")?.thumbnailUrl()
                ?: header.findAll("musicThumbnailRenderer").firstNotNullOfOrNull { it.thumbnailUrl() }
            if (artwork == null && metadataRuns.isEmpty()) continue
            val artistRun = metadataRuns.firstOrNull { it.browsePageType() == "MUSIC_PAGE_TYPE_ARTIST" }
            val artist = artistRun?.str("text")
                ?: metadataRuns.firstOrNull { run ->
                    val text = run.str("text")?.trim() ?: ""
                    text.isNotBlank() && text != "•" && text.toIntOrNull() == null &&
                        text.lowercase() !in HEADER_TYPE_WORDS && !text.endsWith(" songs", ignoreCase = true)
                }?.str("text")
            val year = metadataRuns.mapNotNull { it.str("text")?.toIntOrNull() }
                .firstOrNull { it in 1900..2100 }
            return Header(
                title = title,
                artist = artist,
                artistId = artistRun?.browseId()?.let { "${idPrefix}artist:$it" },
                artwork = artwork,
                year = year,
            )
        }
        return null
    }

    private fun parseSong(
        renderer: JsonObject,
        albumFallback: String? = null,
        albumIdFallback: String? = null,
        artworkFallback: String? = null,
        artistFallback: String? = null,
    ): Track? {
        val title = renderer.flexRuns(0).firstOrNull()?.str("text")?.takeIf { it.isNotBlank() } ?: return null
        val videoId = renderer.findVideoId() ?: return null
        val runs = renderer.flexRuns(1)
        val artistRun = runs.firstOrNull { it.browsePageType() == "MUSIC_PAGE_TYPE_ARTIST" }
        val albumRun = runs.firstOrNull { it.browsePageType() == "MUSIC_PAGE_TYPE_ALBUM" }
        val artist = artistRun?.str("text")
            ?: runs.firstOrNull { run ->
                val text = run.str("text") ?: ""
                text.isNotBlank() && text != " • " && text.toIntOrNull() == null && parseClock(text) == 0L
            }?.str("text")
            ?: artistFallback
            ?: "Unknown artist"
        val album = albumRun?.str("text") ?: albumFallback ?: title
        val albumId = albumRun?.browseId()?.let { "${idPrefix}album:$it" } ?: albumIdFallback
        val duration = runs.asReversed().firstNotNullOfOrNull { run ->
            parseClock(run.str("text")).takeIf { it > 0 }
        } ?: renderer.fixedColumnTexts().firstNotNullOfOrNull { text ->
            parseClock(text).takeIf { it > 0 }
        } ?: 0L
        val artwork = renderer.thumbnailUrl() ?: artworkFallback
        val explicit = renderer.toString().contains("EXPLICIT", ignoreCase = true)
        return Track(
            id = "${idPrefix}track:$videoId",
            title = title,
            artist = artist,
            album = album,
            durationMs = duration,
            artworkUrl = artwork,
            streamUrl = null,
            source = MusicSourceId.YOUTUBE_MUSIC,
            artistId = artistRun?.browseId()?.let { "${idPrefix}artist:$it" },
            albumId = albumId,
            genre = "Online catalog",
            explicit = explicit,
        )
    }

    private fun parsePlaylistPanelVideo(renderer: JsonObject): Track? {
        val videoId = renderer.str("videoId")
            ?: renderer.findAll("watchEndpoint").firstNotNullOfOrNull { it.str("videoId") }
            ?: return null
        val title = renderer.obj("title")?.let { title ->
            title.str("simpleText")
                ?: title.textRuns().joinToString("") { it.str("text") ?: "" }.takeIf { it.isNotBlank() }
        } ?: return null
        val byline = renderer.obj("longBylineText")?.textRuns()
            ?: renderer.obj("shortBylineText")?.textRuns()
            ?: emptyList()
        val artistRun = byline.firstOrNull { it.browsePageType() == "MUSIC_PAGE_TYPE_ARTIST" }
        val artist = artistRun?.str("text")
            ?: byline.joinToString("") { it.str("text") ?: "" }
                .substringBefore("•")
                .trim()
                .takeIf { it.isNotBlank() }
            ?: "Unknown artist"
        val albumRun = byline.firstOrNull { it.browsePageType() == "MUSIC_PAGE_TYPE_ALBUM" }
        val lengthText = renderer.obj("lengthText")?.str("simpleText")
            ?: renderer.findAll("lengthText").firstNotNullOfOrNull { it.str("simpleText") }
        return Track(
            id = "${idPrefix}track:$videoId",
            title = title,
            artist = artist,
            album = albumRun?.str("text") ?: title,
            durationMs = parseClock(lengthText),
            artworkUrl = renderer.thumbnailUrl(),
            streamUrl = null,
            source = MusicSourceId.YOUTUBE_MUSIC,
            artistId = artistRun?.browseId()?.let { "${idPrefix}artist:$it" },
            albumId = albumRun?.browseId()?.let { "${idPrefix}album:$it" },
            genre = "Online catalog",
        )
    }

    private fun parseAlbum(renderer: JsonObject): Album? {
        val title = renderer.flexRuns(0).firstOrNull()?.str("text")?.takeIf { it.isNotBlank() } ?: return null
        val runs = renderer.flexRuns(1)
        val artistRun = runs.firstOrNull { it.browsePageType() == "MUSIC_PAGE_TYPE_ARTIST" }
        val artist = artistRun?.str("text")
            ?: runs.mapNotNull { it.str("text") }.firstOrNull { it != "Album" && it != " • " && it.isNotBlank() }
            ?: "Unknown artist"
        val year = runs.mapNotNull { it.str("text")?.toIntOrNull() }.firstOrNull { it in 1900..2100 }
        val albumBrowseId = renderer.findAll("browseEndpoint")
            .firstOrNull { endpoint ->
                endpoint.obj("browseEndpointContextSupportedConfigs")
                    ?.obj("browseEndpointContextMusicConfig")
                    ?.str("pageType") == "MUSIC_PAGE_TYPE_ALBUM"
            }
            ?.str("browseId")
            ?: renderer.findAll("browseEndpoint")
                .firstOrNull { it.str("browseId")?.startsWith("MPRE") == true }
                ?.str("browseId")
        val playlistId = renderer.findAll("watchPlaylistEndpoint")
            .firstNotNullOfOrNull { endpoint ->
                endpoint.str("playlistId")?.takeIf { it.startsWith("OLAK5uy_") || it.startsWith("RD") }
            }
        val key = albumBrowseId ?: playlistId ?: return null
        return Album(
            id = "${idPrefix}album:$key",
            title = title,
            artist = artist,
            artworkUrl = renderer.thumbnailUrl(),
            year = year,
            trackCount = 0,
            genre = "Online catalog",
            artistId = artistRun?.browseId()?.let { "${idPrefix}artist:$it" },
            source = MusicSourceId.YOUTUBE_MUSIC,
        )
    }

    private fun parseAlbumCard(renderer: JsonObject, artistFallback: String? = null): Album? {
        val title = renderer.obj("title")?.firstText() ?: return null
        val browseId = renderer.obj("navigationEndpoint")?.obj("browseEndpoint")?.str("browseId") ?: return null
        if (!browseId.startsWith("MPRE") && !browseId.startsWith("OLAK5uy_")) return null
        val subtitleRuns = renderer.obj("subtitle")?.textRuns() ?: emptyList()
        val artistRun = subtitleRuns.firstOrNull { it.browsePageType() == "MUSIC_PAGE_TYPE_ARTIST" }
        val year = subtitleRuns.mapNotNull { it.str("text")?.toIntOrNull() }.firstOrNull { it in 1900..2100 }
        return Album(
            id = "${idPrefix}album:$browseId",
            title = title,
            artist = artistRun?.str("text") ?: subtitleRuns.firstOrNull { run ->
                val text = run.str("text") ?: ""
                text.isNotBlank() && text != " • " && text.toIntOrNull() == null
            }?.str("text") ?: artistFallback ?: "Unknown artist",
            artworkUrl = renderer.obj("thumbnail")?.thumbnailUrl(),
            year = year,
            genre = "Online catalog",
            artistId = artistRun?.browseId()?.let { "${idPrefix}artist:$it" },
            source = MusicSourceId.YOUTUBE_MUSIC,
        )
    }

    private fun parseOfficialMix(renderer: JsonObject): Album? {
        val title = renderer.obj("title")?.firstText()?.takeIf { it.isNotBlank() } ?: return null
        val browse = renderer.obj("navigationEndpoint")?.obj("browseEndpoint")
        val pageType = browse
            ?.obj("browseEndpointContextSupportedConfigs")
            ?.obj("browseEndpointContextMusicConfig")
            ?.str("pageType")
        if (pageType != null && pageType != "MUSIC_PAGE_TYPE_PLAYLIST") return null
        val rawId = browse?.str("browseId")?.removePrefix("VL")?.takeIf { it.startsWith("RD") }
            ?: renderer.findAll("watchPlaylistEndpoint")
                .mapNotNull { endpoint -> endpoint.str("playlistId") }
                .firstOrNull { it.startsWith("RD") }
            ?: return null
        val lineup = renderer.obj("subtitle")?.firstText().orEmpty()
        return Album(
            id = "${idPrefix}playlist:$rawId",
            title = title,
            artist = lineup.ifBlank { "YouTube Music" },
            artworkUrl = renderer.thumbnailUrl(),
            trackCount = 0,
            genre = "Official YouTube Music mix",
            source = MusicSourceId.YOUTUBE_MUSIC,
            isPlaylist = true,
        )
    }

    private fun parsePlaylist(renderer: JsonObject): Album? {
        val title = renderer.flexRuns(0).firstOrNull()?.str("text")?.takeIf { it.isNotBlank() }
            ?: renderer.obj("title")?.firstText()
            ?: return null
        val browseIds = renderer.findAll("browseEndpoint")
            .mapNotNull { endpoint ->
                endpoint.str("browseId")?.takeIf { id ->
                    id.startsWith("VL") && endpoint
                        .obj("browseEndpointContextSupportedConfigs")
                        ?.obj("browseEndpointContextMusicConfig")
                        ?.str("pageType") == "MUSIC_PAGE_TYPE_PLAYLIST"
                }
            }
            .toList()
        val browseId = browseIds.firstOrNull()
            ?: renderer.findAll("browseEndpoint")
                .mapNotNull { it.str("browseId") }
                .firstOrNull { it.startsWith("VL") }
            ?: renderer.findAll("watchPlaylistEndpoint")
                .mapNotNull { it.str("playlistId") }
                .firstOrNull()
            ?: return null
        val key = browseId.removePrefix("VL")
        if (key.isBlank()) return null
        val runs = renderer.flexRuns(1)
        val ownerRun = runs.firstOrNull { run ->
            run.browsePageType() == "MUSIC_PAGE_TYPE_USER_CHANNEL" ||
                run.browsePageType() == "MUSIC_PAGE_TYPE_ARTIST"
        }
        val owner = ownerRun?.str("text")
            ?: runs.mapNotNull { it.str("text") }.firstOrNull { text ->
                text.isNotBlank() && text != " • " && text != "Playlist" &&
                    text.toIntOrNull() == null && !text.contains("song", ignoreCase = true)
            }
            ?: "Online catalog"
        val count = runs.mapNotNull { it.str("text") }
            .firstNotNullOfOrNull { text ->
                Regex("(\\d+)\\s+songs?", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
            } ?: 0
        return Album(
            id = "${idPrefix}playlist:$key",
            title = title,
            artist = owner,
            artworkUrl = renderer.thumbnailUrl(),
            trackCount = count,
            genre = "Online playlist",
            source = MusicSourceId.YOUTUBE_MUSIC,
            isPlaylist = true,
        )
    }

    private fun parseArtist(renderer: JsonObject): Artist? {
        val name = renderer.flexRuns(0).firstOrNull()?.str("text")?.takeIf { it.isNotBlank() } ?: return null
        val browseId = renderer.obj("navigationEndpoint")?.obj("browseEndpoint")?.str("browseId")
            ?: renderer.findAll("browseEndpoint").firstOrNull { it.browsePageType() == "MUSIC_PAGE_TYPE_ARTIST" }?.str("browseId")
            ?: return null
        return Artist(
            id = "${idPrefix}artist:$browseId",
            name = name,
            imageUrl = renderer.thumbnailUrl(),
            genre = "Online catalog",
            source = MusicSourceId.YOUTUBE_MUSIC,
        )
    }

    // ------------------------------------------------------------- JSON helpers

    private fun JsonObject.obj(key: String): JsonObject? = (this[key] as? JsonObject)
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.textRuns(): List<JsonObject> {
        val direct = this["runs"] as? JsonArray
        if (direct != null) return direct.mapNotNull { it as? JsonObject }
        val nested = obj("text")?.let { it["runs"] as? JsonArray }
        return nested?.mapNotNull { it as? JsonObject } ?: emptyList()
    }

    private fun JsonObject.firstText(): String? =
        textRuns().joinToString("") { it.str("text") ?: "" }.takeIf { it.isNotBlank() }
            ?: str("simpleText")

    private fun JsonObject.flexRuns(index: Int): List<JsonObject> {
        val columns = this["flexColumns"] as? JsonArray ?: return emptyList()
        val column = columns.getOrNull(index) as? JsonObject ?: return emptyList()
        return column.obj("musicResponsiveListItemFlexColumnRenderer")?.textRuns() ?: emptyList()
    }

    /** Album and playlist rows put the length in a fixed column, not the flexible metadata column. */
    private fun JsonObject.fixedColumnTexts(): List<String> {
        val columns = this["fixedColumns"] as? JsonArray ?: return emptyList()
        return columns.mapNotNull { it as? JsonObject }
            .mapNotNull { column -> column.obj("musicResponsiveListItemFixedColumnRenderer")?.firstText() }
            .flatMap { text -> text.split('•', '/').map(String::trim) }
            .filter { it.isNotBlank() }
    }

    private fun JsonObject.browseId(): String? =
        obj("navigationEndpoint")?.obj("browseEndpoint")?.str("browseId")

    private fun JsonObject.browsePageType(): String? =
        obj("navigationEndpoint")?.obj("browseEndpoint")
            ?.obj("browseEndpointContextSupportedConfigs")
            ?.obj("browseEndpointContextMusicConfig")
            ?.str("pageType")

    private fun JsonObject.findVideoId(): String? =
        obj("navigationEndpoint")?.obj("watchEndpoint")?.str("videoId")
            ?: findAll("watchEndpoint").firstNotNullOfOrNull { it.str("videoId") }

    private fun JsonObject.thumbnailUrl(): String? {
        val thumbnail = obj("thumbnail")
        val thumbnailRenderer = obj("thumbnailRenderer")
        val thumbs = (this["thumbnails"] as? JsonArray)
            ?: thumbnail?.obj("musicThumbnailRenderer")?.obj("thumbnail")?.let { it["thumbnails"] as? JsonArray }
            ?: thumbnailRenderer?.obj("musicThumbnailRenderer")?.obj("thumbnail")?.let { it["thumbnails"] as? JsonArray }
            ?: thumbnail?.let { it["thumbnails"] as? JsonArray }
            ?: thumbnailRenderer?.let { it["thumbnails"] as? JsonArray }
            ?: obj("musicThumbnailRenderer")?.obj("thumbnail")?.let { it["thumbnails"] as? JsonArray }
            ?: return null
        val best = thumbs.mapNotNull { it as? JsonObject }
            .maxByOrNull { it.str("width")?.toIntOrNull() ?: 0 }
            ?: return null
        return best.str("url")?.let(::highResolutionArtworkUrl)
    }

    private fun parseClock(text: String?): Long {
        val parts = text?.trim()?.split(':') ?: return 0
        if (parts.size !in 2..3) return 0
        val numbers = parts.map { it.toLongOrNull() ?: return 0 }
        return when (numbers.size) {
            2 -> numbers[0] * 60_000 + numbers[1] * 1000
            else -> numbers[0] * 3_600_000 + numbers[1] * 60_000 + numbers[2] * 1000
        }
    }

    private fun JsonElement.findAll(key: String): Sequence<JsonObject> = sequence {
        suspend fun SequenceScope<JsonObject>.visit(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    (element[key] as? JsonObject)?.let { yield(it) }
                    element.values.forEach { visit(it) }
                }
                is JsonArray -> element.forEach { visit(it) }
                else -> Unit
            }
        }
        visit(this@findAll)
    }

    companion object {
        private val HEADER_TYPE_WORDS = setOf("album", "single", "ep", "playlist", "song", "video")

        private const val SONG_PARAMS = "EgWKAQIIAWoKEAkQBRAKEAMQBA=="
        private const val ALBUM_PARAMS = "EgWKAQIYAWoKEAkQBRAKEAMQBA=="
        private const val ARTIST_PARAMS = "EgWKAQIgAWoKEAkQBRAKEAMQBA=="
        private const val PLAYLIST_PARAMS = "EgWKAQIoAWoKEAkQChAFEAMQBA=="
    }
}

/** True only for formats the JavaFX desktop engine can decode. */
fun isJavaFxCompatible(stream: ExtractedStream): Boolean =
    isJavaFxCompatible(
        mimeType = stream.mimeType,
        codecs = stream.codecs,
        hasSabrBootstrap = stream.sabrBootstrap != null,
        url = stream.audioUrl,
    )

fun isJavaFxCompatible(
    mimeType: String?,
    codecs: String?,
    hasSabrBootstrap: Boolean,
    url: String,
): Boolean {
    if (hasSabrBootstrap) return false
    if (url.isBlank()) return false
    val mime = mimeType?.lowercase() ?: return false
    val codec = codecs?.lowercase().orEmpty()
    if (mime.contains("webm") || codec.contains("opus") || codec.contains("vorbis")) return false
    if (mime.contains("mpegurl") || url.contains(".m3u8")) return false
    if (mime == "video/mp4" || mime == "audio/mp4" || mime == "audio/m4a") {
        return codec.isEmpty() || codec.contains("mp4a") || mime.startsWith("audio/")
    }
    return false
}

fun formatAvailability(status: YouTubeAvailability): String = status.label
