package com.revibe.playback

import org.bytedeco.javacv.FFmpegFrameGrabber
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Windows desktop playback engine.
 *
 * FFmpeg (JavaCPP/Bytedeco) demuxes and decodes in-process. PCM goes to Java
 * Sound's SourceDataLine, the same Windows output path used by Metrolist's
 * desktop client. The engine owns one active [FfmpegAudioPlayer] and at most
 * one prepared player; each player owns its decoder, render queue, line and
 * clock. That is Metrolist's desktop shape, without LibVLC, vlcj, an external
 * ffmpeg process or a second playback engine.
 *
 * Commands are serialized on one worker so a slow stream open cannot race a
 * quick Next/Pause click. Actual playback progress comes from the active
 * player's output-line clock, not from how many bytes the engine decoded.
 */
class FfmpegPlaybackEngine internal constructor(
    private val sinkFactory: () -> AudioSink = { JavaSoundSink() },
) : PlaybackEngine {
    private sealed interface Command {
        data class Load(val uri: String, val autoPlay: Boolean, val token: Long) : Command
        data class Seek(val positionMs: Long) : Command
        data object Play : Command
        data object Pause : Command
        data object Stop : Command
        data object Release : Command
    }

    @Volatile
    private var listener: PlaybackEngine.Listener? = null

    @Volatile
    private var volume = 0.85f

    @Volatile
    private var released = false

    @Volatile
    private var desiredPreloadUri: String? = null

    @Volatile
    private var active: FfmpegAudioPlayer? = null

    @Volatile
    private var activeUri: String? = null

    private val debug = System.getenv("REVIBE_FFMPEG_DEBUG") == "1" ||
        System.getProperty("revibe.debug") == "true"
    private val commands = LinkedBlockingQueue<Command>()
    private val loadToken = AtomicLong()
    private val preparedLock = Any()

    private var prepared: FfmpegAudioPlayer? = null
    private var preparedUri: String? = null

    @Volatile
    private var lastEmittedPositionMs = -1L
    private var lastProgressAtMs = 0L

    private val preloader = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "revibe-ffmpeg-preload").apply { isDaemon = true }
    }
    private val closer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "revibe-ffmpeg-close").apply { isDaemon = true }
    }

    private val worker = Thread(::runLoop, "revibe-ffmpeg-engine").apply {
        isDaemon = true
        start()
    }

    init {
        // JavaCPP extracts libav* on first use. Warm that off the UI thread so
        // the first Play click does not pay for native library extraction.
        preloader.execute { runCatching { FFmpegFrameGrabber.tryLoad() } }
    }

    override fun setListener(listener: PlaybackEngine.Listener) {
        this.listener = listener
    }

    override fun load(uri: String, autoPlay: Boolean) {
        if (released) return
        commands.offer(Command.Load(uri, autoPlay, loadToken.incrementAndGet()))
    }

    override fun preload(uri: String) {
        if (released || uri.isBlank()) return
        if (activeUri == uri) return
        synchronized(preparedLock) {
            if (prepared?.uri == uri) return
        }
        desiredPreloadUri = uri
        runCatching {
            preloader.execute {
                if (released || desiredPreloadUri != uri) return@execute
                val next = runCatching {
                    FfmpegAudioPlayer(uri, sinkFactory, volume)
                }.getOrNull() ?: return@execute
                var keep = false
                synchronized(preparedLock) {
                    if (!released && desiredPreloadUri == uri && prepared?.uri != uri) {
                        prepared?.close()
                        prepared = next
                        preparedUri = uri
                        keep = true
                    }
                }
                if (!keep) next.close()
            }
        }
    }

    override fun play() {
        if (released) return
        commands.offer(Command.Play)
    }

    override fun pause() {
        if (released) return
        commands.offer(Command.Pause)
    }

    override fun seekTo(positionMs: Long) {
        if (released) return
        commands.offer(Command.Seek(positionMs.coerceAtLeast(0)))
    }

    override fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        this.volume = clamped
        active?.setVolume(clamped)
        synchronized(preparedLock) { prepared?.setVolume(clamped) }
    }

    override fun stop() {
        if (released) return
        loadToken.incrementAndGet()
        commands.offer(Command.Stop)
    }

    override fun release() {
        if (released) return
        released = true
        loadToken.incrementAndGet()
        commands.offer(Command.Release)
        preloader.shutdownNow()
        synchronized(preparedLock) {
            prepared?.close()
            prepared = null
        }
        worker.interrupt()
    }

    // --------------------------------------------------------------- worker

    private fun runLoop() {
        try {
            while (!released || commands.isNotEmpty()) {
                val command = try {
                    commands.poll(20, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    null
                }
                if (command != null) handle(command)
                emitProgressIfDue()
            }
        } finally {
            closeActiveNow()
            synchronized(preparedLock) {
                prepared?.close()
                prepared = null
                preparedUri = null
            }
            closer.shutdown()
        }
    }

    private fun handle(command: Command) {
        when (command) {
            is Command.Load -> {
                if (command.token == loadToken.get()) {
                    loadInternal(command.uri, command.autoPlay, command.token)
                }
            }
            is Command.Seek -> seekInternal(command.positionMs)
            Command.Play -> playInternal()
            Command.Pause -> active?.pause()
            Command.Stop -> stopInternal()
            Command.Release -> {
                released = true
                closeActiveNow()
                synchronized(preparedLock) {
                    prepared?.close()
                    prepared = null
                    preparedUri = null
                }
            }
        }
    }

    private fun loadInternal(uri: String, autoPlay: Boolean, token: Long) {
        if (released || token != loadToken.get()) return
        val lead = takePrepared(uri)
        var player: FfmpegAudioPlayer? = null
        try {
            player = lead ?: FfmpegAudioPlayer(uri, sinkFactory, volume)
            player.setVolume(volume)
            if (released || token != loadToken.get()) {
                closeAsync(player)
                return
            }
            val current = player
            current.listener = playerListener(current)
            val previous = active
            active = current
            activeUri = uri
            lastEmittedPositionMs = 0
            lastProgressAtMs = System.currentTimeMillis()

            listener?.onBuffering(true)
            listener?.onReady(current.durationMs)
            listener?.onProgress(0)

            // Stop the outgoing line before starting the prepared one. The old
            // player is closed on a separate thread, so a slow network read in
            // its decoder cannot delay the transition.
            previous?.pause()
            if (autoPlay) current.play() else current.pause()
            previous?.let(::closeAsync)
            // Always log the decoded format so a real session can be checked
            // against the resolver's selected itag without enabling debug mode.
            println(
                "[stream] playing rate=${current.sampleRate}Hz channels=${current.channelCount} " +
                    "duration=${current.durationMs}ms prepared=${lead != null}",
            )
            debugLog(
                "engine load prepared=${lead != null} autoPlay=$autoPlay " +
                    "rate=${current.sampleRate} channels=${current.channelCount} " +
                    "duration=${current.durationMs} host=${runCatching { java.net.URI(uri).host }.getOrNull() ?: "local"}",
            )
        } catch (error: Throwable) {
            runCatching { player?.close() }
            val previous = active
            active = null
            activeUri = null
            previous?.pause()
            previous?.let(::closeAsync)
            listener?.onBuffering(false)
            listener?.onPlayingChanged(false)
            listener?.onError(
                "FFmpeg could not open this stream: ${error.message ?: error::class.simpleName}",
            )
        }
    }

    private fun seekInternal(positionMs: Long) {
        val current = active ?: return
        debugLog("engine seek target=$positionMs paused=${current.isPaused()} position=${current.currentPositionMs()}")
        val target = if (current.durationMs > 0) {
            positionMs.coerceIn(0, current.durationMs)
        } else {
            positionMs.coerceAtLeast(0)
        }
        if (!current.isPaused()) listener?.onBuffering(true)
        current.seekTo(target)
        lastEmittedPositionMs = target
        lastProgressAtMs = System.currentTimeMillis()
        listener?.onProgress(target)
    }

    private fun playInternal() {
        val current = active ?: return
        debugLog("engine play ended=${current.isEnded()} paused=${current.isPaused()} seeking=${current.isSeeking()} position=${current.currentPositionMs()}")
        if (current.isEnded()) listener?.onBuffering(true)
        current.play()
        if (current.hasAudioStarted() && !current.isPaused()) {
            listener?.onBuffering(false)
            listener?.onPlayingChanged(true)
        }
    }

    private fun stopInternal() {
        val current = active
        active = null
        activeUri = null
        lastEmittedPositionMs = 0
        current?.pause()
        current?.let(::closeAsync)
        listener?.onBuffering(false)
        listener?.onPlayingChanged(false)
        listener?.onProgress(0)
    }

    private fun closeActiveNow() {
        val current = active
        active = null
        activeUri = null
        current?.close()
    }

    private fun closeAsync(player: FfmpegAudioPlayer) {
        runCatching {
            closer.execute { player.close() }
        }.onFailure {
            player.close()
        }
    }

    private fun takePrepared(uri: String): FfmpegAudioPlayer? = synchronized(preparedLock) {
        val current = prepared
        if (current?.uri == uri) {
            prepared = null
            preparedUri = null
            current
        } else {
            if (current != null) {
                prepared = null
                preparedUri = null
                current.close()
            }
            null
        }
    }

    private fun playerListener(player: FfmpegAudioPlayer): FfmpegAudioPlayer.Listener =
        object : FfmpegAudioPlayer.Listener {
            override fun onAudioStarted() {
                if (active !== player || released) return
                listener?.onBuffering(false)
                listener?.onPlayingChanged(true)
            }

            override fun onPaused() {
                if (active !== player || released) return
                listener?.onPlayingChanged(false)
            }

            override fun onSeeked(positionMs: Long) {
                if (active !== player || released) return
                lastEmittedPositionMs = positionMs
                listener?.onProgress(positionMs)
                if (player.isPaused()) listener?.onBuffering(false)
            }

            override fun onEnded() {
                if (active !== player || released) return
                val position = if (player.durationMs > 0) {
                    player.durationMs
                } else {
                    player.currentPositionMs()
                }
                lastEmittedPositionMs = position
                listener?.onProgress(position)
                listener?.onBuffering(false)
                listener?.onPlayingChanged(false)
                listener?.onEnded()
            }

            override fun onError(message: String) {
                if (active !== player || released) return
                debugLog("engine player error: $message")
                active = null
                activeUri = null
                player.pause()
                closeAsync(player)
                listener?.onBuffering(false)
                listener?.onPlayingChanged(false)
                listener?.onError("FFmpeg could not play this stream: $message")
            }
        }

    private fun emitProgressIfDue() {
        val current = active ?: return
        if (current.isPaused() || current.isSeeking()) return
        val now = System.currentTimeMillis()
        if (now - lastProgressAtMs < PROGRESS_INTERVAL_MS) return
        val position = current.currentPositionMs()
        if (position == lastEmittedPositionMs) return
        lastProgressAtMs = now
        lastEmittedPositionMs = position
        listener?.onProgress(position)
    }

    private fun debugLog(message: String) {
        if (!debug) return
        println("[ffmpeg] $message")
        val path = System.getenv("REVIBE_DEBUG_LOG")
            ?: System.getProperty("revibe.debugLog")
            ?: return
        runCatching {
            java.io.File(path).appendText("${System.currentTimeMillis()} $message\n")
        }
    }

    internal companion object {
        const val PROGRESS_INTERVAL_MS = 100L
    }
}
