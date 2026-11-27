package com.revibe.sync

import com.revibe.model.Track

/**
 * Local persistence contract for account sync. Implementations must be safe to
 * call from background coroutines and must never enqueue an outbox row while
 * applying remote data (otherwise every pull would echo back to the cloud).
 */
interface SyncStore {
    suspend fun deviceId(): String
    suspend fun activeAccountId(): String?
    suspend fun activeAccountEmail(): String?

    /**
     * Binds the device's local library to [accountId]. Returns false when this
     * database already belongs to a different account; callers must not upload
     * or download any rows in that case.
     */
    suspend fun associateAccount(accountId: String, email: String): Boolean

    /** Enqueues existing local rows once per account so first login uploads them. */
    suspend fun bootstrapAccount(accountId: String, includeSearchHistory: Boolean)

    suspend fun pendingChanges(limit: Int = 500, excludeSearchHistory: Boolean = false): List<PendingChange>
    suspend fun clearPending(change: PendingChange)
    suspend fun pendingTombstones(limit: Int = 200): List<SyncTombstone>
    suspend fun clearTombstone(tombstone: SyncTombstone)
    suspend fun pendingFor(entityType: SyncEntityType, entityId: String): Boolean

    /** Search history is excluded while the privacy toggle is off. */
    suspend fun pendingCount(excludeSearchHistory: Boolean = false): Int

    suspend fun playlistForSync(playlistId: String): SyncedPlaylist?
    suspend fun likeForSync(trackId: String): SyncedLike?
    suspend fun albumForSync(albumId: String): SyncedAlbum?
    suspend fun followForSync(artistId: String): SyncedFollow?
    suspend fun importBatchForSync(importId: String): SyncedImportBatch?
    suspend fun historyForSync(syncId: String): SyncedHistory?
    suspend fun searchForSync(id: String): SyncedSearch?
    suspend fun preferencesForSync(): SyncedPreferences

    suspend fun applyRemotePlaylist(sync: SyncedPlaylist)
    suspend fun applyRemotePlaylistDelete(playlistId: String)
    suspend fun applyRemoteLike(sync: SyncedLike)
    suspend fun applyRemoteAlbum(sync: SyncedAlbum)
    suspend fun applyRemoteFollow(sync: SyncedFollow)
    suspend fun applyRemoteImportBatch(batch: SyncedImportBatch)
    suspend fun applyRemoteHistory(sync: SyncedHistory)
    suspend fun applyRemoteSearch(sync: SyncedSearch)
    suspend fun applyRemotePreferences(preferences: SyncedPreferences)

    suspend fun syncCursor(key: String): String?
    suspend fun setSyncCursor(key: String, value: String)
    /** [pullStartedAt] becomes the next cursor so changes during the cycle are re-read. */
    suspend fun markSyncComplete(atMillis: Long, pullStartedAt: Long = atMillis)
    suspend fun lastSyncedAt(): Long?
    suspend fun lastPullAt(): Long?
    suspend fun upsertCloudTracks(tracks: List<Track>)
    suspend fun cloudTracks(ids: List<String>): Map<String, Track>
}
