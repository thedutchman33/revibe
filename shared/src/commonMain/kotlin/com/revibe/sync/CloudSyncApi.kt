package com.revibe.sync

import com.revibe.account.CloudConfig
import com.revibe.account.StoredSession
import com.revibe.model.MusicSourceId
import com.revibe.model.Playlist
import com.revibe.model.Track
import com.revibe.platform.isoToMillis
import com.revibe.platform.millisToIso
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * PostgREST transport for sync. Track payloads are rebuilt from [CloudTrack],
 * which has no stream URL or local path fields at all, so private data cannot
 * accidentally be uploaded.
 */
class CloudSyncApi(
    private val http: HttpClient,
    private val config: CloudConfig,
) {
    private val readJson = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
    private val writeJson = Json { ignoreUnknownKeys = true; explicitNulls = true }

    suspend fun touchDevice(session: StoredSession, deviceId: String, name: String, platform: String) = guarded {
        val payload = DevicePayload(
            user_id = session.user.id,
            device_id = deviceId,
            name = name.take(120),
            platform = platform.take(200),
            last_seen_at = millisToIso(System.currentTimeMillis()),
        )
        val response = http.post(config.restUrl("/user_devices")) {
            cloud(session)
            parameter("on_conflict", "user_id,device_id")
            setBody(writeJson.encodeToString(DevicePayload.serializer(), payload))
        }
        ensureSuccess(response)
    }

    suspend fun pushPlaylist(session: StoredSession, sync: SyncedPlaylist) = guarded {
        val playlist = sync.playlist
        val playlistPayload = PlaylistPayload(
            id = playlist.id,
            user_id = session.user.id,
            name = playlist.name.take(200).ifBlank { "Untitled playlist" },
            description = playlist.description,
            created_at = millisToIso(playlist.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis()),
            deleted_at = null,
            source_platform = sync.provenance?.sourcePlatform,
            source_import_id = sync.provenance?.sourceImportId,
            imported_at = sync.provenance?.importedAt?.let(::millisToIso),
            original_source_identifier = sync.provenance?.originalSourceIdentifier,
        )
        val response = http.post(config.restUrl("/playlists")) {
            cloud(session)
            parameter("on_conflict", "id")
            setBody(writeJson.encodeToString(PlaylistPayload.serializer(), playlistPayload))
        }
        ensureSuccess(response)

        val tracks = sync.entries.mapIndexed { index, entry ->
            PlaylistTrackPayload(
                entry_id = entry.entryId.ifBlank { "${playlist.id}-$index" },
                track_id = entry.track.id,
                position = index + 1,
                track = CloudTrack.from(entry.track),
                added_at = millisToIso(entry.addedAt.takeIf { it > 0 } ?: System.currentTimeMillis()),
                source_platform = entry.sourcePlatform,
                source_import_id = entry.sourceImportId,
                imported_at = entry.importedAt?.let(::millisToIso),
            )
        }
        val rpc = http.post(config.restUrl("/rpc/sync_replace_playlist_tracks")) {
            cloud(session)
            setBody(
                writeJson.encodeToString(
                    ReplaceTracksArgs.serializer(),
                    ReplaceTracksArgs(p_playlist_id = playlist.id, p_tracks = tracks),
                ),
            )
        }
        ensureSuccess(rpc)
    }

    suspend fun pushDelete(session: StoredSession, table: String, id: String) = guarded {
        val response = http.post(config.restUrl("/rpc/sync_soft_delete")) {
            cloud(session)
            setBody(writeJson.encodeToString(DeleteArgs.serializer(), DeleteArgs(p_table = table, p_id = id)))
        }
        ensureSuccess(response)
    }

    suspend fun pushLike(session: StoredSession, sync: SyncedLike) = guarded {
        val payload = LikePayload(
            user_id = session.user.id,
            track_id = sync.track.id,
            track = CloudTrack.from(sync.track),
            added_at = millisToIso(sync.addedAt.takeIf { it > 0 } ?: System.currentTimeMillis()),
            deleted_at = null,
            source_platform = sync.provenance?.sourcePlatform,
            source_import_id = sync.provenance?.sourceImportId,
            imported_at = sync.provenance?.importedAt?.let(::millisToIso),
            original_source_identifier = sync.provenance?.originalSourceIdentifier,
        )
        val response = http.post(config.restUrl("/liked_songs")) {
            cloud(session)
            parameter("on_conflict", "user_id,track_id")
            setBody(writeJson.encodeToString(LikePayload.serializer(), payload))
        }
        ensureSuccess(response)
    }

    suspend fun pushFollow(session: StoredSession, sync: SyncedFollow) = guarded {
        val payload = FollowPayload(
            user_id = session.user.id,
            artist_id = sync.artist.id,
            name = sync.artist.name,
            image_url = sync.artist.imageUrl,
            genre = sync.artist.genre,
            added_at = millisToIso(sync.addedAt.takeIf { it > 0 } ?: System.currentTimeMillis()),
            deleted_at = null,
            source_platform = sync.provenance?.sourcePlatform,
            source_import_id = sync.provenance?.sourceImportId,
            imported_at = sync.provenance?.importedAt?.let(::millisToIso),
            original_source_identifier = sync.provenance?.originalSourceIdentifier,
        )
        val response = http.post(config.restUrl("/followed_artists")) {
            cloud(session)
            parameter("on_conflict", "user_id,artist_id")
            setBody(writeJson.encodeToString(FollowPayload.serializer(), payload))
        }
        ensureSuccess(response)
    }

    suspend fun pushHistory(session: StoredSession, sync: SyncedHistory) = guarded {
        val payload = HistoryPayload(
            id = sync.syncId,
            user_id = session.user.id,
            track_id = sync.track.id,
            track = CloudTrack.from(sync.track),
            played_at = millisToIso(sync.playedAt),
            played_ms = sync.playedMs,
            completed = sync.completed,
            skipped = sync.skipped,
            deleted_at = null,
            source_platform = sync.provenance?.sourcePlatform,
            source_import_id = sync.provenance?.sourceImportId,
            imported_at = sync.provenance?.importedAt?.let(::millisToIso),
            source_event_id = sync.provenance?.sourceEventId,
            original_source_identifier = sync.provenance?.originalSourceIdentifier,
        )
        val response = http.post(config.restUrl("/listening_history")) {
            cloud(session)
            parameter("on_conflict", "id")
            setBody(writeJson.encodeToString(HistoryPayload.serializer(), payload))
        }
        ensureSuccess(response)
    }

    // The server generates the UUID; `user_id,query_key` makes this idempotent even
    // when two devices searched the same term while offline.
    suspend fun pushSearch(session: StoredSession, sync: SyncedSearch) = guarded {
        val payload = SearchPayload(
            user_id = session.user.id,
            query_key = sync.queryKey,
            query = sync.query.take(300),
            searched_at = millisToIso(sync.searchedAt),
            deleted_at = null,
            source_platform = sync.provenance?.sourcePlatform,
            source_import_id = sync.provenance?.sourceImportId,
            imported_at = sync.provenance?.importedAt?.let(::millisToIso),
            source_event_id = sync.provenance?.sourceEventId,
            original_source_identifier = sync.provenance?.originalSourceIdentifier,
        )
        val response = http.post(config.restUrl("/search_history")) {
            cloud(session)
            parameter("on_conflict", "user_id,query_key")
            setBody(writeJson.encodeToString(SearchPayload.serializer(), payload))
        }
        ensureSuccess(response)
    }

    suspend fun pushAlbum(session: StoredSession, sync: SyncedAlbum) = guarded {
        val payload = AlbumPayload(
            user_id = session.user.id,
            album_id = sync.album.id,
            title = sync.album.title.take(500),
            artist = sync.album.artist.take(500),
            artwork_url = sync.album.artworkUrl,
            year = sync.album.year,
            track_count = sync.album.trackCount,
            genre = sync.album.genre,
            artist_id = sync.album.artistId,
            added_at = millisToIso(sync.addedAt.takeIf { it > 0 } ?: System.currentTimeMillis()),
            deleted_at = null,
            source_platform = sync.provenance?.sourcePlatform,
            source_import_id = sync.provenance?.sourceImportId,
            imported_at = sync.provenance?.importedAt?.let(::millisToIso),
            original_source_identifier = sync.provenance?.originalSourceIdentifier,
        )
        val response = http.post(config.restUrl("/saved_albums")) {
            cloud(session)
            parameter("on_conflict", "user_id,album_id")
            setBody(writeJson.encodeToString(AlbumPayload.serializer(), payload))
        }
        ensureSuccess(response)
    }

    suspend fun pushImportBatch(session: StoredSession, sync: SyncedImportBatch) = guarded {
        val payload = ImportBatchPayload(
            id = sync.id,
            user_id = session.user.id,
            source_platform = sync.sourcePlatform,
            fingerprint = sync.fingerprint,
            imported_at = millisToIso(sync.importedAt),
            summary = sync.summary.take(10_000),
            playlists_count = sync.playlists,
            tracks_count = sync.tracks,
            likes_count = sync.likes,
            albums_count = sync.albums,
            artists_count = sync.artists,
            history_count = sync.history,
            searches_count = sync.searches,
            deleted_at = null,
        )
        val response = http.post(config.restUrl("/import_batches")) {
            cloud(session)
            parameter("on_conflict", "id")
            setBody(writeJson.encodeToString(ImportBatchPayload.serializer(), payload))
        }
        ensureSuccess(response)
    }

    suspend fun pushPreferences(session: StoredSession, sync: SyncedPreferences) = guarded {
        val payload = PreferencesPayload(
            user_id = session.user.id,
            market = sync.market,
            autoplay_enabled = sync.autoplayEnabled,
            sync_search_history = sync.syncSearchHistory,
        )
        val response = http.post(config.restUrl("/user_preferences")) {
            cloud(session)
            parameter("on_conflict", "user_id")
            setBody(writeJson.encodeToString(PreferencesPayload.serializer(), payload))
        }
        ensureSuccess(response)
    }

    suspend fun pullPlaylists(session: StoredSession, since: Long?, limit: Int, offset: Int): List<PlaylistRow> =
        pull("/playlists", session, since, limit, offset, "id")

    suspend fun pullPlaylistTracks(session: StoredSession, playlistId: String): List<TrackRow> = guarded {
        val response = http.get(config.restUrl("/playlist_tracks")) {
            cloud(session)
            parameter("select", "*")
            parameter("playlist_id", "eq.$playlistId")
            parameter("deleted_at", "is.null")
            parameter("order", "position.asc")
            parameter("limit", "5000")
        }
        ensureSuccess(response)
        response.body<List<TrackRow>>()
    }

    suspend fun pullLikes(session: StoredSession, since: Long?, limit: Int, offset: Int): List<LikedRow> =
        pull("/liked_songs", session, since, limit, offset, "track_id")

    suspend fun pullFollows(session: StoredSession, since: Long?, limit: Int, offset: Int): List<FollowRow> =
        pull("/followed_artists", session, since, limit, offset, "artist_id")

    suspend fun pullAlbums(session: StoredSession, since: Long?, limit: Int, offset: Int): List<AlbumRow> =
        pull("/saved_albums", session, since, limit, offset, "album_id")

    suspend fun pullImportBatches(session: StoredSession, since: Long?, limit: Int, offset: Int): List<ImportBatchRow> =
        pull("/import_batches", session, since, limit, offset, "id")

    suspend fun pullHistory(session: StoredSession, since: Long?, limit: Int, offset: Int): List<HistoryRow> =
        pull("/listening_history", session, since, limit, offset, "id")

    suspend fun pullSearches(session: StoredSession, since: Long?, limit: Int, offset: Int): List<SearchRow> =
        pull("/search_history", session, since, limit, offset, "id")

    suspend fun pullPreferences(session: StoredSession): PreferencesRow? = guarded {
        val response = http.get(config.restUrl("/user_preferences")) {
            cloud(session)
            parameter("select", "*")
            parameter("limit", "1")
        }
        ensureSuccess(response)
        response.body<List<PreferencesRow>>().firstOrNull()
    }

    private suspend inline fun <reified T> pull(
        path: String,
        session: StoredSession,
        since: Long?,
        limit: Int,
        offset: Int,
        idColumn: String,
    ): List<T> = guarded {
        val response = http.get(config.restUrl(path)) {
            cloud(session)
            parameter("select", "*")
            if (since != null && since > 0) parameter("updated_at", "gt.${millisToIso(since)}")
            parameter("order", "updated_at.asc,$idColumn.asc")
            parameter("limit", limit)
            parameter("offset", offset)
        }
        ensureSuccess(response)
        response.body<List<T>>()
    }

    private fun HttpRequestBuilder.cloud(session: StoredSession) {
        header("apikey", config.anonKey)
        bearerAuth(session.accessToken)
        header("X-Client-Info", "revibe-desktop")
        contentType(ContentType.Application.Json)
        header("Prefer", "resolution=merge-duplicates,return=minimal")
        header(HttpHeaders.Accept, ContentType.Application.Json.toString())
    }

    private suspend fun ensureSuccess(response: HttpResponse) {
        if (response.status.isSuccess()) return
        val body = runCatching { response.bodyAsText() }.getOrDefault("")
        val parsed = runCatching { readJson.decodeFromString<PostgrestError>(body) }.getOrNull()
        val message = when (response.status) {
            HttpStatusCode.Unauthorized -> "Your session expired. Sign in again."
            HttpStatusCode.Forbidden -> "This account is not allowed to change that data."
            HttpStatusCode.TooManyRequests -> "The cloud is busy. Revibe will retry shortly."
            else -> parsed?.message?.takeIf { it.isNotBlank() }
                ?: "Cloud sync failed (${response.status.value})."
        }
        throw SyncException(response.status.value, message)
    }

    private suspend fun <T> guarded(block: suspend () -> T): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: SyncException) {
        throw error
    } catch (error: Exception) {
        throw SyncException(0, "Offline. Changes are saved on this device and will sync when you reconnect.", error)
    }

    @Serializable
    data class CloudTrack(
        val id: String,
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val artworkUrl: String? = null,
        val source: String = MusicSourceId.YOUTUBE_MUSIC.name,
        val artistId: String? = null,
        val albumId: String? = null,
        val year: Int? = null,
        val trackNumber: Int? = null,
        val genre: String? = null,
        val explicit: Boolean = false,
    ) {
        fun toTrack(): Track {
            val parsedSource = MusicSourceId.entries.firstOrNull { it.name == source }
                ?.takeUnless { it == MusicSourceId.LOCAL || it == MusicSourceId.ARCHIVED }
                ?: MusicSourceId.YOUTUBE_MUSIC
            return Track(
                id = id,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                artworkUrl = artworkUrl,
                streamUrl = null,
                source = parsedSource,
                artistId = artistId,
                albumId = albumId,
                year = year,
                trackNumber = trackNumber,
                genre = genre,
                explicit = explicit,
                localPath = null,
            )
        }

        companion object {
            fun from(track: Track) = CloudTrack(
                id = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs,
                artworkUrl = track.artworkUrl,
                source = track.source.name,
                artistId = track.artistId,
                albumId = track.albumId,
                year = track.year,
                trackNumber = track.trackNumber,
                genre = track.genre,
                explicit = track.explicit,
            )
        }
    }

    @Serializable
    data class PlaylistRow(
        val id: String,
        val name: String = "",
        val description: String = "",
        val created_at: String? = null,
        val updated_at: String? = null,
        val deleted_at: String? = null,
        val source_platform: String? = null,
        val source_import_id: String? = null,
        val imported_at: String? = null,
        val original_source_identifier: String? = null,
    ) {
        fun toPlaylist() = Playlist(
            id = id,
            name = name,
            description = description,
            createdAt = created_at?.let(::isoToMillis) ?: 0,
        )
        val provenance: ImportProvenance?
            get() {
                val platform = source_platform ?: return null
                val importId = source_import_id ?: return null
                return ImportProvenance(
                    sourcePlatform = platform,
                    sourceImportId = importId,
                    importedAt = imported_at?.let(::isoToMillis) ?: 0,
                    originalSourceIdentifier = original_source_identifier,
                )
            }
        val deleted: Boolean get() = deleted_at != null
    }

    @Serializable
    data class TrackRow(
        val entry_id: String? = null,
        val track_id: String,
        val position: Int = 1,
        val track: CloudTrack? = null,
        val added_at: String? = null,
        val source_platform: String? = null,
        val source_import_id: String? = null,
        val imported_at: String? = null,
    )

    @Serializable
    data class LikedRow(
        val track_id: String,
        val track: CloudTrack? = null,
        val added_at: String? = null,
        val deleted_at: String? = null,
        val source_platform: String? = null,
        val source_import_id: String? = null,
        val imported_at: String? = null,
        val original_source_identifier: String? = null,
    ) {
        val deleted: Boolean get() = deleted_at != null
        val provenance: ImportProvenance? get() = provenanceOf(source_platform, source_import_id, imported_at, original_source_identifier)
    }

    @Serializable
    data class FollowRow(
        val artist_id: String,
        val name: String = "",
        val image_url: String? = null,
        val genre: String? = null,
        val added_at: String? = null,
        val deleted_at: String? = null,
        val source_platform: String? = null,
        val source_import_id: String? = null,
        val imported_at: String? = null,
        val original_source_identifier: String? = null,
    ) {
        val deleted: Boolean get() = deleted_at != null
        val provenance: ImportProvenance? get() = provenanceOf(source_platform, source_import_id, imported_at, original_source_identifier)
    }

    @Serializable
    data class AlbumRow(
        val album_id: String,
        val title: String = "",
        val artist: String = "",
        val artwork_url: String? = null,
        val year: Int? = null,
        val track_count: Int = 0,
        val genre: String? = null,
        val artist_id: String? = null,
        val added_at: String? = null,
        val deleted_at: String? = null,
        val source_platform: String? = null,
        val source_import_id: String? = null,
        val imported_at: String? = null,
        val original_source_identifier: String? = null,
    ) {
        val deleted: Boolean get() = deleted_at != null
        val provenance: ImportProvenance? get() = provenanceOf(source_platform, source_import_id, imported_at, original_source_identifier)
        fun toAlbum() = com.revibe.model.Album(
            id = album_id,
            title = title,
            artist = artist,
            artworkUrl = artwork_url,
            year = year,
            trackCount = track_count,
            genre = genre,
            artistId = artist_id,
        )
    }

    @Serializable
    data class ImportBatchRow(
        val id: String,
        val source_platform: String = "",
        val fingerprint: String = "",
        val imported_at: String? = null,
        val summary: String = "",
        val playlists_count: Int = 0,
        val tracks_count: Int = 0,
        val likes_count: Int = 0,
        val albums_count: Int = 0,
        val artists_count: Int = 0,
        val history_count: Int = 0,
        val searches_count: Int = 0,
        val deleted_at: String? = null,
    ) {
        val deleted: Boolean get() = deleted_at != null
    }

    @Serializable
    data class HistoryRow(
        val id: String,
        val track_id: String,
        val track: CloudTrack? = null,
        val played_at: String? = null,
        @SerialName("played_ms")         val played_ms: Long = 0,
        val completed: Boolean = false,
        val skipped: Boolean = false,
        val deleted_at: String? = null,
        val source_platform: String? = null,
        val source_import_id: String? = null,
        val imported_at: String? = null,
        val source_event_id: String? = null,
        val original_source_identifier: String? = null,
    ) {
        val deleted: Boolean get() = deleted_at != null
        val provenance: ImportProvenance? get() = provenanceOf(source_platform, source_import_id, imported_at, original_source_identifier, source_event_id)
    }

    @Serializable
    data class SearchRow(
        val id: String,
        val query_key: String,
        val query: String,
        val searched_at: String? = null,
        val deleted_at: String? = null,
        val source_platform: String? = null,
        val source_import_id: String? = null,
        val imported_at: String? = null,
        val source_event_id: String? = null,
        val original_source_identifier: String? = null,
    ) {
        val deleted: Boolean get() = deleted_at != null
        val provenance: ImportProvenance? get() = provenanceOf(source_platform, source_import_id, imported_at, original_source_identifier, source_event_id)
    }

    @Serializable
    data class PreferencesRow(
        val market: String = "IN",
        val autoplay_enabled: Boolean = true,
        val sync_search_history: Boolean = false,
    )

    @Serializable private data class PlaylistPayload(
        val id: String,
        val user_id: String,
        val name: String,
        val description: String,
        val created_at: String,
        // No default: the serializer must emit an explicit null so an upsert
        // resurrects a soft-deleted row instead of leaving deleted_at set.
        val deleted_at: String?,
        val source_platform: String? = null,
        val source_import_id: String? = null,
        val imported_at: String? = null,
        val original_source_identifier: String? = null,
    )

    @Serializable private data class PlaylistTrackPayload(
        val entry_id: String,
        val track_id: String,
        val position: Int,
        val track: CloudTrack,
        val added_at: String,
        val source_platform: String? = null,
        val source_import_id: String? = null,
        val imported_at: String? = null,
    )

    @Serializable private data class ReplaceTracksArgs(
        val p_playlist_id: String,
        val p_tracks: List<PlaylistTrackPayload>,
    )

    @Serializable private data class DeleteArgs(val p_table: String, val p_id: String)

    @Serializable private data class LikePayload(
        val user_id: String,
        val track_id: String,
        val track: CloudTrack,
        val added_at: String,
        val deleted_at: String?,
        val source_platform: String? = null,
        val source_import_id: String? = null,
        val imported_at: String? = null,
        val original_source_identifier: String? = null,
    )

    @Serializable private data class FollowPayload(
        val user_id: String,
        val artist_id: String,
        val name: String,
        val image_url: String?,
        val genre: String?,
        val added_at: String,
        val deleted_at: String?,
        val source_platform: String? = null,
        val source_import_id: String? = null,
        val imported_at: String? = null,
        val original_source_identifier: String? = null,
    )

    @Serializable private data class HistoryPayload(
        val id: String,
        val user_id: String,
        val track_id: String,
        val track: CloudTrack,
        val played_at: String,
        val played_ms: Long,
        val completed: Boolean,
        val skipped: Boolean,
        val deleted_at: String?,
        val source_platform: String? = null,
        val source_import_id: String? = null,
        val imported_at: String? = null,
        val source_event_id: String? = null,
        val original_source_identifier: String? = null,
    )

    @Serializable private data class SearchPayload(
        val user_id: String,
        val query_key: String,
        val query: String,
        val searched_at: String,
        val deleted_at: String?,
        val source_platform: String? = null,
        val source_import_id: String? = null,
        val imported_at: String? = null,
        val source_event_id: String? = null,
        val original_source_identifier: String? = null,
    )

    @Serializable private data class AlbumPayload(
        val user_id: String,
        val album_id: String,
        val title: String,
        val artist: String,
        val artwork_url: String?,
        val year: Int?,
        val track_count: Int,
        val genre: String?,
        val artist_id: String?,
        val added_at: String,
        val deleted_at: String?,
        val source_platform: String? = null,
        val source_import_id: String? = null,
        val imported_at: String? = null,
        val original_source_identifier: String? = null,
    )

    @Serializable private data class ImportBatchPayload(
        val id: String,
        val user_id: String,
        val source_platform: String,
        val fingerprint: String,
        val imported_at: String,
        val summary: String,
        val playlists_count: Int,
        val tracks_count: Int,
        val likes_count: Int,
        val albums_count: Int,
        val artists_count: Int,
        val history_count: Int,
        val searches_count: Int,
        val deleted_at: String?,
    )

    @Serializable private data class PreferencesPayload(
        val user_id: String,
        val market: String,
        val autoplay_enabled: Boolean,
        val sync_search_history: Boolean,
    )

    @Serializable private data class DevicePayload(
        val user_id: String,
        val device_id: String,
        val name: String,
        val platform: String,
        val last_seen_at: String,
    )

    @Serializable private data class PostgrestError(val message: String? = null, val details: String? = null)
}

private fun provenanceOf(
    platform: String?,
    importId: String?,
    importedAt: String?,
    originalId: String?,
    eventId: String? = null,
): ImportProvenance? {
    val source = platform?.takeIf { it.isNotBlank() } ?: return null
    val id = importId?.takeIf { it.isNotBlank() } ?: return null
    return ImportProvenance(
        sourcePlatform = source,
        sourceImportId = id,
        importedAt = importedAt?.let(::isoToMillis) ?: 0,
        sourceEventId = eventId,
        originalSourceIdentifier = originalId,
    )
}
