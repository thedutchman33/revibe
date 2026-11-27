package com.revibe.account

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Publishable Supabase credentials for the desktop client. The service-role key
 * is deliberately rejected: shipping it would bypass every RLS policy.
 */
data class CloudConfig(
    val url: String,
    val anonKey: String,
) {
    val baseUrl: String = url.trim().trimEnd('/')

    val validationError: String? by lazy {
        when {
            baseUrl.isBlank() -> "Cloud sync is not configured. Set REVIBE_SUPABASE_URL and REVIBE_SUPABASE_ANON_KEY."
            anonKey.isBlank() -> "Cloud sync is not configured. The Supabase anon key is missing."
            !isAcceptableUrl(baseUrl) -> "Supabase URL must use https:// (localhost is allowed for development)."
            isServiceRoleKey(anonKey) -> "Service-role keys are not allowed in Revibe. Use the publishable/anon key."
            else -> null
        }
    }

    val isConfigured: Boolean get() = validationError == null

    fun authUrl(path: String): String = "$baseUrl/auth/v1$path"
    fun restUrl(path: String): String = "$baseUrl/rest/v1$path"

    companion object {
        private fun isAcceptableUrl(value: String): Boolean {
            if (value.startsWith("https://")) return true
            return value.startsWith("http://localhost") || value.startsWith("http://127.0.0.1")
        }

        @OptIn(ExperimentalEncodingApi::class)
        internal fun isServiceRoleKey(key: String): Boolean {
            if (key.startsWith("sb_secret_")) return true
            if (key.contains("service_role")) return true
            val parts = key.split('.')
            if (parts.size != 3) return false
            val padded = parts[1] + "=".repeat((4 - parts[1].length % 4) % 4)
            val payload = runCatching { Base64.UrlSafe.decode(padded).decodeToString() }.getOrNull() ?: return false
            return payload.contains("\"role\":\"service_role\"")
        }
    }
}

object CloudEnvironment {
    const val URL_KEY = "REVIBE_SUPABASE_URL"
    const val ANON_KEY = "REVIBE_SUPABASE_ANON_KEY"
}
