package com.revibe.account

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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.io.encoding.ExperimentalEncodingApi

class AuthApiTest {

    private val config = CloudConfig("https://example.supabase.co", "anon-key")

    private fun api(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): AuthApi {
        val client = HttpClient(
            MockEngine { request -> handler(request) },
        ) {
            expectSuccess = false
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
        }
        return AuthApi(client, config, now = { 1_000_000L })
    }

    @Test
    fun `password grant returns a session and never needs a service role key`() = runBlocking {
        var sawPasswordGrant = false
        val api = api { request ->
            sawPasswordGrant = request.url.encodedPath == "/auth/v1/token" &&
                request.url.parameters["grant_type"] == "password"
            respond(
                content = """
                    {"access_token":"access-1","refresh_token":"refresh-1","expires_in":3600,
                     "user":{"id":"user-1","email":"a@example.com","user_metadata":{"display_name":"A"}}}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val session = api.signIn("a@example.com", "secret123")
        assertTrue(sawPasswordGrant)
        assertEquals("access-1", session.accessToken)
        assertEquals("refresh-1", session.refreshToken)
        assertEquals("A", session.user.displayName)
        assertEquals(1_000_000L + 3_600_000L, session.expiresAt)
    }

    @Test
    fun `signup without a session reports email confirmation`() = runBlocking {
        val api = api {
            respond(
                content = """{"id":"user-1","email":"a@example.com"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val outcome = api.signUp("a@example.com", "secret123")
        assertIs<SignUpOutcome.ConfirmationRequired>(outcome)
        assertEquals("a@example.com", outcome.email)
    }

    @Test
    fun `invalid credentials surface a friendly error without leaking tokens`() = runBlocking {
        val api = api {
            respondError(
                status = HttpStatusCode.BadRequest,
                content = """{"msg":"Invalid login credentials"}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val error = runCatching { api.signIn("a@example.com", "wrong") }.exceptionOrNull()
        assertIs<AuthException>(error)
        assertEquals(400, error.statusCode)
        assertEquals("Invalid email or password.", error.message)
        assertFalse(error.message.orEmpty().contains("access"))
    }

    @Test
    fun `expired refresh token clears the stored session`() = runBlocking {
        val store = FakeSessionStore(
            StoredSession(
                accessToken = "old-access",
                refreshToken = "old-refresh",
                expiresAt = 0,
                user = AccountUser("user-1", "a@example.com"),
            ),
        )
        val api = api {
            respondError(HttpStatusCode.Unauthorized, """{"msg":"Invalid Refresh Token"}""")
        }
        val manager = AuthManager(config, api, store, now = { 2_000_000L })

        manager.restore()

        val state = manager.state.value
        assertIs<AccountState.SignedOut>(state)
        assertTrue(state.message.orEmpty().contains("expired", ignoreCase = true))
        assertNull(store.value)
    }

    @Test
    fun `restore refreshes an expired token and fetches the profile`() = runBlocking {
        val store = FakeSessionStore(
            StoredSession(
                accessToken = "old",
                refreshToken = "refresh-1",
                expiresAt = 0,
                user = AccountUser("user-1", "a@example.com"),
            ),
        )
        var paths = mutableListOf<String>()
        val api = api { request ->
            paths += request.url.encodedPath
            when (request.url.encodedPath) {
                "/auth/v1/token" -> respond(
                    content = """{"access_token":"new","refresh_token":"refresh-2","expires_in":3600,"user":{"id":"user-1","email":"a@example.com"}}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                "/rest/v1/profiles" -> respond(
                    content = """[{"id":"user-1","email":"a@example.com","display_name":"Listener"}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val manager = AuthManager(config, api, store, now = { 2_000_000L })

        manager.restore()

        val state = manager.state.value
        assertIs<AccountState.SignedIn>(state)
        assertEquals("Listener", state.user.displayName)
        assertEquals("new", store.value?.accessToken)
        assertEquals("refresh-2", store.value?.refreshToken)
        assertTrue(paths.contains("/rest/v1/profiles"))
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `service role keys are rejected while publishable keys are accepted`() {
        val serviceKey = listOf(
            """{"alg":"none"}""",
            """{"role":"service_role","iss":"supabase"}""",
            "signature",
        ).joinToString(".") { segment ->
            kotlin.io.encoding.Base64.UrlSafe.encode(segment.encodeToByteArray()).trimEnd('=')
        }
        val service = CloudConfig("https://example.supabase.co", serviceKey)
        assertFalse(service.isConfigured)
        assertTrue(service.validationError.orEmpty().contains("Service-role"))

        val secret = CloudConfig("https://example.supabase.co", "sb_secret_abc")
        assertFalse(secret.isConfigured)

        val anon = CloudConfig("https://example.supabase.co", "sb_publishable_abc")
        assertTrue(anon.isConfigured)
    }

    @Test
    fun `signup rejects a short password before calling the cloud`() = runBlocking {
        var requested = false
        val api = api {
            requested = true
            respondError(HttpStatusCode.BadRequest)
        }
        val manager = AuthManager(config, api, FakeSessionStore(null))

        val error = runCatching { manager.signUp("a@example.com", "seven77") }.exceptionOrNull()

        assertIs<IllegalArgumentException>(error)
        assertEquals("Use at least 8 characters for your password.", error.message)
        assertFalse(requested)
    }

    @Test
    fun `google sign in exchanges the pkce code and persists the session`() = runBlocking {
        var tokenBody = ""
        var grant = ""
        val api = api { request ->
            when (request.url.encodedPath) {
                "/auth/v1/token" -> {
                    grant = request.url.parameters["grant_type"].orEmpty()
                    tokenBody = request.body.toByteArray().decodeToString()
                    respond(
                        content = """{"access_token":"google-access","refresh_token":"google-refresh","expires_in":3600,"user":{"id":"user-7","email":"g@example.com"}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                "/rest/v1/profiles" -> respond(
                    content = """[{"id":"user-7","email":"g@example.com","display_name":"Google Listener"}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val store = FakeSessionStore(null)
        val launcher = FakeOAuthLauncher(OAuthAuthorization(code = "code-1", codeVerifier = "verifier-1"))
        val manager = AuthManager(config, api, store, launcher, now = { 2_000_000L })

        manager.signInWithGoogle()

        val state = manager.state.value
        assertIs<AccountState.SignedIn>(state)
        assertEquals("Google Listener", state.user.displayName)
        assertEquals("pkce", grant)
        assertTrue(tokenBody.contains("\"auth_code\":\"code-1\""))
        assertTrue(tokenBody.contains("\"code_verifier\":\"verifier-1\""))
        assertEquals("google-access", store.value?.accessToken)
        assertEquals("google", launcher.lastProvider)
    }

    @Test
    fun `google sign in reports missing configuration instead of simulating a login`() = runBlocking {
        val manager = AuthManager(
            CloudConfig("", ""),
            api { respondError(HttpStatusCode.InternalServerError) },
            FakeSessionStore(null),
            FakeOAuthLauncher(OAuthAuthorization("code", "verifier")),
        )

        val error = runCatching { manager.signInWithGoogle() }.exceptionOrNull()

        assertIs<AuthException>(error)
        assertTrue(error.message.orEmpty().contains("REVIBE_SUPABASE_URL"))
        assertTrue(manager.state.value is AccountState.NotConfigured)
    }

    @Test
    fun `a cancelled google round trip surfaces a cancellation message`() = runBlocking {
        val manager = AuthManager(
            config,
            api { respondError(HttpStatusCode.BadRequest) },
            FakeSessionStore(null),
            FakeOAuthLauncher(failure = OAuthException("Google sign-in was cancelled.", cancelled = true)),
        )

        val error = runCatching { manager.signInWithGoogle() }.exceptionOrNull()

        assertIs<AuthException>(error)
        assertEquals("Google sign-in was cancelled.", error.message)
    }

    @Test
    fun `sign out revokes the token and clears the stored session`() = runBlocking {
        val store = FakeSessionStore(
            StoredSession(
                accessToken = "access-1",
                refreshToken = "refresh-1",
                expiresAt = Long.MAX_VALUE,
                user = AccountUser("user-1", "a@example.com"),
            ),
        )
        var logoutCalls = 0
        val api = api { request ->
            when (request.url.encodedPath) {
                "/rest/v1/profiles" -> respond(
                    content = """[{"id":"user-1","email":"a@example.com"}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                "/auth/v1/logout" -> {
                    logoutCalls++
                    respond("", HttpStatusCode.NoContent)
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val manager = AuthManager(config, api, store, now = { 1_000_000L })
        manager.restore()
        assertIs<AccountState.SignedIn>(manager.state.value)

        manager.signOut()

        assertEquals(1, logoutCalls)
        assertNull(store.value)
        assertTrue(manager.state.value is AccountState.SignedOut)
    }

    private class FakeOAuthLauncher(
        private val authorization: OAuthAuthorization? = null,
        private val failure: OAuthException? = null,
        override val isConfigured: Boolean = true,
    ) : OAuthLauncher {
        var lastProvider: String? = null
            private set

        override suspend fun authorize(provider: String): OAuthAuthorization {
            lastProvider = provider
            failure?.let { throw it }
            return authorization ?: error("No authorization configured")
        }
    }

    private class FakeSessionStore(var value: StoredSession?) : AuthSessionStore {
        override suspend fun load(): StoredSession? = value
        override suspend fun save(session: StoredSession) {
            value = session
        }

        override suspend fun clear() {
            value = null
        }
    }
}
