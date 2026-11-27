package com.revibe.provider

import com.revibe.model.Track

/**
 * Enrichment contracts. They are deliberately independent of InnerTubeX: a
 * provider may add a cover, tags or lyrics, but it can never replace playback,
 * the catalog or the native queue.
 */
interface ArtworkProvider {
    val id: String
    suspend fun artwork(query: ArtworkQuery): ArtworkCandidate?
}

data class ArtworkQuery(
    val artist: String,
    val album: String,
    val title: String,
    val durationMs: Long,
    val releaseMbid: String? = null,
    val recordingMbid: String? = null,
)

data class ArtworkCandidate(
    val url: String,
    val provider: String,
    val matchedBy: String,
    val width: Int? = null,
    val height: Int? = null,
)

data class TrackMetadata(
    val recordingMbid: String? = null,
    val releaseMbid: String? = null,
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)

interface MusicMetadataProvider {
    val id: String
    suspend fun metadata(track: Track): TrackMetadata?
}

interface LyricsProvider {
    val id: String
    suspend fun lyrics(query: LyricsQuery): String?
}

data class LyricsQuery(val artist: String, val title: String, val album: String, val durationMs: Long)

interface RecognitionProvider {
    val id: String
    suspend fun identify(sample: AudioSample): RecognizedTrack?
}

data class AudioSample(val bytes: ByteArray, val sampleRate: Int)

data class RecognizedTrack(val title: String, val artist: String, val provider: String)

interface ListeningHistoryProvider {
    val id: String
    suspend fun submit(listen: ListenEvent)
}

data class ListenEvent(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0,
    val playedAtEpochSeconds: Long = 0,
    val completed: Boolean = false,
)

interface ExternalAccountProvider {
    val id: String
    val configured: Boolean
    suspend fun connect(): Boolean
}

interface VisualBackgroundProvider {
    val id: String
    suspend fun visual(track: Track): VisualAsset?
}

data class VisualAsset(
    val uri: String,
    val kind: Kind,
    val provider: String,
    val attribution: String? = null,
) {
    enum class Kind { VIDEO, ANIMATED, STILL }
}
