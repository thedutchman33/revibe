package com.revibe.sync

import com.revibe.account.AccountUser
import com.revibe.account.CloudConfig
import com.revibe.account.StoredSession
import com.revibe.model.MusicSourceId
import com.revibe.model.Playlist
import com.revibe.model.PlaylistTrackEntry
import com.revibe.model.Track
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
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CloudSyncApiTest {
    private val session = StoredSession(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        expiresAt = Long.MAX_VALUE,
        user = AccountUser("user-1", "a@example.com"),
    )

    private fun api(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): CloudSyncApi {
        val client = HttpClient(
            MockEngine { request -> handler(request) },
        ) {
            expectSuccess = false
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
        }
        return CloudSyncApi(client, CloudConfig("https://example.supabase.co", "anon-key"))
    }

    @Test
    fun `playlist push strips stream urls and local paths before upload`() = runBlocking {
        val bodies = mutableListOf<String>()
        val paths = mutableListOf<String>()
        val api = api { request ->
            paths += request.url.encodedPath + "?" + request.url.encodedQuery
            bodies += request.body.toByteArray().decodeToString()
            respond("{}", HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val track = Track(
            id = "ytm:abc",
            title = "Kesariya",
            artist = "Arijit Singh",
            album = "Brahmastra",
            durationMs = 268_000,
            streamUrl = "https://rr1.googlevideo.com/expiring",
            source = MusicSourceId.YOUTUBE_MUSIC,
            localPath = null,
        )

        api.pushPlaylist(
            session,
            SyncedPlaylist(
                playlist = Playlist("11111111-1111-1111-1111-111111111111", "Favourites", "test"),
                entries = listOf(PlaylistTrackEntry(track, 1_700_000_000_000)),
            ),
        )

        assertEquals(2, bodies.size)
        assertTrue(paths[0].contains("/rest/v1/playlists"))
        assertTrue(paths[0].contains("on_conflict=id"))
        assertTrue(paths[1].contains("/rest/v1/rpc/sync_replace_playlist_tracks"))
        val all = bodies.joinToString("\n")
        assertTrue(all.contains("Kesariya"))
        assertTrue(all.contains("\"position\":1"))
        assertTrue(all.contains("\"user_id\":\"user-1\""))
        assertFalse(all.contains("streamUrl"))
        assertFalse(all.contains("localPath"))
        assertFalse(all.contains("googlevideo"))
    }

    @Test
    fun `search push relies on the unique query key and never sends a client uuid`() = runBlocking {
        var body = ""
        val api = api { request ->
            body = request.body.toByteArray().decodeToString()
            respond("{}", HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        api.pushSearch(
            session,
            SyncedSearch("local-id", "arijit singh", "Arijit Singh", 1_700_000_000_000),
        )

        assertTrue(body.contains("\"query_key\":\"arijit singh\""))
        assertFalse(body.contains("\"id\""))
    }

    @Test
    fun `pull maps soft-deleted likes and ignores user supplied ownership`() = runBlocking {
        val api = api { request ->
            assertTrue(request.url.parameters["updated_at"] == "gt.1970-01-01T00:16:40Z")
            respond(
                content = """
                    [{"track_id":"ytm:abc","added_at":"2026-01-01T10:00:00+00:00","deleted_at":"2026-01-02T10:00:00+00:00",
                      "track":{"id":"ytm:abc","title":"Kesariya","artist":"Arijit Singh","album":"Brahmastra","durationMs":268000}}]
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val rows = api.pullLikes(session, since = 1_000_000, limit = 500, offset = 0)
        val row = assertNotNull(rows.singleOrNull())
        assertEquals("ytm:abc", row.track_id)
        assertTrue(row.deleted)
        assertEquals("Kesariya", row.track?.title)
    }

    @Test
    fun `expired cloud session is reported as 401 for the engine to refresh`() = runBlocking {
        val api = api { _ ->
            respondError(HttpStatusCode.Unauthorized, """{"message":"JWT expired"}""")
        }

        val error = runCatching {
            api.pushLike(
                session,
                SyncedLike(
                    Track("ytm:abc", "Kesariya", "Arijit Singh", "Brahmastra", 268_000),
                    addedAt = 1,
                ),
            )
        }.exceptionOrNull()

        val syncError = assertIs<SyncException>(error)
        assertEquals(401, syncError.statusCode)
        assertTrue(syncError.isUnauthorized)
    }

    @Test
    fun `offline failures preserve queued work as a retryable sync error`() = runBlocking {
        val client = HttpClient(
            MockEngine { throw RuntimeException("network down") },
        ) {
            expectSuccess = false
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val api = CloudSyncApi(client, CloudConfig("https://example.supabase.co", "anon-key"))

        val error = runCatching { api.pullPlaylists(session, null, 500, 0) }.exceptionOrNull()
        val syncError = assertIs<SyncException>(error)
        assertEquals(0, syncError.statusCode)
        assertTrue(syncError.isOffline)
    }
}
