package com.revibe.data

import com.revibe.imports.HistoryImportRow
import com.revibe.imports.ImportBatchRecord
import com.revibe.imports.ImportHistoryItem
import com.revibe.imports.ImportStore
import com.revibe.imports.UndoImportResult
import com.revibe.imports.ImportedPlaylistResult
import com.revibe.imports.SearchImportRow
import com.revibe.library.LibraryStore
import com.revibe.library.RecommendationCacheRow
import com.revibe.model.Album
import com.revibe.model.AlbumAffinity
import com.revibe.model.Artist
import com.revibe.model.ArtistAffinity
import com.revibe.model.CachedLyrics
import com.revibe.model.GenreAffinity
import com.revibe.model.MusicSourceId
import com.revibe.model.PlayedPlaylist
import com.revibe.model.PlayedTrack
import com.revibe.model.Playlist
import com.revibe.model.PlaylistTrackEntry
import com.revibe.model.Track
import com.revibe.model.UserActivitySnapshot
import com.revibe.sync.PendingChange
import com.revibe.sync.SearchEntry
import com.revibe.sync.SyncEntityType
import com.revibe.sync.SyncOperation
import com.revibe.sync.SyncStore
import com.revibe.sync.ImportProvenance
import com.revibe.sync.SyncTombstone
import com.revibe.sync.SyncedAlbum
import com.revibe.sync.SyncedFollow
import com.revibe.sync.SyncedImportBatch
import com.revibe.sync.SyncedHistory
import com.revibe.sync.SyncedLike
import com.revibe.sync.SyncedPlaylist
import com.revibe.sync.SyncedPreferences
import com.revibe.sync.SyncedSearch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.UUID

/**
 * Desktop persistence: SQLite through JDBC, behind the platform-neutral
 * LibraryStore interface. All access is serialised through one connection, which
 * is the right trade-off for a single-window desktop app.
 */
class JdbcLibraryStore(dbFile: File, private val io: CoroutineDispatcher) : LibraryStore, SyncStore, ImportStore {

    private val mutex = Mutex()
    private val connection: Connection

