package com.revibe.playback

import uk.co.caprica.vlcj.binding.lib.LibC
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import java.io.File
import java.net.URI
import kotlin.math.roundToInt

/**
 * Native LibVLC engine. This is currently a proof-of-concept implementation
 * behind the existing [PlaybackEngine] seam; JavaFX remains the default until
 * the native engine has passed the compatibility suite.
 *
 * LibVLC handles MP4/AAC, HLS, WebM/Opus, FLAC, Vorbis and the local formats
 * that JavaFX cannot decode. SABR/UMP is a YouTube transport protocol, not a
 * media container, so it still needs a dedicated transport implementation.
 */
class VlcPlaybackEngine(
    private val vlcHome: File? = VlcNativeLocator.locate(),
) : PlaybackEngine {
    private var listener: PlaybackEngine.Listener? = null
    private var factory: MediaPlayerFactory? = null
    private var player: MediaPlayer? = null
    private var volume = 0.85f
    private var unavailable: String? = null
    private var eventAdapter: MediaPlayerEventAdapter? = null

    init {
        val home = vlcHome
        if (home == null) {
            unavailable = "LibVLC was not found. Install VLC or set REVIBE_VLC_HOME."
        } else {
            runCatching { createFactory(home) }
                .onSuccess { factory = it }
                .onFailure { error ->
                    unavailable = "LibVLC could not be loaded: ${error.message ?: error::class.simpleName}"
                }
        }
    }

    override fun setListener(listener: PlaybackEngine.Listener) {
        this.listener = listener
    }

    override fun load(uri: String, autoPlay: Boolean) {
        val failure = unavailable
        if (failure != null) {
            listener?.onError(failure)
            return
        }
        val mediaFactory = factory ?: run {
            listener?.onError("LibVLC is not initialized.")
            return
        }
        runCatching {
            disposePlayer()
            listener?.onBuffering(true)
            val current = mediaFactory.mediaPlayers().newMediaPlayer()
            player = current
            current.audio().setVolume((volume * 100).roundToInt())
            val adapter = object : MediaPlayerEventAdapter() {
                private fun isCurrent(mediaPlayer: MediaPlayer): Boolean =
                    this@VlcPlaybackEngine.player === mediaPlayer

                override fun opening(mediaPlayer: MediaPlayer) {
                    if (isCurrent(mediaPlayer)) listener?.onBuffering(true)
                }

                override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                    if (isCurrent(mediaPlayer)) listener?.onBuffering(newCache < 100f)
                }

                override fun playing(mediaPlayer: MediaPlayer) {
                    if (!isCurrent(mediaPlayer)) return
                    listener?.onBuffering(false)
                    listener?.onPlayingChanged(true)
                    mediaPlayer.status().length().takeIf { it > 0 }?.let { listener?.onReady(it) }
                }

                override fun paused(mediaPlayer: MediaPlayer) {
                    if (isCurrent(mediaPlayer)) listener?.onPlayingChanged(false)
                }

                override fun stopped(mediaPlayer: MediaPlayer) {
                    if (isCurrent(mediaPlayer)) listener?.onPlayingChanged(false)
                }

                override fun finished(mediaPlayer: MediaPlayer) {
                    if (!isCurrent(mediaPlayer)) return
                    listener?.onPlayingChanged(false)
                    listener?.onEnded()
                }

                override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                    if (isCurrent(mediaPlayer) && newTime >= 0) listener?.onProgress(newTime)
                }

                override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                    if (isCurrent(mediaPlayer) && newLength > 0) listener?.onReady(newLength)
                }

                override fun error(mediaPlayer: MediaPlayer) {
                    if (!isCurrent(mediaPlayer)) return
                    listener?.onBuffering(false)
                    listener?.onError("LibVLC could not play this stream.")
                }
            }
            eventAdapter = adapter
            current.events().addMediaPlayerEventListener(adapter)

            val mrl = mediaMrl(uri)
            if (autoPlay) {
                val started = current.media().play(mrl)
                if (!started) {
                    listener?.onError("This stream is not supported by LibVLC.")
                }
            } else if (!current.media().prepare(mrl)) {
                listener?.onError("This stream is not supported by LibVLC.")
            }
        }.onFailure { error ->
            listener?.onBuffering(false)
            listener?.onError(error.message ?: "The native audio engine could not load this track.")
        }
    }

    override fun play() {
        runCatching {
            player?.controls()?.play()
        }.onFailure { listener?.onError(it.message ?: "The native audio engine could not resume.") }
    }

    override fun pause() {
        runCatching { player?.controls()?.setPause(true) }
    }

    override fun seekTo(positionMs: Long) {
        runCatching { player?.controls()?.setTime(positionMs.coerceAtLeast(0)) }
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        runCatching { player?.audio()?.setVolume((this.volume * 100).roundToInt()) }
    }

    override fun stop() {
        runCatching { player?.controls()?.stop() }
    }

    override fun release() {
        disposePlayer()
        runCatching { factory?.release() }
        factory = null
    }

    private fun disposePlayer() {
        val current = player
        player = null
        eventAdapter = null
        if (current != null) {
            runCatching { current.controls().stop() }
            runCatching { current.release() }
        }
    }

    /** LibVLC wants a path/MRL; VLCJ does not consistently accept a file: URI. */
    private fun mediaMrl(uri: String): String {
        if (!uri.startsWith("file:", ignoreCase = true)) return uri
        return runCatching { File(URI(uri)).absolutePath }.getOrDefault(uri)
    }

    private fun createFactory(home: File): MediaPlayerFactory {
        // JNA loads libvlc from the explicit directory. Passing a null discovery
        // avoids accidentally loading a different system VLC installation.
        System.setProperty(
            "jna.library.path",
            listOfNotNull(home.absolutePath, System.getProperty("jna.library.path"))
                .joinToString(File.pathSeparator),
        )
        val plugins = VlcNativeLocator.pluginDirectory(home)
        if (plugins.isDirectory) {
            // VLC 3 no longer accepts --plugin-path; its core reads VLC_PLUGIN_PATH.
            // VLCJ's own Windows discovery uses this same process-environment call.
            runCatching { LibC.INSTANCE._putenv("VLC_PLUGIN_PATH=${plugins.absolutePath}") }
        }
        val args = buildList {
            add("--no-video")
            add("--quiet")
            add("--no-snapshot-preview")
        }
        return MediaPlayerFactory(null as NativeDiscovery?, *args.toTypedArray())
    }
}
