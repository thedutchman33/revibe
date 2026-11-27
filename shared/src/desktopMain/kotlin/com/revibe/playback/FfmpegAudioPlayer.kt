package com.revibe.playback

import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.io.File
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.FloatControl
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.SourceDataLine
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * One FFmpeg decoder and one Java Sound line.
 *
 * This mirrors Metrolist's desktop [AudioPlayer]: a decode thread and a render
 * thread share a short PCM queue, seeking is performed by the decoder itself,
 * and the playback clock comes from the output line's own frame position. A
 * prepared player is the same object opened paused, with its first few PCM
 * frames already decoded, so promoting it does not reopen FFmpeg or the line.
 */
internal class FfmpegAudioPlayer(
    val uri: String,
    private val sinkFactory: () -> AudioSink,
    initialVolume: Float,
) {
    interface Listener {
        fun onAudioStarted()
        fun onPaused()
        fun onSeeked(positionMs: Long)
        fun onEnded()
        fun onError(message: String)
    }

    @Volatile
    var listener: Listener? = null

    val durationMs: Long
    val sampleRate: Int
    val channelCount: Int

    private val id = nextId.incrementAndGet()
    private val debug = System.getenv("REVIBE_FFMPEG_DEBUG") == "1" ||
        System.getProperty("revibe.debug") == "true"
    @Volatile
    private var decoder: FFmpegFrameGrabber
    private val renderer: PcmRenderer
    private val sink: AudioSink = sinkFactory()
    private val queue = ArrayBlockingQueue<PcmFrame>(QUEUE_CAPACITY)
    private val stateLock = ReentrantLock()
    private val stateChanged = stateLock.newCondition()

    private val decoderThread: Thread
    private val renderThread: Thread

    @Volatile
    private var paused = true

    @Volatile
    private var ended = false

    @Volatile
    private var closed = false

    @Volatile
    private var failed = false

    @Volatile
    private var seeking = false

    @Volatile
    private var pendingSeekMs: Long? = null

    @Volatile
    private var seekVersion = 0L

    @Volatile
    private var eofEnqueued = false

    @Volatile
    private var audioStarted = false

    @Volatile
    private var needsStartSignal = false

    init {
        val opened = openGrabber(uri)
        decoder = opened
        try {
            durationMs = opened.lengthInTime.coerceAtLeast(0) / 1_000
            sampleRate = opened.sampleRate
            channelCount = opened.audioChannels
            require(sampleRate > 0 && channelCount > 0) {
                "FFmpeg decoded no audio format for this stream"
            }
            val format = AudioFormat(sampleRate.toFloat(), 16, channelCount, true, false)
            val lineBufferBytes = (sampleRate * LINE_BUFFER_SECONDS * format.frameSize)
                .roundToInt()
                .coerceAtLeast(MIN_LINE_BUFFER_BYTES)
            renderer = PcmRenderer(sink, format, lineBufferBytes)
            renderer.setVolume(initialVolume)
            // A prepared player is silent until promoted. Opening the line now
            // reserves the output path and avoids paying for it on Next.
            renderer.pause()
            prebuffer()
        } catch (error: Throwable) {
            runCatching { opened.stop() }
            runCatching { opened.release() }
            runCatching { sink.close() }
            throw error
        }

        decoderThread = Thread({ decodeLoop() }, "revibe-ffmpeg-decode-$id").apply {
            isDaemon = true
        }
        renderThread = Thread({ renderLoop() }, "revibe-ffmpeg-render-$id").apply {
            isDaemon = true
        }
        decoderThread.start()
        renderThread.start()
    }

    fun play() {
        if (closed || failed) return
        if (ended) {
            ended = false
            seekTo(0)
        }
        stateLock.lock()
        try {
            paused = false
            needsStartSignal = true
            stateChanged.signalAll()
        } finally {
            stateLock.unlock()
        }
        // Starting the line while the decoder is reconnecting would play stale
        // queued PCM and race the seek's clock reset. performSeek() starts it
        // once the decoder is actually at the requested position.
        if (!seeking) runCatching { renderer.resume() }
        debugLog("player=$id play paused=$paused seeking=$seeking ended=$ended position=${currentPositionMs()}")
    }

    fun pause() {
        if (closed) return
        val wasAudible = !paused && audioStarted
        stateLock.lock()
        try {
            paused = true
            stateChanged.signalAll()
        } finally {
            stateLock.unlock()
        }
        runCatching { renderer.pause() }
        if (wasAudible) listener?.onPaused()
    }

    fun seekTo(positionMs: Long) {
        if (closed) return
        val target = if (durationMs > 0) {
            positionMs.coerceIn(0, durationMs)
        } else {
            positionMs.coerceAtLeast(0)
        }
        stateLock.lock()
        try {
            seekVersion++
            pendingSeekMs = target
            seeking = true
            ended = false
            needsStartSignal = true
            stateChanged.signalAll()
        } finally {
            stateLock.unlock()
        }
        debugLog("player=$id seek request=$target paused=$paused active=${isPlaying()}")
    }

    fun setVolume(volume: Float) {
        runCatching { renderer.setVolume(volume) }
    }

    fun currentPositionMs(): Long {
        val position = runCatching { renderer.positionMs() }.getOrDefault(0)
        return if (durationMs > 0) position.coerceIn(0, durationMs) else position.coerceAtLeast(0)
    }

    fun isPlaying(): Boolean = !closed && !failed && !paused && !ended && !seeking && audioStarted

    fun isPaused(): Boolean = paused

    fun isSeeking(): Boolean = seeking

    fun isEnded(): Boolean = ended

    fun hasAudioStarted(): Boolean = audioStarted

    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        paused = true
        stateLock.lock()
        try {
            stateChanged.signalAll()
        } finally {
            stateLock.unlock()
        }
        // Closing the line first unblocks a render thread waiting for buffer
        // space. FFmpeg stop then releases a network read before the join.
        runCatching { renderer.abort() }
        runCatching { decoder.stop() }
        runCatching { renderer.close() }
        join(decoderThread)
        join(renderThread)
        runCatching { decoder.release() }
    }

    // --------------------------------------------------------------- decode

    private fun decodeLoop() {
        while (!closed) {
            if (failed) {
                awaitState()
                continue
            }

            val request = pendingSeekMs
            if (request != null) {
                pendingSeekMs = null
                val version = seekVersion
                performSeek(request, version)
                continue
            }

            if (paused || seeking || eofEnqueued) {
                awaitState()
                continue
            }

            val pcm = try {
                readPcm()
            } catch (error: Throwable) {
                if (!closed) fail(error)
                return
            }
            if (pcm == null) {
                // The queue can still contain up to 15 decoded frames when the
                // decoder reaches EOF. Marking completion before those frames
                // (and the EOF marker) fit meant the renderer played the tail
                // and then waited forever. Deliver one versioned EOF marker,
                // waiting for the renderer to drain a slot first.
                while (!closed && !seeking && pendingSeekMs == null) {
                    if (paused) {
                        awaitState()
                        continue
                    }
                    val queued = try {
                        queue.offer(PcmFrame(null, seekVersion), QUEUE_WAIT_MS, TimeUnit.MILLISECONDS)
                    } catch (_: InterruptedException) {
                        if (closed) return
                        false
                    }
                    if (queued) {
                        eofEnqueued = true
                        break
                    }
                }
                awaitState()
                continue
            }
            if (seeking || pendingSeekMs != null || closed) continue
            if (pcm.isEmpty()) continue
            val version = seekVersion
            try {
                queue.offer(PcmFrame(pcm, version), QUEUE_WAIT_MS, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                if (closed) return
            }
        }
    }

    private fun performSeek(targetMs: Long, version: Long) {
        try {
            debugLog("player=$id seek start=$targetMs version=$version paused=$paused")
            queue.clear()
            // Flush queued output and reset the line clock at the target. The
            // actual decoder timestamp is applied again once the seek lands.
            renderer.flushTo(targetMs)
            val landedAt = seekDecoder(targetMs, version)
            renderer.resetTo(landedAt)
            eofEnqueued = false
            ended = false
            audioStarted = false
            needsStartSignal = true
            var startLine = false
            stateLock.lock()
            try {
                if (seekVersion == version) {
                    seeking = false
                    startLine = !paused
                    listener?.onSeeked(landedAt)
                }
                stateChanged.signalAll()
            } finally {
                stateLock.unlock()
            }
            if (startLine) runCatching { renderer.resume() }
            debugLog("player=$id seek done requested=$targetMs actual=$landedAt paused=$paused")
        } catch (error: Throwable) {
            debugLog("player=$id seek failed target=$targetMs ${error.message}")
            if (!closed) fail(error)
        }
    }

    private fun seekDecoder(targetMs: Long, version: Long): Long {
        // A restart must never be substituted with a later position. On an
        // already-open decoder FFmpeg can flush and return no packet for 0 ms;
        // reopening and reading from the beginning is the only correct retry.
        if (targetMs <= 0L) return seekToStart(version)

        // A few signed googlevideo byte offsets make FFmpeg's range seek land
        // back near the start even though setTimestamp() returns normally. A
        // fresh connection at a nearby offset has a different byte boundary and
        // reaches the requested region. Retry the exact target once on a fresh
        // connection, then only earlier positions: a later candidate could
        // silently move the user forward past the requested time.
        val candidates = buildList {
            add(targetMs)
            add(targetMs)
            add(targetMs - 5_000)
            add(targetMs - 10_000)
            add(targetMs - 20_000)
        }.filter { it > 0 && (durationMs <= 0 || it < durationMs - 500) }

        var lastLanding = -1L
        for ((attempt, candidate) in candidates.withIndex()) {
            if (closed || pendingSeekMs != null || seekVersion != version) return targetMs
            try {
                if (attempt > 0) reopenDecoder()
                val seekStartedAt = System.nanoTime()
                // checkFrame=false is deliberate. With checkFrame=true JavaCV
                // pulls the first frame inside setTimestamp() and silently
                // returns when that internal grab is null, leaving us with no
                // frame and no error. Pulling it ourselves makes the landing
                // measurable and lets a failed attempt reconnect.
                decoder.setTimestamp(candidate * 1_000, false)
                val firstFrame = readPcmFrame()
                    ?: throw IllegalStateException("No audio frame after seeking to $candidate ms")
                val seekMs = (System.nanoTime() - seekStartedAt) / 1_000_000
                val decodedAt = firstFrame.timestampMs
                lastLanding = decodedAt
                debugLog(
                    "player=$id seek candidate=$candidate actual=$decodedAt " +
                        "seekMs=$seekMs reconnected=${attempt > 0} requested=$targetMs",
                )
                // Never accept a landing after the requested position: that
                // would make a backward seek play a later moment. A landing
                // just before the target is walked forward instead.
                if (decodedAt >= 0 &&
                    decodedAt <= targetMs + SEEK_TOLERANCE_MS &&
                    kotlin.math.abs(decodedAt - candidate) <= SEEK_TOLERANCE_MS
                ) {
                    val distanceBeforeTarget = targetMs - decodedAt
                    if (distanceBeforeTarget > 0) {
                        if (distanceBeforeTarget <= MAX_FORWARD_SKIP_MS) {
                            if (firstFrame.timestampMs + 200L >= targetMs) {
                                queue.offer(PcmFrame(firstFrame.bytes, version))
                                return decodedAt
                            }
                            seekForwardTo(targetMs, version)?.let { return it }
                        }
                        // The landing is too far from the request to be useful;
                        // reconnect at another byte boundary rather than return
                        // a position the user did not ask for.
                        continue
                    }
                    // The decoder is at the requested point; keep its first
                    // decoded frame so seeking does not introduce a silent gap.
                    queue.offer(PcmFrame(firstFrame.bytes, version))
                    return decodedAt
                }
            } catch (error: Throwable) {
                debugLog("player=$id seek candidate=$candidate failed: ${error.message}")
            }
        }
        throw IllegalStateException(
            "FFmpeg could not seek near ${targetMs}ms (last landing ${lastLanding}ms)",
        )
    }

    private fun seekToStart(version: Long): Long {
        fun attempt(): Long {
            decoder.setTimestamp(0L, false)
            val firstFrame = readPcmFrame()
                ?: throw IllegalStateException("No audio frame after seeking to the start")
            val landedAt = firstFrame.timestampMs.coerceAtLeast(0)
            if (landedAt > SEEK_TOLERANCE_MS) {
                throw IllegalStateException("Seeking to the start landed at ${landedAt}ms")
            }
            queue.offer(PcmFrame(firstFrame.bytes, version))
            debugLog("player=$id restart actual=$landedAt")
            return landedAt
        }
        return try {
            attempt()
        } catch (firstError: Throwable) {
            debugLog("player=$id restart retry after ${firstError.message}")
            reopenDecoder()
            attempt()
        }
    }

    private fun seekForwardTo(targetMs: Long, version: Long): Long? {
        var lastTimestamp = -1L
        var framesSkipped = 0
        while (!closed) {
            if (pendingSeekMs != null || seekVersion != version) return null
            val decoded = readPcmFrame() ?: return null
            lastTimestamp = decoded.timestampMs
            if (decoded.timestampMs < 0) {
                debugLog("player=$id forward seek has no frame timestamps at $targetMs")
                return null
            }
            if (decoded.timestampMs + 200L >= targetMs) {
                queue.offer(PcmFrame(decoded.bytes, version))
                debugLog(
                    "player=$id forward seek target=$targetMs actual=${decoded.timestampMs} " +
                        "skipped=$framesSkipped",
                )
                return decoded.timestampMs
            }
            framesSkipped++
        }
        debugLog("player=$id forward seek ended at $lastTimestamp before $targetMs")
        return null
    }

    private fun reopenDecoder() {
        val previous = decoder
        val fresh = openGrabber(uri)
        try {
            require(fresh.sampleRate == sampleRate && fresh.audioChannels == channelCount) {
                "The stream changed its audio format after reconnecting"
            }
        } catch (error: Throwable) {
            runCatching { fresh.stop() }
            runCatching { fresh.release() }
            throw error
        }
        decoder = fresh
        runCatching { previous.stop() }
        runCatching { previous.release() }
    }

    private fun readPcm(): ByteArray? = readPcmFrame()?.bytes

    private fun readPcmFrame(): DecodedPcm? {
        while (!closed) {
            val frame = decoder.grabSamples() ?: return null
            val samples = frame.samples ?: continue
            val buffer = samples.firstOrNull() ?: continue
            val bytes: ByteArray? = when (buffer) {
                is ShortBuffer -> {
                    val source = buffer.duplicate()
                    val samplesCopy = ShortArray(source.remaining())
                    source.get(samplesCopy)
                    ByteArray(samplesCopy.size * 2).also { output ->
                        ByteBuffer.wrap(output)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .asShortBuffer()
                            .put(samplesCopy)
                    }
                }
                is ByteBuffer -> {
                    val source = buffer.duplicate()
                    ByteArray(source.remaining()).also { source.get(it) }
                }
                else -> null
            }
            if (bytes != null && bytes.isNotEmpty()) {
                return DecodedPcm(bytes, frame.timestamp / 1_000)
            }
        }
        return null
    }

    private data class DecodedPcm(val bytes: ByteArray, val timestampMs: Long)

    private fun prebuffer() {
        repeat(PREBUFFER_FRAMES) {
            val pcm = runCatching { readPcm() }.getOrNull()
            if (pcm == null) {
                queue.offer(PcmFrame(null, 0))
                eofEnqueued = true
                return
            }
            if (pcm.isNotEmpty()) queue.offer(PcmFrame(pcm, 0))
        }
    }

    // --------------------------------------------------------------- render

    private fun renderLoop() {
        while (!closed) {
            if (failed) {
                awaitState()
                continue
            }
            if (paused || seeking) {
                awaitState()
                continue
            }

            val frame = try {
                queue.poll(STATE_WAIT_MS, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                if (closed) return
                continue
            } ?: continue

            if (frame.version != seekVersion) continue
            if (frame.pcm == null) {
                if (pendingSeekMs == null && !seeking) finish()
                continue
            }
            try {
                renderer.write(frame.pcm) { !paused && !closed && !failed && !seeking }
            } catch (error: Throwable) {
                if (!closed) fail(error)
                return
            }
            if (!audioStarted || needsStartSignal) {
                audioStarted = true
                needsStartSignal = false
                listener?.onAudioStarted()
            }
        }
    }

    private fun finish() {
        if (ended || closed) return
        debugLog("player=$id finish position=${currentPositionMs()} duration=$durationMs")
        runCatching { renderer.drain() }
        debugLog("player=$id drained position=${currentPositionMs()}")
        ended = true
        paused = true
        runCatching { renderer.pause() }
        stateLock.lock()
        try {
            stateChanged.signalAll()
        } finally {
            stateLock.unlock()
        }
        listener?.onEnded()
    }

    private fun fail(error: Throwable) {
        if (closed || failed) return
        debugLog("player=$id fail: ${error.message}")
        failed = true
        paused = true
        stateLock.lock()
        try {
            stateChanged.signalAll()
        } finally {
            stateLock.unlock()
        }
        listener?.onError(error.message ?: error::class.simpleName ?: "FFmpeg playback failed")
    }

    private fun awaitState() {
        stateLock.lock()
        try {
            stateChanged.await(STATE_WAIT_MS, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            stateLock.unlock()
        }
    }

    private fun debugLog(message: String) {
        if (!debug) return
        println("[ffmpeg] $message")
        val path = System.getenv("REVIBE_DEBUG_LOG")
            ?: System.getProperty("revibe.debugLog")
            ?: return
        runCatching {
            File(path).appendText("${System.currentTimeMillis()} $message\n")
        }
    }

    private fun join(thread: Thread) {
        if (Thread.currentThread() === thread) return
        runCatching { thread.join(CLOSE_JOIN_MS) }
    }

    private class PcmFrame(val pcm: ByteArray?, val version: Long)

    internal companion object {
        private val nextId = AtomicLong()
        private const val QUEUE_CAPACITY = 15
        private const val PREBUFFER_FRAMES = 3
        private const val LINE_BUFFER_SECONDS = 0.15
        private const val MIN_LINE_BUFFER_BYTES = 4_096
        private const val QUEUE_WAIT_MS = 50L
        private const val STATE_WAIT_MS = 50L
        private const val CLOSE_JOIN_MS = 750L
        private const val SEEK_TOLERANCE_MS = 2_000L
        private const val MAX_FORWARD_SKIP_MS = 30_000L

        fun openGrabber(uri: String): FFmpegFrameGrabber {
            val grabber = FFmpegFrameGrabber(mediaPath(uri))
            grabber.setOption("reconnect", "1")
            grabber.setOption("reconnect_streamed", "1")
            grabber.setOption("reconnect_delay_max", "5")
            grabber.setOption("rw_timeout", "15000000")
            val headers = DesktopStreamHeaders.get(uri)
            if (headers.isNotEmpty()) {
                grabber.setOption(
                    "headers",
                    headers.entries.joinToString("\r\n") { "${it.key}: ${it.value}" } + "\r\n",
                )
            }
            // Metrolist asks FFmpeg for signed 16-bit PCM and lets the decoder
            // keep its native sample rate and channel count. Forcing 48 kHz
            // stereo here would add an unnecessary resample and change quality.
            grabber.sampleFormat = AV_SAMPLE_FMT_S16
            grabber.start()
            return grabber
        }

        private fun mediaPath(uri: String): String {
            if (!uri.startsWith("file:", ignoreCase = true)) return uri
            return runCatching { File(URI(uri)).absolutePath }.getOrDefault(uri)
        }

        fun seekToleranceMs() = SEEK_TOLERANCE_MS
    }
}

/**
 * PCM output abstraction. Production is Java Sound; tests inject a paced
 * in-memory line so the deterministic suite never needs an audio device.
 */
internal interface AudioSink {
    val positionFrames: Long
    val bufferSize: Int
    val hasVolumeControl: Boolean
    fun open(format: AudioFormat, bufferSize: Int)
    fun write(bytes: ByteArray, shouldContinue: () -> Boolean = { true })
    fun start()
    fun stop()
    fun flush()
    fun drain()
    fun setVolume(volume: Float) = Unit
    fun close()
}

/**
 * Windows audio output through Java Sound. The default line is tried first,
 * then every Java Sound mixer, with four retries; this is the Windows branch
 * documented by Metrolist. No ALSA/PipeWire mixer names are involved here.
 */
internal class JavaSoundSink : AudioSink {
    private var line: SourceDataLine? = null
    private var gainControl: FloatControl? = null
    private var bytesPerSecond = 0

    @Volatile
    private var closed = false

    override var bufferSize: Int = 0
        private set

    override val positionFrames: Long
        get() = line?.longFramePosition ?: 0L

    override val hasVolumeControl: Boolean
        get() = gainControl != null

    override fun open(format: AudioFormat, bufferSize: Int) {
        check(!closed) { "audio sink is closed" }
        this.bufferSize = bufferSize
        bytesPerSecond = (format.sampleRate * format.frameSize).roundToInt()
        val info = DataLine.Info(SourceDataLine::class.java, format)
        val failures = mutableListOf<Throwable>()
        repeat(OPEN_ATTEMPTS) { attempt ->
            val candidates = buildList {
                runCatching { AudioSystem.getLine(info) as SourceDataLine }
                    .onSuccess(::add)
                    .onFailure(failures::add)
                for (mixerInfo in AudioSystem.getMixerInfo()) {
                    runCatching { AudioSystem.getMixer(mixerInfo).getLine(info) as SourceDataLine }
                        .onSuccess(::add)
                        .onFailure(failures::add)
                }
            }
            for (candidate in candidates) {
                try {
                    candidate.open(format, bufferSize)
                    line = candidate
                    gainControl = runCatching {
                        candidate.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                    }.getOrNull()
                    return
                } catch (error: Throwable) {
                    failures += error
                    runCatching { candidate.close() }
                }
            }
            if (attempt < OPEN_ATTEMPTS - 1) {
                runCatching { Thread.sleep(OPEN_RETRY_DELAY_MS) }
            }
        }
        throw LineUnavailableException("No Windows audio output line is available").apply {
            failures.forEach(::addSuppressed)
        }
    }

    override fun write(bytes: ByteArray, shouldContinue: () -> Boolean) {
        var offset = 0
        while (!closed && offset < bytes.size && shouldContinue()) {
            val current = line ?: return
            val available = runCatching { current.available() }.getOrDefault(0)
            if (available <= 0) {
                runCatching { Thread.sleep(WRITE_WAIT_MS) }
                continue
            }
            val count = minOf(available, bytes.size - offset)
            runCatching { current.write(bytes, offset, count) }
                .onFailure { if (!closed) throw it }
            offset += count
        }
    }

    override fun start() {
        runCatching { line?.start() }
    }

    override fun stop() {
        runCatching { line?.stop() }
    }

    override fun flush() {
        runCatching { line?.flush() }
    }

    override fun drain() {
        // SourceDataLine.drain() can block forever on some Windows mixers. Wait
        // for the line's own buffer to empty, bounded by the queued duration
        // plus the same scheduling margin Metrolist uses.
        val current = line ?: return
        val queuedMs = if (bytesPerSecond > 0) {
            bufferSize.toLong() * 1_000L / bytesPerSecond
        } else {
            0L
        }
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(queuedMs + 250L)
        while (!closed && current.isOpen && current.available() < bufferSize) {
            if (System.nanoTime() >= deadline) break
            runCatching { Thread.sleep(5L) }
        }
    }

    override fun setVolume(volume: Float) {
        val control = gainControl ?: return
        val clamped = volume.coerceIn(0f, 1f)
        val decibels = if (clamped <= 0.0001f) {
            control.minimum
        } else {
            (20.0 * log10(clamped.toDouble())).toFloat()
                .coerceIn(control.minimum, control.maximum)
        }
        runCatching { control.value = decibels }
    }

    override fun close() {
        if (closed) return
        closed = true
        val current = line
        line = null
        gainControl = null
        bytesPerSecond = 0
        runCatching { current?.stop() }
        runCatching { current?.close() }
    }

    internal companion object {
        private const val OPEN_ATTEMPTS = 4
        private const val OPEN_RETRY_DELAY_MS = 150L
        private const val WRITE_WAIT_MS = 2L
    }
}

/**
 * Converts the line's frame position into media time. `longFramePosition`
 * counts frames actually presented by the output, not frames queued by the
 * decoder, so pausing freezes it and seeks reset its media base explicitly.
 */
private class PcmRenderer(
    private val sink: AudioSink,
    private val format: AudioFormat,
    bufferSize: Int,
) {
    private val sampleRate = format.sampleRate.roundToInt()
    private var baseLineFrame = 0L
    private var baseMediaMs = 0L
    private var volume = 1f

    init {
        sink.open(format, bufferSize)
        sink.start()
        baseLineFrame = sink.positionFrames
    }

    @Synchronized
    fun write(bytes: ByteArray, shouldContinue: () -> Boolean) {
        val output = when {
            sink.hasVolumeControl -> bytes
            volume >= 0.999f -> bytes
            else -> scale(bytes, volume)
        }
        sink.write(output, shouldContinue)
    }

    @Synchronized
    fun pause() {
        sink.stop()
    }

    @Synchronized
    fun resume() {
        if (sink.bufferSize > 0) sink.start()
    }

    @Synchronized
    fun flushTo(mediaMs: Long) {
        sink.flush()
        baseLineFrame = sink.positionFrames
        baseMediaMs = mediaMs.coerceAtLeast(0)
    }

    @Synchronized
    fun resetTo(mediaMs: Long) {
        baseLineFrame = sink.positionFrames
        baseMediaMs = mediaMs.coerceAtLeast(0)
    }

    @Synchronized
    fun positionMs(): Long {
        val frames = (sink.positionFrames - baseLineFrame).coerceAtLeast(0)
        if (sampleRate <= 0) return baseMediaMs
        return baseMediaMs + frames * 1_000L / sampleRate
    }

    @Synchronized
    fun drain() {
        sink.drain()
    }

    fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        if (sink.hasVolumeControl) sink.setVolume(this.volume)
    }

    fun abort() {
        // Not synchronized: this is the unblock path for a writer parked in
        // SourceDataLine.write while close() is waiting to take the same lock.
        sink.close()
    }

    @Synchronized
    fun close() {
        sink.close()
    }

    private fun scale(bytes: ByteArray, gain: Float): ByteArray {
        val output = bytes.copyOf()
        val buffer = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        for (index in 0 until buffer.limit()) {
            buffer.put(index, (buffer.get(index) * gain).roundToInt().toShort())
        }
        return output
    }
}
