package com.revibe.playback

import com.revibe.model.MusicSourceId
import com.revibe.model.Track
import com.revibe.youtube.YouTubeMusicSource

/**
 * Desktop implementation: local file URIs pass through, YouTube streams are
 * resolved and exposed through the loopback `.m4a` proxy.
 */
class DesktopTrackStreamResolver(
    private val youtube: YouTubeMusicSource,
) : TrackStreamResolver, AutoCloseable {
    private val proxy = LocalStreamProxy()

    override suspend fun resolve(track: Track, forceRefresh: Boolean): ResolvedStream {
        if (track.source != MusicSourceId.YOUTUBE_MUSIC) {
            return ResolvedStream(track.streamUrl)
        }
        return runCatching {
            val stream = youtube.resolveStream(track, forceRefresh)
            val localUrl = proxy.expose(stream.url, stream.headers, stream.mimeType)
            ResolvedStream(localUrl, note = stream.note)
        }.getOrElse { error ->
            ResolvedStream(null, error.message ?: "YouTube Music could not resolve this track.")
        }
    }

    override fun close() {
        proxy.close()
    }
}
