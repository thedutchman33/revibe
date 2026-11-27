package com.revibe.imports

import com.revibe.catalog.CatalogRepository
import com.revibe.library.LibraryRepository
import kotlin.random.Random

/**
 * Orchestrates parsing/matching and commits imported music through the existing library
 * repositories. The archive itself stays in memory on the device and is never uploaded.
 */
class ImportRepository(
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
    private val store: ImportStore,
) {
    private val matcher = TrackMatcher(catalog)

    suspend fun importHistory(): List<ImportHistoryItem> = store.importHistory()

    suspend fun undoImport(importId: String): UndoImportResult = store.undoImport(importId)

    suspend fun matchPlaylistFile(
        file: PickedTextFile,
        market: String,
        onProgress: (ImportProgress) -> Unit = {},
    ): PlaylistFilePreview {
        val parsed = parsePlaylistFile(file.name, file.text)
        onProgress(ImportProgress("Matching tracks", 0, parsed.tracks.size))
        val matched = parsed.tracks.mapIndexed { index, item ->
            val result = matcher.match(item.source, market)
            if (index % 4 == 0 || index == parsed.tracks.lastIndex) {
                onProgress(ImportProgress("Matching tracks", index + 1, parsed.tracks.size))
            }
            result
        }
        return parsed.copy(tracks = matched)
    }

    suspend fun matchArchive(
        entries: SpotifyArchiveEntries,
        market: String,
        onProgress: (ImportProgress) -> Unit = {},
    ): SpotifyMatchedArchive {
        val archive = parseSpotifyArchive(entries)
        val total = archive.playlists.sumOf { it.tracks.size } + archive.likedTracks.size +
            archive.history.size + archive.savedAlbums.size + archive.followedArtists.size
        var done = 0
        fun tick(stage: String) {
            done++
            if (done % 4 == 0 || done == total) onProgress(ImportProgress(stage, done, total))
        }

        val playlists = archive.playlists.map { playlist ->
            MatchedPlaylist(
                playlist,
                playlist.tracks.map { source ->
                    matcher.match(source, market).also { tick("Matching playlist tracks") }
                },
            )
        }
        val liked = archive.likedTracks.map { source ->
            matcher.match(source, market).also { tick("Matching saved tracks") }
        }
        val history = archive.history.map { event ->
            MatchedHistory(event, matcher.match(event.track, market).also { tick("Matching listening history") })
        }
        val albums = archive.savedAlbums.map { source ->
            MatchedAlbum(source, matchAlbum(source, market)).also { tick("Matching saved albums") }
        }
        val artists = archive.followedArtists.map { source ->
            MatchedArtist(source, matchArtist(source, market)).also { tick("Matching followed artists") }
        }
        return SpotifyMatchedArchive(archive, playlists, liked, history, albums, artists)
    }

    suspend fun commitPlaylistFile(
        preview: PlaylistFilePreview,
        destinationPlaylistId: String?,
        newPlaylistName: String,
        market: String,
        onProgress: (ImportProgress) -> Unit = {},
    ): ImportSummary {
        val selected = preview.tracks.filter { it.selected && it.track != null }
        val existingImportId = store.importBatchId(FILE_SOURCE, preview.fingerprint)
        val importId = existingImportId ?: newImportId()
        val already = existingImportId != null
        var added = 0
        val playlistId: String?
        var skipped = 0
        if (destinationPlaylistId != null) {
            selected.forEachIndexed { index, item ->
                val track = item.track ?: return@forEachIndexed
                if (library.addToPlaylist(destinationPlaylistId, track)) added++ else skipped++
                if (index % 5 == 0) onProgress(ImportProgress("Adding tracks", index + 1, selected.size))
            }
            playlistId = destinationPlaylistId
        } else {
            val result = store.createImportedPlaylist(
                sourcePlatform = FILE_SOURCE,
                sourceId = preview.fingerprint,
                importId = importId,
                name = newPlaylistName.ifBlank { preview.playlistName },
                description = "Imported from ${preview.fileName}",
                tracks = selected.mapNotNull { it.track },
            )
            added = result.tracksAdded
            skipped = result.duplicatesSkipped
            playlistId = result.playlistId
        }
        val targetName = if (destinationPlaylistId != null) {
            library.playlists().firstOrNull { it.id == destinationPlaylistId }?.name
        } else {
            (newPlaylistName.ifBlank { preview.playlistName }).ifBlank { null }
        }
        val summary = ImportSummary(
            primaryPlaylistId = playlistId,
            playlistName = targetName,
            playlistsCreated = if (playlistId != null && destinationPlaylistId == null) 1 else 0,
            tracksAdded = added,
            needsReview = preview.tracks.count { it.status == MatchStatus.NEEDS_REVIEW },
            unmatched = preview.tracks.filter { it.track == null }.map { it.source },
            alreadyImported = if (already) 1 else 0,
            notes = buildList {
                if (preview.duplicateCount > 0) add("${preview.duplicateCount} repeated source rows were detected and kept as separate entries.")
                if (already) add("This exact file was imported before; existing mapped playlists were skipped.")
                if (preview.tracks.any { it.status == MatchStatus.NEEDS_REVIEW }) {
                    add("${preview.tracks.count { it.status == MatchStatus.NEEDS_REVIEW }} close matches need review; they stay unselected until you include them.")
                }
            },
        )
        store.recordImportBatch(
            ImportBatchRecord(
                importId = importId,
                sourcePlatform = FILE_SOURCE,
                fingerprint = preview.fingerprint,
                importedAt = System.currentTimeMillis(),
                summary = summary.toString(),
                playlists = summary.playlistsCreated,
                tracks = summary.tracksAdded,
            ),
        )
        return summary
    }

    suspend fun commitArchive(
        matched: SpotifyMatchedArchive,
        selection: ImportCategorySelection,
        onProgress: (ImportProgress) -> Unit = {},
    ): ImportSummary {
        val archive = matched.archive
        val existingImportId = store.importBatchId(SPOTIFY_SOURCE, archive.fingerprint)
        val importId = existingImportId ?: newImportId()
        val already = existingImportId != null
        var playlistsCreated = 0
        var playlistsSkipped = 0
        var tracksAdded = 0
        var likedAdded = 0
        var albumsAdded = 0
        var artistsAdded = 0
        var historyAdded = 0
        var historySkipped = 0
        var searchesAdded = 0
        val notes = mutableListOf<String>()

        if (selection.playlists) {
            matched.playlists.forEachIndexed { index, playlist ->
                val tracks = playlist.tracks
                    .filter { it.selected && it.track != null }
                    .mapNotNull { it.track }
                val result = store.createImportedPlaylist(
                    sourcePlatform = SPOTIFY_SOURCE,
                    sourceId = playlist.source.sourceId,
                    importId = importId,
                    name = playlist.source.name,
                    description = playlist.source.description,
                    tracks = tracks,
                )
                if (result.alreadyImported) playlistsSkipped++ else playlistsCreated++
                tracksAdded += result.tracksAdded
                onProgress(ImportProgress("Importing playlists", index + 1, matched.playlists.size))
            }
        }

        if (selection.likedTracks) {
            matched.likedTracks.filter { it.selected && it.track != null }.forEachIndexed { index, item ->
                val track = item.track ?: return@forEachIndexed
                val sourceId = item.source.spotifyUri ?: sourceTicket(item.source)
                if (store.addImportedLike(SPOTIFY_SOURCE, sourceId, importId, track)) likedAdded++
                if (index % 10 == 0) {
                    onProgress(ImportProgress("Importing saved tracks", index + 1, matched.likedTracks.size))
                }
            }
        }

        if (selection.savedAlbums) {
            matched.albums.forEachIndexed { index, item ->
                val album = item.album ?: return@forEachIndexed
                if (store.addImportedAlbum(SPOTIFY_SOURCE, item.source.sourceId, importId, album)) albumsAdded++
                onProgress(ImportProgress("Importing albums", index + 1, matched.albums.size))
            }
        }

        if (selection.followedArtists) {
            matched.artists.forEachIndexed { index, item ->
                val artist = item.artist ?: return@forEachIndexed
                if (store.addImportedArtist(SPOTIFY_SOURCE, item.source.sourceId, importId, artist)) artistsAdded++
                onProgress(ImportProgress("Importing artists", index + 1, matched.artists.size))
            }
        }

        if (selection.history) {
            val rows = matched.history.mapNotNull { item ->
                item.match.track?.takeIf {
                    item.match.selected &&
                        (item.match.status == MatchStatus.MATCHED || item.match.status == MatchStatus.NEEDS_REVIEW)
                }?.let { HistoryImportRow(item.event, it) }
            }
            rows.chunked(500).forEachIndexed { index, chunk ->
                val (added, skipped) = store.addImportedHistory(SPOTIFY_SOURCE, importId, chunk)
                historyAdded += added
                historySkipped += skipped
                onProgress(ImportProgress("Importing listening history", index * 500 + chunk.size, rows.size))
            }
        }

        if (selection.searches && archive.searches.isNotEmpty()) {
            searchesAdded = store.addImportedSearches(
                SPOTIFY_SOURCE,
                importId,
                archive.searches.map(::SearchImportRow),
            )
        }

        if (!archive.tasteProfileSummary.isNullOrBlank() && selection.tasteProfile) {
            notes += "Taste profile retained as bounded local import metadata; it did not create plays."
        }
        if (!archive.wrappedSummary.isNullOrBlank() && selection.tasteProfile) {
            notes += "Wrapped summary retained without inventing listening events."
        }
        if (already) notes += "This archive fingerprint was imported before; mapped records were skipped."
        if (archive.isEmpty) notes += "No supported Spotify music records were found."

        val summary = ImportSummary(
            playlistsCreated = playlistsCreated,
            playlistsSkipped = playlistsSkipped,
            tracksAdded = tracksAdded,
            likedTracksAdded = likedAdded,
            albumsAdded = albumsAdded,
            artistsAdded = artistsAdded,
            historyAdded = historyAdded,
            historySkipped = historySkipped,
            searchesAdded = searchesAdded,
            unmatched = matched.unmatched.map { it.source },
            alreadyImported = playlistsSkipped,
            notes = notes,
        )
        val metadata = buildString {
            archive.tasteProfileSummary?.let { append("taste=").append(it.take(1_500)).append('\n') }
            archive.wrappedSummary?.let { append("wrapped=").append(it.take(1_500)).append('\n') }
        }
        store.recordImportBatch(
            ImportBatchRecord(
                importId,
                SPOTIFY_SOURCE,
                archive.fingerprint,
                System.currentTimeMillis(),
                summary.toString() + if (metadata.isBlank()) "" else "\n$metadata",
                playlists = summary.playlistsCreated,
                tracks = summary.tracksAdded,
                likes = summary.likedTracksAdded,
                albums = summary.albumsAdded,
                artists = summary.artistsAdded,
                history = summary.historyAdded,
                searches = summary.searchesAdded,
            ),
        )
        return summary
    }

    private suspend fun matchAlbum(source: SourceAlbum, market: String): com.revibe.model.Album? {
        val query = listOf(source.name, source.artist).filter { it.isNotBlank() }.joinToString(" ")
        val results = runCatching { catalog.search(query, market).albums }.getOrDefault(emptyList())
        return results
            .map { it to albumScore(source, it) }
            .filter { it.second >= 0.78f }
            .maxByOrNull { it.second }
            ?.first
    }

    private suspend fun matchArtist(source: SourceArtist, market: String): com.revibe.model.Artist? {
        val results = runCatching { catalog.search(source.name, market).artists }.getOrDefault(emptyList())
        return results.firstOrNull { normalize(it.name) == normalize(source.name) }
            ?: results.firstOrNull {
                val candidate = normalize(it.name)
                val wanted = normalize(source.name)
                candidate.contains(wanted) || wanted.contains(candidate)
            }
    }

    private fun albumScore(source: SourceAlbum, album: com.revibe.model.Album): Float {
        val title = if (normalize(source.name) == normalize(album.title)) 1f else 0f
        if (title == 0f) return 0f
        val artist = if (normalize(source.artist) == normalize(album.artist)) 1f else 0f
        return title * 0.7f + artist * 0.3f
    }

    private fun sourceTicket(source: SourceTrack): String =
        source.sourceKey ?: "${source.title.lowercase()}|${source.artist.lowercase()}"

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").replace(Regex("\\s+"), " ").trim()

    private fun newImportId(): String =
        (1..4).joinToString("") { Random.nextLong().toULong().toString(16).padStart(16, '0') }

    companion object {
        const val SPOTIFY_SOURCE = "spotify"
        const val FILE_SOURCE = "tune_my_music"
    }
}
