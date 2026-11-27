package com.revibe.sync

import com.revibe.model.Album
import com.revibe.model.Artist
import com.revibe.model.Playlist
import com.revibe.model.PlaylistTrackEntry
import com.revibe.model.Track

/**
 * Every locally owned entity that participates in account sync. [wire] is the
 * stable value written to the local outbox and consumed by the sync engine;
 * [table] is the PostgREST table name used for pulls.
 */
enum class SyncEntityType(val wire: String, val table: String) {
    PLAYLIST("playlist", "playlists"),
    LIKED_SONG("liked_song", "liked_songs"),
    LIKED_ALBUM("liked_album", "saved_albums"),
    FOLLOWED_ARTIST("followed_artist", "followed_artists"),
    IMPORT_BATCH("import_batch", "import_batches"),
    HISTORY("history", "listening_history"),
    SEARCH_HISTORY("search_history", "search_history"),
    PREFERENCES("preferences", "user_preferences"),
    ;

    companion object {
        fun fromWire(value: String): SyncEntityType? = entries.firstOrNull { it.wire == value }
    }
}

enum class SyncOperation(val wire: String) { UPSERT("upsert"), DELETE("delete") }

data class PendingChange(
    val entityType: SyncEntityType,
    val entityId: String,
    val operation: SyncOperation,
    val changedAt: Long,
    val accountId: String?,
)

data class SyncTombstone(
    val entityType: SyncEntityType,
    val entityId: String,
    val changedAt: Long,
    val accountId: String?,
)

/** Provenance is optional for native rows and required for imported rows. */
data class ImportProvenance(
    val sourcePlatform: String,
    val sourceImportId: String,
    val importedAt: Long,
    val sourceEventId: String? = null,
    val originalSourceIdentifier: String? = null,
)

/** A local playlist snapshot with only cloud-safe tracks. */
data class SyncedPlaylist(
    val playlist: Playlist,
    val entries: List<PlaylistTrackEntry>,
    val deletedAt: Long? = null,
    val provenance: ImportProvenance? = null,
)

data class SyncedLike(
    val track: Track,
    val addedAt: Long,
    val deletedAt: Long? = null,
    val provenance: ImportProvenance? = null,
)

data class SyncedAlbum(
    val album: Album,
    val addedAt: Long,
    val deletedAt: Long? = null,
    val provenance: ImportProvenance? = null,
)

data class SyncedFollow(
    val artist: Artist,
    val addedAt: Long,
    val deletedAt: Long? = null,
    val provenance: ImportProvenance? = null,
)

data class SyncedHistory(
    val syncId: String,
    val track: Track,
    val playedAt: Long,
    val playedMs: Long,
    val completed: Boolean,
    val skipped: Boolean,
    val deletedAt: Long? = null,
    val provenance: ImportProvenance? = null,
)

data class SyncedSearch(
    val id: String,
    val queryKey: String,
    val query: String,
    val searchedAt: Long,
    val deletedAt: Long? = null,
    val provenance: ImportProvenance? = null,
)

data class SyncedImportBatch(
    val id: String,
    val sourcePlatform: String,
    val fingerprint: String,
    val importedAt: Long,
    val summary: String,
    val playlists: Int = 0,
    val tracks: Int = 0,
    val likes: Int = 0,
    val albums: Int = 0,
    val artists: Int = 0,
    val history: Int = 0,
    val searches: Int = 0,
    val deletedAt: Long? = null,
)

data class SyncedPreferences(
    val market: String,
    val autoplayEnabled: Boolean,
    val syncSearchHistory: Boolean,
    val updatedAt: Long,
)

data class SearchEntry(
    val id: String,
    val query: String,
    val searchedAt: Long,
)

enum class SyncPhase {
    NOT_CONFIGURED,
    RESTORING,
    SIGNED_OUT,
    IDLE,
    SYNCING,
    OFFLINE,
    ERROR,
}

/**
 * UI-safe sync state. It never carries access tokens, refresh tokens or full API
 * responses. [message] is a short, user-facing explanation.
 */
data class SyncStatus(
    val phase: SyncPhase = SyncPhase.RESTORING,
    val message: String? = null,
    val lastSyncedAt: Long? = null,
    val pendingChanges: Int = 0,
    val searchHistorySynced: Boolean = false,
    val deviceId: String? = null,
)

/** Emitted after a pull changed local data so the UI can refresh without polling. */
enum class SyncEvent { DATA_CHANGED }

/** Thrown for API failures whose details are safe to show to the user. */
class SyncException(
    val statusCode: Int,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    val isOffline: Boolean get() = statusCode == 0
    val isUnauthorized: Boolean get() = statusCode == 401
}
