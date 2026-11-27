package com.revibe.sync

import com.revibe.account.AccountState
import com.revibe.account.AuthException
import com.revibe.account.AuthManager
import com.revibe.account.StoredSession
import com.revibe.model.Artist
import com.revibe.model.PlaylistTrackEntry
import com.revibe.model.Track
import com.revibe.platform.currentTimeMillis
import com.revibe.platform.isoToMillis
import com.revibe.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Central account sync service. Local writes are already queued in SQLite; this
 * engine drains that outbox, then pulls incremental changes. Push-before-pull
 * plus a per-entity pending check gives deterministic last-writer-wins conflict
 * resolution without blocking playback or the UI.
 */
class SyncEngine(
    private val api: CloudSyncApi,
    private val auth: AuthManager,
    private val store: SyncStore,
    private val settings: SettingsRepository,
    private val deviceName: String,
    private val platform: String,
    private val scope: CoroutineScope,
) {
    private val _status = MutableStateFlow(
        SyncStatus(
            phase = if (auth.isConfigured) SyncPhase.RESTORING else SyncPhase.NOT_CONFIGURED,
            searchHistorySynced = settings.settings.value.syncSearchHistory,
        ),
    )
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val _events = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SyncEvent> = _events.asSharedFlow()

    private val syncMutex = Mutex()
    private var started = false
    private var lastAccountId: String? = null
    private var backgroundJob: Job? = null

    fun start() {
        if (started) return
        started = true
        scope.launch {
            auth.state.collect { state ->
                when (state) {
                    is AccountState.SignedIn -> {
                        if (lastAccountId != state.user.id) {
                            lastAccountId = state.user.id
                            handleSignedIn(state)
                        } else {
                            refreshStatus()
                        }
                    }
                    is AccountState.SignedOut -> {
                        lastAccountId = null
                        _status.value = _status.value.copy(
                            phase = SyncPhase.SIGNED_OUT,
                            message = state.message,
                        )
                    }
                    is AccountState.NotConfigured -> {
                        lastAccountId = null
                        _status.value = _status.value.copy(phase = SyncPhase.NOT_CONFIGURED, message = state.reason)
                    }
                    is AccountState.AwaitingConfirmation -> {
                        lastAccountId = null
                        _status.value = _status.value.copy(phase = SyncPhase.SIGNED_OUT)
                    }
                    AccountState.Restoring -> Unit
                }
            }
        }
        backgroundJob = scope.launch {
            var failures = 0
            while (isActive) {
                delay(if (failures == 0) 60_000 else (15_000L * (1 shl failures.coerceAtMost(4))).coerceAtMost(300_000))
                if (auth.state.value is AccountState.SignedIn) {
                    failures = if (syncNow()) 0 else (failures + 1).coerceAtMost(8)
                }
            }
        }
        scope.launch { refreshStatus() }
    }

    fun requestSync() {
        scope.launch { syncNow() }
    }

    /**
     * Binds this SQLite library to the signed-in account. A false result means
     * the library already belongs to another account; the caller must not keep
     * the new account signed in or let it see synced data.
     */
    suspend fun claimLocalLibrary(): Boolean {
        val signedIn = auth.state.value as? AccountState.SignedIn ?: return false
        val associated = runCatching { store.associateAccount(signedIn.user.id, signedIn.user.email) }.getOrDefault(false)
        if (associated) {
            runCatching { store.bootstrapAccount(signedIn.user.id, settings.settings.value.syncSearchHistory) }
        }
        return associated
    }

    /** Runs one full sync cycle. Returns true when it completed successfully. */
    suspend fun syncNow(): Boolean {
        if (!auth.isConfigured) {
            _status.value = _status.value.copy(phase = SyncPhase.NOT_CONFIGURED)
            return false
        }
        val signedIn = auth.state.value as? AccountState.SignedIn ?: run {
            _status.value = _status.value.copy(phase = SyncPhase.SIGNED_OUT)
            return false
        }
        if (!syncMutex.tryLock()) return false
        try {
            val accountId = store.activeAccountId()
            if (accountId != null && accountId != signedIn.user.id) {
                _status.value = _status.value.copy(
                    phase = SyncPhase.ERROR,
                    message = "This device's library is linked to a different account. Sign in with that account to sync it.",
                )
                return false
            }
            if (accountId == null) {
                if (!store.associateAccount(signedIn.user.id, signedIn.user.email)) {
                    _status.value = _status.value.copy(
                        phase = SyncPhase.ERROR,
                        message = "This device's library is linked to a different account.",
                    )
                    return false
                }
            }
            // Idempotent: covers a crash between association and the first upload.
            store.bootstrapAccount(signedIn.user.id, settings.settings.value.syncSearchHistory)

            _status.value = _status.value.copy(phase = SyncPhase.SYNCING, message = null, pendingChanges = pendingToShow())
            var session = auth.validSession()
            val pullStartedAt = try {
                runCycle(session)
            } catch (error: SyncException) {
                if (error.statusCode != 401) throw error
                session = auth.forceRefresh()
                runCycle(session)
            }
            val now = currentTimeMillis()
            store.markSyncComplete(now, pullStartedAt)
            _status.value = _status.value.copy(
                phase = SyncPhase.IDLE,
                message = null,
                lastSyncedAt = now,
                pendingChanges = pendingToShow(),
                searchHistorySynced = settings.settings.value.syncSearchHistory,
                deviceId = store.deviceId(),
            )
            _events.tryEmit(SyncEvent.DATA_CHANGED)
            return true
        } catch (error: CancellationException) {
            throw error
        } catch (error: AuthException) {
            _status.value = _status.value.copy(
                phase = if (error.isNetwork) SyncPhase.OFFLINE else SyncPhase.ERROR,
                message = error.message ?: "Sign in to sync your account.",
                pendingChanges = runCatching { pendingToShow() }.getOrDefault(0),
            )
            return false
        } catch (error: SyncException) {
            _status.value = _status.value.copy(
                phase = if (error.isOffline) SyncPhase.OFFLINE else SyncPhase.ERROR,
                message = error.message ?: "Sync failed.",
                pendingChanges = runCatching { pendingToShow() }.getOrDefault(0),
            )
            return false
        } catch (error: Exception) {
            _status.value = _status.value.copy(
                phase = SyncPhase.ERROR,
                message = "Sync failed: ${error.message ?: "unexpected error"}",
                pendingChanges = runCatching { pendingToShow() }.getOrDefault(0),
            )
            return false
        } finally {
            syncMutex.unlock()
        }
    }

    suspend fun refreshStatus() {
        val signedIn = auth.state.value is AccountState.SignedIn
        val current = _status.value
        _status.value = current.copy(
            phase = when {
                !auth.isConfigured -> SyncPhase.NOT_CONFIGURED
                !signedIn -> if (current.phase == SyncPhase.RESTORING) SyncPhase.SIGNED_OUT else current.phase
                current.phase == SyncPhase.SYNCING -> SyncPhase.SYNCING
                else -> current.phase
            },
            lastSyncedAt = runCatching { store.lastSyncedAt() }.getOrNull() ?: current.lastSyncedAt,
            pendingChanges = runCatching { pendingToShow() }.getOrDefault(current.pendingChanges),
            searchHistorySynced = settings.settings.value.syncSearchHistory,
            deviceId = runCatching { store.deviceId() }.getOrNull() ?: current.deviceId,
        )
    }

    private suspend fun pendingToShow(): Int = store.pendingCount(!settings.settings.value.syncSearchHistory)

    private companion object {
        const val PREFERENCES_ID = "preferences"
    }

    private suspend fun handleSignedIn(state: AccountState.SignedIn) {
        try {
            if (!store.associateAccount(state.user.id, state.user.email)) {
                _status.value = _status.value.copy(
                    phase = SyncPhase.ERROR,
                    message = "This device's library is linked to a different account. Sign in with that account to sync it.",
                )
                return
            }
            store.bootstrapAccount(state.user.id, settings.settings.value.syncSearchHistory)
            syncNow()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _status.value = _status.value.copy(
                phase = SyncPhase.ERROR,
                message = "Could not prepare this device for sync: ${error.message ?: "unknown error"}",
            )
        }
    }

    /** Pushes, then returns the instant used as the next pull cursor. */
    private suspend fun runCycle(session: StoredSession): Long {
        runCatching { api.touchDevice(session, store.deviceId(), deviceName, platform) }
        pushPending(session)
        val pullStartedAt = currentTimeMillis()
        val cursor = store.lastPullAt()?.let { (it - 5_000).coerceAtLeast(0) }
        val changed = pullAll(session, cursor)
        runCatching { auth.refreshProfile() }
        if (changed) _events.tryEmit(SyncEvent.DATA_CHANGED)
        return pullStartedAt
    }

    private suspend fun pushPending(session: StoredSession) {
        val includeSearch = settings.settings.value.syncSearchHistory
        store.pendingChanges(500, excludeSearchHistory = !includeSearch).forEach { change ->
            if (change.accountId != null && change.accountId != session.user.id) {
                store.clearPending(change)
                return@forEach
            }
            val pushed = when (change.entityType) {
                SyncEntityType.PLAYLIST -> {
                    val playlist = store.playlistForSync(change.entityId)
                    if (playlist == null) {
                        api.pushDelete(session, change.entityType.table, change.entityId)
                    } else {
                        api.pushPlaylist(session, playlist)
                    }
                    true
                }
                SyncEntityType.LIKED_SONG -> {
                    val like = store.likeForSync(change.entityId)
                    if (like == null) {
                        api.pushDelete(session, change.entityType.table, change.entityId)
                    } else {
                        api.pushLike(session, like)
                    }
                    true
                }
                SyncEntityType.LIKED_ALBUM -> {
                    val album = store.albumForSync(change.entityId)
                    if (album == null) {
                        api.pushDelete(session, change.entityType.table, change.entityId)
                    } else {
                        api.pushAlbum(session, album)
                    }
                    true
                }
                SyncEntityType.IMPORT_BATCH -> {
                    val batch = store.importBatchForSync(change.entityId)
                    if (batch == null) {
                        api.pushDelete(session, change.entityType.table, change.entityId)
                    } else {
                        api.pushImportBatch(session, batch)
                    }
                    true
                }
                SyncEntityType.FOLLOWED_ARTIST -> {
                    val follow = store.followForSync(change.entityId)
                    if (follow == null) {
                        api.pushDelete(session, change.entityType.table, change.entityId)
                    } else {
                        api.pushFollow(session, follow)
                    }
                    true
                }
                SyncEntityType.HISTORY -> {
                    store.historyForSync(change.entityId)?.let { api.pushHistory(session, it) }
                    true
                }
                SyncEntityType.SEARCH_HISTORY -> {
                    if (!includeSearch) {
                        false
                    } else {
                        store.searchForSync(change.entityId)?.let { api.pushSearch(session, it) }
                        true
                    }
                }
                SyncEntityType.PREFERENCES -> {
                    api.pushPreferences(session, store.preferencesForSync())
                    true
                }
            }
            if (pushed) store.clearPending(change)
        }

        store.pendingTombstones(200).forEach { tombstone ->
            if (tombstone.accountId != null && tombstone.accountId != session.user.id) {
                store.clearTombstone(tombstone)
                return@forEach
            }
            api.pushDelete(session, tombstone.entityType.table, tombstone.entityId)
            store.clearTombstone(tombstone)
        }
    }

    private suspend fun pullAll(session: StoredSession, cursor: Long?): Boolean {
        var changed = false
        val pageSize = 500

        var offset = 0
        while (true) {
            val rows = api.pullPlaylists(session, cursor, pageSize, offset)
            rows.forEach { row ->
                if (store.pendingFor(SyncEntityType.PLAYLIST, row.id)) return@forEach
                if (row.deleted) {
                    store.applyRemotePlaylistDelete(row.id)
                    changed = true
                } else {
                    val tracks = api.pullPlaylistTracks(session, row.id)
                    val entries = tracks.mapNotNull { trackRow ->
                        val track = trackRow.track?.toTrack() ?: return@mapNotNull null
                        PlaylistTrackEntry(
                            track = track,
                            addedAt = trackRow.added_at?.let(::isoToMillis) ?: 0,
                            entryId = trackRow.entry_id ?: "${row.id}:${trackRow.position}:${trackRow.track_id}",
                            position = trackRow.position,
                            sourcePlatform = trackRow.source_platform,
                            sourceImportId = trackRow.source_import_id,
                            importedAt = trackRow.imported_at?.let(::isoToMillis),
                        )
                    }
                    store.applyRemotePlaylist(SyncedPlaylist(row.toPlaylist(), entries, provenance = row.provenance))
                    changed = true
                }
            }
            if (rows.size < pageSize) break
            offset += pageSize
        }

        offset = 0
        while (true) {
            val rows = api.pullLikes(session, cursor, pageSize, offset)
            rows.forEach { row ->
                if (store.pendingFor(SyncEntityType.LIKED_SONG, row.track_id)) return@forEach
                val track = row.track?.toTrack() ?: return@forEach
                store.applyRemoteLike(
                    SyncedLike(
                        track = track,
                        addedAt = row.added_at?.let(::isoToMillis) ?: 0,
                        deletedAt = if (row.deleted) currentTimeMillis() else null,
                        provenance = row.provenance,
                    ),
                )
                changed = true
            }
            if (rows.size < pageSize) break
            offset += pageSize
        }

        offset = 0
        while (true) {
            val rows = api.pullAlbums(session, cursor, pageSize, offset)
            rows.forEach { row ->
                if (store.pendingFor(SyncEntityType.LIKED_ALBUM, row.album_id)) return@forEach
                store.applyRemoteAlbum(
                    SyncedAlbum(
                        album = row.toAlbum(),
                        addedAt = row.added_at?.let(::isoToMillis) ?: 0,
                        deletedAt = if (row.deleted) currentTimeMillis() else null,
                        provenance = row.provenance,
                    ),
                )
                changed = true
            }
            if (rows.size < pageSize) break
            offset += pageSize
        }

        offset = 0
        while (true) {
            val rows = api.pullFollows(session, cursor, pageSize, offset)
            rows.forEach { row ->
                if (store.pendingFor(SyncEntityType.FOLLOWED_ARTIST, row.artist_id)) return@forEach
                store.applyRemoteFollow(
                    SyncedFollow(
                        artist = Artist(row.artist_id, row.name, row.image_url, row.genre),
                        addedAt = row.added_at?.let(::isoToMillis) ?: 0,
                        deletedAt = if (row.deleted) currentTimeMillis() else null,
                        provenance = row.provenance,
                    ),
                )
                changed = true
            }
            if (rows.size < pageSize) break
            offset += pageSize
        }

        offset = 0
        while (true) {
            val rows = api.pullHistory(session, cursor, pageSize, offset)
            rows.forEach { row ->
                if (store.pendingFor(SyncEntityType.HISTORY, row.id)) return@forEach
                val track = row.track?.toTrack() ?: return@forEach
                store.applyRemoteHistory(
                    SyncedHistory(
                        syncId = row.id,
                        track = track,
                        playedAt = row.played_at?.let(::isoToMillis) ?: 0,
                        playedMs = row.played_ms,
                        completed = row.completed,
                        skipped = row.skipped,
                        deletedAt = if (row.deleted) currentTimeMillis() else null,
                        provenance = row.provenance,
                    ),
                )
                changed = true
            }
            if (rows.size < pageSize) break
            offset += pageSize
        }

        offset = 0
        while (true) {
            val rows = api.pullImportBatches(session, cursor, pageSize, offset)
            rows.forEach { row ->
                if (store.pendingFor(SyncEntityType.IMPORT_BATCH, row.id)) return@forEach
                store.applyRemoteImportBatch(
                    SyncedImportBatch(
                        id = row.id,
                        sourcePlatform = row.source_platform,
                        fingerprint = row.fingerprint,
                        importedAt = row.imported_at?.let(::isoToMillis) ?: 0,
                        summary = row.summary,
                        playlists = row.playlists_count,
                        tracks = row.tracks_count,
                        likes = row.likes_count,
                        albums = row.albums_count,
                        artists = row.artists_count,
                        history = row.history_count,
                        searches = row.searches_count,
                        deletedAt = if (row.deleted) currentTimeMillis() else null,
                    ),
                )
                changed = true
            }
            if (rows.size < pageSize) break
            offset += pageSize
        }

        // Preferences come before search history so a remote privacy opt-in takes
        // effect for this same pull instead of only on the next cycle.
        if (!store.pendingFor(SyncEntityType.PREFERENCES, PREFERENCES_ID)) {
            api.pullPreferences(session)?.let { row ->
                val current = settings.settings.value
                if (current.market != row.market ||
                    current.autoplayEnabled != row.autoplay_enabled ||
                    current.syncSearchHistory != row.sync_search_history
                ) {
                    store.applyRemotePreferences(
                        SyncedPreferences(
                            market = row.market,
                            autoplayEnabled = row.autoplay_enabled,
                            syncSearchHistory = row.sync_search_history,
                            updatedAt = currentTimeMillis(),
                        ),
                    )
                    settings.load()
                    changed = true
                }
            }
        }

        if (settings.settings.value.syncSearchHistory) {
            offset = 0
            while (true) {
                val rows = api.pullSearches(session, cursor, pageSize, offset)
                rows.forEach { row ->
                    if (store.pendingFor(SyncEntityType.SEARCH_HISTORY, row.id)) return@forEach
                    store.applyRemoteSearch(
                        SyncedSearch(
                            id = row.id,
                            queryKey = row.query_key,
                            query = row.query,
                            searchedAt = row.searched_at?.let(::isoToMillis) ?: 0,
                            deletedAt = if (row.deleted) currentTimeMillis() else null,
                            provenance = row.provenance,
                        ),
                    )
                    changed = true
                }
                if (rows.size < pageSize) break
                offset += pageSize
            }
        }
        return changed
    }
}
