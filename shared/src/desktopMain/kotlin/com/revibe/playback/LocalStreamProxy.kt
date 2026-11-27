package com.revibe.playback

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * JavaFX Media chooses its demuxer from the URL/response. Signed googlevideo
 * audio URLs have no file extension, so an audio-only MP4 is rejected even
 * though the bytes are valid. This loopback proxy exposes each stream as a
 * distinct `/stream/<id>.m4a`, forwards Range requests and the resolver's
 * headers, and keeps the signed URL out of the player.
 *
 * The proxy holds multiple sources on purpose: warming the next queue row must
 * never overwrite the URL backing the track currently being decoded.
 */
class LocalStreamProxy : AutoCloseable {
    private data class Source(
        val url: String,
        val headers: Map<String, String>,
        val contentType: String,
    )

    private val sources = ConcurrentHashMap<String, Source>()
    private val sequence = AtomicLong()

    private val executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "revibe-stream-proxy").apply { isDaemon = true }
    }

    private val server: HttpServer = HttpServer.create(
        InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
        0,
    ).apply {
        createContext("/") { exchange -> handle(exchange) }
        this.executor = this@LocalStreamProxy.executor
        start()
    }

    fun expose(url: String, headers: Map<String, String>, mimeType: String?): String {
        val id = sequence.incrementAndGet().toString(36)
        sources[id] = Source(
            url = url,
            headers = headers.filterKeys { it.isNotBlank() && !it.equals("Host", ignoreCase = true) },
            contentType = mimeType?.takeIf { it.contains("mp4", ignoreCase = true) } ?: "audio/mp4",
        )
        // Queue prefetch is bounded; this only prevents a long session from
        // retaining every historical token. The active/next sources are tiny.
        while (sources.size > MAX_SOURCES) {
            sources.keys.firstOrNull()?.let(sources::remove)
        }
        return "http://127.0.0.1:${server.address.port}/stream/$id.m4a"
    }

    private fun handle(exchange: HttpExchange) {
        val id = exchange.requestURI.path
            .substringAfterLast('/', "")
            .substringBefore('.')
        val current = sources[id]
        if (current == null) {
            exchange.sendResponseHeaders(404, 0)
            exchange.close()
            return
        }
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(current.url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 35_000
                requestMethod = exchange.requestMethod.takeIf { it == "HEAD" } ?: "GET"
                current.headers.forEach { (name, value) -> setRequestProperty(name, value) }
                exchange.requestHeaders["Range"]?.firstOrNull()?.let { setRequestProperty("Range", it) }
            }
            val status = connection.responseCode
            exchange.responseHeaders.add("Content-Type", current.contentType)
            exchange.responseHeaders.add("Accept-Ranges", connection.getHeaderField("Accept-Ranges") ?: "bytes")
            connection.getHeaderField("Content-Length")?.let { exchange.responseHeaders.add("Content-Length", it) }
            connection.getHeaderField("Content-Range")?.let { exchange.responseHeaders.add("Content-Range", it) }

            if (exchange.requestMethod == "HEAD") {
                exchange.sendResponseHeaders(status, -1)
                return
            }
            val length = connection.contentLengthLong.takeIf { it > 0 } ?: 0
            exchange.sendResponseHeaders(status, if (status == 206) 0 else length)
            connection.inputStream.use { input ->
                exchange.responseBody.use { output -> input.copyTo(output, 64 * 1024) }
            }
        } catch (_: Exception) {
            runCatching {
                if (exchange.responseCode == -1) exchange.sendResponseHeaders(502, 0)
            }
        } finally {
            runCatching { exchange.close() }
            connection?.disconnect()
        }
    }

    override fun close() {
        sources.clear()
        runCatching { server.stop(0) }
        runCatching { executor.shutdownNow() }
    }

    private companion object {
        const val MAX_SOURCES = 32
    }
}
