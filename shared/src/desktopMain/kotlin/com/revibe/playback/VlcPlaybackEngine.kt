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
 * Native LibVLC engine.
 *
 * LibVLC is expensive to initialise and a MediaPlayer is expensive to rebuild.
 * Two players are kept for the whole app lifetime: one active decoder and one
 * muted preloader. A prefetched next track is opened and buffered in the
 * preloader, then the roles swap on Next with no native allocation or HTTP
 * attach at click time. Neither player is recreated per track.
 */
class VlcPlaybackEngine(
    private val vlcHome: File? = VlcNativeLocator.locate(),
) : PlaybackEngine {
    private var listener: PlaybackEngine.Listener? = null
    private var factory: MediaPlayerFactory? = null
    private var player: MediaPlayer? = null
    private var spare: MediaPlayer? = null
    private var preloadedUri: String? = null
    private var activeUri: String? = null
    private var volume = 0.85f
    private var unavailable: String? = null
    private var released = false

    /** Suppresses late events from the previous media while a new one opens. */
    @Volatile
    private var loading = false

    private val eventAdapter = object : MediaPlayerEventAdapter() {
        private fun isCurrent(mediaPlayer: MediaPlayer): Boolean =
            this@VlcPlaybackEngine.player === mediaPlayer

        private fun isSpare(mediaPlayer: MediaPlayer): Boolean =
            this@VlcPlaybackEngine.spare === mediaPlayer

        override fun opening(mediaPlayer: MediaPlayer) {
            if (isCurrent(mediaPlayer)) listener?.onBuffering(true)
        }

        override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
            if (isCurrent(mediaPlayer)) listener?.onBuffering(newCache < 100f)
        }

        override fun playing(mediaPlayer: MediaPlayer) {
            if (!isCurrent(mediaPlayer)) return
            loading = false
            listener?.onBuffering(false)
            listener?.onPlayingChanged(true)
            mediaPlayer.status().length().takeIf { it > 0 }?.let { listener?.onReady(it) }
        }

        override fun paused(mediaPlayer: MediaPlayer) {
            if (isCurrent(mediaPlayer) && !loading) listener?.onPlayingChanged(false)
        }

        override fun stopped(mediaPlayer: MediaPlayer) {
            if (isCurrent(mediaPlayer) && !loading) listener?.onPlayingChanged(false)
        }

        override fun finished(mediaPlayer: MediaPlayer) {
            if (isSpare(mediaPlayer)) {
                preloadedUri = null
                return
            }
            if (!isCurrent(mediaPlayer) || loading) return
            listener?.onPlayingChanged(false)
            listener?.onEnded()
        }

        override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
            if (isCurrent(mediaPlayer) && !loading && newTime >= 0) listener?.onProgress(newTime)
        }

        override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
            if (isCurrent(mediaPlayer) && newLength > 0) listener?.onReady(newLength)
        }

        override fun error(mediaPlayer: MediaPlayer) {
            if (isSpare(mediaPlayer)) {
                preloadedUri = null
                runCatching { mediaPlayer.controls().stop() }
                return
            }
            if (!isCurrent(mediaPlayer)) return
            loading = false
            listener?.onBuffering(false)
            listener?.onError("LibVLC could not play this stream.")
        }
    }

    init {
        val home = vlcHome
        if (home == null) {
            unavailable = "LibVLC was not found. Install VLC or set REVIBE_VLC_HOME."
        } else {
            runCatching { createFactory(home) }
                .onSuccess {
                    factory = it
                    // Pay native player construction at app startup, not on the
                    // first Play click. The player is reused for the whole session.
                    runCatching { ensurePlayer() }
                }
                .onFailure { error ->
                    unavailable = "LibVLC could not be loaded: ${error.message ?: error::class.simpleName}"
                }
        }
    }

    override fun setListener(listener: PlaybackEngine.Listener) {
        this.listener = listener
    }

    @Synchronized
    override fun load(uri: String, autoPlay: Boolean) {
        val failure = unavailable
        if (failure != null) {
            listener?.onError(failure)
            return
        }
        if (released) {
            listener?.onError("The audio engine has been released.")
            return
        }
        runCatching {
            if (preloadedUri == uri && spare != null) {
                activatePreloaded(uri, autoPlay)
                return
            }

            val current = ensurePlayer()
            if (current == null) {
                listener?.onError("LibVLC is not initialized.")
                return
            }
            // Keep a spare that was warmed for a different row. On a new queue
            // the next prefetch may finish before this current row resolves; the
            // controller will replace stale warm-ups on its next prefetch.
            loading = true
            // Reusing one player means the previous media must be stopped first;
            // stopping avoids old audio continuing under the new track's state.
            runCatching { current.controls().stop() }
            listener?.onBuffering(true)
            activeUri = uri
            val mrl = mediaMrl(uri)
            val started = if (autoPlay) {
                current.media().play(mrl)
            } else {
                current.media().prepare(mrl).also { loading = false }
            }
            if (!started) {
                loading = false
                listener?.onError("This stream is not supported by LibVLC.")
            }
        }.onFailure { error ->
            loading = false
            listener?.onBuffering(false)
            listener?.onError(error.message ?: "The native audio engine could not load this track.")
        }
    }

    @Synchronized
    override fun preload(uri: String) {
        if (released || unavailable != null || uri.isBlank()) return
        if (uri == preloadedUri || uri == activeUri) return
        runCatching {
            val factory = factory ?: return
            val target = spare ?: factory.mediaPlayers().newMediaPlayer().also { created ->
                created.audio().setVolume(0)
                created.events().addMediaPlayerEventListener(eventAdapter)
                spare = created
            }
            target.controls().stop()
            target.audio().setVolume(0)
            preloadedUri = uri
            // Muted playback opens the HTTP connection and fills LibVLC's cache.
            // The active player is untouched; the roles swap on load().
            if (!target.media().play(mediaMrl(uri))) {
                preloadedUri = null
            }
        }.onFailure { preloadedUri = null }
    }

    private fun activatePreloaded(uri: String, autoPlay: Boolean) {
        val next = spare ?: return
        val previous = player
        player = next
        spare = previous
        preloadedUri = null
        activeUri = uri
        loading = false

        runCatching { previous?.controls()?.stop() }
        runCatching { previous?.audio()?.setVolume(0) }
        next.audio().setVolume((volume * 100).roundToInt())
        next.controls().setTime(0)
        if (autoPlay) {
            next.controls().play()
            listener?.onBuffering(false)
            listener?.onPlayingChanged(true)
            next.status().length().takeIf { it > 0 }?.let { listener?.onReady(it) }
        } else {
            next.controls().setPause(true)
        }
    }

    @Synchronized
    override fun play() {
        runCatching {
            player?.controls()?.play()
        }.onFailure { listener?.onError(it.message ?: "The native audio engine could not resume.") }
    }

    @Synchronized
    override fun pause() {
        runCatching { player?.controls()?.setPause(true) }
    }

    @Synchronized
    override fun seekTo(positionMs: Long) {
        runCatching { player?.controls()?.setTime(positionMs.coerceAtLeast(0)) }
    }

    @Synchronized
    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        runCatching { player?.audio()?.setVolume((this.volume * 100).roundToInt()) }
    }

    @Synchronized
    override fun stop() {
        runCatching { player?.controls()?.stop() }
    }

    @Synchronized
    override fun release() {
        released = true
        loading = false
        val active = player
        val preloaded = spare
        player = null
        spare = null
        activeUri = null
        preloadedUri = null
        listOfNotNull(active, preloaded).forEach { current ->
            runCatching { current.controls().stop() }
            runCatching { current.release() }
        }
        runCatching { factory?.release() }
        factory = null
    }

    private fun ensurePlayer(): MediaPlayer? {
        player?.let { return it }
        val mediaFactory = factory ?: return null
        return mediaFactory.mediaPlayers().newMediaPlayer().also { created ->
            created.audio().setVolume((volume * 100).roundToInt())
            created.events().addMediaPlayerEventListener(eventAdapter)
            player = created
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
