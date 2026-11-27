package com.revibe.account

import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Base64
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopOAuthLauncherTest {

    private val config = CloudConfig("https://example.supabase.co", "anon-key")

    @Test
    fun `authorize builds a PKCE URL and accepts a matching loopback callback`() = runBlocking {
        var authorizeUrl = ""
        var challenge = ""
        val launcher = DesktopOAuthLauncher(
            config = config,
            port = 0,
            timeoutMillis = 5_000,
            openBrowser = { url ->
                authorizeUrl = url
                val params = query(url)
                challenge = params.getValue("code_challenge")
                val redirect = URLDecoder.decode(params.getValue("redirect_to"), Charsets.UTF_8)
                callbackLater("$redirect&code=code-1")
            },
        )

        val authorization = launcher.authorize("google")

        val params = query(authorizeUrl)
        assertEquals("google", params["provider"])
        assertEquals("S256", params["code_challenge_method"])
        assertTrue(params.getValue("redirect_to").startsWith("http://127.0.0.1:"))
        assertTrue(params.getValue("redirect_to").contains("/auth/callback?state="))
        assertTrue(authorizeUrl.startsWith("https://example.supabase.co/auth/v1/authorize"))
        assertTrue(!params.containsKey("state"))
        assertEquals("code-1", authorization.code)
        assertTrue(authorization.codeVerifier.length >= 43)
        assertEquals(pkceChallenge(authorization.codeVerifier), challenge)
    }

    @Test
    fun `a mismatched state is rejected`() = runBlocking {
        val launcher = DesktopOAuthLauncher(
            config = config,
            port = 0,
            timeoutMillis = 5_000,
            openBrowser = { url ->
                val redirect = URLDecoder.decode(query(url).getValue("redirect_to"), Charsets.UTF_8)
                val callback = redirect.substringBefore('?')
                callbackLater("$callback?code=code-1&state=not-the-state")
            },
        )

        val error = runCatching { launcher.authorize("google") }.exceptionOrNull()

        assertIs<OAuthException>(error)
        assertTrue(error.message.orEmpty().contains("state"))
    }

    @Test
    fun `a provider denial is reported as a cancellation`() = runBlocking {
        val launcher = DesktopOAuthLauncher(
            config = config,
            port = 0,
            timeoutMillis = 5_000,
            openBrowser = { url ->
                val redirect = URLDecoder.decode(query(url).getValue("redirect_to"), Charsets.UTF_8)
                callbackLater("$redirect&error=access_denied&error_description=User+denied")
            },
        )

        val error = runCatching { launcher.authorize("google") }.exceptionOrNull()

        assertIs<OAuthException>(error)
        assertTrue(error.cancelled)
        assertEquals("Google sign-in was cancelled.", error.message)
    }

    @Test
    fun `an abandoned browser round trip times out`() = runBlocking {
        val launcher = DesktopOAuthLauncher(
            config = config,
            port = 0,
            timeoutMillis = 300,
            openBrowser = { },
        )

        val error = runCatching { launcher.authorize("google") }.exceptionOrNull()

        assertIs<OAuthException>(error)
        assertTrue(error.cancelled)
        assertTrue(error.message.orEmpty().contains("timed out"))
    }

    private fun callbackLater(url: String) {
        thread(isDaemon = true) {
            runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 2_000
                connection.readTimeout = 2_000
                connection.responseCode
                connection.inputStream.use { it.readBytes() }
                connection.disconnect()
            }
        }
    }

    private fun query(url: String): Map<String, String> =
        URI(url).rawQuery.orEmpty()
            .split('&')
            .filter { it.isNotBlank() }
            .associate { part ->
                URLDecoder.decode(part.substringBefore('='), Charsets.UTF_8) to
                    URLDecoder.decode(part.substringAfter('=', ""), Charsets.UTF_8)
            }

    private fun pkceChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.encodeToByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
