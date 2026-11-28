package com.revibe.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Streams one HTTPS installer to a .part file, hashing as it writes. Redirects
 * are checked again so an HTTPS URL cannot downgrade to HTTP.
 *
 * Windows networks drop connections silently, so every stage is bounded: the
 * response must arrive within [responseTimeout], and the body must keep moving
 * within [stallTimeout]. A stalled exchange is aborted and retried instead of
 * leaving the UI on "Updating 0%" forever.
 */
class HttpUpdateDownloader(
    private val responseTimeout: Duration = Duration.ofSeconds(30),
    private val stallTimeout: Duration = Duration.ofSeconds(45),
    private val attempts: Int = 3,
    private val retryDelayMs: Long = 2_000L,
) : UpdateDownloader {
    override suspend fun download(
        url: String,
        destinationPath: String,
        onProgress: (receivedBytes: Long, totalBytes: Long?) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        require(url.startsWith("https://", ignoreCase = true)) { "Installer URL must use HTTPS." }
        val destination = File(destinationPath)
        destination.parentFile?.mkdirs()
        val part = File(destination.parentFile, destination.name + ".part")

        var failure: Exception? = null
        for (attempt in 1..attempts) {
            try {
                return@withContext downloadOnce(url, part, destination, onProgress, attempt)
            } catch (cancelled: CancellationException) {
                part.delete()
                destination.delete()
                throw cancelled
            } catch (error: Exception) {
                part.delete()
                destination.delete()
                if (error !is RetryableDownloadException) {
                    throw if (error is UpdateException) {
                        error
                    } else {
                        UpdateException("The update download was interrupted.", error)
                    }
                }
                failure = error
                if (attempt < attempts) {
                    onProgress(0L, null)
                    delay(retryDelayMs)
                }
            }
        }
        throw UpdateException(
            "${failure?.message ?: "The update download failed."} Tried $attempts times.",
            failure,
        )
    }

    private fun downloadOnce(
        url: String,
        part: File,
        destination: File,
        onProgress: (receivedBytes: Long, totalBytes: Long?) -> Unit,
        attempt: Int,
    ): String {
        part.delete()
        destination.delete()

        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
        val request = HttpRequest.newBuilder(URI.create(url))
            // Covers the response headers only; the body is covered by the stall watchdog.
            .timeout(responseTimeout)
            .header("User-Agent", "Revibe updater")
            .GET()
            .build()

        val future = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
        val response = try {
            future.get(responseTimeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (timeout: TimeoutException) {
            future.cancel(true)
            throw RetryableDownloadException(
                "The update server did not respond in time (attempt $attempt).",
                timeout,
            )
        } catch (failure: ExecutionException) {
            throw RetryableDownloadException(
                "The update server could not be reached (attempt $attempt).",
                failure.cause,
            )
        } catch (interrupted: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            throw UpdateException("The update download was interrupted.", interrupted)
        }
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
        val lastActivity = AtomicLong(System.currentTimeMillis())
        val stalled = AtomicBoolean(false)
        val watchdog = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "revibe-update-watchdog").apply { isDaemon = true }
        }
        val body = response.body()
        watchdog.scheduleWithFixedDelay(
            {
                if (System.currentTimeMillis() - lastActivity.get() >= stallTimeout.toMillis()) {
                    stalled.set(true)
                    // Closing the stream unblocks the reader with an IOException.
                    runCatching { body.close() }
                }
            },
            1L,
            1L,
            TimeUnit.SECONDS,
        )
        try {
            body.use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        received += read
                        lastActivity.set(System.currentTimeMillis())
                        val now = System.currentTimeMillis()
                        if (now - lastReport >= 150L) {
                            lastReport = now
                            onProgress(received, total)
                        }
                    }
                }
            }
        } catch (io: IOException) {
            if (stalled.get()) {
                throw RetryableDownloadException(
                    "The update download stalled at ${percent(received, total)} (attempt $attempt).",
                    io,
                )
            }
            throw RetryableDownloadException(
                "The update download was interrupted at ${percent(received, total)} (attempt $attempt).",
                io,
            )
        } finally {
            watchdog.shutdownNow()
        }
        if (stalled.get()) {
            throw RetryableDownloadException(
                "The update download stalled at ${percent(received, total)} (attempt $attempt).",
            )
        }
        if (total != null && received != total) {
            throw RetryableDownloadException(
                "The update download ended early ($received of $total bytes).",
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
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun percent(received: Long, total: Long?): String =
        total?.takeIf { it > 0L }?.let { "${(received * 100 / it).coerceIn(0, 100)}%" } ?: "0%"

    /** A failure that a fresh connection can plausibly fix. */
    private class RetryableDownloadException(message: String, cause: Throwable? = null) :
        Exception(message, cause)
}
