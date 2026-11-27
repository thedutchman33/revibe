package com.revibe.account

import kotlinx.serialization.Serializable

@Serializable
data class AccountUser(
    val id: String,
    val email: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
) {
    val initials: String
        get() {
            val source = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: email.substringBefore('@')
            val words = source.split(' ', '.', '_', '-').filter { it.isNotBlank() }
            return when {
                words.size >= 2 -> (words[0].take(1) + words[1].take(1)).uppercase()
                source.isNotBlank() -> source.take(2).uppercase()
                else -> "R"
            }
        }
}

/**
 * Local encrypted session. Access and refresh tokens never leave this object,
 * are never logged and are never rendered by the UI.
 */
@Serializable
data class StoredSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val user: AccountUser,
)

sealed interface AccountState {
    data object Restoring : AccountState
    data class NotConfigured(val reason: String) : AccountState
    data class SignedOut(val message: String? = null) : AccountState
    data class AwaitingConfirmation(val email: String) : AccountState
    data class SignedIn(val user: AccountUser, val offline: Boolean = false) : AccountState
}

sealed interface SignUpOutcome {
    data class SignedIn(val session: StoredSession) : SignUpOutcome
    data class ConfirmationRequired(val email: String) : SignUpOutcome
}

/** Auth/network failure with a user-safe message. */
class AuthException(
    message: String,
    val statusCode: Int = 0,
    cause: Throwable? = null,
) : Exception(message, cause) {
    val isNetwork: Boolean get() = statusCode == 0
    val isSessionExpired: Boolean get() = statusCode == 401
}

interface AuthSessionStore {
    suspend fun load(): StoredSession?
    suspend fun save(session: StoredSession)
    suspend fun clear()
}

/** Field-level validation results so the form can mark the exact input that needs attention. */
data class AuthFormErrors(
    val email: String? = null,
    val password: String? = null,
    val confirmation: String? = null,
) {
    val hasErrors: Boolean get() = email != null || password != null || confirmation != null
    val firstMessage: String? get() = email ?: password ?: confirmation
}

/** Client-side rules; Supabase remains the authority for duplicates and server errors. */
object AuthValidation {
    const val MIN_PASSWORD_LENGTH = 8

    private val emailPattern = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")

    fun isValidEmail(value: String): Boolean = emailPattern.matches(value.trim())

    fun validateLogin(email: String, password: String): AuthFormErrors = AuthFormErrors(
        email = validateEmail(email),
        password = if (password.isEmpty()) "Enter your password." else null,
    )

    fun validateSignUp(email: String, password: String, confirmation: String): AuthFormErrors = AuthFormErrors(
        email = validateEmail(email),
        password = when {
            password.isEmpty() -> "Enter your password."
            password.length < MIN_PASSWORD_LENGTH -> "Use at least $MIN_PASSWORD_LENGTH characters for your password."
            else -> null
        },
        confirmation = when {
            confirmation.isEmpty() -> "Confirm your password."
            confirmation != password -> "Passwords do not match."
            else -> null
        },
    )

    fun validateEmail(email: String): String? = when {
        email.isBlank() -> "Enter your email address."
        !isValidEmail(email) -> "Enter a valid email address."
        else -> null
    }
}

/** PKCE code returned by the browser round trip. The verifier never leaves the process. */
data class OAuthAuthorization(val code: String, val codeVerifier: String)

/**
 * Opens the provider consent page in the system browser and waits for the loopback
 * callback. Implementations must validate OAuth state and must never ask for or
 * store a provider password.
 */
interface OAuthLauncher {
    val isConfigured: Boolean

    suspend fun authorize(provider: String): OAuthAuthorization
}

/** Browser round trip failure. [cancelled] is true when the user abandoned the flow. */
class OAuthException(message: String, val cancelled: Boolean = false) : Exception(message)
