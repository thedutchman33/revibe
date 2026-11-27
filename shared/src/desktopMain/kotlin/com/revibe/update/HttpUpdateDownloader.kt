package com.revibe.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration

/**
 * Streams one HTTPS installer to a .part file, hashing as it writes. Redirects
 * are checked again so an HTTPS URL cannot downgrade to HTTP.
 */
class HttpUpdateDownloader : UpdateDownloader {
    override suspend fun download(
        url: String,
        destinationPath: String,
        onProgress: (receivedBytes: Long, totalBytes: Long?) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        require(url.startsWith("https://", ignoreCase = true)) { "Installer URL must use HTTPS." }
        val destination = File(destinationPath)
        destination.parentFile?.mkdirs()
        val part = File(destination.parentFile, destination.name + ".part")
        part.delete()
        destination.delete()

        try {
            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
            val request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(15))
                .header("User-Agent", "Revibe updater")
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            if (response.statusCode() != 200) {
                throw UpdateException("Update download failed with HTTP ${response.statusCode()}.")
            }
            if (!response.uri().scheme.equals("https", ignoreCase = true)) {
                throw UpdateException("Update download was redirected to a non-HTTPS URL.")
            }

            val declaredLength = response.headers()
                .firstValueAsLong("Content-Length")
                .orElse(-1L)
            val total = declaredLength.takeIf { it >= 0L }
            onProgress(0L, total)
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            var lastReport = 0L
            response.body().use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        received += read
                        val now = System.currentTimeMillis()
                        if (now - lastReport >= 150L) {
                            lastReport = now
                            onProgress(received, total)
                        }
                    }
                }
            }
            if (total != null && received != total) {
                throw UpdateException(
                    "Update download ended early ($received of $total bytes).",
                )
            }
            onProgress(received, total ?: received)
            runCatching {
                Files.move(
                    part.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            }.onFailure {
                Files.move(part.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        } catch (cancelled: CancellationException) {
            part.delete()
            destination.delete()
            throw cancelled
        } catch (error: Exception) {
            part.delete()
            destination.delete()
            if (error is UpdateException) throw error
            throw UpdateException("The update download was interrupted.", error)
        }
    }
}
