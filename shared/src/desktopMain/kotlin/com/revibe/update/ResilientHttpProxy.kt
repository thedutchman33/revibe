package com.revibe.update

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Loopback HTTP CONNECT proxy with address failover for update traffic.
 *
 * GitHub's release CDN rotates a handful of IPv4 addresses. On some networks one
 * of those edges is blackholed: TCP connect to it never answers, while the other
 * addresses reply in milliseconds. Java's HTTP clients connect to the first
 * resolved address only (Ktor CIO, `HttpURLConnection` and `java.net.http` all
 * behaved the same in testing), so an update can hang forever even though the
 * service is healthy.
 *
 * The updater points its clients at this proxy. Each CONNECT target is resolved
 * here and its addresses are tried in order with a short timeout; the first live
 * one is tunnelled byte-for-byte. TLS is untouched, so SNI and certificate
 * validation still happen between the real client and GitHub.
 */
class ResilientHttpProxy(
    private val connectTimeoutMs: Int = 1_200,
    private val resolver: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
) : AutoCloseable {
    private val running = AtomicBoolean(true)
    private val server = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))

    val port: Int get() = server.localPort

    init {
        thread(name = "revibe-update-proxy", isDaemon = true) {
            while (running.get()) {
                val client = try {
                    server.accept()
                } catch (_: Exception) {
                    break
                }
                thread(name = "revibe-update-proxy-conn", isDaemon = true) { handle(client) }
            }
        }
    }

    private fun handle(client: Socket) {
        client.use {
            runCatching {
                client.soTimeout = HEADER_TIMEOUT_MS
                val input = BufferedInputStream(client.getInputStream())
                val output = BufferedOutputStream(client.getOutputStream())
                val header = readHeader(input) ?: return@use
                val requestLine = header.lineSequence().firstOrNull().orEmpty().trim()
                val parts = requestLine.split(' ')
                if (parts.size < 2 || !parts[0].equals("CONNECT", ignoreCase = true)) {
                    writeStatus(output, "405 Method Not Allowed")
                    return@use
                }
                val (host, port) = splitHostPort(parts[1]) ?: run {
                    writeStatus(output, "400 Bad Request")
                    return@use
                }
                val upstream = connectFirst(host, port) ?: run {
                    writeStatus(output, "502 Bad Gateway")
                    return@use
                }
                upstream.use {
                    output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                    output.flush()
                    client.soTimeout = 0
                    upstream.soTimeout = 0
                    val toUpstream = thread(name = "revibe-update-proxy-up", isDaemon = true) {
                        runCatching { client.getInputStream().copyTo(upstream.getOutputStream(), BUFFER_SIZE) }
                        runCatching { upstream.shutdownOutput() }
                    }
                    runCatching { upstream.getInputStream().copyTo(client.getOutputStream(), BUFFER_SIZE) }
                    toUpstream.join(2_000)
                }
            }
        }
    }

    /** An address that cannot be reached costs one short timeout before the next is tried. */
    private fun connectFirst(host: String, port: Int): Socket? {
        val addresses = runCatching { resolver(host) }.getOrNull().orEmpty()
        for (address in addresses) {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(address, port), connectTimeoutMs)
                socket.tcpNoDelay = true
                return socket
            } catch (_: Exception) {
                runCatching { socket.close() }
            }
        }
        return null
    }

    private fun writeStatus(output: BufferedOutputStream, status: String) {
        output.write("HTTP/1.1 $status\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".toByteArray())
        output.flush()
    }

    private fun readHeader(input: BufferedInputStream): String? {
        val builder = StringBuilder()
        var lastFour = 0
        while (builder.length < MAX_HEADER_BYTES) {
            val byte = input.read()
            if (byte < 0) return null
            builder.append(byte.toChar())
            lastFour = (lastFour shl 8) or byte
            if (lastFour == 0x0D0A0D0A) return builder.toString()
        }
        return null
    }

    /** CONNECT uses `host:port`, with IPv6 literals bracketed. */
    private fun splitHostPort(value: String): Pair<String, Int>? {
        val target = value.trim()
        if (target.isEmpty()) return null
        if (target.startsWith("[")) {
            val end = target.indexOf(']')
            if (end < 0) return null
            val host = target.substring(1, end)
            val port = target.substring(end + 1).removePrefix(":").toIntOrNull() ?: 443
            return host to port
        }
        val separator = target.lastIndexOf(':')
        if (separator < 0) return target to 443
        val host = target.substring(0, separator)
        if (host.isEmpty()) return null
        val port = target.substring(separator + 1).toIntOrNull() ?: return null
        return host to port
    }

    override fun close() {
        running.set(false)
        runCatching { server.close() }
    }

    private companion object {
        const val HEADER_TIMEOUT_MS = 15_000
        const val MAX_HEADER_BYTES = 32_768
        const val BUFFER_SIZE = 64 * 1024
    }
}
