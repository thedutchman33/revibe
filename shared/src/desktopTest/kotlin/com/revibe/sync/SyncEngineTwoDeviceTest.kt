package com.revibe.sync

import com.revibe.account.AccountUser
import com.revibe.account.AuthApi
import com.revibe.account.AuthManager
import com.revibe.account.AuthSessionStore
import com.revibe.account.CloudConfig
import com.revibe.account.StoredSession
import com.revibe.data.JdbcLibraryStore
import com.revibe.imports.HistoryImportRow
import com.revibe.imports.ImportBatchRecord
import com.revibe.imports.SearchImportRow
import com.revibe.imports.SourceHistoryEvent
import com.revibe.imports.SourceSearch
import com.revibe.imports.SourceTrack
import com.revibe.model.Album
import com.revibe.model.Artist
import com.revibe.model.MusicSourceId
import com.revibe.model.Playlist
import com.revibe.model.Track
import com.revibe.settings.SettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import kotlin.math.max
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Drives the real CloudSyncApi and SyncEngine against an in-memory PostgREST
 * stand-in. It cannot replace applying the SQL migration to a real Supabase
 * project, but it does exercise two independent SQLite libraries, incremental
 * pulls, retries and last-writer-wins conflicts deterministically.
 */
class SyncEngineTwoDeviceTest {
    private val cloud = FakeCloud()
    private val scopes = mutableListOf<CoroutineScope>()

    @AfterTest
    fun closeScopes() {
        scopes.forEach { it.coroutineContext[kotlinx.coroutines.Job]?.cancel() }
    }

    @Test
    fun `second device receives playlists likes follows history and combined activity`() = runBlocking {
        val deviceA = newDevice("user-a", "a@example.com", "A")
        val settingsA = deviceA.settings
        settingsA.setSyncSearchHistory(true)
        val playlist = deviceA.store.createPlaylist("Road trip")
        val first = track("ytm:one")
        val second = track("ytm:two")
        deviceA.store.addToPlaylist(playlist.id, first)
        deviceA.store.addToPlaylist(playlist.id, second)
        deviceA.store.addLikedTrack(first)
        deviceA.store.addFollowedArtist(Artist("artist:arijit", "Arijit Singh"))
        deviceA.store.recordPlay(first)
        deviceA.store.recordSearch("Arijit Singh")

        assertTrue(deviceA.engine.syncNow())
        assertTrue(cloud.playlistCount("user-a") == 1)

        val deviceB = newDevice("user-a", "a@example.com", "B")
        deviceB.settings.setSyncSearchHistory(true)
        assertTrue(deviceB.engine.syncNow())

        val remotePlaylist = deviceB.store.playlists().single()
        assertEquals("Road trip", remotePlaylist.name)
        assertEquals(listOf("ytm:one", "ytm:two"), deviceB.store.playlistTracks(remotePlaylist.id).map { it.id })
        assertEquals(setOf("ytm:one"), deviceB.store.likedIds())
        assertEquals(listOf("Arijit Singh"), deviceB.store.followedArtists().map { it.name })
        assertEquals(1, deviceB.store.recentHistory(10).size)
        assertEquals(listOf("Arijit Singh"), deviceB.store.searchHistory(10).map { it.query })

        // The homepage activity is derived from the same account history, not a fixture.
        assertTrue(deviceB.store.userActivity().playCount >= 1)
        assertTrue(deviceB.store.userActivity().followedArtistCount >= 1)
    }

