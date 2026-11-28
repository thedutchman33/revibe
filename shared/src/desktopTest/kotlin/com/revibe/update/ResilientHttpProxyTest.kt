package com.revibe.update

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A dead first address must not stop the tunnel: this reproduces the GitHub CDN
 * edge that times out while the remaining addresses answer instantly.
 */
class ResilientHttpProxyTest {

    @Test
    fun tunnellingFailsOverFromAnUnreachableAddress() {
        val target = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val responder = thread(name = "proxy-test-target", isDaemon = true) {
            runCatching {
                target.accept().use { socket ->
                    socket.getOutputStream().write("pong".toByteArray())
                    socket.getOutputStream().flush()
                    socket.getInputStream().read()
                }
            }
        }
        try {
            ResilientHttpProxy(
                connectTimeoutMs = 250,
                // 192.0.2.1 is TEST-NET-1: never routed, so the connect times out.
                resolver = {
                    listOf(
                        InetAddress.getByName("192.0.2.1"),
                        InetAddress.getByName("127.0.0.1"),
                    )
                },
            ).use { proxy ->
                val started = System.currentTimeMillis()
                Socket().use { client ->
                    client.connect(InetSocketAddress("127.0.0.1", proxy.port), 3_000)
                    client.soTimeout = 5_000
                    client.getOutputStream().apply {
                        write(
                            (
                                "CONNECT localhost:${target.localPort} HTTP/1.1\r\n" +
                                    "Host: localhost:${target.localPort}\r\n\r\n"
                                ).toByteArray(),
                        )
                        flush()
                    }
                    val input = client.getInputStream()
                    val header = buildString {
                        while (!endsWith("\r\n\r\n")) {
                            val byte = input.read()
                            if (byte < 0) break
                            append(byte.toChar())
                        }
                    }
                    assertTrue(header.startsWith("HTTP/1.1 200"), "Proxy did not establish the tunnel: $header")
                    val payload = ByteArray(4)
                    var read = 0
                    while (read < payload.size) {
                        val next = input.read(payload, read, payload.size - read)
                        if (next < 0) break
                        read += next
                    }
                    assertEquals("pong", String(payload, 0, read))
                }
                assertTrue(
                    System.currentTimeMillis() - started < 5_000,
                    "Failover took too long",
                )
            }
        } finally {
            runCatching { target.close() }
            responder.join(2_000)
        }
    }
}
