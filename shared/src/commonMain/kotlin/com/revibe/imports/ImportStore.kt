package com.revibe.imports

import com.revibe.model.Album
import com.revibe.model.Artist
import com.revibe.model.Track

data class ImportBatchRecord(
    val importId: String,
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
)

data class ImportHistoryItem(
    val batch: ImportBatchRecord,
    val canUndo: Boolean,
    val undone: Boolean = false,
)

data class UndoImportResult(
    val playlists: Int = 0,
    val tracks: Int = 0,
    val likes: Int = 0,
    val albums: Int = 0,
    val artists: Int = 0,
    val history: Int = 0,
    val searches: Int = 0,
) {
    val any: Boolean get() = playlists + tracks + likes + albums + artists + history + searches > 0
}

data class ImportedPlaylistResult(
    val playlistId: String?,
    val alreadyImported: Boolean,
    val tracksAdded: Int,
    val duplicatesSkipped: Int,
)

data class HistoryImportRow(val event: SourceHistoryEvent, val track: Track)
data class SearchImportRow(val search: SourceSearch)

/**
 * Import-only persistence. Normal playlist/like/follow/history sync stays in the existing
 * outbox; this contract adds idempotency mappings and provenance without changing wire formats.
 */
interface ImportStore {
    suspend fun importBatchExists(sourcePlatform: String, fingerprint: String): Boolean
    suspend fun importBatchId(sourcePlatform: String, fingerprint: String): String?
    suspend fun createImportedPlaylist(
        sourcePlatform: String,
        sourceId: String,
        importId: String,
        name: String,
        description: String,
        tracks: List<Track>,
    ): ImportedPlaylistResult

    suspend fun addImportedLike(
        sourcePlatform: String,
        sourceId: String,
        importId: String,
        track: Track,
    ): Boolean

    suspend fun addImportedAlbum(
        sourcePlatform: String,
        sourceId: String,
        importId: String,
        album: Album,
    ): Boolean

    suspend fun addImportedArtist(
        sourcePlatform: String,
        sourceId: String,
        importId: String,
        artist: Artist,
    ): Boolean

    suspend fun addImportedHistory(
        sourcePlatform: String,
        importId: String,
        rows: List<HistoryImportRow>,
    ): Pair<Int, Int>

    suspend fun addImportedSearches(
        sourcePlatform: String,
        importId: String,
        rows: List<SearchImportRow>,
    ): Int

    suspend fun recordImportBatch(batch: ImportBatchRecord)
    suspend fun importHistory(limit: Int = 50): List<ImportHistoryItem>
    suspend fun undoImport(importId: String): UndoImportResult
}