    @Test
    fun `edits propagate and the last push wins simultaneous renames`() = runBlocking {
        val (a, b, playlist) = baseline()
        assertEquals(1, b.store.playlists().size)

        b.store.renamePlaylist(playlist.id, "Renamed on B")
        assertTrue(b.engine.syncNow())
        assertTrue(a.engine.syncNow())
        assertEquals("Renamed on B", a.store.playlist(playlist.id)?.name)

        cloud.online = false
        a.store.renamePlaylist(playlist.id, "Name from A")
        b.store.renamePlaylist(playlist.id, "Name from B")
        cloud.online = true
        assertTrue(a.engine.syncNow())
        assertTrue(b.engine.syncNow())
        assertTrue(a.engine.syncNow())
        assertEquals("Name from B", a.store.playlist(playlist.id)?.name)
        assertEquals("Name from B", b.store.playlist(playlist.id)?.name)
    }

    @Test
    fun `offline edits stay queued and sync when connectivity returns`() = runBlocking {
        val (a, b, _) = baseline()
        cloud.online = false

        val offlinePlaylist = b.store.createPlaylist("Made offline")
        b.store.addToPlaylist(offlinePlaylist.id, track("ytm:offline"))
        assertFalse(b.engine.syncNow())
        assertEquals(SyncPhase.OFFLINE, b.engine.status.value.phase)
        assertTrue(b.engine.status.value.pendingChanges > 0)
        assertEquals("Made offline", b.store.playlist(offlinePlaylist.id)?.name)

        cloud.online = true
        assertTrue(b.engine.syncNow())
        assertTrue(a.engine.syncNow())
        assertTrue(a.store.playlists().any { it.name == "Made offline" })
    }

    @Test
    fun `a deleted playlist is resurrected by a later offline rename`() = runBlocking {
        val (a, b, playlist) = baseline()
        cloud.online = false
        a.store.deletePlaylist(playlist.id)
        b.store.renamePlaylist(playlist.id, "B kept it")
        cloud.online = true

        assertTrue(a.engine.syncNow())
        assertTrue(b.engine.syncNow())
        assertTrue(a.engine.syncNow())

        assertEquals("B kept it", a.store.playlist(playlist.id)?.name)
    }

    @Test
    fun `another account cannot see the first account's cloud rows`() = runBlocking {
        baseline()
        val other = newDevice("user-c", "c@example.com", "C")
        assertTrue(other.engine.syncNow())

        assertTrue(other.store.playlists().isEmpty())
        assertTrue(other.store.likedIds().isEmpty())
        assertTrue(other.store.followedArtists().isEmpty())
        assertTrue(other.store.recentHistory(10).isEmpty())
    }

