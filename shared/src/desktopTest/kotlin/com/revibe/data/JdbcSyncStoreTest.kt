package com.revibe.data

import com.revibe.model.Album
import com.revibe.model.Artist
import com.revibe.model.MusicSourceId
import com.revibe.model.Playlist
import com.revibe.model.PlaylistTrackEntry
import com.revibe.model.Track
import com.revibe.sync.SyncEntityType
import com.revibe.sync.SyncOperation
import com.revibe.sync.SyncedAlbum
import com.revibe.sync.SyncedPlaylist
import com.revibe.sync.SyncedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JdbcSyncStoreTest {

    private fun newStore(name: String = "sync"): JdbcLibraryStore {
        val dir = Files.createTempDirectory("revibe-$name").toFile()
        return JdbcLibraryStore(File(dir, "library.db"), Dispatchers.IO)
    }

    private fun onlineTrack(id: String = "ytm:abc123") = Track(
        id = id,
        title = "Kesariya",
        artist = "Arijit Singh",
        album = "Brahmastra",
        durationMs = 268_000,
        artworkUrl = "https://example.invalid/art.jpg",
        streamUrl = "https://example.invalid/expiring-stream.m4a",
        source = MusicSourceId.YOUTUBE_MUSIC,
        artistId = "artist:arijit",
    )

    private fun localTrack(id: String = "file:C:/Music/private.mp3") = Track(
        id = id,
        title = "Private recording",
        artist = "Me",
        album = "Home",
        durationMs = 90_000,
        source = MusicSourceId.LOCAL,
        localPath = "C:\\Music\\private.mp3",
    )

    @Test
    fun `search history persists and de-duplicates case-insensitively`() = runBlocking {
        val store = newStore("search")
        val first = store.recordSearch("Arijit Singh")
        val second = store.recordSearch("  arijit singh  ")

        assertEquals(first.id, second.id)
        val history = store.searchHistory(10)
        assertEquals(1, history.size)
        assertEquals("arijit singh", history.single().query.lowercase())
    }

    @Test
    fun `local writes are queued and claimed by the first account then protected`() = runBlocking {
        val store = newStore("account")
        val track = onlineTrack()
        val playlist = store.createPlaylist("Road trip")
        store.addToPlaylist(playlist.id, track)
        store.addLikedTrack(track)
        store.addFollowedArtist(Artist("artist:arijit", "Arijit Singh"))
        store.recordPlay(track)
        store.setSetting("market", "US")

        val before = store.pendingChanges(100)
        assertTrue(before.any { it.entityType == SyncEntityType.PLAYLIST })
        assertTrue(before.any { it.entityType == SyncEntityType.HISTORY })
        assertTrue(before.all { it.accountId == null })

        assertTrue(store.associateAccount("user-a", "a@example.com"))
        store.bootstrapAccount("user-a", includeSearchHistory = true)
        val claimed = store.pendingChanges(200)
        assertTrue(claimed.all { it.accountId == "user-a" })
        assertTrue(claimed.any { it.entityType == SyncEntityType.PREFERENCES })

        // Every local row still exists after the migration.
        assertEquals(listOf(track.id), store.playlistTracks(playlist.id).map { it.id })
        assertTrue(track.id in store.likedIds())

        // A second account can never claim this device's library or upload rows.
        assertFalse(store.associateAccount("user-b", "b@example.com"))
        assertEquals("user-a", store.activeAccountId())
        assertEquals("a@example.com", store.activeAccountEmail())
    }

    @Test
    fun `playlist sync snapshot rejects local files and keeps cloud tracks ordered`() = runBlocking {
        val store = newStore("playlist")
        val first = onlineTrack("ytm:first")
        val second = onlineTrack("ytm:second")
        val local = localTrack()

        val playlist = store.createPlaylist("Mixed")
        store.addToPlaylist(playlist.id, first)
        store.addToPlaylist(playlist.id, local)
        store.addToPlaylist(playlist.id, second)

        val snapshot = assertNotNull(store.playlistForSync(playlist.id))
        assertEquals(listOf(first.id, second.id), snapshot.entries.map { it.track.id })
        assertTrue(snapshot.entries.none { it.track.source == MusicSourceId.LOCAL })
        assertTrue(snapshot.entries.none { it.track.localPath != null })
    }

    @Test
    fun `applying a remote playlist preserves local files and their position`() = runBlocking {
        val store = newStore("apply")
        val cloud = onlineTrack("ytm:cloud")
        val local = localTrack()

        val playlist = store.createPlaylist("Mixed")
        store.addToPlaylist(playlist.id, local)
        store.addToPlaylist(playlist.id, cloud)

        val remote = Playlist(id = playlist.id, name = "From device B", description = "Remote edit", createdAt = 1_700_000_000_000)
        store.applyRemotePlaylist(
            SyncedPlaylist(
                playlist = remote,
                entries = listOf(PlaylistTrackEntry(onlineTrack("ytm:remote"), 1_700_000_100_000)),
            ),
        )

        val entries = store.playlistTrackEntries(playlist.id)
        assertEquals("ytm:remote", entries.first().track.id)
        assertEquals(local.id, entries.last().track.id)
        assertEquals("From device B", store.playlist(playlist.id)?.name)
    }

    @Test
    fun `deleting a playlist creates a tombstone instead of an upload`() = runBlocking {
        val store = newStore("tombstone")
        store.associateAccount("user-a", "a@example.com")
        val playlist = store.createPlaylist("Delete me")

        store.deletePlaylist(playlist.id)

        assertFalse(store.pendingChanges(100).any { it.entityType == SyncEntityType.PLAYLIST && it.entityId == playlist.id })
        assertTrue(store.pendingFor(SyncEntityType.PLAYLIST, playlist.id))
        val tombstone = store.pendingTombstones(10).single()
        assertEquals(SyncEntityType.PLAYLIST, tombstone.entityType)
        assertEquals(playlist.id, tombstone.entityId)
    }

    @Test
    fun `history rows carry stable cloud ids and a pending upload`() = runBlocking {
        val store = newStore("history")
        val track = onlineTrack()
        store.recordPlay(track)

        val historyChange = store.pendingChanges(50).first { it.entityType == SyncEntityType.HISTORY }
        assertEquals(SyncOperation.UPSERT, historyChange.operation)
        val snapshot = assertNotNull(store.historyForSync(historyChange.entityId))
        assertEquals(track.id, snapshot.track.id)
        assertEquals(historyChange.entityId, snapshot.syncId)
    }

    @Test
    fun `remote preferences update local settings without creating an echo`() = runBlocking {
        val store = newStore("prefs")
        store.associateAccount("user-a", "a@example.com")
        store.setSetting("market", "IN")
        store.bootstrapAccount("user-a", includeSearchHistory = false)

        store.applyRemotePreferences(
            SyncedPreferences(market = "GB", autoplayEnabled = false, syncSearchHistory = true, updatedAt = 1),
        )

        assertEquals("GB", store.getSetting("market"))
        assertEquals("false", store.getSetting("autoplayEnabled"))
        assertEquals("true", store.getSetting("syncSearchHistory"))
        assertEquals("GB", store.preferencesForSync().market)
    }

    @Test
    fun `saved albums are queued, exposed for sync and removed with a tombstone`() = runBlocking {
        val store = newStore("albums")
        val album = Album("ytm:album:1", "After Hours", "The Weeknd", year = 2020, trackCount = 14)

        store.addLikedAlbum(album)
        assertTrue(store.pendingChanges(50).any { it.entityType == SyncEntityType.LIKED_ALBUM && it.entityId == album.id })
        val snapshot = assertNotNull(store.albumForSync(album.id))
        assertEquals("After Hours", snapshot.album.title)
        assertNull(snapshot.provenance)

        store.removeLikedAlbum(album.id)
        assertTrue(store.likedAlbums().isEmpty())
        assertTrue(store.pendingTombstones(10).any { it.entityType == SyncEntityType.LIKED_ALBUM && it.entityId == album.id })
    }

    @Test
    fun `remote album and import batch snapshots apply locally`() = runBlocking {
        val store = newStore("remote-albums")
        store.applyRemoteAlbum(
            SyncedAlbum(
                album = Album("ytm:album:remote", "Remote Album", "Remote Artist"),
                addedAt = 1_700_000_000_000,
                provenance = com.revibe.sync.ImportProvenance("spotify", "import-remote", 1_700_000_000_000),
            ),
        )
        assertTrue(store.likedAlbums().any { it.id == "ytm:album:remote" })
        assertEquals("import-remote", store.albumForSync("ytm:album:remote")!!.provenance?.sourceImportId)

        store.applyRemoteImportBatch(
            com.revibe.sync.SyncedImportBatch(
                id = "batch-1",
                sourcePlatform = "spotify",
                fingerprint = "fingerprint-1",
                importedAt = 1_700_000_000_000,
                summary = "summary",
                playlists = 1,
                tracks = 4,
                history = 9,
            ),
        )
        val batch = store.importHistory().single().batch
        assertEquals(4, batch.tracks)
        assertEquals(9, batch.history)
    }

    @Test
    fun `a pre-v5 playlist table is migrated without losing order`() = runBlocking {
        val dir = Files.createTempDirectory("revibe-v4").toFile()
        val db = File(dir, "old.db")
        java.sql.DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE playlists (
                        id TEXT PRIMARY KEY, name TEXT NOT NULL, description TEXT NOT NULL DEFAULT '',
                        created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                statement.execute(
                    """
                    CREATE TABLE tracks (
                        id TEXT PRIMARY KEY, title TEXT NOT NULL, artist TEXT NOT NULL, album TEXT NOT NULL,
                        duration_ms INTEGER NOT NULL DEFAULT 0, artwork_url TEXT, stream_url TEXT,
                        source TEXT NOT NULL, artist_id TEXT, album_id TEXT, year INTEGER,
                        track_number INTEGER, genre TEXT, explicit INTEGER NOT NULL DEFAULT 0, local_path TEXT
                    )
                    """.trimIndent(),
                )
                statement.execute(
                    """
                    CREATE TABLE playlist_tracks (
                        playlist_id TEXT NOT NULL, track_id TEXT NOT NULL, position INTEGER NOT NULL,
                        added_at INTEGER NOT NULL, PRIMARY KEY (playlist_id, track_id)
                    )
                    """.trimIndent(),
                )
                statement.execute("INSERT INTO playlists VALUES ('p1', 'Old mix', '', 1, 2)")
                statement.execute("INSERT INTO tracks VALUES ('t1', 'One', 'Artist', 'Album', 1, NULL, NULL, 'YOUTUBE_MUSIC', NULL, NULL, NULL, NULL, NULL, 0, NULL)")
                statement.execute("INSERT INTO tracks VALUES ('t2', 'Two', 'Artist', 'Album', 1, NULL, NULL, 'YOUTUBE_MUSIC', NULL, NULL, NULL, NULL, NULL, 0, NULL)")
                statement.execute("INSERT INTO playlist_tracks VALUES ('p1', 't2', 2, 20)")
                statement.execute("INSERT INTO playlist_tracks VALUES ('p1', 't1', 1, 10)")
            }
        }

        val store = JdbcLibraryStore(db, Dispatchers.IO)
        val entries = store.playlistTrackEntries("p1")
        assertEquals(listOf("t1", "t2"), entries.map { it.track.id })
        assertEquals(listOf(1, 2), entries.map { it.position })
        assertTrue(entries.all { it.entryId.isNotBlank() && it.entryId != it.track.id })
        assertTrue(entries[0].entryId != entries[1].entryId)
    }

    @Test
    fun `a failed v5 rebuild rolls back and leaves the old playlist intact`() {
        val dir = Files.createTempDirectory("revibe-v4-rollback").toFile()
        val db = File(dir, "old.db")
        java.sql.DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE playlists (
                        id TEXT PRIMARY KEY, name TEXT NOT NULL, description TEXT NOT NULL DEFAULT '',
                        created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                statement.execute(
                    """
                    CREATE TABLE tracks (
                        id TEXT PRIMARY KEY, title TEXT NOT NULL, artist TEXT NOT NULL, album TEXT NOT NULL,
                        duration_ms INTEGER NOT NULL DEFAULT 0, artwork_url TEXT, stream_url TEXT,
                        source TEXT NOT NULL, artist_id TEXT, album_id TEXT, year INTEGER,
                        track_number INTEGER, genre TEXT, explicit INTEGER NOT NULL DEFAULT 0, local_path TEXT
                    )
                    """.trimIndent(),
                )
                statement.execute(
                    """
                    CREATE TABLE playlist_tracks (
                        playlist_id TEXT NOT NULL, track_id TEXT NOT NULL, position INTEGER NOT NULL,
                        added_at INTEGER NOT NULL, PRIMARY KEY (playlist_id, track_id)
                    )
                    """.trimIndent(),
                )
                // Make the transactional rename fail after the migration starts.
                statement.execute("CREATE TABLE playlist_tracks_v4 (dummy TEXT)")
                statement.execute("INSERT INTO playlists VALUES ('p1', 'Old mix', '', 1, 2)")
                statement.execute("INSERT INTO tracks VALUES ('t1', 'One', 'Artist', 'Album', 1, NULL, NULL, 'YOUTUBE_MUSIC', NULL, NULL, NULL, NULL, NULL, 0, NULL)")
                statement.execute("INSERT INTO playlist_tracks VALUES ('p1', 't1', 1, 10)")
            }
        }

        val result = runCatching { JdbcLibraryStore(db, Dispatchers.IO) }
        assertTrue(result.isFailure)

        java.sql.DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT playlist_id, track_id, position FROM playlist_tracks").use { rows ->
                    assertTrue(rows.next())
                    assertEquals("p1", rows.getString(1))
                    assertEquals("t1", rows.getString(2))
                    assertEquals(1, rows.getInt(3))
                }
                statement.executeQuery("PRAGMA table_info(playlist_tracks)").use { rows ->
                    var hasEntryId = false
                    while (rows.next()) if (rows.getString("name") == "entry_id") hasEntryId = true
                    assertFalse(hasEntryId)
                }
            }
        }
    }

    @Test
    fun `a database created before accounts receives sync columns without losing rows`() = runBlocking {
        val dir = Files.createTempDirectory("revibe-migrate").toFile()
        val db = File(dir, "old.db")
        val store = JdbcLibraryStore(db, Dispatchers.IO)
        assertNotNull(store.deviceId())

        val deviceOne = store.deviceId()
        assertNull(store.activeAccountId())
        assertEquals(deviceOne, store.deviceId())
    }
}
