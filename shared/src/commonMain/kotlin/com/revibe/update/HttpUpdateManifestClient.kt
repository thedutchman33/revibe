package com.revibe.update

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText

class HttpUpdateManifestClient(private val client: HttpClient) : UpdateManifestClient {
    override suspend fun fetch(url: String): String {
        if (!url.startsWith("https://", ignoreCase = true)) {
            throw UpdateException("Update checks require an HTTPS manifest URL.")
        }
        val response = try {
            client.get(url) {
                header("Cache-Control", "no-cache")
                header("Accept", "application/json")
            }
        } catch (error: Exception) {
            throw UpdateException("Could not reach the update server.", error)
        }
        if (response.status.value !in 200..299) {
            throw UpdateException("Update server returned HTTP ${response.status.value}.")
        }
        if (!response.call.request.url.protocol.name.equals("https", ignoreCase = true)) {
            throw UpdateException("The update server redirected to a non-HTTPS URL.")
        }
        return try {
            response.bodyAsText()
        } catch (error: Exception) {
            throw UpdateException("Could not read the update manifest.", error)
        }
    }
}