    @Test
    fun `imported records sync with provenance and a second-device undo removes only that import`() = runBlocking {
        val a = newDevice("user-a", "a@example.com", "A")
        a.settings.setSyncSearchHistory(true)
        val importId = "import-two-device"
        val importedTrack = track("ytm:imported")
        val playlistId = a.store.createImportedPlaylist(
            sourcePlatform = "spotify",
            sourceId = "spotify:playlist:imported",
            importId = importId,
            name = "Imported mix",
            description = "from Spotify",
            tracks = listOf(importedTrack, importedTrack),
        ).playlistId!!
        a.store.addImportedLike("spotify", "spotify:track:imported", importId, importedTrack)
        a.store.addImportedAlbum("spotify", "spotify:album:imported", importId, Album("ytm:album:imported", "Imported album", "Arijit Singh"))
        a.store.addImportedArtist("spotify", "spotify:artist:imported", importId, Artist("artist:imported", "Imported artist"))
        val event = SourceHistoryEvent(
            sourceKey = "two-device-event",
            timestampMs = 1_700_000_000_000,
            track = SourceTrack(1, importedTrack.title, importedTrack.artist, importedTrack.album, spotifyUri = "spotify:track:imported"),
            playedMs = 180_000,
            completed = true,
            skipped = false,
            sourceEventId = "spotify-event-2d",
        )
        a.store.addImportedHistory("spotify", importId, listOf(HistoryImportRow(event, importedTrack)))
        a.store.addImportedSearches("spotify", importId, listOf(SearchImportRow(SourceSearch("s2d", "imported search", 1_700_000_000_000))))
        a.store.recordImportBatch(
            ImportBatchRecord(importId, "spotify", "fp-2d", 1_700_000_000_000, "summary", playlists = 1, tracks = 2, likes = 1, albums = 1, artists = 1, history = 1, searches = 1),
        )

        assertTrue(a.engine.syncNow())
        val b = newDevice("user-a", "a@example.com", "B")
        b.settings.setSyncSearchHistory(true)
        assertTrue(b.engine.syncNow())

        val pulled = b.store.playlistForSync(playlistId)!!
        assertEquals(2, pulled.entries.size)
        assertEquals("spotify", pulled.provenance?.sourcePlatform)
        assertTrue(pulled.entries.all { it.sourceImportId == importId })
        assertTrue(pulled.entries[0].entryId != pulled.entries[1].entryId)
        assertEquals(importId, b.store.likeForSync(importedTrack.id)!!.provenance?.sourceImportId)
        assertTrue(b.store.likedAlbums().any { it.id == "ytm:album:imported" })
        assertTrue(b.store.followedArtists().any { it.id == "artist:imported" })
        assertEquals(1, b.store.importHistory().size)
        assertEquals(1, b.store.userActivity().importedPlayCount)
        assertEquals(1, b.store.userActivity().importedPlaylistCount)

        // A native entry added on B must survive when B undoes the import.
        b.store.addToPlaylist(playlistId, track("ytm:native-after"))
        assertTrue(b.engine.syncNow())
        assertTrue(a.engine.syncNow())
        assertEquals(3, a.store.playlistTrackEntries(playlistId).size)

        val removed = b.store.undoImport(importId)
        assertEquals(2, removed.tracks)
        assertTrue(b.engine.syncNow())
        assertTrue(a.engine.syncNow())

        val remaining = a.store.playlistTrackEntries(playlistId).single()
        assertEquals("ytm:native-after", remaining.track.id)
        assertFalse(remaining.imported)
        assertFalse(a.store.likedIds().contains(importedTrack.id))
        assertTrue(a.store.likedAlbums().isEmpty())
        assertTrue(a.store.followedArtists().isEmpty())
        assertEquals(0, a.store.userActivity().importedPlayCount)
        assertTrue(a.store.searchHistory(10).isEmpty())
        assertTrue(a.store.importHistory().single().undone)
    }

    @Test
    fun `search history stays off until the account opts in`() = runBlocking {
        val a = newDevice("user-a", "a@example.com", "A")
        a.store.recordSearch("private query")
        assertTrue(a.engine.syncNow())
        assertEquals(0, cloud.searchCount("user-a"))

        val b = newDevice("user-a", "a@example.com", "B")
        assertTrue(b.engine.syncNow())
        assertTrue(b.store.searchHistory(10).isEmpty())

        a.settings.setSyncSearchHistory(true)
        assertTrue(a.engine.syncNow())
        assertTrue(b.engine.syncNow())
        assertEquals(listOf("private query"), b.store.searchHistory(10).map { it.query })
    }

    private suspend fun baseline(): Triple<Device, Device, Playlist> {
        val a = newDevice("user-a", "a@example.com", "A")
        val playlist = a.store.createPlaylist("Shared")
        a.store.addToPlaylist(playlist.id, track("ytm:shared"))
        a.store.addLikedTrack(track("ytm:shared"))
        a.store.addFollowedArtist(Artist("artist:arijit", "Arijit Singh"))
        a.store.recordPlay(track("ytm:shared"))
        a.settings.setSyncSearchHistory(true)
        assertTrue(a.engine.syncNow())

        val b = newDevice("user-a", "a@example.com", "B")
        b.settings.setSyncSearchHistory(true)
        assertTrue(b.engine.syncNow())
        return Triple(a, b, playlist)
    }

