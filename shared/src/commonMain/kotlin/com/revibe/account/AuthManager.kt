package com.revibe.account

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single source of truth for the signed-in account. Tokens stay private to this
 * class and the encrypted [AuthSessionStore]; only [state] is exposed to the UI.
 */
class AuthManager(
    private val config: CloudConfig,
    private val api: AuthApi,
    private val sessionStore: AuthSessionStore,
    private val oauth: OAuthLauncher? = null,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val mutex = Mutex()
    private var session: StoredSession? = null

    private val _state = MutableStateFlow<AccountState>(
        if (config.isConfigured) AccountState.Restoring else AccountState.NotConfigured(config.validationError.orEmpty()),
    )
    val state: StateFlow<AccountState> = _state.asStateFlow()

    val isConfigured: Boolean get() = config.isConfigured

    suspend fun restore() {
        if (!config.isConfigured) {
            _state.value = AccountState.NotConfigured(config.validationError.orEmpty())
            return
        }
        val stored = runCatching { sessionStore.load() }.getOrNull()
        if (stored == null) {
            _state.value = AccountState.SignedOut()
            return
        }
        session = stored
        _state.value = AccountState.SignedIn(stored.user)
        try {
            val valid = validSessionLocked(stored, refreshAheadMillis = 0)
            val profile = api.fetchProfile(valid)
            session = valid.copy(user = profile)
            persist(valid.copy(user = profile))
            _state.value = AccountState.SignedIn(profile)
        } catch (error: AuthException) {
            when {
                error.statusCode == 401 || error.statusCode == 400 -> expireSession(error.message)
                error.isNetwork -> _state.value = AccountState.SignedIn(stored.user, offline = true)
                else -> _state.value = AccountState.SignedIn(stored.user)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            _state.value = AccountState.SignedIn(stored.user)
        }
    }

    suspend fun signIn(email: String, password: String) {
        requireConfigured()
        val normalized = email.trim()
        AuthValidation.validateLogin(normalized, password).firstMessage?.let { throw IllegalArgumentException(it) }
        adoptSession(api.signIn(normalized, password))
    }

    /**
     * Email/password signup. Supabase may answer with a confirmation requirement
     * instead of a session; both outcomes are surfaced to the UI.
     */
    suspend fun signUp(email: String, password: String) {
        requireConfigured()
        val normalized = email.trim()
        val errors = AuthValidation.validateSignUp(normalized, password, password)
        (errors.email ?: errors.password)?.let { throw IllegalArgumentException(it) }
        when (val outcome = api.signUp(normalized, password)) {
            is SignUpOutcome.ConfirmationRequired -> _state.value = AccountState.AwaitingConfirmation(normalized)
            is SignUpOutcome.SignedIn -> {
                mutex.withLock {
                    session = outcome.session
                    persist(outcome.session)
                    _state.value = AccountState.SignedIn(outcome.session.user)
                }
            }
        }
    }

    /**
     * Google sign-in through Supabase's PKCE flow. The consent page is opened in
     * the system browser; Revibe only ever sees the one-time authorization code.
     */
    suspend fun signInWithGoogle() {
        requireConfigured()
        val launcher = oauth
            ?: throw AuthException("Google sign-in is not available in this build. Use email and password instead.", 0)
        if (!launcher.isConfigured) {
            throw AuthException(
                "Google sign-in is not configured. Enable the Google provider in Supabase and add the Revibe redirect URL.",
                0,
            )
        }
        val authorization = try {
            launcher.authorize("google")
        } catch (error: CancellationException) {
            throw error
        } catch (error: OAuthException) {
            throw AuthException(error.message ?: "Google sign-in failed. Try again.", 0, error)
        } catch (error: Exception) {
            throw AuthException("Google sign-in could not be completed. Try again.", 0, error)
        }
        adoptSession(api.exchangeOAuthCode(authorization.code, authorization.codeVerifier))
    }

    suspend fun signOut() {
        val token = mutex.withLock { session?.accessToken }
        if (token != null) api.signOut(token)
        mutex.withLock {
            session = null
            runCatching { sessionStore.clear() }
            _state.value = AccountState.SignedOut()
        }
    }

    suspend fun requestPasswordReset(email: String) {
        requireConfigured()
        val normalized = email.trim()
        require(normalized.isNotEmpty()) { "Enter your email address." }
        require(AuthValidation.isValidEmail(normalized)) { "Enter a valid email address." }
        api.requestPasswordReset(normalized)
    }

    suspend fun updateProfile(displayName: String, avatarUrl: String) {
        val current = validSession()
        val user = api.updateProfile(current, displayName, avatarUrl)
        mutex.withLock {
            val active = session ?: current
            session = active.copy(user = user)
            persist(active.copy(user = user))
            _state.value = AccountState.SignedIn(user)
        }
    }

    /** Pulls profile changes made on another device without forcing a restart. */
    suspend fun refreshProfile() {
        val current = validSession()
        val user = api.fetchProfile(current)
        mutex.withLock {
            val active = session ?: current
            if (active.user.id != user.id || active.user == user) return@withLock
            session = active.copy(user = user)
            persist(active.copy(user = user))
            _state.value = AccountState.SignedIn(user)
        }
    }

    /** Returns a session whose access token is usable for at least another minute. */
    suspend fun validSession(): StoredSession = mutex.withLock {
        val current = session ?: throw AuthException("Sign in to sync your account.", 401)
        try {
            validSessionLocked(current, refreshAheadMillis = 60_000)
        } catch (error: AuthException) {
            if (error.statusCode == 401 || error.statusCode == 400) expireSession(error.message)
            throw error
        }
    }

    /** Forces a refresh after a 401 from PostgREST, then returns the new session. */
    suspend fun forceRefresh(): StoredSession = mutex.withLock {
        val current = session ?: throw AuthException("Sign in to sync your account.", 401)
        try {
            refreshLocked(current)
        } catch (error: AuthException) {
            if (error.statusCode == 401 || error.statusCode == 400) expireSession(error.message)
            throw error
        }
    }

    suspend fun clearExpiredSession() {
        mutex.withLock { expireSession("Your session expired. Sign in again.") }
    }

    private fun requireConfigured() {
        if (!config.isConfigured) {
            throw AuthException(config.validationError ?: "Cloud sign-in is not configured.", 0)
        }
    }

    /** Adopts a fresh session and lets a profile fetch refine it without blocking sign-in. */
    private suspend fun adoptSession(fresh: StoredSession) {
        mutex.withLock {
            session = fresh
            persist(fresh)
            _state.value = AccountState.SignedIn(fresh.user)
        }
        runCatching {
            val profile = api.fetchProfile(fresh)
            mutex.withLock {
                val current = session
                if (current != null && current.user.id == profile.id) {
                    session = current.copy(user = profile)
                    persist(current.copy(user = profile))
                    _state.value = AccountState.SignedIn(profile)
                }
            }
        }
    }

    private suspend fun validSessionLocked(current: StoredSession, refreshAheadMillis: Long): StoredSession {
        if (now() < current.expiresAt - refreshAheadMillis) return current
        return refreshLocked(current)
    }

    private suspend fun refreshLocked(current: StoredSession): StoredSession {
        val refreshed = api.refresh(current.refreshToken)
        val merged = refreshed.copy(
            user = if (refreshed.user.id == current.user.id) refreshed.user else current.user,
        )
        session = merged
        persist(merged)
        _state.value = AccountState.SignedIn(merged.user)
        return merged
    }

    private suspend fun expireSession(message: String?) {
        session = null
        runCatching { sessionStore.clear() }
        _state.value = AccountState.SignedOut(message ?: "Your session expired. Sign in again.")
    }

    private suspend fun persist(value: StoredSession) {
        runCatching { sessionStore.save(value) }
    }
}
