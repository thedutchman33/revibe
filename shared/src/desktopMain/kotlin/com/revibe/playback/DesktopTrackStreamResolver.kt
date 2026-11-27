package com.revibe.playback

import com.revibe.model.MusicSourceId
import com.revibe.model.Track
import com.revibe.youtube.YouTubeMusicSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Desktop implementation: local file URIs pass through, YouTube streams are
 * resolved to their signed URL. FFmpeg opens the signed URL directly with the
 * resolver's headers, which is also the URL shape its demuxer knows how to
 * seek; a loopback re-server can break seeks at some byte boundaries.
 *
 * Resolved URLs are retained until the signed source URL is close to expiry.
 * That makes a queue prefetch and the subsequent click return the exact same
 * URI, which lets the engine preload it and start immediately.
 */
class DesktopTrackStreamResolver(
    private val youtube: YouTubeMusicSource,
) : TrackStreamResolver, AutoCloseable {
    private data class Prepared(
        val stream: ResolvedStream,
        val validUntilMs: Long,
    )

    private val prepared = mutableMapOf<String, Prepared>()
    private val preparedMutex = Mutex()

    override suspend fun resolve(track: Track, forceRefresh: Boolean): ResolvedStream {
        if (track.source != MusicSourceId.YOUTUBE_MUSIC) {
            return ResolvedStream(track.streamUrl)
        }
        if (!forceRefresh) {
            cached(track.id)?.let { return it }
        } else {
            preparedMutex.withLock { prepared.remove(track.id) }
        }
        return try {
            val stream = youtube.resolveStream(track, forceRefresh)
            logSelected(track, stream)
            DesktopStreamHeaders.put(stream.url, stream.headers)
            val resolved = ResolvedStream(
                stream.url,
                note = stream.note,
                expiresAtEpochMs = stream.expiresAtEpochMs,
            )
            remember(track.id, resolved)
            resolved
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            ResolvedStream(null, error.message ?: "YouTube Music could not resolve this track.")
        }
    }

    /**
     * Resolves the next stream without touching the active audio engine. The
     * returned signed URI stays valid until the signed URL expiry.
     */
    override suspend fun prefetch(track: Track): ResolvedStream? {
        if (track.source != MusicSourceId.YOUTUBE_MUSIC) return null
        cached(track.id)?.let { return it }
        return resolve(track).takeIf { it.uri != null }
    }

    override fun close() {
        DesktopStreamHeaders.clear()
    }

    /** Selection evidence for installed runs; contains no signed URL or headers. */
    private fun logSelected(track: com.revibe.model.Track, stream: com.revibe.youtube.YouTubeStream) {
        val line = "[stream] selected track=\"${track.title}\" itag=${stream.itag} " +
            "mime=${stream.mimeType} codec=${stream.codecs ?: "unknown"} " +
            "bitrate=${stream.bitrate?.let { "$it bps" } ?: "unknown"} " +
            "sampleRate=${stream.sampleRate?.let { "$it Hz" } ?: "unknown"} " +
            "client=${stream.clientName}"
        println(line)
        val path = System.getenv("REVIBE_DEBUG_LOG")
            ?: System.getProperty("revibe.debugLog")
            ?: return
        runCatching { File(path).appendText("${System.currentTimeMillis()} $line\n") }
    }

    private suspend fun cached(trackId: String): ResolvedStream? = preparedMutex.withLock {
        val current = prepared[trackId] ?: return@withLock null
        if (System.currentTimeMillis() >= current.validUntilMs) {
            prepared.remove(trackId)
            return@withLock null
        }
        current.stream
    }

    private suspend fun remember(trackId: String, stream: ResolvedStream) {
        val now = System.currentTimeMillis()
        val validUntil = stream.expiresAtEpochMs
            ?.minus(EXPIRY_SAFETY_MS)
            ?: (now + DEFAULT_TTL_MS)
        if (validUntil <= now) return
        preparedMutex.withLock {
            prepared[trackId] = Prepared(stream, validUntil)
            while (prepared.size > MAX_PREPARED) {
                val oldest = prepared.entries.firstOrNull { it.value.validUntilMs <= now }
                    ?: prepared.entries.firstOrNull()
                if (oldest == null) break
                prepared.remove(oldest.key)
            }
        }
    }

    private companion object {
        const val MAX_PREPARED = 24
        const val EXPIRY_SAFETY_MS = 30_000L
        const val DEFAULT_TTL_MS = 10L * 60L * 1_000L
    }
}