    private suspend fun newDevice(userId: String, email: String, label: String): Device {
        val dir = Files.createTempDirectory("revibe-device-${label.lowercase()}").toFile()
        val store = JdbcLibraryStore(File(dir, "library.db"), Dispatchers.IO)
        val settings = SettingsRepository(store).also { it.load() }
        val account = AccountUser(userId, email, displayName = "Listener $label")
        val session = StoredSession(
            accessToken = "access-$userId",
            refreshToken = "refresh-$userId",
            expiresAt = Long.MAX_VALUE,
            user = account,
        )
        val auth = AuthManager(
            cloud.config,
            AuthApi(cloud.client, cloud.config),
            MemorySessionStore(session),
        )
        auth.restore()
        assertTrue(auth.state.value is com.revibe.account.AccountState.SignedIn)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scopes += scope
        val engine = SyncEngine(
            api = CloudSyncApi(cloud.client, cloud.config),
            auth = auth,
            store = store,
            settings = settings,
            deviceName = label,
            platform = "test",
            scope = scope,
        )
        assertTrue(engine.claimLocalLibrary())
        return Device(store, settings, engine)
    }

    private fun track(id: String) = Track(
        id = id,
        title = "Kesariya $id",
        artist = "Arijit Singh",
        album = "Brahmastra",
        durationMs = 268_000,
        artworkUrl = "https://example.invalid/art.jpg",
        streamUrl = "https://example.invalid/temporary-stream",
        source = MusicSourceId.YOUTUBE_MUSIC,
        artistId = "artist:arijit",
    )

    private data class Device(
        val store: JdbcLibraryStore,
        val settings: SettingsRepository,
        val engine: SyncEngine,
    )

    private class MemorySessionStore(var value: StoredSession?) : AuthSessionStore {
        override suspend fun load(): StoredSession? = value
        override suspend fun save(session: StoredSession) {
            value = session
        }

        override suspend fun clear() {
            value = null
        }
    }

    // ------------------------------------------------------------ fake cloud

    private class FakeCloud {
        val config = CloudConfig("https://example.supabase.co", "anon-key")
        var online = true
        private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
        private val data = mutableMapOf<String, MutableMap<String, MutableMap<String, JsonObject>>>()
        private var tick = System.currentTimeMillis()

