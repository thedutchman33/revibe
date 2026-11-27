package com.revibe.playback

import com.revibe.model.MusicSourceId
import com.revibe.model.Track
import com.revibe.youtube.YouTubeMusicSource

/** A playable URI plus an optional user-facing note. Never contains a signed URL. */
data class ResolvedStream(
    val uri: String?,
    val message: String? = null,
    val note: String? = null,
)

/**
 * Resolves a track immediately before playback. iTune/local tracks already
 * carry a URI; YouTube Music tracks need a fresh signed stream per session.
 */
interface TrackStreamResolver {
    suspend fun resolve(track: Track, forceRefresh: Boolean = false): ResolvedStream
}

class DefaultTrackStreamResolver(private val youtube: YouTubeMusicSource?) : TrackStreamResolver {
    override suspend fun resolve(track: Track, forceRefresh: Boolean): ResolvedStream {
        val source = youtube
        if (track.source == MusicSourceId.YOUTUBE_MUSIC && source != null) {
            return runCatching {
                val stream = source.resolveStream(track, forceRefresh)
                ResolvedStream(stream.url, note = stream.note)
            }.getOrElse { error ->
                ResolvedStream(null, error.message ?: "The catalog could not resolve this track.")
            }
        }
        return ResolvedStream(track.streamUrl)
    }
}
