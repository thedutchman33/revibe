package com.revibe.playback

import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration

/**
 * Desktop audio engine built on JavaFX Media. It handles MP3, AAC/M4A, WAV and
 * AIFF, both local files and progressive HTTP previews, and reports real
 * playback position from the decoder.
 *
 * FLAC/OGG/OPUS need a different decoder; the intended next step is a native
 * engine behind this same interface (see docs/PLAYBACK_ENGINE.md).
 */
class JavaFxPlaybackEngine : PlaybackEngine {
    private var listener: PlaybackEngine.Listener? = null
    private var player: MediaPlayer? = null
    private var volume: Float = 0.85f
    private var startupFailure: String? = null

    init {
        runCatching {
            Platform.setImplicitExit(false)
            Platform.startup { }
        }.onFailure { error ->
            // IllegalStateException just means the toolkit was already running.
            if (error !is IllegalStateException) {
                startupFailure = error.message ?: "The audio toolkit failed to start."
            }
        }
    }

    override fun setListener(listener: PlaybackEngine.Listener) {
        this.listener = listener
    }

    override fun load(uri: String, autoPlay: Boolean) {
        val failure = startupFailure
        if (failure != null) {
            listener?.onError(failure)
            return
        }
        runOnFx {
            disposePlayer()
            listener?.onBuffering(true)
            val media = Media(uri)
            val mediaPlayer = MediaPlayer(media)
            mediaPlayer.volume = volume.toDouble()
            player = mediaPlayer

            mediaPlayer.setOnReady {
                val duration = mediaPlayer.totalDuration
                val millis = if (duration == null || duration == Duration.UNKNOWN) 0L else duration.toMillis().toLong()
                listener?.onReady(millis)
                listener?.onBuffering(false)
                if (autoPlay) mediaPlayer.play()
            }
            mediaPlayer.currentTimeProperty().addListener { _, _, value ->
                if (value != null) listener?.onProgress(value.toMillis().toLong())
            }
            mediaPlayer.setOnPlaying { listener?.onPlayingChanged(true) }
            mediaPlayer.setOnPaused { listener?.onPlayingChanged(false) }
            mediaPlayer.setOnStopped { listener?.onPlayingChanged(false) }
            mediaPlayer.setOnEndOfMedia {
                listener?.onPlayingChanged(false)
                listener?.onEnded()
            }
            mediaPlayer.setOnStalled { listener?.onBuffering(true) }
            mediaPlayer.setOnError {
                val message = mediaPlayer.error?.message ?: "The audio engine could not play this track."
                listener?.onError(friendly(message))
                listener?.onBuffering(false)
            }
        }
    }

    override fun play() {
        runOnFx {
            player?.let {
                if (it.status == MediaPlayer.Status.PAUSED || it.status == MediaPlayer.Status.STOPPED) it.play()
            }
        }
    }

    override fun pause() {
        runOnFx { player?.pause() }
    }

    override fun seekTo(positionMs: Long) {
        runOnFx { player?.seek(Duration.millis(positionMs.toDouble().coerceAtLeast(0.0))) }
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        runOnFx { player?.volume = this.volume.toDouble() }
    }

    override fun stop() {
        runOnFx { player?.stop() }
    }

    override fun release() {
        runOnFx { disposePlayer() }
    }

    private fun disposePlayer() {
        runCatching {
            player?.stop()
            player?.dispose()
        }
        player = null
    }

    private fun friendly(message: String): String = when {
        message.contains("MEDIA_UNAVAILABLE", ignoreCase = true) -> "This track could not be reached. Check your connection."
        message.contains("UNSUPPORTED", ignoreCase = true) -> "This audio format is not supported by the current engine."
        message.contains("MEDIA_CORRUPTED", ignoreCase = true) -> "The audio file appears to be damaged."
        else -> message
    }

    private fun runOnFx(block: () -> Unit) {
        runCatching {
            if (Platform.isFxApplicationThread()) {
                block()
            } else {
                Platform.runLater { runCatching(block).onFailure { error -> listener?.onError(error.message ?: "Audio error") } }
            }
        }.onFailure { error ->
            listener?.onError(error.message ?: "The audio engine is unavailable.")
        }
    }
}