        val client: HttpClient = HttpClient(
            MockEngine { request -> handle(request) },
        ) {
            expectSuccess = false
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }) }
        }

        fun playlistCount(userId: String) = table(userId, "playlists").size
        fun searchCount(userId: String) = table(userId, "search_history").size

        private fun table(userId: String, name: String) =
            data.getOrPut(userId) { mutableMapOf() }.getOrPut(name) { mutableMapOf() }

        private fun stamp(): String {
            tick = max(tick + 1, System.currentTimeMillis())
            return com.revibe.platform.millisToIso(tick)
        }

        private suspend fun MockRequestHandleScope.handle(request: HttpRequestData): HttpResponseData {
            if (!online) throw IOException("simulated offline")
            val path = request.url.encodedPath
            return when {
                path.startsWith("/auth/") -> auth(path, request)
                path.startsWith("/rest/v1/rpc/") -> rpc(path.removePrefix("/rest/v1/rpc/"), request)
                path.startsWith("/rest/v1/") -> rest(path.removePrefix("/rest/v1/"), request)
                else -> respondError(HttpStatusCode.NotFound)
            }
        }

        private suspend fun MockRequestHandleScope.auth(path: String, request: HttpRequestData): HttpResponseData {
            if (path == "/auth/v1/logout" || path == "/auth/v1/recover") {
                return respond("", HttpStatusCode.OK)
            }
            if (path == "/auth/v1/token") {
                val body = body(request)
                val token = body["refresh_token"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val userId = token.removePrefix("refresh-")
                return jsonResponse(
                    buildJsonObject {
                        put("access_token", JsonPrimitive("access-$userId"))
                        put("refresh_token", JsonPrimitive("refresh-$userId"))
                        put("expires_in", JsonPrimitive(3600))
                        put("user", userJson(userId, "$userId@example.com"))
                    },
                )
            }
            return respondError(HttpStatusCode.NotFound)
        }

        private suspend fun MockRequestHandleScope.rest(table: String, request: HttpRequestData): HttpResponseData {
            val userId = userId(request) ?: return respondError(HttpStatusCode.Unauthorized)
            if (table == "profiles") {
                return jsonResponse(
                    JsonArray(
                        listOf(
                            buildJsonObject {
                                put("id", JsonPrimitive(userId))
                                put("email", JsonPrimitive("$userId@example.com"))
                                put("display_name", JsonPrimitive("Listener $userId"))
                                put("avatar_url", JsonNull)
                            },
                        ),
                    ),
                )
            }
            if (table == "user_devices") {
                return respond("", HttpStatusCode.Created)
            }
            return if (request.method == HttpMethod.Get) {
                pull(table, request, userId)
            } else {
                push(table, request, userId)
            }
        }

        private suspend fun MockRequestHandleScope.push(
            table: String,
            request: HttpRequestData,
            userId: String,
        ): HttpResponseData {
            val payload = body(request)
            val rows = table(userId, table)
            val key = when (table) {
                "playlists" -> payload.str("id")
                "liked_songs" -> payload.str("track_id")
                "saved_albums" -> payload.str("album_id")
                "followed_artists" -> payload.str("artist_id")
                "import_batches" -> payload.str("id")
                "listening_history" -> payload.str("id")
                "search_history" -> payload.str("query_key")
                "user_preferences" -> "prefs"
                else -> payload.str("id")
            }.orEmpty()
            val existing = rows[key]
            val merged = LinkedHashMap<String, JsonElement>()
            existing?.let { merged.putAll(it) }
            merged.putAll(payload)
            if (existing == null && table == "search_history") {
                merged["id"] = JsonPrimitive("srv-${UUID.randomUUID()}")
            }
            merged["user_id"] = JsonPrimitive(userId)
            merged["updated_at"] = JsonPrimitive(stamp())
            if (merged["created_at"] == null) merged["created_at"] = JsonPrimitive(stamp())
            rows[key] = JsonObject(merged)
            return respond("{}", HttpStatusCode.Created, jsonHeaders)
        }

        private suspend fun MockRequestHandleScope.pull(
            table: String,
            request: HttpRequestData,
            userId: String,
        ): HttpResponseData {
            val rows = table(userId, table)
            if (table == "playlist_tracks") {
                val playlistId = request.url.parameters["playlist_id"]?.removePrefix("eq.")
                val result = rows.values
                    .filter { it.str("playlist_id") == playlistId && !it.deleted() }
                    .sortedBy { it["position"]?.jsonPrimitive?.int ?: 0 }
                return jsonResponse(JsonArray(result))
            }
            if (table == "user_preferences") {
                return jsonResponse(JsonArray(rows.values.take(1)))
            }
            val since = request.url.parameters["updated_at"]?.removePrefix("gt.")
            val ordered = rows.values
                .filter { since == null || (it.str("updated_at") ?: "") > since }
                .sortedWith(compareBy({ it.str("updated_at") }, { it.str(idColumn(table)) }))
            val offset = request.url.parameters["offset"]?.toIntOrNull() ?: 0
            val limit = request.url.parameters["limit"]?.toIntOrNull() ?: 500
            return jsonResponse(JsonArray(ordered.drop(offset).take(limit)))
        }

        private suspend fun MockRequestHandleScope.rpc(
            name: String,
            request: HttpRequestData,
        ): HttpResponseData {
            val userId = userId(request) ?: return respondError(HttpStatusCode.Unauthorized)
            val payload = body(request)
            when (name) {
                "sync_replace_playlist_tracks" -> {
                    val playlistId = payload.str("p_playlist_id").orEmpty()
                    val tracks = table(userId, "playlist_tracks")
                    tracks.entries
                        .filter { it.value.str("playlist_id") == playlistId }
                        .forEach { (key, row) -> tracks[key] = row.markDeleted() }
                    payload["p_tracks"]?.jsonArray?.forEach { element ->
                        val item = element.jsonObject()
                        val trackId = item.str("track_id").orEmpty()
                        val entryId = item.str("entry_id").orEmpty()
                        tracks["$playlistId|$entryId"] = buildJsonObject {
                            put("playlist_id", JsonPrimitive(playlistId))
                            put("entry_id", JsonPrimitive(entryId))
                            put("track_id", JsonPrimitive(trackId))
                            put("user_id", JsonPrimitive(userId))
                            put("position", JsonPrimitive(item["position"]?.jsonPrimitive?.int ?: 1))
                            item["track"]?.let { put("track", it) }
                            item["added_at"]?.let { put("added_at", it) }
                            item["source_platform"]?.let { put("source_platform", it) }
                            item["source_import_id"]?.let { put("source_import_id", it) }
                            item["imported_at"]?.let { put("imported_at", it) }
                            put("updated_at", JsonPrimitive(stamp()))
                        }
                    }
                    table(userId, "playlists")[playlistId]?.let { row ->
                        table(userId, "playlists")[playlistId] = row.copyWith("updated_at", JsonPrimitive(stamp()))
                    }
                }
                "sync_soft_delete" -> {
                    val target = payload.str("p_table").orEmpty()
                    val id = payload.str("p_id").orEmpty()
                    val rows = table(userId, target)
                    val key = when (target) {
                        "playlists" -> id
                        "liked_songs" -> id
                        "saved_albums" -> id
                        "followed_artists" -> id
                        "import_batches" -> rows.entries.firstOrNull { it.value.str("id") == id }?.key
                        "listening_history" -> rows.entries.firstOrNull { it.value.str("id") == id }?.key
                        "search_history" -> rows.entries.firstOrNull { it.value.str("id") == id }?.key
                        else -> null
                    }
                    if (key != null) rows[key]?.let { rows[key] = it.markDeleted() }
                    if (target == "playlists") {
                        val tracks = table(userId, "playlist_tracks")
                        tracks.entries.filter { it.value.str("playlist_id") == id }
                            .forEach { (trackKey, row) -> tracks[trackKey] = row.markDeleted() }
                    }
                }
            }
            return respond("", HttpStatusCode.NoContent)
        }

        private fun userId(request: HttpRequestData): String? {
            val bearer = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim().orEmpty()
            return when (bearer) {
                "access-user-a" -> "user-a"
                "access-user-b" -> "user-b"
                "access-user-c" -> "user-c"
                else -> null
            }
        }

        private suspend fun body(request: HttpRequestData): JsonObject =
            json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject()

        private fun idColumn(table: String) = when (table) {
            "liked_songs" -> "track_id"
            "saved_albums" -> "album_id"
            "followed_artists" -> "artist_id"
            else -> "id"
        }

        private fun JsonObject.str(key: String): String? =
            this[key]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }

        private fun JsonObject.deleted(): Boolean = str("deleted_at") != null

        private fun JsonObject.markDeleted(): JsonObject = copyWith("deleted_at", JsonPrimitive(stamp()))

        private fun JsonObject.copyWith(key: String, value: JsonElement): JsonObject {
            val next = LinkedHashMap<String, JsonElement>()
            next.putAll(this)
            next[key] = value
            return JsonObject(next)
        }

        private fun JsonElement.jsonObject(): JsonObject = this as JsonObject

        private fun userJson(id: String, email: String) = buildJsonObject {
            put("id", JsonPrimitive(id))
            put("email", JsonPrimitive(email))
        }

        private fun MockRequestHandleScope.jsonResponse(
            element: JsonElement,
            status: HttpStatusCode = HttpStatusCode.OK,
        ): HttpResponseData = respond(element.toString(), status, jsonHeaders)

        private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
