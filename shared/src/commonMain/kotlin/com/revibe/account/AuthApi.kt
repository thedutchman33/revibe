package com.revibe.account

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Thin GoTrue + PostgREST client. Only the publishable/anon key is used, so all
 * authorization is enforced by the database's Row Level Security policies.
 */
class AuthApi(
    private val http: HttpClient,
    private val config: CloudConfig,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    suspend fun signUp(email: String, password: String): SignUpOutcome = guard {
        val response = http.post(config.authUrl("/signup")) {
            cloudHeaders()
            contentType(ContentType.Application.Json)
            setBody(Credentials(email, password))
        }
        ensureSuccess(response)
        val token = response.body<TokenResponse>()
        val session = token.toSession()
        if (session == null) {
            SignUpOutcome.ConfirmationRequired(email)
        } else {
            SignUpOutcome.SignedIn(session)
        }
    }

    suspend fun signIn(email: String, password: String): StoredSession = guard {
        val response = http.post(config.authUrl("/token")) {
            cloudHeaders()
            parameter("grant_type", "password")
            contentType(ContentType.Application.Json)
            setBody(Credentials(email, password))
        }
        ensureSuccess(response)
        response.body<TokenResponse>().toSession()
            ?: throw AuthException("The cloud returned no session. Confirm your email and try again.", 401)
    }

    suspend fun refresh(refreshToken: String): StoredSession = guard {
        val response = http.post(config.authUrl("/token")) {
            cloudHeaders()
            parameter("grant_type", "refresh_token")
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(refreshToken))
        }
        if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.BadRequest) {
            throw AuthException("Your session expired. Sign in again.", 401)
        }
        ensureSuccess(response)
        response.body<TokenResponse>().toSession()
            ?: throw AuthException("The cloud returned no session. Sign in again.", 401)
    }

    suspend fun requestPasswordReset(email: String): Unit = guard {
        val response = http.post(config.authUrl("/recover")) {
            cloudHeaders()
            contentType(ContentType.Application.Json)
            setBody(EmailRequest(email))
        }
        ensureSuccess(response)
    }

    /**
     * Exchanges a PKCE authorization code from the system browser for a session.
     * The verifier is generated in-process and is never persisted.
     */
    suspend fun exchangeOAuthCode(code: String, codeVerifier: String): StoredSession = guard {
        val response = http.post(config.authUrl("/token")) {
            cloudHeaders()
            parameter("grant_type", "pkce")
            contentType(ContentType.Application.Json)
            setBody(PkceExchange(auth_code = code, code_verifier = codeVerifier))
        }
        ensureSuccess(response)
        response.body<TokenResponse>().toSession()
            ?: throw AuthException("Google sign-in returned no session. Try again.", 401)
    }

    /** Best-effort revocation. Local credentials are cleared even if this fails. */
    suspend fun signOut(accessToken: String) {
        runCatching {
            http.post(config.authUrl("/logout")) {
                cloudHeaders()
                bearerAuth(accessToken)
            }
        }
    }

    suspend fun fetchProfile(session: StoredSession): AccountUser {
        val response = http.get(config.restUrl("/profiles")) {
            cloudHeaders()
            bearerAuth(session.accessToken)
            parameter("select", "id,email,display_name,avatar_url")
            parameter("id", "eq.${session.user.id}")
        }
        ensureSuccess(response)
        val profile = response.body<List<ProfileDto>>().firstOrNull()
        return profile?.toUser() ?: session.user
    }

    suspend fun updateProfile(session: StoredSession, displayName: String?, avatarUrl: String?): AccountUser = guard {
        val response = http.patch(config.restUrl("/profiles")) {
            cloudHeaders()
            bearerAuth(session.accessToken)
            parameter("id", "eq.${session.user.id}")
            header("Prefer", "return=representation")
            contentType(ContentType.Application.Json)
            setBody(ProfilePatch(displayName?.trim()?.takeIf { it.isNotEmpty() }, avatarUrl?.trim()?.takeIf { it.isNotEmpty() }))
        }
        ensureSuccess(response)
        response.body<List<ProfileDto>>().firstOrNull()?.toUser()
            ?: session.user.copy(displayName = displayName, avatarUrl = avatarUrl)
    }

    private fun io.ktor.client.request.HttpRequestBuilder.cloudHeaders() {
        header("apikey", config.anonKey)
        header("X-Client-Info", "revibe-desktop")
    }

    private fun TokenResponse.toSession(): StoredSession? {
        val nested = session
        val access = access_token ?: nested?.access_token ?: return null
        val refresh = refresh_token ?: nested?.refresh_token ?: return null
        val seconds = expires_in ?: nested?.expires_in ?: 3600
        val account = (user ?: nested?.user)?.toUser() ?: return null
        return StoredSession(
            accessToken = access,
            refreshToken = refresh,
            expiresAt = now() + (seconds * 1000L),
            user = account,
        )
    }

    private fun GoTrueUser.toUser(): AccountUser {
        val metadata = user_metadata
        val name = metadata?.get("display_name")?.jsonPrimitive?.contentOrNullSafe()
            ?: metadata?.get("full_name")?.jsonPrimitive?.contentOrNullSafe()
        val avatar = metadata?.get("avatar_url")?.jsonPrimitive?.contentOrNullSafe()
        return AccountUser(
            id = id,
            email = email.orEmpty(),
            displayName = name,
            avatarUrl = avatar,
        )
    }

    private fun ProfileDto.toUser() = AccountUser(
        id = id,
        email = email.orEmpty(),
        displayName = display_name,
        avatarUrl = avatar_url,
    )

    private suspend fun ensureSuccess(response: HttpResponse) {
        if (response.status.isSuccess()) return
        val text = runCatching { response.bodyAsText() }.getOrDefault("")
        val apiMessage = runCatching {
            json.decodeFromString<GoTrueError>(text).bestMessage()
        }.getOrNull()
        val fallback = when (response.status) {
            HttpStatusCode.Unauthorized -> "Invalid email or password."
            HttpStatusCode.TooManyRequests -> "Too many attempts. Wait a moment and try again."
            HttpStatusCode.Conflict -> "An account with this email already exists."
            else -> "The cloud rejected the request (${response.status.value})."
        }
        throw AuthException(apiMessage ?: fallback, response.status.value)
    }

    private fun GoTrueError.bestMessage(): String? =
        listOfNotNull(msg, error_description, message, error)
            .firstOrNull { it.isNotBlank() }
            ?.let { raw ->
                when {
                    raw.contains("Invalid login credentials", ignoreCase = true) -> "Invalid email or password."
                    raw.contains("Email not confirmed", ignoreCase = true) -> "Confirm your email before signing in."
                    raw.contains("User already registered", ignoreCase = true) -> "An account with this email already exists."
                    raw.contains("Password should be", ignoreCase = true) -> raw
                    raw.contains("code verifier", ignoreCase = true) -> "Google sign-in could not be verified. Try again."
                    raw.contains("flow state", ignoreCase = true) -> "Google sign-in expired. Try again."
                    else -> raw
                }
            }

    private suspend fun <T> guard(block: suspend () -> T): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: AuthException) {
        throw error
    } catch (error: Exception) {
        throw AuthException("Could not reach the Revibe cloud. Check your connection and try again.", 0, error)
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
        runCatching { content }.getOrNull()?.takeIf { it.isNotBlank() }

    @Serializable private data class Credentials(val email: String, val password: String)
    @Serializable private data class PkceExchange(val auth_code: String, val code_verifier: String)
    @Serializable private data class RefreshRequest(val refresh_token: String)
    @Serializable private data class EmailRequest(val email: String)
    @Serializable private data class GoTrueError(
        val msg: String? = null,
        val error_description: String? = null,
        val message: String? = null,
        val error: String? = null,
    )

    @Serializable private data class ProfilePatch(val display_name: String?, val avatar_url: String?)
    @Serializable private data class ProfileDto(
        val id: String,
        val email: String? = null,
        val display_name: String? = null,
        val avatar_url: String? = null,
    )

    @Serializable private data class GoTrueUser(
        val id: String,
        val email: String? = null,
        val user_metadata: JsonObject? = null,
    )

    @Serializable private data class TokenResponse(
        val access_token: String? = null,
        val refresh_token: String? = null,
        val expires_in: Long? = null,
        val user: GoTrueUser? = null,
        val session: TokenResponse? = null,
    )
}
