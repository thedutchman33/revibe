package com.revibe.data

import com.revibe.library.LibraryStore
import com.revibe.model.Album
import com.revibe.model.Artist
import com.revibe.model.CachedLyrics
import com.revibe.model.MusicSourceId
import com.revibe.model.Playlist
import com.revibe.model.Track
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
class JdbcLibraryStore(dbFile: File, private val io: CoroutineDispatcher) : LibraryStore {

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
        createSchema()
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
                    playlist_id TEXT NOT NULL,
                    track_id TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    added_at INTEGER NOT NULL,
                    PRIMARY KEY (playlist_id, track_id)
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    track_id TEXT NOT NULL,
                    played_at INTEGER NOT NULL
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
            statement.execute("CREATE INDEX IF NOT EXISTS history_played_at ON history(played_at DESC)")
            statement.execute("CREATE INDEX IF NOT EXISTS playlist_position ON playlist_tracks(playlist_id, position)")
            statement.execute("CREATE INDEX IF NOT EXISTS tracks_local ON tracks(local_path)")
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
            connection.prepareStatement("INSERT OR IGNORE INTO liked_tracks(track_id, added_at) VALUES(?, ?)").use { statement ->
                statement.setString(1, track.id)
                statement.setLong(2, System.currentTimeMillis())
                statement.executeUpdate()
            }
        }
        Unit
    }

    override suspend fun removeLikedTrack(trackId: String) = withDb { connection ->
        connection.prepareStatement("DELETE FROM liked_tracks WHERE track_id = ?").use { statement ->
            statement.setString(1, trackId)
            statement.executeUpdate()
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
        connection.prepareStatement(
            """
            INSERT INTO liked_albums (id, title, artist, artwork_url, year, track_count, genre, artist_id, added_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                title = excluded.title,
                artist = excluded.artist,
                artwork_url = COALESCE(excluded.artwork_url, liked_albums.artwork_url)
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
        Unit
    }

    override suspend fun removeLikedAlbum(albumId: String) = withDb { connection ->
        connection.prepareStatement("DELETE FROM liked_albums WHERE id = ?").use { statement ->
            statement.setString(1, albumId)
            statement.executeUpdate()
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
        connection.prepareStatement(
            """
            INSERT INTO followed_artists (id, name, image_url, genre, added_at) VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET name = excluded.name, image_url = COALESCE(excluded.image_url, followed_artists.image_url)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, artist.id)
            statement.setString(2, artist.name)
            statement.setString(3, artist.imageUrl)
            statement.setString(4, artist.genre)
            statement.setLong(5, System.currentTimeMillis())
            statement.executeUpdate()
        }
        Unit
    }

    override suspend fun removeFollowedArtist(artistId: String) = withDb { connection ->
        connection.prepareStatement("DELETE FROM followed_artists WHERE id = ?").use { statement ->
            statement.setString(1, artistId)
            statement.executeUpdate()
        }
        Unit
    }

    // --------------------------------------------------------------- playlists

    override suspend fun playlists(): List<Playlist> = withDb { connection ->
        connection.prepareStatement(
            """
            SELECT p.*, (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlist_id = p.id) AS track_count
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
            SELECT p.*, (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlist_id = p.id) AS track_count
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
        playlist
    }

    override suspend fun renamePlaylist(playlistId: String, name: String) = withDb { connection ->
        connection.prepareStatement("UPDATE playlists SET name = ?, updated_at = ? WHERE id = ?").use { statement ->
            statement.setString(1, name)
            statement.setLong(2, System.currentTimeMillis())
            statement.setString(3, playlistId)
            statement.executeUpdate()
        }
        Unit
    }

    override suspend fun deletePlaylist(playlistId: String) = withDb { connection ->
        connection.transaction {
            connection.prepareStatement("DELETE FROM playlist_tracks WHERE playlist_id = ?").use { statement ->
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

    override suspend fun addToPlaylist(playlistId: String, track: Track): Boolean = withDb { connection ->
        connection.transaction {
            upsertTrack(connection, track)
            val exists = connection.prepareStatement(
                "SELECT 1 FROM playlist_tracks WHERE playlist_id = ? AND track_id = ?",
            ).use { statement ->
                statement.setString(1, playlistId)
                statement.setString(2, track.id)
                statement.executeQuery().use { it.next() }
            }
            if (exists) {
                false
            } else {
                val position = connection.prepareStatement(
                    "SELECT COALESCE(MAX(position), 0) + 1 FROM playlist_tracks WHERE playlist_id = ?",
                ).use { statement ->
                    statement.setString(1, playlistId)
                    statement.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else 1 }
                }
                connection.prepareStatement(
                    "INSERT INTO playlist_tracks(playlist_id, track_id, position, added_at) VALUES (?, ?, ?, ?)",
                ).use { statement ->
                    statement.setString(1, playlistId)
                    statement.setString(2, track.id)
                    statement.setInt(3, position)
                    statement.setLong(4, System.currentTimeMillis())
                    statement.executeUpdate()
                }
                connection.prepareStatement("UPDATE playlists SET updated_at = ? WHERE id = ?").use { statement ->
                    statement.setLong(1, System.currentTimeMillis())
                    statement.setString(2, playlistId)
                    statement.executeUpdate()
                }
                true
            }
        }
    }

    override suspend fun removeFromPlaylist(playlistId: String, trackId: String) = withDb { connection ->
        connection.prepareStatement("DELETE FROM playlist_tracks WHERE playlist_id = ? AND track_id = ?").use { statement ->
            statement.setString(1, playlistId)
            statement.setString(2, trackId)
            statement.executeUpdate()
        }
        Unit
    }

    override suspend fun movePlaylistTrack(playlistId: String, fromIndex: Int, toIndex: Int) = withDb { connection ->
        connection.transaction {
            val ids = connection.prepareStatement(
                "SELECT track_id FROM playlist_tracks WHERE playlist_id = ? ORDER BY position ASC",
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
                    "UPDATE playlist_tracks SET position = ? WHERE playlist_id = ? AND track_id = ?",
                ).use { statement ->
                    moved.forEachIndexed { index, trackId ->
                        statement.setInt(1, index + 1)
                        statement.setString(2, playlistId)
                        statement.setString(3, trackId)
                        statement.executeUpdate()
                    }
                }
            }
        }
        Unit
    }

    // ----------------------------------------------------------------- history

    override suspend fun recordPlay(track: Track) = withDb { connection ->
        connection.transaction {
            upsertTrack(connection, track)
            connection.prepareStatement("INSERT INTO history(track_id, played_at) VALUES (?, ?)").use { statement ->
                statement.setString(1, track.id)
                statement.setLong(2, System.currentTimeMillis())
                statement.executeUpdate()
            }
            connection.prepareStatement(
                "DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY played_at DESC LIMIT 1000)",
            ).use { it.executeUpdate() }
        }
        Unit
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

    // ---------------------------------------------------------------- settings

    override suspend fun getSetting(key: String): String? = withDb { connection ->
        connection.prepareStatement("SELECT value FROM settings WHERE key = ?").use { statement ->
            statement.setString(1, key)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
        }
    }

    override suspend fun setSetting(key: String, value: String) = withDb { connection ->
        connection.prepareStatement(
            "INSERT INTO settings(key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
        ).use { statement ->
            statement.setString(1, key)
            statement.setString(2, value)
            statement.executeUpdate()
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
        while (next()) {
            add(
                Track(
                    id = getString("id"),
                    title = getString("title"),
                    artist = getString("artist"),
                    album = getString("album"),
                    durationMs = getLong("duration_ms"),
                    artworkUrl = getString("artwork_url"),
                    streamUrl = getString("stream_url"),
                    source = runCatching { MusicSourceId.valueOf(getString("source")) }.getOrDefault(MusicSourceId.ITUNES),
                    artistId = getString("artist_id"),
                    albumId = getString("album_id"),
                    year = getObject("year")?.let { (it as Number).toInt() },
                    trackNumber = getObject("track_number")?.let { (it as Number).toInt() },
                    genre = getString("genre"),
                    explicit = getInt("explicit") == 1,
                    localPath = getString("local_path"),
                ),
            )
        }
    }

    private fun ResultSet.mapAlbums(): List<Album> = buildList {
        while (next()) {
            add(
                Album(
                    id = getString("id"),
                    title = getString("title"),
                    artist = getString("artist"),
                    artworkUrl = getString("artwork_url"),
                    year = getObject("year")?.let { (it as Number).toInt() },
                    trackCount = getInt("track_count"),
                    genre = getString("genre"),
                    artistId = getString("artist_id"),
                ),
            )
        }
    }

    private fun ResultSet.toPlaylist(): Playlist = Playlist(
        id = getString("id"),
        name = getString("name"),
        description = getString("description") ?: "",
        trackCount = getInt("track_count"),
        createdAt = getLong("created_at"),
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
