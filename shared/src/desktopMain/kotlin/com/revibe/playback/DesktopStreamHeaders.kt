package com.revibe.playback

import java.util.concurrent.ConcurrentHashMap

/**
 * Headers belonging to a signed YouTube stream URL. FFmpeg needs the resolver's
 * headers when it opens the stream directly; keeping them beside the URI avoids
 * putting signed URLs or cookies into the playback API or the UI.
 */
internal object DesktopStreamHeaders {
    private val headersByUri = ConcurrentHashMap<String, Map<String, String>>()

    fun put(uri: String, headers: Map<String, String>) {
        val clean = headers.filterKeys { it.isNotBlank() && !it.equals("Host", ignoreCase = true) }
        if (clean.isEmpty()) headersByUri.remove(uri) else headersByUri[uri] = clean
    }

    fun get(uri: String): Map<String, String> = headersByUri[uri] ?: emptyMap()

    fun remove(uri: String) {
        headersByUri.remove(uri)
    }

    fun clear() = headersByUri.clear()
}
