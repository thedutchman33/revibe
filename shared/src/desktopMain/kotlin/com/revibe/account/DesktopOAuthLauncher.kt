package com.revibe.account

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.Desktop
import java.net.BindException
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.Executors

/**
 * Supabase's PKCE flow for desktop. The consent page opens in the user's normal
 * browser; Supabase redirects back to a loopback listener that only accepts
 * 127.0.0.1 traffic. Revibe never sees a Google password and never stores an
 * authorization code.
 */
class DesktopOAuthLauncher(
    private val config: CloudConfig,
    private val port: Int = DEFAULT_PORT,
    private val timeoutMillis: Long = 180_000,
    private val openBrowser: (String) -> Unit = ::openSystemBrowser,
) : OAuthLauncher {

    override val isConfigured: Boolean get() = config.isConfigured

    override suspend fun authorize(provider: String): OAuthAuthorization = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            throw OAuthException(config.validationError ?: "Cloud sign-in is not configured.")
        }
        val server = try {
            HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
        } catch (_: BindException) {
            throw OAuthException("Port $port is already in use. Close the other sign-in window and try again.", cancelled = true)
        }
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "revibe-oauth").apply { isDaemon = true }
        }
        server.executor = executor

        val state = randomToken(24)
        val verifier = randomToken(48)
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.encodeToByteArray()))
        val callbackPath = "/auth/callback"
        // Supabase's social-login /authorize endpoint uses its own internal
        // state and does not echo an app-supplied state. Keep our CSRF token in
        // the allowed loopback redirect instead; Supabase preserves that query
        // when it appends the one-time code.
        val redirectUri = "http://127.0.0.1:${server.address.port}$callbackPath?state=${encode(state)}"
        val authorizeUrl = buildString {
            append(config.authUrl("/authorize"))
            append("?provider=").append(encode(provider))
            append("&redirect_to=").append(encode(redirectUri))
            append("&code_challenge=").append(encode(challenge))
            append("&code_challenge_method=S256")
        }

        val pending = CompletableDeferred<Map<String, String>>()
        server.createContext(callbackPath) { exchange ->
            runCatching {
                val parameters = parseQuery(exchange.requestURI.rawQuery)
                respondWithPage(exchange, "Sign-in complete", "You can close this window and return to Revibe.")
                if (!pending.isCompleted) pending.complete(parameters)
            }.onFailure {
                respondWithPage(exchange, "Sign-in failed", "Return to Revibe and try again.")
                if (!pending.isCompleted) pending.complete(emptyMap())
            }
            exchange.close()
        }
        server.start()

        try {
            try {
                openBrowser(authorizeUrl)
            } catch (_: Exception) {
                throw OAuthException("Could not open the web browser for Google sign-in.")
            }
            val callback = withTimeoutOrNull(timeoutMillis) { pending.await() }
                ?: throw OAuthException("Google sign-in timed out. Try again.", cancelled = true)

            val errorCode = callback["error"]
            val description = callback["error_description"]
            if (!errorCode.isNullOrBlank() || !description.isNullOrBlank()) {
                val cancelled = errorCode == "access_denied"
                val message = when {
                    cancelled -> "Google sign-in was cancelled."
                    !description.isNullOrBlank() -> "Google sign-in failed: ${description.take(180)}"
                    else -> "Google sign-in failed. Try again."
                }
                throw OAuthException(message, cancelled = cancelled)
            }
            if (callback["state"] != state) {
                throw OAuthException("Google sign-in returned an unexpected state. Try again.")
            }
            val code = callback["code"]?.takeIf { it.isNotBlank() }
                ?: throw OAuthException("Google sign-in did not return an authorization code. Try again.")
            OAuthAuthorization(code = code, codeVerifier = verifier)
        } finally {
            server.stop(0)
            executor.shutdownNow()
        }
    }

    private fun respondWithPage(exchange: HttpExchange, title: String, message: String) {
        val body = """
            <!doctype html>
            <html lang="en"><head><meta charset="utf-8"><title>Revibe — $title</title></head>
            <body style="margin:0;background:#121212;color:#fff;font-family:Segoe UI,Arial,sans-serif;display:flex;min-height:100vh;align-items:center;justify-content:center">
              <main style="max-width:420px;padding:32px;text-align:center">
                <h1 style="font-size:22px">$title</h1>
                <p style="color:#b3b3b3;font-size:14px;line-height:1.5">$message</p>
              </main>
            </body></html>
        """.trimIndent().encodeToByteArray()
        exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        exchange.sendResponseHeaders(200, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    private fun parseQuery(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split('&')
            .mapNotNull { part ->
                val key = part.substringBefore('=', "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val value = part.substringAfter('=', "")
                URLDecoder.decode(key, Charsets.UTF_8) to URLDecoder.decode(value, Charsets.UTF_8)
            }
            .toMap()
    }

    private fun randomToken(bytes: Int): String {
        val buffer = ByteArray(bytes)
        SecureRandom().nextBytes(buffer)
        return base64Url(buffer)
    }

    private fun base64Url(value: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value)

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)

    companion object {
        /** Must match the redirect URL allow-listed in Supabase Authentication → URL Configuration. */
        const val DEFAULT_PORT = 8765
        const val REDIRECT_PATH = "/auth/callback"
        const val REDIRECT_URI = "http://127.0.0.1:$DEFAULT_PORT$REDIRECT_PATH"
    }
}

private fun openSystemBrowser(url: String) {
    check(Desktop.isDesktopSupported()) { "Browsing is not supported on this desktop." }
    val desktop = Desktop.getDesktop()
    check(desktop.isSupported(Desktop.Action.BROWSE)) { "No default browser is available." }
    desktop.browse(URI(url))
}
