package com.revibe.playback

import com.revibe.model.MusicSourceId
import com.revibe.model.Track
import com.revibe.youtube.YouTubeMusicSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Desktop implementation: local file URIs pass through, YouTube streams are
 * resolved and exposed through the loopback `.m4a` proxy.
 *
 * Resolved proxy URIs are retained until the signed source URL is close to
 * expiry. That makes a queue prefetch and the subsequent click return the exact
 * same URI, which lets the engine preload it and start immediately.
 */
class DesktopTrackStreamResolver(
    private val youtube: YouTubeMusicSource,
) : TrackStreamResolver, AutoCloseable {
    private data class Prepared(
        val stream: ResolvedStream,
        val validUntilMs: Long,
    )

    private val proxy = LocalStreamProxy()
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
            val localUrl = proxy.expose(stream.url, stream.headers, stream.mimeType)
            val resolved = ResolvedStream(
                localUrl,
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
     * Resolves and exposes the next stream without touching the active audio
     * engine. The returned proxy URI stays valid until the signed URL expiry.
     */
    override suspend fun prefetch(track: Track): ResolvedStream? {
        if (track.source != MusicSourceId.YOUTUBE_MUSIC) return null
        cached(track.id)?.let { return it }
        return resolve(track).takeIf { it.uri != null }
    }

    override fun close() {
        proxy.close()
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