    init {
        dbFile.parentFile?.mkdirs()
        Class.forName("org.sqlite.JDBC")
        connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA journal_mode=WAL")
            statement.execute("PRAGMA synchronous=NORMAL")
            statement.execute("PRAGMA busy_timeout=5000")
            statement.execute("PRAGMA foreign_keys=ON")
        }
        try {
            createSchema()
            migrateImportSchema()
            migrateImportIntegritySchema()
            backfillImportBatchCounts()
        } catch (error: Throwable) {
            runCatching { connection.close() }
            throw error
        }
    }

    private suspend fun <T> withDb(block: (Connection) -> T): T = withContext(io) {
        mutex.withLock { block(connection) }
    }

    private fun createSchema() {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS tracks (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    artist TEXT NOT NULL,
                    album TEXT NOT NULL,
                    duration_ms INTEGER NOT NULL DEFAULT 0,
                    artwork_url TEXT,
                    stream_url TEXT,
                    source TEXT NOT NULL,
                    artist_id TEXT,
                    album_id TEXT,
                    year INTEGER,
                    track_number INTEGER,
                    genre TEXT,
                    explicit INTEGER NOT NULL DEFAULT 0,
                    local_path TEXT
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS liked_tracks (
                    track_id TEXT PRIMARY KEY,
                    added_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS liked_albums (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    artist TEXT NOT NULL,
                    artwork_url TEXT,
                    year INTEGER,
                    track_count INTEGER NOT NULL DEFAULT 0,
                    genre TEXT,
                    artist_id TEXT,
                    added_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS followed_artists (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    image_url TEXT,
                    genre TEXT,
                    added_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS playlists (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    description TEXT NOT NULL DEFAULT '',
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS playlist_tracks (
                    entry_id TEXT PRIMARY KEY,
                    playlist_id TEXT NOT NULL,
                    track_id TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    added_at INTEGER NOT NULL,
                    source_platform TEXT,
                    source_import_id TEXT,
                    imported_at INTEGER
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS playlist_plays (
                    playlist_id TEXT PRIMARY KEY,
                    played_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    track_id TEXT NOT NULL,
                    played_at INTEGER NOT NULL,
                    played_ms INTEGER NOT NULL DEFAULT 0,
                    completed INTEGER NOT NULL DEFAULT 0,
                    skipped INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS lyrics_cache (
                    track_id TEXT PRIMARY KEY,
                    plain TEXT,
                    synced TEXT,
                    source TEXT NOT NULL,
                    fetched_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS settings (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS recommendation_cache (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    refreshed_at INTEGER NOT NULL,
                    payload TEXT NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS scan_folders (
                    path TEXT PRIMARY KEY,
                    added_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS local_files (
                    path TEXT PRIMARY KEY,
                    mtime INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS search_history (
                    id TEXT PRIMARY KEY,
                    query_key TEXT NOT NULL UNIQUE,
                    query TEXT NOT NULL,
                    searched_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS sync_outbox (
                    seq INTEGER PRIMARY KEY AUTOINCREMENT,
                    entity_type TEXT NOT NULL,
                    entity_id TEXT NOT NULL,
                    operation TEXT NOT NULL,
                    changed_at INTEGER NOT NULL,
                    account_id TEXT,
                    UNIQUE(entity_type, entity_id)
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS sync_tombstones (
                    entity_type TEXT NOT NULL,
                    entity_id TEXT NOT NULL,
                    changed_at INTEGER NOT NULL,
                    account_id TEXT,
                    PRIMARY KEY(entity_type, entity_id)
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS sync_state (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS sync_cursors (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute("CREATE INDEX IF NOT EXISTS history_played_at ON history(played_at DESC)")
            statement.execute("CREATE INDEX IF NOT EXISTS playlist_position ON playlist_tracks(playlist_id, position)")
            statement.execute("CREATE INDEX IF NOT EXISTS playlist_plays_played_at ON playlist_plays(played_at DESC)")
            statement.execute("CREATE INDEX IF NOT EXISTS tracks_local ON tracks(local_path)")
            statement.execute("CREATE INDEX IF NOT EXISTS search_history_searched ON search_history(searched_at DESC)")
            statement.execute("CREATE INDEX IF NOT EXISTS sync_outbox_changed ON sync_outbox(changed_at ASC)")
            statement.execute("CREATE INDEX IF NOT EXISTS sync_tombstones_changed ON sync_tombstones(changed_at ASC)")
        }
        migrateHistorySchema()
        migrateSyncSchema()
    }

    /** Adds behavior columns to databases created before the queue milestone. */
    private fun migrateHistorySchema() {
        val columns = mutableSetOf<String>()
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA table_info(history)").use { rows ->
                while (rows.next()) columns += rows.getString("name")
            }
        }
        fun add(name: String, ddl: String) {
            if (name !in columns) connection.createStatement().use { it.execute("ALTER TABLE history ADD COLUMN $ddl") }
        }
        add("played_ms", "played_ms INTEGER NOT NULL DEFAULT 0")
        add("completed", "completed INTEGER NOT NULL DEFAULT 0")
        add("skipped", "skipped INTEGER NOT NULL DEFAULT 0")
    }

    /**
     * Adds stable cloud-side history ids and the sync bookkeeping tables to
     * databases that predate accounts. Existing rows are never recreated or
     * deleted; each history row receives a UUID once.
     */
    private fun migrateSyncSchema() {
        val historyColumns = mutableSetOf<String>()
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA table_info(history)").use { rows ->
                while (rows.next()) historyColumns += rows.getString("name")
            }
        }
        if ("sync_id" !in historyColumns) {
            connection.createStatement().use { it.execute("ALTER TABLE history ADD COLUMN sync_id TEXT") }
        }
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT id FROM history WHERE sync_id IS NULL OR sync_id = ''").use { rows ->
                val ids = buildList { while (rows.next()) add(rows.getLong(1)) }
                ids.forEach { historyId ->
                    connection.prepareStatement("UPDATE history SET sync_id = ? WHERE id = ?").use { update ->
                        update.setString(1, UUID.randomUUID().toString())
                        update.setLong(2, historyId)
                        update.executeUpdate()
                    }
                }
            }
            statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS history_sync_id ON history(sync_id)")
            statement.execute("PRAGMA user_version = 3")
        }
    }

    /**
     * Schema v5: unique playlist entries, cloud provenance, synced import batches and
     * repeated-entry support. Every step is additive or transactional; existing playlists,
     * entry order, likes, albums, artists and history rows are copied rather than recreated
     * from scratch.
     */
    private fun migrateImportIntegritySchema() {
        val playlistColumns = mutableSetOf<String>()
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA table_info(playlist_tracks)").use { rows ->
                while (rows.next()) playlistColumns += rows.getString("name")
            }
        }
        if ("entry_id" !in playlistColumns) {
            connection.transaction {
                connection.createStatement().use {
                    it.execute("DROP INDEX IF EXISTS playlist_position")
                    it.execute("ALTER TABLE playlist_tracks RENAME TO playlist_tracks_v4")
                }
                connection.createStatement().use {
                    it.execute(
                        """
                        CREATE TABLE playlist_tracks (
                            entry_id TEXT PRIMARY KEY,
                            playlist_id TEXT NOT NULL,
                            track_id TEXT NOT NULL,
                            position INTEGER NOT NULL,
                            added_at INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                }
                connection.createStatement().use { statement ->
                    statement.executeQuery(
                        "SELECT playlist_id, track_id, position, added_at FROM playlist_tracks_v4 ORDER BY playlist_id, position, rowid",
                    ).use { rows ->
                        val copy = connection.prepareStatement(
                            "INSERT INTO playlist_tracks(entry_id, playlist_id, track_id, position, added_at) VALUES (?, ?, ?, ?, ?)",
                        )
                        copy.use { insert ->
                            while (rows.next()) {
                                insert.setString(1, UUID.randomUUID().toString())
                                insert.setString(2, rows.getString(1))
                                insert.setString(3, rows.getString(2))
                                insert.setInt(4, rows.getInt(3))
                                insert.setLong(5, rows.getLong(4))
                                insert.executeUpdate()
                            }
                        }
                    }
                }
                connection.createStatement().use { it.execute("DROP TABLE playlist_tracks_v4") }
            }
        }
        connection.createStatement().use { statement ->
            statement.execute("CREATE INDEX IF NOT EXISTS playlist_position ON playlist_tracks(playlist_id, position)")
        }

        val provenanceColumns = listOf(
            "playlists" to listOf("source_platform", "source_import_id", "imported_at", "original_source_identifier"),
            "playlist_tracks" to listOf("source_platform", "source_import_id", "imported_at"),
            "liked_tracks" to listOf("source_platform", "source_import_id", "imported_at", "original_source_identifier"),
            "liked_albums" to listOf("source_platform", "source_import_id", "imported_at", "original_source_identifier"),
            "followed_artists" to listOf("source_platform", "source_import_id", "imported_at", "original_source_identifier"),
            "history" to listOf("source_platform", "source_event_id", "imported_at"),
            "search_history" to listOf("source_event_id", "imported_at"),
            "import_batches" to listOf(
                "playlists_count", "tracks_count", "likes_count", "albums_count",
                "artists_count", "history_count", "searches_count", "deleted_at",
            ),
        )
        provenanceColumns.forEach { (table, columns) ->
            val existing = mutableSetOf<String>()
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA table_info($table)").use { rows ->
                    while (rows.next()) existing += rows.getString("name")
                }
            }
            columns.forEach { column ->
                if (column !in existing) {
                    val type = if (column.endsWith("_count") || column == "imported_at" || column == "deleted_at") "INTEGER" else "TEXT"
                    connection.createStatement().use { it.execute("ALTER TABLE $table ADD COLUMN $column $type") }
                }
            }
        }
        connection.createStatement().use { statement ->
            statement.execute("CREATE INDEX IF NOT EXISTS import_batches_deleted ON import_batches(deleted_at)")
            statement.execute("PRAGMA user_version = 5")
        }
    }

    /**
     * Schema v5 introduced category counts on import batches. Existing batches keep accurate
     * durable records, so derive their counts once at startup and persist them. This is also
     * idempotent: zero-count batches are re-checked only until they receive their real values.
     */
    private fun backfillImportBatchCounts() {
        connection.transaction {
            val ids = connection.createStatement().use { statement ->
                statement.executeQuery(
                    """
                    SELECT id FROM import_batches
                    WHERE (COALESCE(playlists_count, 0) + COALESCE(tracks_count, 0) + COALESCE(likes_count, 0) +
                           COALESCE(albums_count, 0) + COALESCE(artists_count, 0) + COALESCE(history_count, 0) +
                           COALESCE(searches_count, 0)) = 0
                    """.trimIndent(),
                ).use { rows -> buildList { while (rows.next()) add(rows.getString(1)) } }
            }
            ids.forEach { importId ->
                fun count(sql: String): Int = connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, importId)
                    statement.executeQuery().use { if (it.next()) it.getInt(1) else 0 }
                }
                fun recordCount(entity: String): Int = connection.prepareStatement(
                    "SELECT COUNT(*) FROM import_records WHERE import_id = ? AND entity_type = ?",
                ).use { statement ->
                    statement.setString(1, importId)
                    statement.setString(2, entity)
                    statement.executeQuery().use { if (it.next()) it.getInt(1) else 0 }
                }

                val playlists = recordCount("playlist")
                val tracks = recordCount("playlist_entry").takeIf { it > 0 } ?: count(
                    """
                    SELECT COUNT(*) FROM playlist_tracks pt
                    JOIN import_records ir ON ir.local_id = pt.playlist_id AND ir.entity_type = 'playlist'
                    WHERE ir.import_id = ?
                    """.trimIndent(),
                )
                val likes = recordCount("liked_track")
                val albums = recordCount("liked_album")
                val artists = recordCount("followed_artist")
                val history = count("SELECT COUNT(*) FROM history WHERE source_import_id = ?")
                val searches = count("SELECT COUNT(*) FROM search_history WHERE source_import_id = ?")
                if (playlists + tracks + likes + albums + artists + history + searches == 0) return@forEach

                connection.prepareStatement(
                    """
                    UPDATE import_batches SET playlists_count = ?, tracks_count = ?, likes_count = ?, albums_count = ?,
                        artists_count = ?, history_count = ?, searches_count = ? WHERE id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setInt(1, playlists)
                    statement.setInt(2, tracks)
                    statement.setInt(3, likes)
                    statement.setInt(4, albums)
                    statement.setInt(5, artists)
                    statement.setInt(6, history)
                    statement.setInt(7, searches)
                    statement.setString(8, importId)
                    statement.executeUpdate()
                }
                enqueueUpsert(connection, SyncEntityType.IMPORT_BATCH, importId)
            }
        }
    }

    /** Schema v4: idempotent import provenance and batch bookkeeping. */
    private fun migrateImportSchema() {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS import_batches (
                    id TEXT PRIMARY KEY,
                    source_platform TEXT NOT NULL,
                    fingerprint TEXT NOT NULL,
                    imported_at INTEGER NOT NULL,
                    summary TEXT NOT NULL DEFAULT '',
                    UNIQUE(source_platform, fingerprint)
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS import_records (
                    source_platform TEXT NOT NULL,
                    source_id TEXT NOT NULL,
                    entity_type TEXT NOT NULL,
                    local_id TEXT NOT NULL,
                    import_id TEXT NOT NULL,
                    imported_at INTEGER NOT NULL,
                    original_uri TEXT,
                    PRIMARY KEY (source_platform, source_id, entity_type)
                )
                """.trimIndent(),
            )
            listOf(
                "history" to "source_import_id", "history" to "source_uri", "history" to "source_event_key",
                "history" to "source_start_reason", "history" to "source_end_reason",
                "history" to "source_shuffle", "history" to "source_device",
                "search_history" to "source_import_id", "search_history" to "source_platform",
                "search_history" to "source_result_uris",
            ).forEach { (table, column) ->
                val columns = mutableSetOf<String>()
                statement.executeQuery("PRAGMA table_info($table)").use { rows ->
                    while (rows.next()) columns += rows.getString("name")
                }
                if (column !in columns) statement.execute("ALTER TABLE $table ADD COLUMN $column TEXT")
            }
            statement.execute("CREATE INDEX IF NOT EXISTS import_records_local ON import_records(entity_type, local_id)")
            statement.execute("CREATE INDEX IF NOT EXISTS import_batches_fingerprint ON import_batches(source_platform, fingerprint)")
            statement.execute("PRAGMA user_version = 4")
        }
    }

    // ------------------------------------------------------------------ tracks

    override suspend fun likedTracks(): List<Track> = withDb { connection ->
        connection.prepareStatement(
            """
            SELECT t.* FROM tracks t
            JOIN liked_tracks l ON l.track_id = t.id
            ORDER BY l.added_at DESC
            """.trimIndent(),
        ).use { statement ->
            statement.executeQuery().use { rows -> rows.mapTracks() }
        }
    }

    override suspend fun likedIds(): Set<String> = withDb { connection ->
        connection.prepareStatement("SELECT track_id FROM liked_tracks").use { statement ->
            statement.executeQuery().use { rows ->
                buildSet { while (rows.next()) add(rows.getString(1)) }
            }
        }
    }

    override suspend fun addLikedTrack(track: Track) = withDb { connection ->
        connection.transaction {
            upsertTrack(connection, track)
            connection.prepareStatement(
                """
                INSERT INTO liked_tracks(track_id, added_at) VALUES(?, ?)
                ON CONFLICT(track_id) DO UPDATE SET added_at = excluded.added_at,
                    source_platform = NULL, source_import_id = NULL, imported_at = NULL,
                    original_source_identifier = NULL
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, track.id)
                statement.setLong(2, System.currentTimeMillis())
                statement.executeUpdate()
            }
            enqueueUpsert(connection, SyncEntityType.LIKED_SONG, track.id)
        }
        Unit
    }

    override suspend fun removeLikedTrack(trackId: String) = withDb { connection ->
        connection.transaction {
            connection.prepareStatement("DELETE FROM liked_tracks WHERE track_id = ?").use { statement ->
                statement.setString(1, trackId)
                statement.executeUpdate()
            }
            enqueueDelete(connection, SyncEntityType.LIKED_SONG, trackId)
        }
        Unit
    }

    // ------------------------------------------------------------------ albums

    override suspend fun likedAlbums(): List<Album> = withDb { connection ->
        connection.prepareStatement(
            "SELECT * FROM liked_albums ORDER BY added_at DESC",
        ).use { statement ->
            statement.executeQuery().use { rows -> rows.mapAlbums() }
        }
    }

    override suspend fun addLikedAlbum(album: Album) = withDb { connection ->
        connection.transaction {
            connection.prepareStatement(
                """
                INSERT INTO liked_albums (id, title, artist, artwork_url, year, track_count, genre, artist_id, added_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    title = excluded.title,
                    artist = excluded.artist,
                    artwork_url = COALESCE(excluded.artwork_url, liked_albums.artwork_url),
                    source_platform = NULL, source_import_id = NULL, imported_at = NULL,
                    original_source_identifier = NULL
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, album.id)
                statement.setString(2, album.title)
                statement.setString(3, album.artist)
                statement.setString(4, album.artworkUrl)
                statement.setObject(5, album.year)
                statement.setInt(6, album.trackCount)
                statement.setString(7, album.genre)
                statement.setString(8, album.artistId)
                statement.setLong(9, System.currentTimeMillis())
                statement.executeUpdate()
            }
            enqueueUpsert(connection, SyncEntityType.LIKED_ALBUM, album.id)
        }
        Unit
    }

    override suspend fun removeLikedAlbum(albumId: String) = withDb { connection ->
        connection.transaction {
            connection.prepareStatement("DELETE FROM liked_albums WHERE id = ?").use { statement ->
                statement.setString(1, albumId)
                statement.executeUpdate()
            }
            enqueueDelete(connection, SyncEntityType.LIKED_ALBUM, albumId)
        }
        Unit
    }

    // ----------------------------------------------------------------- artists

    override suspend fun followedArtists(): List<Artist> = withDb { connection ->
        connection.prepareStatement("SELECT * FROM followed_artists ORDER BY added_at DESC").use { statement ->
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            Artist(
                                id = rows.getString("id"),
                                name = rows.getString("name"),
                                imageUrl = rows.getString("image_url"),
                                genre = rows.getString("genre"),
                            ),
                        )
                    }
                }
            }
        }
    }

    override suspend fun addFollowedArtist(artist: Artist) = withDb { connection ->
        connection.transaction {
            connection.prepareStatement(
                """
                INSERT INTO followed_artists (id, name, image_url, genre, added_at) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET name = excluded.name,
                    image_url = COALESCE(excluded.image_url, followed_artists.image_url),
                    source_platform = NULL, source_import_id = NULL, imported_at = NULL,
                    original_source_identifier = NULL
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, artist.id)
                statement.setString(2, artist.name)
                statement.setString(3, artist.imageUrl)
                statement.setString(4, artist.genre)
                statement.setLong(5, System.currentTimeMillis())
                statement.executeUpdate()
            }
            enqueueUpsert(connection, SyncEntityType.FOLLOWED_ARTIST, artist.id)
        }
        Unit
    }

    override suspend fun removeFollowedArtist(artistId: String) = withDb { connection ->
        connection.transaction {
            connection.prepareStatement("DELETE FROM followed_artists WHERE id = ?").use { statement ->
                statement.setString(1, artistId)
                statement.executeUpdate()
            }
            enqueueDelete(connection, SyncEntityType.FOLLOWED_ARTIST, artistId)
        }
        Unit
    }

    // --------------------------------------------------------------- playlists

    override suspend fun playlists(): List<Playlist> = withDb { connection ->
        connection.prepareStatement(
            """
            SELECT p.*, (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlist_id = p.id) AS track_count,
                (SELECT COALESCE(NULLIF(t.artwork_url, ''), NULLIF(t.local_path, ''))
                    FROM playlist_tracks pt JOIN tracks t ON t.id = pt.track_id
                    WHERE pt.playlist_id = p.id
                      AND COALESCE(NULLIF(t.artwork_url, ''), NULLIF(t.local_path, '')) IS NOT NULL
                    ORDER BY pt.position ASC, pt.entry_id ASC LIMIT 1) AS artwork_url
            FROM playlists p ORDER BY p.updated_at DESC
            """.trimIndent(),
        ).use { statement ->
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) add(rows.toPlaylist())
                }
            }
        }
    }

    override suspend fun playlist(playlistId: String): Playlist? = withDb { connection ->
        connection.prepareStatement(
            """
            SELECT p.*, (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlist_id = p.id) AS track_count,
                (SELECT COALESCE(NULLIF(t.artwork_url, ''), NULLIF(t.local_path, ''))
                    FROM playlist_tracks pt JOIN tracks t ON t.id = pt.track_id
                    WHERE pt.playlist_id = p.id
                      AND COALESCE(NULLIF(t.artwork_url, ''), NULLIF(t.local_path, '')) IS NOT NULL
                    ORDER BY pt.position ASC, pt.entry_id ASC LIMIT 1) AS artwork_url
            FROM playlists p WHERE p.id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, playlistId)
            statement.executeQuery().use { rows -> if (rows.next()) rows.toPlaylist() else null }
        }
    }

    override suspend fun createPlaylist(name: String): Playlist = withDb { connection ->
        val now = System.currentTimeMillis()
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name,
            description = "",
            trackCount = 0,
            createdAt = now,
        )
        connection.transaction {
            connection.prepareStatement(
                "INSERT INTO playlists(id, name, description, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
            ).use { statement ->
                statement.setString(1, playlist.id)
                statement.setString(2, playlist.name)
                statement.setString(3, playlist.description)
                statement.setLong(4, now)
                statement.setLong(5, now)
                statement.executeUpdate()
            }
            enqueueUpsert(connection, SyncEntityType.PLAYLIST, playlist.id)
        }
        playlist
    }

    override suspend fun renamePlaylist(playlistId: String, name: String) = withDb { connection ->
        connection.transaction {
            connection.prepareStatement("UPDATE playlists SET name = ?, updated_at = ? WHERE id = ?").use { statement ->
                statement.setString(1, name)
                statement.setLong(2, System.currentTimeMillis())
                statement.setString(3, playlistId)
                statement.executeUpdate()
            }
            enqueueUpsert(connection, SyncEntityType.PLAYLIST, playlistId)
        }
        Unit
    }

    override suspend fun setPlaylistDescription(playlistId: String, description: String) = withDb { connection ->
        connection.transaction {
            connection.prepareStatement("UPDATE playlists SET description = ?, updated_at = ? WHERE id = ?").use { statement ->
                statement.setString(1, description)
                statement.setLong(2, System.currentTimeMillis())
                statement.setString(3, playlistId)
                statement.executeUpdate()
            }
            enqueueUpsert(connection, SyncEntityType.PLAYLIST, playlistId)
        }
        Unit
    }

    override suspend fun deletePlaylist(playlistId: String) = withDb { connection ->
        connection.transaction {
            connection.prepareStatement("DELETE FROM playlist_tracks WHERE playlist_id = ?").use { statement ->
                statement.setString(1, playlistId)
                statement.executeUpdate()
            }
            connection.prepareStatement("DELETE FROM playlist_plays WHERE playlist_id = ?").use { statement ->
                statement.setString(1, playlistId)
                statement.executeUpdate()
            }
            connection.prepareStatement("DELETE FROM playlists WHERE id = ?").use { statement ->
                statement.setString(1, playlistId)
                statement.executeUpdate()
            }
            enqueueDelete(connection, SyncEntityType.PLAYLIST, playlistId)
        }
        Unit
    }

    override suspend fun recordPlaylistPlay(playlistId: String) = withDb { connection ->
        val now = System.currentTimeMillis()
        connection.prepareStatement(
            """
            INSERT INTO playlist_plays(playlist_id, played_at)
            SELECT ?, MAX(?, COALESCE((SELECT MAX(played_at) FROM playlist_plays), 0) + 1)
            WHERE EXISTS (SELECT 1 FROM playlists WHERE id = ?)
            ON CONFLICT(playlist_id) DO UPDATE SET played_at = excluded.played_at
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, playlistId)
            statement.setLong(2, now)
            statement.setString(3, playlistId)
            statement.executeUpdate()
        }
        Unit
    }

    override suspend fun recentlyPlayedPlaylists(limit: Int): List<PlayedPlaylist> = withDb { connection ->
        connection.prepareStatement(
            """
            SELECT p.*,
                (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlist_id = p.id) AS track_count,
                (SELECT COALESCE(t.artwork_url, t.local_path) FROM playlist_tracks pt
                    JOIN tracks t ON t.id = pt.track_id
                    WHERE pt.playlist_id = p.id AND COALESCE(t.artwork_url, t.local_path) IS NOT NULL
                    ORDER BY pt.position ASC LIMIT 1) AS artwork_url,
                pp.played_at AS last_played_at
            FROM playlist_plays pp
            JOIN playlists p ON p.id = pp.playlist_id
            ORDER BY pp.played_at DESC
            LIMIT ?
            """.trimIndent(),
        ).use { statement ->
            statement.setInt(1, limit.coerceAtLeast(0))
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            PlayedPlaylist(
                                playlist = rows.toPlaylist(),
                                artworkUrl = rows.getString("artwork_url"),
                                playedAt = rows.getLong("last_played_at"),
                            ),
                        )
                    }
                }
            }
        }
    }

    override suspend fun playlistTracks(playlistId: String): List<Track> = withDb { connection ->
        connection.prepareStatement(
            """
            SELECT t.* FROM tracks t
            JOIN playlist_tracks pt ON pt.track_id = t.id
            WHERE pt.playlist_id = ?
            ORDER BY pt.position ASC
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, playlistId)
            statement.executeQuery().use { rows -> rows.mapTracks() }
        }
    }

    override suspend fun playlistTrackEntries(playlistId: String): List<PlaylistTrackEntry> = withDb { connection ->
        connection.prepareStatement(
            """
            SELECT t.*, pt.entry_id AS entry_id, pt.position AS position, pt.added_at AS added_at,
                   pt.source_platform AS source_platform, pt.source_import_id AS source_import_id,
                   pt.imported_at AS imported_at
            FROM tracks t JOIN playlist_tracks pt ON pt.track_id = t.id
            WHERE pt.playlist_id = ?
            ORDER BY pt.position ASC, pt.entry_id ASC
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, playlistId)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) add(
                        PlaylistTrackEntry(
                            track = rows.toTrack(),
                            addedAt = rows.getLong("added_at"),
                            entryId = rows.getString("entry_id"),
                            position = rows.getInt("position"),
                            sourcePlatform = rows.getString("source_platform"),
                            sourceImportId = rows.getString("source_import_id"),
                            importedAt = rows.getObject("imported_at")?.let { (it as Number).toLong() },
                        ),
                    )
                }
            }
        }
    }

    /** Repeated adds create another explicit entry; they are no longer collapsed by track id. */
    override suspend fun addToPlaylist(playlistId: String, track: Track): Boolean = withDb { connection ->
        connection.transaction {
            if (!playlistExists(connection, playlistId)) return@transaction false
            upsertTrack(connection, track)
            val position = connection.prepareStatement(
                "SELECT COALESCE(MAX(position), 0) + 1 FROM playlist_tracks WHERE playlist_id = ?",
            ).use { statement ->
                statement.setString(1, playlistId)
                statement.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else 1 }
            }
            connection.prepareStatement(
                "INSERT INTO playlist_tracks(entry_id, playlist_id, track_id, position, added_at) VALUES (?, ?, ?, ?, ?)",
            ).use { statement ->
                statement.setString(1, UUID.randomUUID().toString())
                statement.setString(2, playlistId)
                statement.setString(3, track.id)
                statement.setInt(4, position)
                statement.setLong(5, System.currentTimeMillis())
                statement.executeUpdate()
            }
            touchPlaylist(connection, playlistId)
            enqueueUpsert(connection, SyncEntityType.PLAYLIST, playlistId)
            true
        }
    }

    /** Legacy API: removes the first occurrence only, so repeated entries are not silently destroyed. */
    override suspend fun removeFromPlaylist(playlistId: String, trackId: String) = withDb { connection ->
        connection.transaction {
            connection.prepareStatement(
                "DELETE FROM playlist_tracks WHERE entry_id = (SELECT entry_id FROM playlist_tracks WHERE playlist_id = ? AND track_id = ? ORDER BY position ASC LIMIT 1)",
            ).use { statement ->
                statement.setString(1, playlistId)
                statement.setString(2, trackId)
                statement.executeUpdate()
            }
            touchPlaylist(connection, playlistId)
            enqueueUpsert(connection, SyncEntityType.PLAYLIST, playlistId)
        }
        Unit
    }

    override suspend fun removePlaylistEntry(playlistId: String, entryId: String) = withDb { connection ->
        connection.transaction {
            connection.prepareStatement("DELETE FROM playlist_tracks WHERE playlist_id = ? AND entry_id = ?").use { statement ->
                statement.setString(1, playlistId)
                statement.setString(2, entryId)
                statement.executeUpdate()
            }
            touchPlaylist(connection, playlistId)
            enqueueUpsert(connection, SyncEntityType.PLAYLIST, playlistId)
        }
        Unit
    }

    override suspend fun movePlaylistTrack(playlistId: String, fromIndex: Int, toIndex: Int) = withDb { connection ->
        connection.transaction {
            val ids = connection.prepareStatement(
                "SELECT entry_id FROM playlist_tracks WHERE playlist_id = ? ORDER BY position ASC, entry_id ASC",
            ).use { statement ->
                statement.setString(1, playlistId)
                statement.executeQuery().use { rows ->
                    buildList { while (rows.next()) add(rows.getString(1)) }
                }
            }
            if (fromIndex in ids.indices && toIndex in ids.indices && fromIndex != toIndex) {
                val moved = ids.toMutableList()
                val item = moved.removeAt(fromIndex)
                moved.add(toIndex, item)
                connection.prepareStatement(
                    "UPDATE playlist_tracks SET position = ? WHERE playlist_id = ? AND entry_id = ?",
                ).use { statement ->
                    moved.forEachIndexed { index, entryId ->
                        statement.setInt(1, index + 1)
                        statement.setString(2, playlistId)
                        statement.setString(3, entryId)
                        statement.executeUpdate()
                    }
                }
                touchPlaylist(connection, playlistId)
                enqueueUpsert(connection, SyncEntityType.PLAYLIST, playlistId)
            }
        }
        Unit
    }

    private fun playlistExists(connection: Connection, playlistId: String): Boolean =
        connection.prepareStatement("SELECT 1 FROM playlists WHERE id = ?").use { statement ->
            statement.setString(1, playlistId)
            statement.executeQuery().use { it.next() }
        }

    private fun touchPlaylist(connection: Connection, playlistId: String) {
        connection.prepareStatement("UPDATE playlists SET updated_at = ? WHERE id = ?").use { statement ->
            statement.setLong(1, System.currentTimeMillis())
            statement.setString(2, playlistId)
            statement.executeUpdate()
        }
    }

    // ----------------------------------------------------------------- history

    override suspend fun recordPlay(track: Track) = withDb { connection ->
        insertHistory(connection, track, playedMs = 0, completed = false, skipped = false)
        Unit
    }

    override suspend fun recordSkip(track: Track, playedMs: Long) = withDb { connection ->
        insertHistory(connection, track, playedMs = playedMs, completed = false, skipped = true)
        Unit
    }

    override suspend fun recordCompletion(track: Track, playedMs: Long) = withDb { connection ->
        insertHistory(connection, track, playedMs = playedMs, completed = true, skipped = false)
        Unit
    }

    private fun insertHistory(connection: Connection, track: Track, playedMs: Long, completed: Boolean, skipped: Boolean) {
        connection.transaction {
            upsertTrack(connection, track)
            val syncId = UUID.randomUUID().toString()
            connection.prepareStatement(
                "INSERT INTO history(track_id, played_at, played_ms, completed, skipped, sync_id) VALUES (?, ?, ?, ?, ?, ?)",
            ).use { statement ->
                statement.setString(1, track.id)
                statement.setLong(2, System.currentTimeMillis())
                statement.setLong(3, playedMs.coerceAtLeast(0))
                statement.setInt(4, if (completed) 1 else 0)
                statement.setInt(5, if (skipped) 1 else 0)
                statement.setString(6, syncId)
                statement.executeUpdate()
            }
            connection.prepareStatement(
                """
                DELETE FROM history WHERE source_import_id IS NULL AND id NOT IN
                    (SELECT id FROM history WHERE source_import_id IS NULL ORDER BY played_at DESC LIMIT 5000)
                """.trimIndent(),
            ).use { it.executeUpdate() }
            enqueueUpsert(connection, SyncEntityType.HISTORY, syncId)
        }
    }

    override suspend fun skippedTrackIds(limit: Int): List<String> = withDb { connection ->
        connection.prepareStatement(
            """
            SELECT track_id FROM history
            WHERE skipped = 1 AND played_at >= ?
            GROUP BY track_id ORDER BY MAX(played_at) DESC LIMIT ?
            """.trimIndent(),
        ).use { statement ->
            statement.setLong(1, System.currentTimeMillis() - 14L * 24 * 60 * 60 * 1000)
            statement.setInt(2, limit)
            statement.executeQuery().use { rows ->
                buildList { while (rows.next()) add(rows.getString(1)) }
            }
        }
    }

    override suspend fun recentlyPlayed(limit: Int): List<Track> = withDb { connection ->
        connection.prepareStatement(
            """
            SELECT t.* FROM tracks t
            JOIN (
                SELECT track_id, MAX(played_at) AS last_played
                FROM history GROUP BY track_id ORDER BY last_played DESC LIMIT ?
            ) recent ON recent.track_id = t.id
            ORDER BY recent.last_played DESC
            """.trimIndent(),
        ).use { statement ->
            statement.setInt(1, limit)
            statement.executeQuery().use { rows -> rows.mapTracks() }
        }
    }

    override suspend fun recentHistory(limit: Int): List<PlayedTrack> = withDb { connection ->
        connection.prepareStatement(
            """
            SELECT t.*, recent.last_played FROM tracks t
            JOIN (
                SELECT track_id, MAX(played_at) AS last_played
                FROM history GROUP BY track_id ORDER BY last_played DESC LIMIT ?
            ) recent ON recent.track_id = t.id
            ORDER BY recent.last_played DESC
            """.trimIndent(),
        ).use { statement ->
            statement.setInt(1, limit)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add(PlayedTrack(rows.toTrack(), rows.getLong("last_played")))
                    }
                }
            }
        }
    }

    override suspend fun userActivity(): UserActivitySnapshot = withDb { connection ->
        fun count(sql: String): Int = connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rows -> if (rows.next()) rows.getInt(1) else 0 }
        }

        val plays = count("SELECT COUNT(*) FROM history")
        val completions = count("SELECT COUNT(*) FROM history WHERE completed = 1")
        val skips = count("SELECT COUNT(*) FROM history WHERE skipped = 1")

        var distinctArtists = 0
        var distinctGenres = 0
        var oldestPlay = 0L
        var newestPlay = 0L
        connection.createStatement().use { statement ->
            statement.executeQuery(
                """
                SELECT COUNT(DISTINCT t.artist) AS artists,
                       COUNT(DISTINCT NULLIF(TRIM(t.genre), '')) AS genres,
                       COALESCE(MIN(h.played_at), 0) AS oldest,
                       COALESCE(MAX(h.played_at), 0) AS newest
                FROM history h JOIN tracks t ON t.id = h.track_id
                """.trimIndent(),
            ).use { rows ->
                if (rows.next()) {
                    distinctArtists = rows.getInt("artists")
                    distinctGenres = rows.getInt("genres")
                    oldestPlay = rows.getLong("oldest")
                    newestPlay = rows.getLong("newest")
                }
            }
        }

        val topArtists = connection.createStatement().use { statement ->
            statement.executeQuery(
                """
                SELECT t.artist, COUNT(*) AS plays, MAX(NULLIF(t.artist_id, '')) AS artist_id
                FROM history h
                JOIN tracks t ON t.id = h.track_id
                WHERE TRIM(t.artist) <> ''
                GROUP BY LOWER(t.artist) ORDER BY plays DESC, t.artist ASC LIMIT 5
                """.trimIndent(),
            ).use { rows ->
                buildList {
                    while (rows.next()) {
                        add(ArtistAffinity(rows.getString(1), rows.getInt(2), rows.getString(3)))
                    }
                }
            }
        }

        // Albums are a taste signal for recommendation quality and a guard against
        // showing the same record the user already has in heavy rotation.
        val topAlbums = connection.createStatement().use { statement ->
            statement.executeQuery(
                """
                SELECT t.album_id, t.album, t.artist, COUNT(*) AS plays,
                       MAX(NULLIF(t.artist_id, '')) AS artist_id
                FROM history h
                JOIN tracks t ON t.id = h.track_id
                WHERE t.source = 'YOUTUBE_MUSIC'
                  AND t.album_id IS NOT NULL AND TRIM(t.album_id) <> ''
                GROUP BY t.album_id ORDER BY plays DESC, t.album ASC LIMIT 5
                """.trimIndent(),
            ).use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            AlbumAffinity(
                                albumId = rows.getString(1),
                                title = rows.getString(2),
                                artist = rows.getString(3),
                                plays = rows.getInt(4),
                                artistId = rows.getString(5),
                            ),
                        )
                    }
                }
            }
        }

        val topGenres = connection.createStatement().use { statement ->
            statement.executeQuery(
                """
                SELECT t.genre, COUNT(*) AS plays FROM history h
                JOIN tracks t ON t.id = h.track_id
                WHERE t.genre IS NOT NULL AND TRIM(t.genre) <> ''
                GROUP BY LOWER(t.genre) ORDER BY plays DESC, t.genre ASC LIMIT 5
                """.trimIndent(),
            ).use { rows ->
                buildList { while (rows.next()) add(GenreAffinity(rows.getString(1), rows.getInt(2))) }
            }
        }

        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val spanDays = if (oldestPlay > 0 && newestPlay >= oldestPlay) {
            ((newestPlay - oldestPlay) / day).toInt()
        } else {
            0
        }

        UserActivitySnapshot(
            playCount = plays,
            nativePlayCount = count("SELECT COUNT(*) FROM history WHERE source_import_id IS NULL"),
            importedPlayCount = count("SELECT COUNT(*) FROM history WHERE source_import_id IS NOT NULL"),
            importedLikedCount = count("SELECT COUNT(*) FROM liked_tracks WHERE source_import_id IS NOT NULL"),
            importedPlaylistCount = count("SELECT COUNT(*) FROM playlists WHERE source_import_id IS NOT NULL"),
            completionCount = completions,
            skipCount = skips,
            distinctArtists = distinctArtists,
            distinctGenres = distinctGenres,
            likedTrackCount = count("SELECT COUNT(*) FROM liked_tracks"),
            likedAlbumCount = count("SELECT COUNT(*) FROM liked_albums"),
            followedArtistCount = count("SELECT COUNT(*) FROM followed_artists"),
            playlistCount = count("SELECT COUNT(*) FROM playlists"),
            localTrackCount = count("SELECT COUNT(*) FROM tracks WHERE source = 'LOCAL'"),
            historySpanDays = spanDays,
            daysSinceLastPlay = if (newestPlay > 0) ((now - newestPlay) / day).toInt() else null,
            topArtists = topArtists,
            topAlbums = topAlbums,
            topGenres = topGenres,
        )
    }

    override suspend fun searchHistory(limit: Int): List<SearchEntry> = withDb { connection ->
        connection.prepareStatement(
            "SELECT id, query, searched_at FROM search_history ORDER BY searched_at DESC LIMIT ?",
        ).use { statement ->
            statement.setInt(1, limit)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) add(SearchEntry(rows.getString("id"), rows.getString("query"), rows.getLong("searched_at")))
                }
            }
        }
    }

    override suspend fun recordSearch(query: String): SearchEntry = withDb { connection ->
        val trimmed = query.trim()
        require(trimmed.isNotEmpty()) { "Search query cannot be empty" }
        val key = trimmed.lowercase()
        val now = System.currentTimeMillis()
        connection.transaction {
            val existing = connection.prepareStatement("SELECT id FROM search_history WHERE query_key = ?").use { statement ->
                statement.setString(1, key)
                statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
            }
            val id = existing ?: UUID.randomUUID().toString()
            connection.prepareStatement(
                """
                INSERT INTO search_history(id, query_key, query, searched_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(query_key) DO UPDATE SET query = excluded.query, searched_at = excluded.searched_at,
                    updated_at = excluded.updated_at, source_platform = NULL, source_import_id = NULL,
                    source_event_id = NULL, imported_at = NULL, source_result_uris = NULL
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, id)
                statement.setString(2, key)
                statement.setString(3, trimmed)
                statement.setLong(4, now)
                statement.setLong(5, now)
                statement.executeUpdate()
            }
            connection.prepareStatement(
                """
                DELETE FROM search_history WHERE source_import_id IS NULL AND id NOT IN
                    (SELECT id FROM search_history WHERE source_import_id IS NULL ORDER BY searched_at DESC LIMIT 50)
                """.trimIndent(),
            ).use { it.executeUpdate() }
            enqueueUpsert(connection, SyncEntityType.SEARCH_HISTORY, id)
            SearchEntry(id, trimmed, now)
        }
    }

    override suspend fun recentlyPlayedIds(limit: Int): List<String> = withDb { connection ->
        connection.prepareStatement(
            "SELECT track_id FROM history GROUP BY track_id ORDER BY MAX(played_at) DESC LIMIT ?",
        ).use { statement ->
            statement.setInt(1, limit)
            statement.executeQuery().use { rows ->
                buildList { while (rows.next()) add(rows.getString(1)) }
            }
        }
    }

    // ------------------------------------------------------------------ lyrics

    override suspend fun cachedLyrics(trackId: String): CachedLyrics? = withDb { connection ->
        connection.prepareStatement("SELECT plain, synced, source FROM lyrics_cache WHERE track_id = ?").use { statement ->
            statement.setString(1, trackId)
            statement.executeQuery().use { rows ->
                if (rows.next()) CachedLyrics(rows.getString(1), rows.getString(2), rows.getString(3)) else null
            }
        }
    }

    override suspend fun putLyrics(trackId: String, plain: String?, synced: String?, source: String) = withDb { connection ->
        connection.prepareStatement(
            """
            INSERT INTO lyrics_cache(track_id, plain, synced, source, fetched_at) VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(track_id) DO UPDATE SET plain = excluded.plain, synced = excluded.synced, source = excluded.source, fetched_at = excluded.fetched_at
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, trackId)
            statement.setString(2, plain)
            statement.setString(3, synced)
            statement.setString(4, source)
            statement.setLong(5, System.currentTimeMillis())
            statement.executeUpdate()
        }
        Unit
    }

    // ---------------------------------------------------- recommendations

    override suspend fun recommendationCache(): RecommendationCacheRow? = withDb { connection ->
        connection.prepareStatement(
            "SELECT refreshed_at, payload FROM recommendation_cache WHERE id = 1",
        ).use { statement ->
            statement.executeQuery().use { rows ->
                if (rows.next()) {
                    RecommendationCacheRow(rows.getLong("refreshed_at"), rows.getString("payload"))
                } else {
                    null
                }
            }
        }
    }

    override suspend fun saveRecommendationCache(row: RecommendationCacheRow) = withDb { connection ->
        connection.prepareStatement(
            """
            INSERT INTO recommendation_cache(id, refreshed_at, payload) VALUES (1, ?, ?)
            ON CONFLICT(id) DO UPDATE SET refreshed_at = excluded.refreshed_at, payload = excluded.payload
            """.trimIndent(),
        ).use { statement ->
            statement.setLong(1, row.refreshedAt)
            statement.setString(2, row.payload)
            statement.executeUpdate()
        }
        Unit
    }

    // ---------------------------------------------------------------- settings

    override suspend fun getSetting(key: String): String? = withDb { connection ->
        connection.prepareStatement("SELECT value FROM settings WHERE key = ?").use { statement ->
            statement.setString(1, key)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
        }
    }

    override suspend fun setSetting(key: String, value: String) = withDb { connection ->
        connection.transaction {
            connection.prepareStatement(
                "INSERT INTO settings(key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
            ).use { statement ->
                statement.setString(1, key)
                statement.setString(2, value)
                statement.executeUpdate()
            }
            if (key in SYNCED_SETTINGS) {
                putSyncValue(connection, "preferences_set", "1")
                enqueueUpsert(connection, SyncEntityType.PREFERENCES, PREFERENCES_ID)
            }
        }
        Unit
    }

    // ------------------------------------------------------------------- local

    override suspend fun scanFolders(): List<String> = withDb { connection ->
        connection.prepareStatement("SELECT path FROM scan_folders ORDER BY added_at ASC").use { statement ->
            statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getString(1)) } }
        }
    }

    override suspend fun addScanFolder(path: String) = withDb { connection ->
        connection.prepareStatement("INSERT OR IGNORE INTO scan_folders(path, added_at) VALUES (?, ?)").use { statement ->
            statement.setString(1, path)
            statement.setLong(2, System.currentTimeMillis())
            statement.executeUpdate()
        }
        Unit
    }

    override suspend fun removeScanFolder(path: String) = withDb { connection ->
        connection.prepareStatement("DELETE FROM scan_folders WHERE path = ?").use { statement ->
            statement.setString(1, path)
            statement.executeUpdate()
        }
        Unit
    }

    override suspend fun localTracks(): List<Track> = withDb { connection ->
        connection.prepareStatement(
            """
            SELECT * FROM tracks WHERE source = 'LOCAL'
            ORDER BY artist COLLATE NOCASE ASC, album COLLATE NOCASE ASC, track_number ASC, title COLLATE NOCASE ASC
            """.trimIndent(),
        ).use { statement ->
            statement.executeQuery().use { rows -> rows.mapTracks() }
        }
    }

    override suspend fun localFileState(): Map<String, Long> = withDb { connection ->
        connection.prepareStatement("SELECT path, mtime FROM local_files").use { statement ->
            statement.executeQuery().use { rows ->
                buildMap { while (rows.next()) put(rows.getString(1), rows.getLong(2)) }
            }
        }
    }

    override suspend fun upsertLocalTracks(tracks: List<Track>, mtimes: Map<String, Long>) = withDb { connection ->
        connection.transaction {
            tracks.forEach { upsertTrack(connection, it) }
            connection.prepareStatement(
                "INSERT INTO local_files(path, mtime) VALUES (?, ?) ON CONFLICT(path) DO UPDATE SET mtime = excluded.mtime",
            ).use { statement ->
                mtimes.forEach { (path, mtime) ->
                    statement.setString(1, path)
                    statement.setLong(2, mtime)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        }
        Unit
    }

    override suspend fun removeLocalTracks(paths: List<String>) = withDb { connection ->
        if (paths.isEmpty()) return@withDb
        connection.transaction {
            paths.chunked(300).forEach { chunk ->
                val placeholders = chunk.joinToString(",") { "?" }
                connection.prepareStatement("DELETE FROM tracks WHERE local_path IN ($placeholders)").use { statement ->
                    chunk.forEachIndexed { index, path -> statement.setString(index + 1, path) }
                    statement.executeUpdate()
                }
                connection.prepareStatement("DELETE FROM local_files WHERE path IN ($placeholders)").use { statement ->
                    chunk.forEachIndexed { index, path -> statement.setString(index + 1, path) }
                    statement.executeUpdate()
                }
            }
        }
        Unit
    }

    override suspend fun upsertTracks(tracks: List<Track>) = withDb { connection ->
        connection.transaction {
            tracks.forEach { upsertTrack(connection, it) }
        }
        Unit
    }

    // ----------------------------------------------------------------- imports

    override suspend fun importBatchExists(sourcePlatform: String, fingerprint: String): Boolean = withDb { connection ->
        connection.prepareStatement(
            "SELECT 1 FROM import_batches WHERE source_platform = ? AND fingerprint = ?",
        ).use { statement ->
            statement.setString(1, sourcePlatform)
            statement.setString(2, fingerprint)
            statement.executeQuery().use { it.next() }
        }
    }

    override suspend fun importBatchId(sourcePlatform: String, fingerprint: String): String? = withDb { connection ->
        connection.prepareStatement(
            "SELECT id FROM import_batches WHERE source_platform = ? AND fingerprint = ?",
        ).use { statement ->
            statement.setString(1, sourcePlatform)
            statement.setString(2, fingerprint)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
        }
    }

    override suspend fun recordImportBatch(batch: ImportBatchRecord) = withDb { connection ->
        connection.transaction {
            connection.prepareStatement(
                """
                INSERT INTO import_batches(
                    id, source_platform, fingerprint, imported_at, summary,
                    playlists_count, tracks_count, likes_count, albums_count,
                    artists_count, history_count, searches_count, deleted_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)
                ON CONFLICT(source_platform, fingerprint) DO UPDATE SET
                    id = CASE WHEN import_batches.deleted_at IS NOT NULL THEN excluded.id ELSE import_batches.id END,
                    imported_at = CASE WHEN import_batches.deleted_at IS NOT NULL THEN excluded.imported_at ELSE import_batches.imported_at END,
                    summary = CASE WHEN import_batches.deleted_at IS NOT NULL THEN excluded.summary ELSE import_batches.summary END,
                    playlists_count = CASE WHEN import_batches.deleted_at IS NOT NULL THEN excluded.playlists_count ELSE MAX(import_batches.playlists_count, excluded.playlists_count) END,
                    tracks_count = CASE WHEN import_batches.deleted_at IS NOT NULL THEN excluded.tracks_count ELSE MAX(import_batches.tracks_count, excluded.tracks_count) END,
                    likes_count = CASE WHEN import_batches.deleted_at IS NOT NULL THEN excluded.likes_count ELSE MAX(import_batches.likes_count, excluded.likes_count) END,
                    albums_count = CASE WHEN import_batches.deleted_at IS NOT NULL THEN excluded.albums_count ELSE MAX(import_batches.albums_count, excluded.albums_count) END,
                    artists_count = CASE WHEN import_batches.deleted_at IS NOT NULL THEN excluded.artists_count ELSE MAX(import_batches.artists_count, excluded.artists_count) END,
                    history_count = CASE WHEN import_batches.deleted_at IS NOT NULL THEN excluded.history_count ELSE MAX(import_batches.history_count, excluded.history_count) END,
                    searches_count = CASE WHEN import_batches.deleted_at IS NOT NULL THEN excluded.searches_count ELSE MAX(import_batches.searches_count, excluded.searches_count) END,
                    deleted_at = NULL
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, batch.importId)
                statement.setString(2, batch.sourcePlatform)
                statement.setString(3, batch.fingerprint)
                statement.setLong(4, batch.importedAt)
                statement.setString(5, batch.summary)
                statement.setInt(6, batch.playlists)
                statement.setInt(7, batch.tracks)
                statement.setInt(8, batch.likes)
                statement.setInt(9, batch.albums)
                statement.setInt(10, batch.artists)
                statement.setInt(11, batch.history)
                statement.setInt(12, batch.searches)
                statement.executeUpdate()
            }
            val actualId = connection.prepareStatement(
                "SELECT id FROM import_batches WHERE source_platform = ? AND fingerprint = ?",
            ).use { statement ->
                statement.setString(1, batch.sourcePlatform)
                statement.setString(2, batch.fingerprint)
                statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else batch.importId }
            }
            enqueueUpsert(connection, SyncEntityType.IMPORT_BATCH, actualId)
        }
        Unit
    }

    override suspend fun importHistory(limit: Int): List<ImportHistoryItem> = withDb { connection ->
        connection.prepareStatement(
            """
            SELECT * FROM import_batches ORDER BY imported_at DESC LIMIT ?
            """.trimIndent(),
        ).use { statement ->
            statement.setInt(1, limit.coerceIn(1, 200))
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        val batch = ImportBatchRecord(
                            importId = rows.getString("id"),
                            sourcePlatform = rows.getString("source_platform"),
                            fingerprint = rows.getString("fingerprint"),
                            importedAt = rows.getLong("imported_at"),
                            summary = rows.getString("summary") ?: "",
                            playlists = rows.getInt("playlists_count"),
                            tracks = rows.getInt("tracks_count"),
                            likes = rows.getInt("likes_count"),
                            albums = rows.getInt("albums_count"),
                            artists = rows.getInt("artists_count"),
                            history = rows.getInt("history_count"),
                            searches = rows.getInt("searches_count"),
                        )
                        val undone = rows.getObject("deleted_at") != null
                        fun owned(table: String, key: String): Boolean =
                            connection.prepareStatement("SELECT 1 FROM $table WHERE $key = ? LIMIT 1").use { check ->
                                check.setString(1, batch.importId)
                                check.executeQuery().use { it.next() }
                            }
                        val canUndo = !undone && (
                            owned("playlists", "source_import_id") ||
                                owned("playlist_tracks", "source_import_id") ||
                                owned("liked_tracks", "source_import_id") ||
                                owned("liked_albums", "source_import_id") ||
                                owned("followed_artists", "source_import_id") ||
                                owned("history", "source_import_id") ||
                                owned("search_history", "source_import_id") ||
                                connection.prepareStatement("SELECT 1 FROM import_records WHERE import_id = ? LIMIT 1").use { check ->
                                    check.setString(1, batch.importId)
                                    check.executeQuery().use { it.next() }
                                }
                            )
                        add(ImportHistoryItem(batch, canUndo, undone))
                    }
                }
            }
        }
    }

    override suspend fun createImportedPlaylist(
        sourcePlatform: String,
        sourceId: String,
        importId: String,
        name: String,
        description: String,
        tracks: List<Track>,
    ): ImportedPlaylistResult = withDb { connection ->
        connection.transaction {
            val existing = importRecord(connection, sourcePlatform, sourceId, "playlist")
            if (existing != null) {
                ImportedPlaylistResult(existing, alreadyImported = true, tracksAdded = 0, duplicatesSkipped = 0)
            } else {
                val playlistId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                connection.prepareStatement(
                    "INSERT INTO playlists(id, name, description, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                ).use { statement ->
                    statement.setString(1, playlistId)
                    statement.setString(2, name.ifBlank { "Imported playlist" })
                    statement.setString(3, description)
                    statement.setLong(4, now)
                    statement.setLong(5, now)
                    statement.executeUpdate()
                }
                var added = 0
                tracks.forEachIndexed { index, track ->
                    upsertTrack(connection, track)
                    val entryId = UUID.randomUUID().toString()
                    connection.prepareStatement(
                        """
                        INSERT INTO playlist_tracks(
                            entry_id, playlist_id, track_id, position, added_at,
                            source_platform, source_import_id, imported_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setString(1, entryId)
                        statement.setString(2, playlistId)
                        statement.setString(3, track.id)
                        statement.setInt(4, index + 1)
                        statement.setLong(5, now)
                        statement.setString(6, sourcePlatform)
                        statement.setString(7, importId)
                        statement.setLong(8, now)
                        added += statement.executeUpdate()
                    }
                    recordImport(connection, sourcePlatform, "$sourceId#${index + 1}", "playlist_entry", entryId, importId, track.id)
                }
                recordImport(connection, sourcePlatform, sourceId, "playlist", playlistId, importId, sourceId)
                setPlaylistProvenance(connection, playlistId, sourcePlatform, importId, now, sourceId)
                enqueueUpsert(connection, SyncEntityType.PLAYLIST, playlistId)
                ImportedPlaylistResult(playlistId, false, added, 0)
            }
        }
    }

    override suspend fun addImportedLike(
        sourcePlatform: String,
        sourceId: String,
        importId: String,
        track: Track,
    ): Boolean = withDb { connection ->
        connection.transaction {
            if (importRecord(connection, sourcePlatform, sourceId, "liked_track") != null) return@transaction false
            val existed = connection.prepareStatement("SELECT 1 FROM liked_tracks WHERE track_id = ?").use { statement ->
                statement.setString(1, track.id)
                statement.executeQuery().use { it.next() }
            }
            if (existed) return@transaction false
            upsertTrack(connection, track)
            val now = System.currentTimeMillis()
            connection.prepareStatement(
                "INSERT INTO liked_tracks(track_id, added_at, source_platform, source_import_id, imported_at, original_source_identifier) VALUES(?, ?, ?, ?, ?, ?)",
            ).use { statement ->
                statement.setString(1, track.id)
                statement.setLong(2, now)
                statement.setString(3, sourcePlatform)
                statement.setString(4, importId)
                statement.setLong(5, now)
                statement.setString(6, sourceId)
                statement.executeUpdate()
            }
            recordImport(connection, sourcePlatform, sourceId, "liked_track", track.id, importId, sourceId)
            enqueueUpsert(connection, SyncEntityType.LIKED_SONG, track.id)
            true
        }
    }

    override suspend fun addImportedAlbum(
        sourcePlatform: String,
        sourceId: String,
        importId: String,
        album: Album,
    ): Boolean = withDb { connection ->
        connection.transaction {
            if (importRecord(connection, sourcePlatform, sourceId, "liked_album") != null) return@transaction false
            val existed = connection.prepareStatement("SELECT 1 FROM liked_albums WHERE id = ?").use { statement ->
                statement.setString(1, album.id)
                statement.executeQuery().use { it.next() }
            }
            if (existed) return@transaction false
            val now = System.currentTimeMillis()
            connection.prepareStatement(
                """
                INSERT INTO liked_albums(
                    id, title, artist, artwork_url, year, track_count, genre, artist_id, added_at,
                    source_platform, source_import_id, imported_at, original_source_identifier
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, album.id)
                statement.setString(2, album.title)
                statement.setString(3, album.artist)
                statement.setString(4, album.artworkUrl)
                statement.setObject(5, album.year)
                statement.setInt(6, album.trackCount)
                statement.setString(7, album.genre)
                statement.setString(8, album.artistId)
                statement.setLong(9, now)
                statement.setString(10, sourcePlatform)
                statement.setString(11, importId)
                statement.setLong(12, now)
                statement.setString(13, sourceId)
                statement.executeUpdate()
            }
            recordImport(connection, sourcePlatform, sourceId, "liked_album", album.id, importId, sourceId)
            enqueueUpsert(connection, SyncEntityType.LIKED_ALBUM, album.id)
            true
        }
    }

    override suspend fun addImportedArtist(
        sourcePlatform: String,
        sourceId: String,
        importId: String,
        artist: Artist,
    ): Boolean = withDb { connection ->
        connection.transaction {
            if (importRecord(connection, sourcePlatform, sourceId, "followed_artist") != null) return@transaction false
            val existed = connection.prepareStatement("SELECT 1 FROM followed_artists WHERE id = ?").use { statement ->
                statement.setString(1, artist.id)
                statement.executeQuery().use { it.next() }
            }
            if (existed) return@transaction false
            val now = System.currentTimeMillis()
            connection.prepareStatement(
                """
                INSERT INTO followed_artists(
                    id, name, image_url, genre, added_at,
                    source_platform, source_import_id, imported_at, original_source_identifier
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, artist.id)
                statement.setString(2, artist.name)
                statement.setString(3, artist.imageUrl)
                statement.setString(4, artist.genre)
                statement.setLong(5, now)
                statement.setString(6, sourcePlatform)
                statement.setString(7, importId)
                statement.setLong(8, now)
                statement.setString(9, sourceId)
                statement.executeUpdate()
            }
            recordImport(connection, sourcePlatform, sourceId, "followed_artist", artist.id, importId, sourceId)
            enqueueUpsert(connection, SyncEntityType.FOLLOWED_ARTIST, artist.id)
            true
        }
    }

    override suspend fun addImportedHistory(
        sourcePlatform: String,
        importId: String,
        rows: List<HistoryImportRow>,
    ): Pair<Int, Int> = withDb { connection ->
        connection.transaction {
            var added = 0
            rows.forEach { row ->
                upsertTrack(connection, row.track)
                val eventId = row.event.sourceEventId?.takeIf { it.isNotBlank() } ?: row.event.sourceKey
                val syncId = UUID.nameUUIDFromBytes(
                    "$sourcePlatform-history|$eventId".toByteArray(),
                ).toString()
                val inserted = connection.prepareStatement(
                    """
                    INSERT OR IGNORE INTO history(
                        track_id, played_at, played_ms, completed, skipped, sync_id,
                        source_import_id, source_uri, source_event_key,
                        source_start_reason, source_end_reason, source_shuffle, source_device,
                        source_platform, source_event_id, imported_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, row.track.id)
                    statement.setLong(2, row.event.timestampMs)
                    statement.setLong(3, row.event.playedMs.coerceAtLeast(0))
                    statement.setInt(4, if (row.event.completed) 1 else 0)
                    statement.setInt(5, if (row.event.skipped) 1 else 0)
                    statement.setString(6, syncId)
                    statement.setString(7, importId)
                    statement.setString(8, row.event.track.spotifyUri)
                    statement.setString(9, row.event.sourceKey)
                    statement.setString(10, row.event.reasonStart)
                    statement.setString(11, row.event.reasonEnd)
                    statement.setString(12, row.event.shuffle?.toString())
                    statement.setString(13, row.event.platform)
                    statement.setString(14, sourcePlatform)
                    statement.setString(15, eventId)
                    statement.setLong(16, System.currentTimeMillis())
                    statement.executeUpdate()
                } > 0
                if (inserted) {
                    added++
                    enqueueUpsert(connection, SyncEntityType.HISTORY, syncId)
                }
            }
            added to (rows.size - added)
        }
    }

    override suspend fun addImportedSearches(
        sourcePlatform: String,
        importId: String,
        rows: List<SearchImportRow>,
    ): Int = withDb { connection ->
        connection.transaction {
            var added = 0
            rows.forEach { row ->
                val search = row.search
                val trimmed = search.query.trim()
                if (trimmed.isEmpty()) return@forEach
                val id = UUID.nameUUIDFromBytes(
                    "$sourcePlatform-search|${search.sourceKey}".toByteArray(),
                ).toString()
                val inserted = connection.prepareStatement(
                    """
                    INSERT OR IGNORE INTO search_history(
                        id, query_key, query, searched_at, updated_at, source_import_id,
                        source_platform, source_result_uris, source_event_id, imported_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, id)
                    statement.setString(2, trimmed.lowercase())
                    statement.setString(3, trimmed)
                    statement.setLong(4, search.timestampMs.takeIf { it > 0 } ?: System.currentTimeMillis())
                    statement.setLong(5, System.currentTimeMillis())
                    statement.setString(6, importId)
                    statement.setString(7, sourcePlatform)
                    statement.setString(8, search.resultUris.takeIf { it.isNotEmpty() }?.joinToString("\n"))
                    statement.setString(9, search.sourceKey)
                    statement.setLong(10, System.currentTimeMillis())
                    statement.executeUpdate()
                } > 0
                if (inserted) {
                    added++
                    enqueueUpsert(connection, SyncEntityType.SEARCH_HISTORY, id)
                }
            }
            added
        }
    }

    private fun importRecord(connection: Connection, platform: String, sourceId: String, entity: String): String? =
        connection.prepareStatement(
            "SELECT local_id FROM import_records WHERE source_platform = ? AND source_id = ? AND entity_type = ?",
        ).use { statement ->
            statement.setString(1, platform)
            statement.setString(2, sourceId)
            statement.setString(3, entity)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
        }

    private fun playlistProvenance(connection: Connection, playlistId: String): ImportProvenance? =
        connection.prepareStatement(
            """
            SELECT source_platform, source_import_id, imported_at, original_source_identifier
            FROM playlists WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, playlistId)
            statement.executeQuery().use { rows ->
                if (!rows.next()) null else rows.provenance()
            }
        }

    private fun setPlaylistProvenance(
        connection: Connection,
        playlistId: String,
        platform: String,
        importId: String,
        importedAt: Long,
        originalId: String,
    ) {
        connection.prepareStatement(
            "UPDATE playlists SET source_platform = ?, source_import_id = ?, imported_at = ?, original_source_identifier = ? WHERE id = ?",
        ).use { statement ->
            statement.setString(1, platform)
            statement.setString(2, importId)
            statement.setLong(3, importedAt)
            statement.setString(4, originalId)
            statement.setString(5, playlistId)
            statement.executeUpdate()
        }
    }

    private fun applyProvenanceToPlaylist(connection: Connection, playlistId: String, provenance: ImportProvenance?) {
        connection.prepareStatement(
            "UPDATE playlists SET source_platform = ?, source_import_id = ?, imported_at = ?, original_source_identifier = ? WHERE id = ?",
        ).use { statement ->
            statement.setString(1, provenance?.sourcePlatform)
            statement.setString(2, provenance?.sourceImportId)
            if (provenance == null) statement.setNull(3, java.sql.Types.INTEGER) else statement.setLong(3, provenance.importedAt)
            statement.setString(4, provenance?.originalSourceIdentifier)
            statement.setString(5, playlistId)
            statement.executeUpdate()
        }
    }

    override suspend fun undoImport(importId: String): UndoImportResult = withDb { connection ->
        connection.transaction {
            var playlists = 0
            var tracks = 0
            var likes = 0
            var albums = 0
            var artists = 0
            var history = 0
            var searches = 0

            val entryIds = mutableSetOf<String>()
            // Local entry provenance works for this device; remote entry provenance makes undo
            // behave identically on a second device that pulled the imported playlist.
            connection.prepareStatement(
                "SELECT entry_id FROM playlist_tracks WHERE source_import_id = ?",
            ).use { statement ->
                statement.setString(1, importId)
                statement.executeQuery().use { rows -> while (rows.next()) entryIds += rows.getString(1) }
            }
            connection.prepareStatement(
                "SELECT local_id FROM import_records WHERE import_id = ? AND entity_type = 'playlist_entry'",
            ).use { statement ->
                statement.setString(1, importId)
                statement.executeQuery().use { rows -> while (rows.next()) entryIds += rows.getString(1) }
            }
            // Legacy rows imported before schema v5 have no per-entry provenance. They are
            // still owned by the import that created their playlist; entries added natively
            // afterwards have a later added_at and are retained.
            connection.prepareStatement(
                """
                SELECT pt.entry_id FROM playlist_tracks pt
                JOIN import_records ir ON ir.local_id = pt.playlist_id AND ir.entity_type = 'playlist'
                WHERE ir.import_id = ? AND pt.source_import_id IS NULL AND pt.added_at <= ir.imported_at
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, importId)
                statement.executeQuery().use { rows -> while (rows.next()) entryIds += rows.getString(1) }
            }
            val playlistsWithEntries = mutableSetOf<String>()
            entryIds.forEach { entryId ->
                val playlistId = connection.prepareStatement(
                    "SELECT playlist_id FROM playlist_tracks WHERE entry_id = ?",
                ).use { find ->
                    find.setString(1, entryId)
                    find.executeQuery().use { if (it.next()) it.getString(1) else null }
                } ?: return@forEach
                connection.prepareStatement("DELETE FROM playlist_tracks WHERE entry_id = ?").use { delete ->
                    delete.setString(1, entryId)
                    tracks += delete.executeUpdate()
                }
                playlistsWithEntries += playlistId
            }

            val importedPlaylistIds = mutableSetOf<String>()
            connection.prepareStatement(
                "SELECT local_id FROM import_records WHERE import_id = ? AND entity_type = 'playlist'",
            ).use { statement ->
                statement.setString(1, importId)
                statement.executeQuery().use { rows -> while (rows.next()) importedPlaylistIds += rows.getString(1) }
            }
            connection.prepareStatement(
                "SELECT id FROM playlists WHERE source_import_id = ?",
            ).use { statement ->
                statement.setString(1, importId)
                statement.executeQuery().use { rows -> while (rows.next()) importedPlaylistIds += rows.getString(1) }
            }
            importedPlaylistIds.forEach { playlistId ->
                        val rowPlaylistId = playlistId
                        val remaining = connection.prepareStatement(
                            "SELECT COUNT(*) FROM playlist_tracks WHERE playlist_id = ?",
                        ).use { count ->
                            count.setString(1, rowPlaylistId)
                            count.executeQuery().use { if (it.next()) it.getInt(1) else 0 }
                        }
                        if (remaining == 0) {
                            connection.prepareStatement("DELETE FROM playlist_tracks WHERE playlist_id = ?").use { delete ->
                                delete.setString(1, rowPlaylistId); delete.executeUpdate()
                            }
                            connection.prepareStatement("DELETE FROM playlist_plays WHERE playlist_id = ?").use { delete ->
                                delete.setString(1, rowPlaylistId); delete.executeUpdate()
                            }
                            connection.prepareStatement("DELETE FROM playlists WHERE id = ?").use { delete ->
                                delete.setString(1, rowPlaylistId); playlists += delete.executeUpdate()
                            }
                            enqueueDelete(connection, SyncEntityType.PLAYLIST, rowPlaylistId)
                        } else if (rowPlaylistId in playlistsWithEntries) {
                            touchPlaylist(connection, rowPlaylistId)
                            enqueueUpsert(connection, SyncEntityType.PLAYLIST, rowPlaylistId)
                        }
            }

            fun removeImported(entity: String, table: String, key: String, sync: SyncEntityType): Int {
                val legacyTimes = mutableMapOf<String, Long>()
                connection.prepareStatement(
                    "SELECT local_id, imported_at FROM import_records WHERE import_id = ? AND entity_type = ?",
                ).use { statement ->
                    statement.setString(1, importId)
                    statement.setString(2, entity)
                    statement.executeQuery().use { rows ->
                        while (rows.next()) legacyTimes[rows.getString(1)] = rows.getLong(2)
                    }
                }
                connection.prepareStatement(
                    "SELECT $key FROM $table WHERE source_import_id = ?",
                ).use { statement ->
                    statement.setString(1, importId)
                    statement.executeQuery().use { rows -> while (rows.next()) legacyTimes.putIfAbsent(rows.getString(1), 0L) }
                }
                var removed = 0
                legacyTimes.forEach { (id, recordTime) ->
                    val deleted = if (recordTime > 0) {
                        connection.prepareStatement(
                            "DELETE FROM $table WHERE $key = ? AND (source_import_id = ? OR (source_import_id IS NULL AND added_at <= ?))",
                        ).use { delete ->
                            delete.setString(1, id)
                            delete.setString(2, importId)
                            delete.setLong(3, recordTime)
                            delete.executeUpdate()
                        }
                    } else {
                        connection.prepareStatement("DELETE FROM $table WHERE $key = ? AND source_import_id = ?").use { delete ->
                            delete.setString(1, id)
                            delete.setString(2, importId)
                            delete.executeUpdate()
                        }
                    }
                    if (deleted > 0) {
                        removed += deleted
                        enqueueDelete(connection, sync, id)
                    }
                }
                return removed
            }
            likes = removeImported("liked_track", "liked_tracks", "track_id", SyncEntityType.LIKED_SONG)
            albums = removeImported("liked_album", "liked_albums", "id", SyncEntityType.LIKED_ALBUM)
            artists = removeImported("followed_artist", "followed_artists", "id", SyncEntityType.FOLLOWED_ARTIST)

            connection.prepareStatement("SELECT sync_id FROM history WHERE source_import_id = ?").use { statement ->
                statement.setString(1, importId)
                statement.executeQuery().use { rows ->
                    while (rows.next()) {
                        val syncId = rows.getString(1)
                        enqueueDelete(connection, SyncEntityType.HISTORY, syncId)
                    }
                }
            }
            history = connection.prepareStatement("DELETE FROM history WHERE source_import_id = ?").use { statement ->
                statement.setString(1, importId); statement.executeUpdate()
            }

            connection.prepareStatement("SELECT id FROM search_history WHERE source_import_id = ?").use { statement ->
                statement.setString(1, importId)
                statement.executeQuery().use { rows ->
                    while (rows.next()) enqueueDelete(connection, SyncEntityType.SEARCH_HISTORY, rows.getString(1))
                }
            }
            searches = connection.prepareStatement("DELETE FROM search_history WHERE source_import_id = ?").use { statement ->
                statement.setString(1, importId); statement.executeUpdate()
            }

            connection.prepareStatement("DELETE FROM import_records WHERE import_id = ?").use { statement ->
                statement.setString(1, importId); statement.executeUpdate()
            }
            connection.prepareStatement("UPDATE import_batches SET deleted_at = ? WHERE id = ?").use { statement ->
                statement.setLong(1, System.currentTimeMillis())
                statement.setString(2, importId)
                statement.executeUpdate()
            }
            enqueueDelete(connection, SyncEntityType.IMPORT_BATCH, importId)
            UndoImportResult(playlists, tracks, likes, albums, artists, history, searches)
        }
    }

    private fun recordImport(
        connection: Connection,
        platform: String,
        sourceId: String,
        entity: String,
        localId: String,
        importId: String,
        originalUri: String?,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO import_records(source_platform, source_id, entity_type, local_id, import_id, imported_at, original_uri)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(source_platform, source_id, entity_type) DO UPDATE SET
                local_id = excluded.local_id, import_id = excluded.import_id, imported_at = excluded.imported_at
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, platform)
            statement.setString(2, sourceId)
            statement.setString(3, entity)
            statement.setString(4, localId)
            statement.setString(5, importId)
            statement.setLong(6, System.currentTimeMillis())
            statement.setString(7, originalUri)
            statement.executeUpdate()
        }
    }

    // -------------------------------------------------------------------- sync

    override suspend fun deviceId(): String = withDb { connection ->
        syncValue(connection, "device_id") ?: UUID.randomUUID().toString().also {
            putSyncValue(connection, "device_id", it)
        }
    }

    override suspend fun activeAccountId(): String? = withDb { connection ->
        syncValue(connection, "account_id")
    }

    override suspend fun activeAccountEmail(): String? = withDb { connection ->
        syncValue(connection, "account_email")
    }

    override suspend fun associateAccount(accountId: String, email: String): Boolean = withDb { connection ->
        connection.transaction {
            val existing = syncValue(connection, "account_id")
            if (existing != null && existing != accountId) {
                false
            } else {
                putSyncValue(connection, "account_id", accountId)
                putSyncValue(connection, "account_email", email)
                connection.prepareStatement("UPDATE sync_outbox SET account_id = ? WHERE account_id IS NULL").use { statement ->
                    statement.setString(1, accountId)
                    statement.executeUpdate()
                }
                connection.prepareStatement("UPDATE sync_tombstones SET account_id = ? WHERE account_id IS NULL").use { statement ->
                    statement.setString(1, accountId)
                    statement.executeUpdate()
                }
                true
            }
        }
    }

    override suspend fun bootstrapAccount(accountId: String, includeSearchHistory: Boolean) = withDb { connection ->
        connection.transaction {
            val key = "bootstrap:$accountId"
            if (syncValue(connection, key) == "1") return@transaction
            val now = System.currentTimeMillis()
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT id FROM playlists").use { rows ->
                    while (rows.next()) enqueueUpsert(connection, SyncEntityType.PLAYLIST, rows.getString(1), now)
                }
            }
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT l.track_id FROM liked_tracks l JOIN tracks t ON t.id = l.track_id WHERE t.source NOT IN ('LOCAL','ARCHIVED')",
                ).use { rows ->
                    while (rows.next()) enqueueUpsert(connection, SyncEntityType.LIKED_SONG, rows.getString(1), now)
                }
            }
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT id FROM followed_artists").use { rows ->
                    while (rows.next()) enqueueUpsert(connection, SyncEntityType.FOLLOWED_ARTIST, rows.getString(1), now)
                }
            }
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT id FROM liked_albums").use { rows ->
                    while (rows.next()) enqueueUpsert(connection, SyncEntityType.LIKED_ALBUM, rows.getString(1), now)
                }
            }
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT id FROM import_batches WHERE deleted_at IS NULL").use { rows ->
                    while (rows.next()) enqueueUpsert(connection, SyncEntityType.IMPORT_BATCH, rows.getString(1), now)
                }
            }
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    """
                    SELECT h.sync_id FROM history h JOIN tracks t ON t.id = h.track_id
                    WHERE h.sync_id IS NOT NULL AND t.source NOT IN ('LOCAL','ARCHIVED')
                    """.trimIndent(),
                ).use { rows ->
                    while (rows.next()) enqueueUpsert(connection, SyncEntityType.HISTORY, rows.getString(1), now)
                }
            }
            if (includeSearchHistory) {
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT id FROM search_history").use { rows ->
                        while (rows.next()) enqueueUpsert(connection, SyncEntityType.SEARCH_HISTORY, rows.getString(1), now)
                    }
                }
            }
            // Only push preferences when this device has an explicit local choice.
            // Otherwise a first login would overwrite the account's cloud defaults
            // with this install's defaults before the first pull.
            if (syncValue(connection, "preferences_set") == "1") {
                enqueueUpsert(connection, SyncEntityType.PREFERENCES, PREFERENCES_ID, now)
            }
            putSyncValue(connection, key, "1")
        }
        Unit
    }

    override suspend fun pendingChanges(limit: Int, excludeSearchHistory: Boolean): List<PendingChange> = withDb { connection ->
        connection.prepareStatement(
            """
            SELECT entity_type, entity_id, operation, changed_at, account_id FROM sync_outbox
            WHERE (? = 0 OR entity_type <> 'search_history')
            ORDER BY changed_at ASC, seq ASC LIMIT ?
            """.trimIndent(),
        ).use { statement ->
            statement.setInt(1, if (excludeSearchHistory) 1 else 0)
            statement.setInt(2, limit)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        val type = SyncEntityType.fromWire(rows.getString("entity_type")) ?: continue
                        val operation = if (rows.getString("operation") == SyncOperation.DELETE.wire) {
                            SyncOperation.DELETE
                        } else {
                            SyncOperation.UPSERT
                        }
                        add(PendingChange(type, rows.getString("entity_id"), operation, rows.getLong("changed_at"), rows.getString("account_id")))
                    }
                }
            }
        }
    }

    override suspend fun clearPending(change: PendingChange) = withDb { connection ->
        connection.prepareStatement(
            "DELETE FROM sync_outbox WHERE entity_type = ? AND entity_id = ? AND changed_at = ?",
        ).use { statement ->
            statement.setString(1, change.entityType.wire)
            statement.setString(2, change.entityId)
            statement.setLong(3, change.changedAt)
            statement.executeUpdate()
        }
        Unit
    }

    override suspend fun pendingTombstones(limit: Int): List<SyncTombstone> = withDb { connection ->
        connection.prepareStatement(
            "SELECT entity_type, entity_id, changed_at, account_id FROM sync_tombstones ORDER BY changed_at ASC LIMIT ?",
        ).use { statement ->
            statement.setInt(1, limit)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        val type = SyncEntityType.fromWire(rows.getString("entity_type")) ?: continue
                        add(SyncTombstone(type, rows.getString("entity_id"), rows.getLong("changed_at"), rows.getString("account_id")))
                    }
                }
            }
        }
    }

    override suspend fun clearTombstone(tombstone: SyncTombstone) = withDb { connection ->
        connection.prepareStatement(
            "DELETE FROM sync_tombstones WHERE entity_type = ? AND entity_id = ? AND changed_at = ?",
        ).use { statement ->
            statement.setString(1, tombstone.entityType.wire)
            statement.setString(2, tombstone.entityId)
            statement.setLong(3, tombstone.changedAt)
            statement.executeUpdate()
        }
        Unit
    }

    override suspend fun pendingFor(entityType: SyncEntityType, entityId: String): Boolean = withDb { connection ->
        connection.prepareStatement(
            "SELECT 1 FROM sync_outbox WHERE entity_type = ? AND entity_id = ? UNION SELECT 1 FROM sync_tombstones WHERE entity_type = ? AND entity_id = ?",
        ).use { statement ->
            statement.setString(1, entityType.wire)
            statement.setString(2, entityId)
            statement.setString(3, entityType.wire)
            statement.setString(4, entityId)
            statement.executeQuery().use { it.next() }
        }
    }

    override suspend fun pendingCount(excludeSearchHistory: Boolean): Int = withDb { connection ->
        connection.prepareStatement(
            "SELECT COUNT(*) FROM sync_outbox WHERE (? = 0 OR entity_type <> 'search_history')",
        ).use { statement ->
            statement.setInt(1, if (excludeSearchHistory) 1 else 0)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else 0 }
        }
    }

    override suspend fun playlistForSync(playlistId: String): SyncedPlaylist? = withDb { connection ->
        val playlist = connection.prepareStatement(
            """
            SELECT p.*, (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlist_id = p.id) AS track_count
            FROM playlists p WHERE p.id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, playlistId)
            statement.executeQuery().use { rows -> if (rows.next()) rows.toPlaylist() else null }
        } ?: return@withDb null
        val entries = connection.prepareStatement(
            """
            SELECT t.*, pt.entry_id AS entry_id, pt.position AS position, pt.added_at AS added_at,
                   pt.source_platform AS source_platform, pt.source_import_id AS source_import_id,
                   pt.imported_at AS imported_at
            FROM tracks t JOIN playlist_tracks pt ON pt.track_id = t.id
            WHERE pt.playlist_id = ? AND t.source NOT IN ('LOCAL','ARCHIVED')
            ORDER BY pt.position ASC, pt.entry_id ASC
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, playlistId)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) add(
                        PlaylistTrackEntry(
                            track = rows.toTrack(),
                            addedAt = rows.getLong("added_at"),
                            entryId = rows.getString("entry_id"),
                            position = rows.getInt("position"),
                            sourcePlatform = rows.getString("source_platform"),
                            sourceImportId = rows.getString("source_import_id"),
                            importedAt = rows.getObject("imported_at")?.let { (it as Number).toLong() },
                        ),
                    )
                }
            }
        }
        SyncedPlaylist(playlist, entries, provenance = playlistProvenance(connection, playlistId))
    }

    override suspend fun likeForSync(trackId: String): SyncedLike? = withDb { connection ->
        connection.prepareStatement(
            """
            SELECT t.*, l.added_at AS added_at, l.source_platform, l.source_import_id, l.imported_at,
                   l.original_source_identifier
            FROM liked_tracks l JOIN tracks t ON t.id = l.track_id
            WHERE l.track_id = ? AND t.source NOT IN ('LOCAL','ARCHIVED')
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, trackId)
            statement.executeQuery().use { rows ->
                if (rows.next()) SyncedLike(
                    rows.toTrack(),
                    rows.getLong("added_at"),
                    provenance = rows.provenance(),
                ) else null
            }
        }
    }

    override suspend fun albumForSync(albumId: String): SyncedAlbum? = withDb { connection ->
        connection.prepareStatement("SELECT * FROM liked_albums WHERE id = ?").use { statement ->
            statement.setString(1, albumId)
            statement.executeQuery().use { rows ->
                if (rows.next()) SyncedAlbum(
                    album = rows.toAlbum(),
                    addedAt = rows.getLong("added_at"),
                    provenance = rows.provenance(),
                ) else null
            }
        }
    }

    override suspend fun importBatchForSync(importId: String): SyncedImportBatch? = withDb { connection ->
        connection.prepareStatement("SELECT * FROM import_batches WHERE id = ? AND deleted_at IS NULL").use { statement ->
            statement.setString(1, importId)
            statement.executeQuery().use { rows ->
                if (rows.next()) SyncedImportBatch(
                    id = rows.getString("id"),
                    sourcePlatform = rows.getString("source_platform"),
                    fingerprint = rows.getString("fingerprint"),
                    importedAt = rows.getLong("imported_at"),
                    // Local summaries may include bounded taste/Wrapped notes. Cloud metadata
                    // only needs the import summary line, never profile-derived text.
                    summary = (rows.getString("summary") ?: "").substringBefore('\n'),
                    playlists = rows.getInt("playlists_count"),
                    tracks = rows.getInt("tracks_count"),
                    likes = rows.getInt("likes_count"),
                    albums = rows.getInt("albums_count"),
                    artists = rows.getInt("artists_count"),
                    history = rows.getInt("history_count"),
                    searches = rows.getInt("searches_count"),
                ) else null
            }
        }
    }

    override suspend fun followForSync(artistId: String): SyncedFollow? = withDb { connection ->
        connection.prepareStatement("SELECT * FROM followed_artists WHERE id = ?").use { statement ->
            statement.setString(1, artistId)
            statement.executeQuery().use { rows ->
                if (rows.next()) {
                    SyncedFollow(
                        Artist(
                            id = rows.getString("id"),
                            name = rows.getString("name"),
                            imageUrl = rows.getString("image_url"),
                            genre = rows.getString("genre"),
                        ),
                        rows.getLong("added_at"),
                        provenance = rows.provenance(),
                    )
                } else {
                    null
                }
            }
        }
    }

    override suspend fun historyForSync(syncId: String): SyncedHistory? = withDb { connection ->
        connection.prepareStatement(
            """
            SELECT t.*, h.sync_id AS sync_id, h.played_at AS played_at, h.played_ms AS played_ms,
                   h.completed AS completed, h.skipped AS skipped, h.source_platform,
                   h.source_import_id, h.imported_at, h.source_event_id, h.source_uri AS original_source_identifier
            FROM history h JOIN tracks t ON t.id = h.track_id
            WHERE h.sync_id = ? AND t.source NOT IN ('LOCAL','ARCHIVED')
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, syncId)
            statement.executeQuery().use { rows ->
                if (rows.next()) {
                    SyncedHistory(
                        syncId = syncId,
                        track = rows.toTrack(),
                        playedAt = rows.getLong("played_at"),
                        playedMs = rows.getLong("played_ms"),
                        completed = rows.getInt("completed") == 1,
                        skipped = rows.getInt("skipped") == 1,
                        provenance = rows.provenance(),
                    )
                } else {
                    null
                }
            }
        }
    }

    override suspend fun searchForSync(id: String): SyncedSearch? = withDb { connection ->
        connection.prepareStatement("SELECT *, NULL AS original_source_identifier FROM search_history WHERE id = ?").use { statement ->
            statement.setString(1, id)
            statement.executeQuery().use { rows ->
                if (rows.next()) {
                    SyncedSearch(
                        id = rows.getString("id"),
                        queryKey = rows.getString("query_key"),
                        query = rows.getString("query"),
                        searchedAt = rows.getLong("searched_at"),
                        provenance = rows.provenance(),
                    )
                } else {
                    null
                }
            }
        }
    }

    override suspend fun preferencesForSync(): SyncedPreferences = withDb { connection ->
        SyncedPreferences(
            market = settingValue(connection, "market") ?: "IN",
            autoplayEnabled = settingValue(connection, "autoplayEnabled")?.toBooleanStrictOrNull() ?: true,
            syncSearchHistory = settingValue(connection, "syncSearchHistory")?.toBooleanStrictOrNull() ?: false,
            updatedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun applyRemotePlaylist(sync: SyncedPlaylist) = withDb { connection ->
        connection.transaction {
            val now = System.currentTimeMillis()
            connection.prepareStatement(
                """
                INSERT INTO playlists(id, name, description, created_at, updated_at) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET name = excluded.name, description = excluded.description, updated_at = excluded.updated_at
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, sync.playlist.id)
                statement.setString(2, sync.playlist.name)
                statement.setString(3, sync.playlist.description)
                statement.setLong(4, sync.playlist.createdAt.takeIf { it > 0 } ?: now)
                statement.setLong(5, now)
                statement.executeUpdate()
            }
            sync.entries.forEach { entry -> upsertTrack(connection, entry.track.copy(localPath = null)) }

            // Local-only files stay on this device. All cloud-owned entries are replaced
            // exactly, preserving duplicate track entries and their explicit positions.
            val localOnly = connection.prepareStatement(
                """
                SELECT t.*, pt.entry_id AS entry_id, pt.position AS position, pt.added_at AS added_at,
                       pt.source_platform AS source_platform, pt.source_import_id AS source_import_id,
                       pt.imported_at AS imported_at
                FROM playlist_tracks pt JOIN tracks t ON t.id = pt.track_id
                WHERE pt.playlist_id = ? AND t.source IN ('LOCAL','ARCHIVED')
                ORDER BY pt.position ASC, pt.entry_id ASC
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, sync.playlist.id)
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) add(
                            PlaylistTrackEntry(
                                track = rows.toTrack(),
                                addedAt = rows.getLong("added_at"),
                                entryId = rows.getString("entry_id"),
                                position = rows.getInt("position"),
                                sourcePlatform = rows.getString("source_platform"),
                                sourceImportId = rows.getString("source_import_id"),
                                importedAt = rows.getObject("imported_at")?.let { (it as Number).toLong() },
                            ),
                        )
                    }
                }
            }
            connection.prepareStatement(
                "DELETE FROM playlist_tracks WHERE playlist_id = ? AND track_id NOT IN (SELECT id FROM tracks WHERE source IN ('LOCAL','ARCHIVED'))",
            ).use { statement ->
                statement.setString(1, sync.playlist.id)
                statement.executeUpdate()
            }
            connection.prepareStatement(
                """
                INSERT INTO playlist_tracks(
                    entry_id, playlist_id, track_id, position, added_at,
                    source_platform, source_import_id, imported_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                sync.entries.forEachIndexed { index, entry ->
                    statement.setString(1, entry.entryId.ifBlank { UUID.randomUUID().toString() })
                    statement.setString(2, sync.playlist.id)
                    statement.setString(3, entry.track.id)
                    statement.setInt(4, index + 1)
                    statement.setLong(5, entry.addedAt.takeIf { it > 0 } ?: now)
                    statement.setString(6, entry.sourcePlatform)
                    statement.setString(7, entry.sourceImportId)
                    if (entry.importedAt == null) statement.setNull(8, java.sql.Types.INTEGER) else statement.setLong(8, entry.importedAt)
                    statement.executeUpdate()
                }
            }
            // Local-only files stay after the cloud rows and keep their entry ids.
            localOnly.forEachIndexed { index, entry ->
                connection.prepareStatement("UPDATE playlist_tracks SET position = ? WHERE playlist_id = ? AND entry_id = ?").use { statement ->
                    statement.setInt(1, sync.entries.size + index + 1)
                    statement.setString(2, sync.playlist.id)
                    statement.setString(3, entry.entryId)
                    statement.executeUpdate()
                }
            }
            applyProvenanceToPlaylist(connection, sync.playlist.id, sync.provenance)
        }
        Unit
    }

    override suspend fun applyRemotePlaylistDelete(playlistId: String) = withDb { connection ->
        connection.transaction {
            connection.prepareStatement("DELETE FROM playlist_tracks WHERE playlist_id = ?").use { statement ->
                statement.setString(1, playlistId)
                statement.executeUpdate()
            }
            connection.prepareStatement("DELETE FROM playlist_plays WHERE playlist_id = ?").use { statement ->
                statement.setString(1, playlistId)
                statement.executeUpdate()
            }
            connection.prepareStatement("DELETE FROM playlists WHERE id = ?").use { statement ->
                statement.setString(1, playlistId)
                statement.executeUpdate()
            }
        }
        Unit
    }

    override suspend fun applyRemoteLike(sync: SyncedLike) = withDb { connection ->
        connection.transaction {
            if (sync.deletedAt != null) {
                connection.prepareStatement("DELETE FROM liked_tracks WHERE track_id = ?").use { statement ->
                    statement.setString(1, sync.track.id)
                    statement.executeUpdate()
                }
            } else {
                upsertTrack(connection, sync.track.copy(localPath = null, streamUrl = null))
                connection.prepareStatement(
                    """
                    INSERT INTO liked_tracks(
                        track_id, added_at, source_platform, source_import_id, imported_at, original_source_identifier
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT(track_id) DO UPDATE SET added_at = MIN(liked_tracks.added_at, excluded.added_at),
                        source_platform = excluded.source_platform, source_import_id = excluded.source_import_id,
                        imported_at = excluded.imported_at, original_source_identifier = excluded.original_source_identifier
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, sync.track.id)
                    statement.setLong(2, sync.addedAt)
                    statement.setString(3, sync.provenance?.sourcePlatform)
                    statement.setString(4, sync.provenance?.sourceImportId)
                    if (sync.provenance == null) statement.setNull(5, java.sql.Types.INTEGER) else statement.setLong(5, sync.provenance.importedAt)
                    statement.setString(6, sync.provenance?.originalSourceIdentifier)
                    statement.executeUpdate()
                }
            }
        }
        Unit
    }

    override suspend fun applyRemoteFollow(sync: SyncedFollow) = withDb { connection ->
        connection.transaction {
            if (sync.deletedAt != null) {
                connection.prepareStatement("DELETE FROM followed_artists WHERE id = ?").use { statement ->
                    statement.setString(1, sync.artist.id)
                    statement.executeUpdate()
                }
            } else {
                connection.prepareStatement(
                    """
                    INSERT INTO followed_artists(
                        id, name, image_url, genre, added_at,
                        source_platform, source_import_id, imported_at, original_source_identifier
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET name = excluded.name,
                        image_url = COALESCE(excluded.image_url, followed_artists.image_url),
                        genre = COALESCE(excluded.genre, followed_artists.genre),
                        source_platform = excluded.source_platform, source_import_id = excluded.source_import_id,
                        imported_at = excluded.imported_at, original_source_identifier = excluded.original_source_identifier
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, sync.artist.id)
                    statement.setString(2, sync.artist.name)
                    statement.setString(3, sync.artist.imageUrl)
                    statement.setString(4, sync.artist.genre)
                    statement.setLong(5, sync.addedAt)
                    statement.setString(6, sync.provenance?.sourcePlatform)
                    statement.setString(7, sync.provenance?.sourceImportId)
                    if (sync.provenance == null) statement.setNull(8, java.sql.Types.INTEGER) else statement.setLong(8, sync.provenance.importedAt)
                    statement.setString(9, sync.provenance?.originalSourceIdentifier)
                    statement.executeUpdate()
                }
            }
        }
        Unit
    }

    override suspend fun applyRemoteHistory(sync: SyncedHistory) = withDb { connection ->
        connection.transaction {
            if (sync.deletedAt != null) {
                connection.prepareStatement("DELETE FROM history WHERE sync_id = ?").use { statement ->
                    statement.setString(1, sync.syncId)
                    statement.executeUpdate()
                }
            } else {
                upsertTrack(connection, sync.track.copy(localPath = null, streamUrl = null))
                connection.prepareStatement(
                    """
                    INSERT INTO history(
                        track_id, played_at, played_ms, completed, skipped, sync_id,
                        source_platform, source_import_id, imported_at, source_event_id, source_uri
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(sync_id) DO UPDATE SET track_id = excluded.track_id, played_at = excluded.played_at,
                        played_ms = excluded.played_ms, completed = excluded.completed, skipped = excluded.skipped,
                        source_platform = excluded.source_platform, source_import_id = excluded.source_import_id,
                        imported_at = excluded.imported_at, source_event_id = excluded.source_event_id,
                        source_uri = excluded.source_uri
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, sync.track.id)
                    statement.setLong(2, sync.playedAt)
                    statement.setLong(3, sync.playedMs)
                    statement.setInt(4, if (sync.completed) 1 else 0)
                    statement.setInt(5, if (sync.skipped) 1 else 0)
                    statement.setString(6, sync.syncId)
                    statement.setString(7, sync.provenance?.sourcePlatform)
                    statement.setString(8, sync.provenance?.sourceImportId)
                    if (sync.provenance == null) statement.setNull(9, java.sql.Types.INTEGER) else statement.setLong(9, sync.provenance.importedAt)
                    statement.setString(10, sync.provenance?.sourceEventId)
                    statement.setString(11, sync.provenance?.originalSourceIdentifier)
                    statement.executeUpdate()
                }
            }
            connection.prepareStatement(
                """
                DELETE FROM history WHERE source_import_id IS NULL AND id NOT IN
                    (SELECT id FROM history WHERE source_import_id IS NULL ORDER BY played_at DESC LIMIT 5000)
                """.trimIndent(),
            ).use { it.executeUpdate() }
        }
        Unit
    }

    override suspend fun applyRemoteSearch(sync: SyncedSearch) = withDb { connection ->
        connection.transaction {
            if (sync.deletedAt != null) {
                connection.prepareStatement("DELETE FROM search_history WHERE query_key = ? OR id = ?").use { statement ->
                    statement.setString(1, sync.queryKey)
                    statement.setString(2, sync.id)
                    statement.executeUpdate()
                }
            } else {
                val existing = connection.prepareStatement("SELECT id FROM search_history WHERE query_key = ?").use { statement ->
                    statement.setString(1, sync.queryKey)
                    statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
                }
                if (existing != null) {
                    connection.prepareStatement(
                        """
                        UPDATE search_history SET query = ?, searched_at = ?, updated_at = ?,
                            source_platform = ?, source_import_id = ?, source_event_id = ?, imported_at = ?
                        WHERE id = ?
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setString(1, sync.query)
                        statement.setLong(2, sync.searchedAt)
                        statement.setLong(3, System.currentTimeMillis())
                        statement.setString(4, sync.provenance?.sourcePlatform)
                        statement.setString(5, sync.provenance?.sourceImportId)
                        statement.setString(6, sync.provenance?.sourceEventId)
                        if (sync.provenance == null) statement.setNull(7, java.sql.Types.INTEGER) else statement.setLong(7, sync.provenance.importedAt)
                        statement.setString(8, existing)
                        statement.executeUpdate()
                    }
                } else {
                    connection.prepareStatement(
                        """
                        INSERT INTO search_history(
                            id, query_key, query, searched_at, updated_at,
                            source_platform, source_import_id, source_event_id, imported_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setString(1, sync.id)
                        statement.setString(2, sync.queryKey)
                        statement.setString(3, sync.query)
                        statement.setLong(4, sync.searchedAt)
                        statement.setLong(5, System.currentTimeMillis())
                        statement.setString(6, sync.provenance?.sourcePlatform)
                        statement.setString(7, sync.provenance?.sourceImportId)
                        statement.setString(8, sync.provenance?.sourceEventId)
                        if (sync.provenance == null) statement.setNull(9, java.sql.Types.INTEGER) else statement.setLong(9, sync.provenance.importedAt)
                        statement.executeUpdate()
                    }
                }
            }
        }
        Unit
    }

    override suspend fun applyRemoteAlbum(sync: SyncedAlbum) = withDb { connection ->
        connection.transaction {
            if (sync.deletedAt != null) {
                connection.prepareStatement("DELETE FROM liked_albums WHERE id = ?").use { statement ->
                    statement.setString(1, sync.album.id)
                    statement.executeUpdate()
                }
            } else {
                connection.prepareStatement(
                    """
                    INSERT INTO liked_albums(
                        id, title, artist, artwork_url, year, track_count, genre, artist_id, added_at,
                        source_platform, source_import_id, imported_at, original_source_identifier
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET title = excluded.title, artist = excluded.artist,
                        artwork_url = COALESCE(excluded.artwork_url, liked_albums.artwork_url),
                        year = excluded.year, track_count = excluded.track_count, genre = excluded.genre,
                        artist_id = excluded.artist_id, added_at = MIN(liked_albums.added_at, excluded.added_at),
                        source_platform = excluded.source_platform, source_import_id = excluded.source_import_id,
                        imported_at = excluded.imported_at, original_source_identifier = excluded.original_source_identifier
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, sync.album.id)
                    statement.setString(2, sync.album.title)
                    statement.setString(3, sync.album.artist)
                    statement.setString(4, sync.album.artworkUrl)
                    statement.setObject(5, sync.album.year)
                    statement.setInt(6, sync.album.trackCount)
                    statement.setString(7, sync.album.genre)
                    statement.setString(8, sync.album.artistId)
                    statement.setLong(9, sync.addedAt)
                    statement.setString(10, sync.provenance?.sourcePlatform)
                    statement.setString(11, sync.provenance?.sourceImportId)
                    if (sync.provenance == null) statement.setNull(12, java.sql.Types.INTEGER) else statement.setLong(12, sync.provenance.importedAt)
                    statement.setString(13, sync.provenance?.originalSourceIdentifier)
                    statement.executeUpdate()
                }
            }
        }
        Unit
    }

    override suspend fun applyRemoteImportBatch(sync: SyncedImportBatch) = withDb { connection ->
        connection.prepareStatement(
            """
            INSERT INTO import_batches(
                id, source_platform, fingerprint, imported_at, summary,
                playlists_count, tracks_count, likes_count, albums_count,
                artists_count, history_count, searches_count, deleted_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET source_platform = excluded.source_platform,
                fingerprint = excluded.fingerprint, imported_at = excluded.imported_at,
                summary = excluded.summary, playlists_count = excluded.playlists_count,
                tracks_count = excluded.tracks_count, likes_count = excluded.likes_count,
                albums_count = excluded.albums_count, artists_count = excluded.artists_count,
                history_count = excluded.history_count, searches_count = excluded.searches_count,
                deleted_at = excluded.deleted_at
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, sync.id)
            statement.setString(2, sync.sourcePlatform)
            statement.setString(3, sync.fingerprint)
            statement.setLong(4, sync.importedAt)
            statement.setString(5, sync.summary)
            statement.setInt(6, sync.playlists)
            statement.setInt(7, sync.tracks)
            statement.setInt(8, sync.likes)
            statement.setInt(9, sync.albums)
            statement.setInt(10, sync.artists)
            statement.setInt(11, sync.history)
            statement.setInt(12, sync.searches)
            if (sync.deletedAt == null) statement.setNull(13, java.sql.Types.INTEGER) else statement.setLong(13, sync.deletedAt)
            statement.executeUpdate()
        }
        Unit
    }

    override suspend fun applyRemotePreferences(preferences: SyncedPreferences) = withDb { connection ->
        connection.transaction {
            fun put(key: String, value: String) {
                connection.prepareStatement(
                    "INSERT INTO settings(key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                ).use { statement ->
                    statement.setString(1, key)
                    statement.setString(2, value)
                    statement.executeUpdate()
                }
            }
            put("market", preferences.market)
            put("autoplayEnabled", preferences.autoplayEnabled.toString())
            put("syncSearchHistory", preferences.syncSearchHistory.toString())
        }
        Unit
    }

    override suspend fun syncCursor(key: String): String? = withDb { connection ->
        connection.prepareStatement("SELECT value FROM sync_cursors WHERE key = ?").use { statement ->
            statement.setString(1, key)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
        }
    }

    override suspend fun setSyncCursor(key: String, value: String) = withDb { connection ->
        connection.prepareStatement(
            "INSERT INTO sync_cursors(key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
        ).use { statement ->
            statement.setString(1, key)
            statement.setString(2, value)
            statement.executeUpdate()
        }
        Unit
    }

    override suspend fun markSyncComplete(atMillis: Long, pullStartedAt: Long) = withDb { connection ->
        putSyncValue(connection, "last_synced_at", atMillis.toString())
        putSyncValue(connection, "last_pull_at", pullStartedAt.toString())
        Unit
    }

    override suspend fun lastSyncedAt(): Long? = withDb { connection ->
        syncValue(connection, "last_synced_at")?.toLongOrNull()
    }

    override suspend fun lastPullAt(): Long? = withDb { connection ->
        syncValue(connection, "last_pull_at")?.toLongOrNull()
    }

    override suspend fun upsertCloudTracks(tracks: List<Track>) = withDb { connection ->
        connection.transaction {
            tracks.forEach { upsertTrack(connection, it.copy(localPath = null, streamUrl = null)) }
        }
        Unit
    }

    override suspend fun cloudTracks(ids: List<String>): Map<String, Track> = withDb { connection ->
        if (ids.isEmpty()) return@withDb emptyMap()
        val result = mutableMapOf<String, Track>()
        ids.distinct().chunked(300).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            connection.prepareStatement("SELECT * FROM tracks WHERE id IN ($placeholders)").use { statement ->
                chunk.forEachIndexed { index, id -> statement.setString(index + 1, id) }
                statement.executeQuery().use { rows ->
                    while (rows.next()) {
                        val track = rows.toTrack()
                        result[track.id] = track
                    }
                }
            }
        }
        result
    }

    private fun syncValue(connection: Connection, key: String): String? =
        connection.prepareStatement("SELECT value FROM sync_state WHERE key = ?").use { statement ->
            statement.setString(1, key)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
        }

    private fun putSyncValue(connection: Connection, key: String, value: String) {
        connection.prepareStatement(
            "INSERT INTO sync_state(key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
        ).use { statement ->
            statement.setString(1, key)
            statement.setString(2, value)
            statement.executeUpdate()
        }
    }

    private fun settingValue(connection: Connection, key: String): String? =
        connection.prepareStatement("SELECT value FROM settings WHERE key = ?").use { statement ->
            statement.setString(1, key)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
        }

    private fun activeAccount(connection: Connection): String? = syncValue(connection, "account_id")

    private fun enqueueUpsert(
        connection: Connection,
        entityType: SyncEntityType,
        entityId: String,
        changedAt: Long = System.currentTimeMillis(),
    ) {
        val accountId = activeAccount(connection)
        connection.prepareStatement(
            """
            INSERT INTO sync_outbox(entity_type, entity_id, operation, changed_at, account_id) VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(entity_type, entity_id) DO UPDATE SET
                operation = excluded.operation, changed_at = excluded.changed_at,
                account_id = COALESCE(excluded.account_id, sync_outbox.account_id)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, entityType.wire)
            statement.setString(2, entityId)
            statement.setString(3, SyncOperation.UPSERT.wire)
            statement.setLong(4, changedAt)
            statement.setString(5, accountId)
            statement.executeUpdate()
        }
        connection.prepareStatement("DELETE FROM sync_tombstones WHERE entity_type = ? AND entity_id = ?").use { statement ->
            statement.setString(1, entityType.wire)
            statement.setString(2, entityId)
            statement.executeUpdate()
        }
    }

    private fun enqueueDelete(connection: Connection, entityType: SyncEntityType, entityId: String) {
        val accountId = activeAccount(connection)
        connection.prepareStatement("DELETE FROM sync_outbox WHERE entity_type = ? AND entity_id = ?").use { statement ->
            statement.setString(1, entityType.wire)
            statement.setString(2, entityId)
            statement.executeUpdate()
        }
        connection.prepareStatement(
            """
            INSERT INTO sync_tombstones(entity_type, entity_id, changed_at, account_id) VALUES (?, ?, ?, ?)
            ON CONFLICT(entity_type, entity_id) DO UPDATE SET
                changed_at = excluded.changed_at, account_id = COALESCE(excluded.account_id, sync_tombstones.account_id)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, entityType.wire)
            statement.setString(2, entityId)
            statement.setLong(3, System.currentTimeMillis())
            statement.setString(4, accountId)
            statement.executeUpdate()
        }
    }

    private companion object {
        const val PREFERENCES_ID = "preferences"
        val SYNCED_SETTINGS = setOf("market", "autoplayEnabled", "syncSearchHistory")
    }

    // ------------------------------------------------------------------ helpers

    private fun upsertTrack(connection: Connection, track: Track) {
        connection.prepareStatement(
            """
            INSERT INTO tracks (id, title, artist, album, duration_ms, artwork_url, stream_url, source, artist_id, album_id, year, track_number, genre, explicit, local_path)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                title = excluded.title,
                artist = excluded.artist,
                album = excluded.album,
                duration_ms = excluded.duration_ms,
                artwork_url = COALESCE(excluded.artwork_url, tracks.artwork_url),
                stream_url = COALESCE(excluded.stream_url, tracks.stream_url),
                source = excluded.source,
                artist_id = COALESCE(excluded.artist_id, tracks.artist_id),
                album_id = COALESCE(excluded.album_id, tracks.album_id),
                year = COALESCE(excluded.year, tracks.year),
                track_number = COALESCE(excluded.track_number, tracks.track_number),
                genre = COALESCE(excluded.genre, tracks.genre),
                explicit = excluded.explicit,
                local_path = COALESCE(excluded.local_path, tracks.local_path)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, track.id)
            statement.setString(2, track.title)
            statement.setString(3, track.artist)
            statement.setString(4, track.album)
            statement.setLong(5, track.durationMs)
            statement.setString(6, track.artworkUrl)
            statement.setString(7, track.streamUrl)
            statement.setString(8, track.source.name)
            statement.setString(9, track.artistId)
            statement.setString(10, track.albumId)
            statement.setObject(11, track.year)
            statement.setObject(12, track.trackNumber)
            statement.setString(13, track.genre)
            statement.setInt(14, if (track.explicit) 1 else 0)
            statement.setString(15, track.localPath)
            statement.executeUpdate()
        }
    }

    private fun ResultSet.mapTracks(): List<Track> = buildList {
        while (next()) add(toTrack())
    }

    private fun ResultSet.toTrack(): Track = Track(
        id = getString("id"),
        title = getString("title"),
        artist = getString("artist"),
        album = getString("album"),
        durationMs = getLong("duration_ms"),
        artworkUrl = getString("artwork_url"),
        streamUrl = getString("stream_url"),
        source = runCatching { MusicSourceId.valueOf(getString("source")) }.getOrDefault(MusicSourceId.ARCHIVED),
        artistId = getString("artist_id"),
        albumId = getString("album_id"),
        year = getObject("year")?.let { (it as Number).toInt() },
        trackNumber = getObject("track_number")?.let { (it as Number).toInt() },
        genre = getString("genre"),
        explicit = getInt("explicit") == 1,
        localPath = getString("local_path"),
    )

    private fun ResultSet.mapAlbums(): List<Album> = buildList {
        while (next()) add(toAlbum())
    }

    private fun ResultSet.toAlbum(): Album = Album(
        id = getString("id"),
        title = getString("title"),
        artist = getString("artist"),
        artworkUrl = getString("artwork_url"),
        year = getObject("year")?.let { (it as Number).toInt() },
        trackCount = getInt("track_count"),
        genre = getString("genre"),
        artistId = getString("artist_id"),
    )

    private fun ResultSet.provenance(): ImportProvenance? {
        val platform = getString("source_platform")?.takeIf { it.isNotBlank() } ?: return null
        val importId = getString("source_import_id")?.takeIf { it.isNotBlank() } ?: return null
        val importedAt = getObject("imported_at")?.let { (it as? Number)?.toLong() }
            ?: System.currentTimeMillis()
        return ImportProvenance(
            sourcePlatform = platform,
            sourceImportId = importId,
            importedAt = importedAt,
            sourceEventId = runCatching { getString("source_event_id") }.getOrNull(),
            originalSourceIdentifier = runCatching { getString("original_source_identifier") }.getOrNull(),
        )
    }

    private fun ResultSet.toPlaylist(): Playlist = Playlist(
        id = getString("id"),
        name = getString("name"),
        description = getString("description") ?: "",
        trackCount = getInt("track_count"),
        createdAt = getLong("created_at"),
        artworkUrl = runCatching { getString("artwork_url") }.getOrNull(),
    )

    private fun <T> Connection.transaction(block: () -> T): T {
        val previousAutoCommit = autoCommit
        autoCommit = false
        return try {
            val result = block()
            commit()
            result
        } catch (error: Throwable) {
            runCatching { rollback() }
            throw error
        } finally {
            autoCommit = previousAutoCommit
        }
    }
}
