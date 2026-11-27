package com.revibe.playback

import com.revibe.model.Track

/**
 * The only audio surface shared code sees. Each platform provides its own engine:
 * JavaFX Media on desktop (milestone 1), Media3 on Android, AVFoundation on Apple.
 */
interface PlaybackEngine {
    fun setListener(listener: Listener)
    fun load(uri: String, autoPlay: Boolean)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setVolume(volume: Float)
    fun stop()
    fun release()

    interface Listener {
        fun onReady(durationMs: Long)
        fun onProgress(positionMs: Long)
        fun onPlayingChanged(isPlaying: Boolean)
        fun onBuffering(isBuffering: Boolean)
        fun onEnded()
        fun onError(message: String)
    }
}

enum class RepeatMode { OFF, ALL, ONE }

/**
 * Queue rows are the source of truth. [queue] remains a derived track view so
 * existing screens and tests keep working during the queue migration.
 */
data class PlayerState(
    val current: Track? = null,
    val entries: List<QueueEntry> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val volume: Float = 0.85f,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val error: String? = null,
    val contextName: String = "",
    val autoplayEnabled: Boolean = true,
    val autoplayState: AutoplayState = AutoplayState.IDLE,
    val autoplayMessage: String? = null,
) {
    val queue: List<Track>
        get() = entries.map { it.track }

    val hasNext: Boolean
        get() = when {
            entries.isEmpty() -> false
            repeat == RepeatMode.ALL -> true
            index < entries.lastIndex -> true
            autoplayEnabled && autoplayState != AutoplayState.EXHAUSTED &&
                autoplayState != AutoplayState.FAILED -> true
            else -> false
        }

    val hasPrevious: Boolean
        get() = entries.isNotEmpty() && (index > 0 || positionMs > 3000)
}
