package com.revibe.data

import com.revibe.imports.HistoryImportRow
import com.revibe.imports.ImportBatchRecord
import com.revibe.imports.SearchImportRow
import com.revibe.imports.SourceHistoryEvent
import com.revibe.imports.SourceSearch
import com.revibe.imports.SourceTrack
import com.revibe.model.Album
import com.revibe.model.Artist
import com.revibe.model.MusicSourceId
import com.revibe.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImportStoreTest {
    private fun newStore(name: String): JdbcLibraryStore {
        val dir = Files.createTempDirectory("revibe-import-$name").toFile()
        return JdbcLibraryStore(File(dir, "library.db"), Dispatchers.IO)
    }

    private fun track(id: String = "a") = Track(
        id = "ytm:track:$id",
        title = "Kesariya",
        artist = "Arijit Singh",
        album = "Brahmastra",
        durationMs = 268_000,
        source = MusicSourceId.YOUTUBE_MUSIC,
    )

    @Test
    fun `imported playlist is idempotent and preserves repeated track rows`() = runBlocking {
        val store = newStore("playlist")
        store.createPlaylist("Existing")
        val first = store.createImportedPlaylist(
            sourcePlatform = "spotify",
            sourceId = "spotify:playlist:one",
            importId = "import-1",
            name = "Hindi Mix",
            description = "Imported",
            tracks = listOf(track(), track()),
        )

        assertEquals(2, first.tracksAdded)
        assertEquals(0, first.duplicatesSkipped)
        val id = first.playlistId
        assertEquals(2, store.playlists().size)
        assertTrue(store.playlists().any { it.name == "Existing" })
        val entries = store.playlistTrackEntries(id!!)
        assertEquals(2, entries.size)
        assertEquals(listOf(1, 2), entries.map { it.position })
        assertTrue(entries[0].entryId != entries[1].entryId)

        val second = store.createImportedPlaylist(
            sourcePlatform = "spotify",
            sourceId = "spotify:playlist:one",
            importId = "import-2",
            name = "Hindi Mix",
            description = "Imported",
            tracks = listOf(track()),
        )
        assertTrue(second.alreadyImported)
        assertEquals(id, second.playlistId)
        assertEquals(2, store.playlists().size)
    }

    @Test
    fun `likes history and searches are deduplicated across retries`() = runBlocking {
        val store = newStore("records")
        val track = track("history")

        assertTrue(store.addImportedLike("spotify", "spotify:track:1", "i1", track))
        assertFalse(store.addImportedLike("spotify", "spotify:track:1", "i2", track))

        val event = SourceHistoryEvent(
            sourceKey = "1700000000000|spotify:track:history|190000",
            timestampMs = 1_700_000_000_000,
            track = SourceTrack(
                1,
                track.title,
                track.artist,
                track.album,
                spotifyUri = "spotify:track:history",
            ),
            playedMs = 190_000,
            completed = true,
            skipped = false,
        )
        assertEquals(1 to 0, store.addImportedHistory("spotify", "i1", listOf(HistoryImportRow(event, track))))
        assertEquals(0 to 1, store.addImportedHistory("spotify", "i2", listOf(HistoryImportRow(event, track))))

        val search = SearchImportRow(
            SourceSearch("1700000000000|arijit", "Arijit Singh", 1_700_000_000_000),
        )
        assertEquals(1, store.addImportedSearches("spotify", "i1", listOf(search)))
        assertEquals(0, store.addImportedSearches("spotify", "i2", listOf(search)))

        assertEquals(1, store.userActivity().playCount)
        assertEquals(1, store.searchHistory(10).size)
    }

    @Test
    fun `batch fingerprint is recorded with import counts`() = runBlocking {
        val store = newStore("batch")
        assertFalse(store.importBatchExists("spotify", "abc"))
        store.recordImportBatch(
            ImportBatchRecord(
                "id", "spotify", "abc", 1L, "summary",
                playlists = 2, tracks = 10, likes = 3, albums = 1,
                artists = 1, history = 5, searches = 4,
            ),
        )
        assertTrue(store.importBatchExists("spotify", "abc"))
        val item = store.importHistory().single()
        assertEquals(10, item.batch.tracks)
        assertEquals(5, item.batch.history)
        assertFalse(item.canUndo)
        assertFalse(item.undone)

        // A second pass over the same archive reuses its batch id and merges newly
        // imported categories without losing the counts from the first pass.
        assertEquals("id", store.importBatchId("spotify", "abc"))
        store.recordImportBatch(
            ImportBatchRecord("id", "spotify", "abc", 2L, "updated", playlists = 0, history = 2),
        )
        val repeated = store.importHistory().single()
        assertEquals("id", repeated.batch.importId)
        assertEquals(10, repeated.batch.tracks)
        assertEquals(5, repeated.batch.history)
    }

    @Test
    fun `legacy zero count batch is backfilled and persisted on open`() = runBlocking {
        val dir = Files.createTempDirectory("revibe-import-backfill").toFile()
        val db = File(dir, "library.db")
        val store = JdbcLibraryStore(db, Dispatchers.IO)
        val playlistTrack = track("backfill")
        store.createImportedPlaylist(
            sourcePlatform = "spotify",
            sourceId = "spotify:playlist:legacy",
            importId = "legacy",
            name = "Legacy",
            description = "",
            tracks = listOf(playlistTrack, playlistTrack),
        )
        store.addImportedLike("spotify", "spotify:track:like", "legacy", track("like"))
        val event = SourceHistoryEvent(
            sourceKey = "1700000000000|spotify:track:history|190000",
            timestampMs = 1_700_000_000_000,
            track = SourceTrack(1, "History", "Artist", "Album", spotifyUri = "spotify:track:history"),
            playedMs = 190_000,
            completed = true,
            skipped = false,
        )
        store.addImportedHistory("spotify", "legacy", listOf(HistoryImportRow(event, track("history"))))
        store.addImportedSearches(
            "spotify",
            "legacy",
            listOf(SearchImportRow(SourceSearch("1700000000000|query", "query", 1_700_000_000_000))),
        )
        // Simulate a batch written before schema v5, when all count columns defaulted to zero.
        store.recordImportBatch(
            ImportBatchRecord("legacy", "spotify", "legacy-fingerprint", 1L, "Legacy import"),
        )

        val reopened = JdbcLibraryStore(db, Dispatchers.IO)
        val item = reopened.importHistory().single()
        assertEquals(1, item.batch.playlists)
        assertEquals(2, item.batch.tracks)
        assertEquals(1, item.batch.likes)
        assertEquals(1, item.batch.history)
        assertEquals(1, item.batch.searches)
        assertTrue(item.canUndo)
    }

    @Test
    fun `playlist entries keep repeats through add remove and reorder`() = runBlocking {
        val store = newStore("repeats")
        val playlist = store.createPlaylist("Repeats")
        val song = track("repeat")

        assertTrue(store.addToPlaylist(playlist.id, song))
        assertTrue(store.addToPlaylist(playlist.id, song))
        val first = store.playlistTrackEntries(playlist.id)
        assertEquals(2, first.size)
        assertEquals(listOf(song.id, song.id), first.map { it.track.id })
        assertEquals(listOf(1, 2), first.map { it.position })
        assertTrue(first[0].entryId != first[1].entryId)

        store.movePlaylistTrack(playlist.id, 0, 1)
        val moved = store.playlistTrackEntries(playlist.id)
        assertEquals(listOf(first[1].entryId, first[0].entryId), moved.map { it.entryId })
        assertEquals(listOf(1, 2), moved.map { it.position })

        store.removePlaylistEntry(playlist.id, moved.first().entryId)
        val remaining = store.playlistTrackEntries(playlist.id).single()
        assertEquals(first[0].entryId, remaining.entryId)
        assertTrue(store.addToPlaylist(playlist.id, song))
        assertEquals(2, store.playlistTrackEntries(playlist.id).size)
    }

    @Test
    fun `undo removes only records owned by that import`() = runBlocking {
        val store = newStore("undo")
        val importId = "import-undo"

        val nativePlaylist = store.createPlaylist("Native")
        val nativeTrack = track("native")
        store.addToPlaylist(nativePlaylist.id, nativeTrack)
        store.addLikedTrack(nativeTrack)
        store.recordPlay(nativeTrack)

        val importedPlaylist = store.createImportedPlaylist(
            sourcePlatform = "spotify",
            sourceId = "spotify:playlist:undo",
            importId = importId,
            name = "Imported",
            description = "",
            tracks = listOf(track("imported"), track("imported")),
        ).playlistId!!
        val importedTrack = track("imported-like")
        assertTrue(store.addImportedLike("spotify", "spotify:track:like", importId, importedTrack))
        assertTrue(store.addImportedAlbum("spotify", "spotify:album:1", importId, Album("album:1", "Album", "Artist")))
        assertTrue(store.addImportedArtist("spotify", "spotify:artist:1", importId, Artist("artist:1", "Artist")))

        val event = SourceHistoryEvent(
            sourceKey = "undo-event",
            timestampMs = 1_700_000_000_000,
            track = SourceTrack(1, "Imported play", "Artist", "Album", spotifyUri = "spotify:track:play"),
            playedMs = 120_000,
            completed = true,
            skipped = false,
        )
        assertEquals(1 to 0, store.addImportedHistory("spotify", importId, listOf(HistoryImportRow(event, track("play")))))
        assertEquals(1, store.addImportedSearches("spotify", importId, listOf(SearchImportRow(SourceSearch("s1", "unique query", 1_700_000_000_000)))))

        store.addToPlaylist(importedPlaylist, track("native-after-import"))
        store.recordImportBatch(ImportBatchRecord(importId, "spotify", "fingerprint", 1L, "summary", playlists = 1, tracks = 2, likes = 1, albums = 1, artists = 1, history = 1, searches = 1))

        val removed = store.undoImport(importId)

        assertEquals(2, removed.tracks)
        assertEquals(1, removed.likes)
        assertEquals(1, removed.albums)
        assertEquals(1, removed.artists)
        assertEquals(1, removed.history)
        assertEquals(1, removed.searches)
        assertEquals(0, removed.playlists, "the playlist retains a native entry added after import")
        assertTrue(store.playlists().any { it.id == nativePlaylist.id })
        assertTrue(store.playlists().any { it.id == importedPlaylist })
        val importedEntries = store.playlistTrackEntries(importedPlaylist).single()
        assertEquals("native-after-import", importedEntries.track.id.removePrefix("ytm:track:"))
        assertFalse(importedEntries.imported)
        assertEquals(setOf(nativeTrack.id), store.likedIds())
        assertTrue(store.likedAlbums().isEmpty())
        assertTrue(store.followedArtists().isEmpty())
        assertEquals(1, store.userActivity().nativePlayCount)
        assertEquals(0, store.userActivity().importedPlayCount)
        assertTrue(store.searchHistory(10).isEmpty())
        assertTrue(store.importHistory().single().undone)
        assertFalse(store.importHistory().single().canUndo)
    }

    @Test
    fun `import provenance survives a store restart and feeds activity counts`() = runBlocking {
        val dir = Files.createTempDirectory("revibe-provenance").toFile()
        val db = File(dir, "library.db")
        val store = JdbcLibraryStore(db, Dispatchers.IO)
        val importId = "import-provenance"
        val imported = track("provenance")

        val playlistId = store.createImportedPlaylist(
            sourcePlatform = "spotify",
            sourceId = "spotify:playlist:provenance",
            importId = importId,
            name = "Imported",
            description = "",
            tracks = listOf(imported, imported),
        ).playlistId!!
        store.addImportedLike("spotify", "spotify:track:provenance", importId, imported)
        store.addImportedAlbum("spotify", "spotify:album:provenance", importId, Album("album:p", "Imported Album", "Artist"))
        store.addImportedArtist("spotify", "spotify:artist:provenance", importId, Artist("artist:p", "Artist"))
        val event = SourceHistoryEvent(
            sourceKey = "provenance-event",
            timestampMs = 1_700_000_000_000,
            track = SourceTrack(1, imported.title, imported.artist, imported.album, spotifyUri = "spotify:track:provenance"),
            playedMs = 180_000,
            completed = true,
            skipped = false,
            sourceEventId = "spotify-event-1",
        )
        store.addImportedHistory("spotify", importId, listOf(HistoryImportRow(event, imported)))
        store.addImportedSearches("spotify", importId, listOf(SearchImportRow(SourceSearch("ps", "provenance query", 1_700_000_000_000))))
        store.recordPlay(track("native-play"))

        val reopened = JdbcLibraryStore(db, Dispatchers.IO)
        val playlist = reopened.playlistForSync(playlistId)!!
        assertEquals("spotify", playlist.provenance?.sourcePlatform)
        assertEquals(importId, playlist.provenance?.sourceImportId)
        assertEquals(2, playlist.entries.size)
        assertTrue(playlist.entries.all { it.sourceImportId == importId })
        assertEquals(importId, reopened.likeForSync(imported.id)!!.provenance?.sourceImportId)
        assertEquals(importId, reopened.albumForSync("album:p")!!.provenance?.sourceImportId)
        assertEquals(importId, reopened.followForSync("artist:p")!!.provenance?.sourceImportId)
        val historyId = reopened.pendingChanges(50).first { it.entityType == com.revibe.sync.SyncEntityType.HISTORY }.entityId
        val history = reopened.historyForSync(historyId)!!
        assertEquals("spotify-event-1", history.provenance?.sourceEventId)
        val searchId = reopened.pendingChanges(50).first { it.entityType == com.revibe.sync.SyncEntityType.SEARCH_HISTORY }.entityId
        assertEquals(importId, reopened.searchForSync(searchId)!!.provenance?.sourceImportId)

        val activity = reopened.userActivity()
        assertEquals(1, activity.nativePlayCount)
        assertEquals(1, activity.importedPlayCount)
        assertEquals(1, activity.importedLikedCount)
        assertEquals(1, activity.importedPlaylistCount)
        assertTrue(activity.topArtists.any { it.artist == imported.artist })
    }
}
