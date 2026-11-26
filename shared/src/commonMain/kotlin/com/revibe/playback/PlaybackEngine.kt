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

data class PlayerState(
    val current: Track? = null,
    val queue: List<Track> = emptyList(),
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
    val previewOnly: Boolean = false,
) {
    val hasNext: Boolean
        get() = when {
            queue.isEmpty() -> false
            repeat == RepeatMode.ALL -> true
            index < queue.lastIndex -> true
            else -> false
        }

    val hasPrevious: Boolean
        get() = queue.isNotEmpty() && (index > 0 || positionMs > 3000)
}
