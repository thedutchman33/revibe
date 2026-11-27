package com.revibe.playback

import com.revibe.library.LibraryRepository
import com.revibe.model.Track
import com.revibe.recommendation.AutoplayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Single source of truth for playback. The engine only decodes audio; every
 * queue decision lives here so the logic is platform independent and testable.
 *
 * Queue rows carry their origin. Context rows are the album/playlist the user
 * started; MANUAL rows came from Play next/Add to queue; AUTO rows are real
 * provider recommendations. Autoplay only ever appends after the user's rows.
 */
class PlayerController(
    private val engine: PlaybackEngine,
    private val library: LibraryRepository,
    private val scope: CoroutineScope,
    initialVolume: Float = 0.85f,
    private val resolver: TrackStreamResolver? = null,
    private val autoplay: AutoplayService? = null,
    initialAutoplayEnabled: Boolean = true,
) : PlaybackEngine.Listener {

    private val _state = MutableStateFlow(
        PlayerState(
            volume = initialVolume,
            autoplayEnabled = initialAutoplayEnabled,
            autoplayState = if (autoplay == null) AutoplayState.OFF else AutoplayState.IDLE,
        ),
    )
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /** Unshuffled queue, kept so shuffle can be turned off without losing order. */
    private var sourceEntries: List<QueueEntry> = emptyList()
    private var loadGeneration = 0
    private var contextGeneration = 0
    private var currentEntryId: String? = null
    private var retryUsedFor: String? = null
    private var entrySequence = 0L

    private var playRecordedEntryId: String? = null
    private var completedEntryId: String? = null
    private var skipRecordedEntryId: String? = null
    private var refillJob: Job? = null
    private var lastRefillEntryId: String? = null
    private var awaitingEndRefill = false
    private var nativeContinuation: String? = null
    private var nativeSeed: Track? = null
    private var nativeQueueEnded = false

    init {
        engine.setListener(this)
        engine.setVolume(initialVolume)
    }

    // ------------------------------------------------------------------ queue

    fun playQueue(tracks: List<Track>, startIndex: Int = 0, contextName: String = "") {
        val playable = tracks.filter { it.playable || (resolver != null && it.needsResolution) }
        if (playable.isEmpty()) return
        val originalIndex = startIndex.coerceIn(tracks.indices)
        val anchor = tracks[originalIndex]
        val context = playable.map {
            QueueEntry(entryId = nextEntryId(it, QueueOrigin.CONTEXT), track = it, origin = QueueOrigin.CONTEXT)
        }
        sourceEntries = context
        val entries = if (_state.value.shuffle) {
            val anchorEntry = context.firstOrNull { it.track.id == anchor.id }
            listOfNotNull(anchorEntry) + context.filterNot { it.track.id == anchor.id }.shuffled()
        } else {
            context
        }
        val index = entries.indexOfFirst { it.track.id == anchor.id }.coerceAtLeast(0)
        resetSessionGuards()
        contextGeneration++
        refillJob?.cancel()
        _state.update {
            it.copy(
                entries = entries,
                index = index,
                contextName = contextName,
                error = null,
                autoplayState = if (it.autoplayEnabled && autoplay != null) AutoplayState.IDLE else AutoplayState.OFF,
                autoplayMessage = null,
            )
        }
        loadCurrent(autoPlay = true)
    }

    fun playTrack(track: Track, contextName: String = "", context: List<Track>? = null) {
        val list = context?.takeIf { it.isNotEmpty() } ?: listOf(track)
        val index = list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playQueue(list, index, contextName)
    }

    fun togglePlayPause() {
        val state = _state.value
        if (state.current == null) {
            if (state.entries.isNotEmpty()) {
                _state.update { it.copy(index = it.index.coerceAtLeast(0)) }
                loadCurrent(autoPlay = true)
            }
            return
        }
        if (state.isPlaying) {
            engine.pause()
        } else {
            engine.play()
        }
    }

    fun next(auto: Boolean = false) = advance(auto = auto, fromEnd = false)

    fun previous() {
        val state = _state.value
        recordSkipIfNeeded(state, state.index)
        if (state.positionMs > 3_000) {
            seekTo(0)
            return
        }
        if (state.index > 0) {
            _state.update { it.copy(index = it.index - 1, positionMs = 0) }
            loadCurrent(autoPlay = true)
        } else {
            seekTo(0)
        }
    }

    private fun advance(auto: Boolean, fromEnd: Boolean) {
        val state = _state.value
        if (state.entries.isEmpty()) return
        if (auto && state.repeat == RepeatMode.ONE) {
            engine.seekTo(0)
            engine.play()
            return
        }
        if (!fromEnd) recordSkipIfNeeded(state, state.index)
        when {
            state.index < state.entries.lastIndex -> {
                _state.update { it.copy(index = it.index + 1, positionMs = 0, current = it.entries[it.index + 1].track) }
                loadCurrent(autoPlay = true)
            }
            state.repeat == RepeatMode.ALL -> {
                _state.update { it.copy(index = 0, positionMs = 0) }
                loadCurrent(autoPlay = true)
            }
            state.autoplayEnabled && autoplay != null -> {
                awaitingEndRefill = true
                lastRefillEntryId = null
                engine.stop()
                _state.update {
                    it.copy(
                        isPlaying = false,
                        isBuffering = false,
                        positionMs = 0,
                        autoplayState = AutoplayState.REFILLING,
                    )
                }
                requestRefill(force = true)
            }
            else -> {
                engine.stop()
                _state.update {
                    it.copy(
                        isPlaying = false,
                        positionMs = 0,
                        autoplayState = if (it.autoplayEnabled) AutoplayState.EXHAUSTED else AutoplayState.OFF,
                    )
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val state = _state.value
        val target = positionMs.coerceIn(0, maxOf(state.durationMs, 0))
        _state.update { it.copy(positionMs = target) }
        engine.seekTo(target)
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _state.update { it.copy(volume = clamped) }
        engine.setVolume(clamped)
    }

    fun setAutoplayEnabled(enabled: Boolean) {
        if (!enabled) {
            refillJob?.cancel()
            awaitingEndRefill = false
            _state.update {
                it.copy(
                    autoplayEnabled = false,
                    autoplayState = AutoplayState.OFF,
                    autoplayMessage = null,
                )
            }
        } else {
            _state.update {
                it.copy(
                    autoplayEnabled = true,
                    autoplayState = if (autoplay == null) AutoplayState.OFF else AutoplayState.IDLE,
                )
            }
            lastRefillEntryId = null
            requestRefill()
        }
    }

    fun toggleShuffle() {
        val state = _state.value
        if (sourceEntries.isEmpty()) {
            _state.update { it.copy(shuffle = !it.shuffle) }
            return
        }
        val current = state.entries.getOrNull(state.index)
        val shuffled = !state.shuffle
        val entries = if (shuffled) {
            if (current != null) {
                listOf(current) + sourceEntries.filterNot { it.entryId == current.entryId }.shuffled()
            } else {
                sourceEntries.shuffled()
            }
        } else {
            sourceEntries
        }
        val index = current?.let { c -> entries.indexOfFirst { it.entryId == c.entryId } }?.takeIf { it >= 0 } ?: 0
        _state.update { it.copy(shuffle = shuffled, entries = entries, index = index) }
    }

    fun cycleRepeat() {
        _state.update {
            it.copy(
                repeat = when (it.repeat) {
                    RepeatMode.OFF -> RepeatMode.ALL
                    RepeatMode.ALL -> RepeatMode.ONE
                    RepeatMode.ONE -> RepeatMode.OFF
                },
            )
        }
    }

    fun playAt(index: Int) {
        val state = _state.value
        if (index !in state.entries.indices) return
        recordSkipIfNeeded(state, state.index)
        _state.update { it.copy(index = index, positionMs = 0) }
        loadCurrent(autoPlay = true)
    }

    fun addToQueue(track: Track) {
        if (!track.playable && !(resolver != null && track.needsResolution)) return
        val state = _state.value
        val entryId = nextEntryId(track, QueueOrigin.MANUAL)
        val entries = QueuePolicy.insertManual(
            entries = state.entries,
            track = track,
            currentIndex = state.index,
            entryId = entryId,
            playNext = false,
        )
        val sourceCurrent = currentEntryId?.let { id -> sourceEntries.indexOfFirst { it.entryId == id } } ?: -1
        sourceEntries = QueuePolicy.insertManual(sourceEntries, track, sourceCurrent, entryId, playNext = false)
        _state.update { it.copy(entries = entries) }
        if (state.current == null) {
            playAt(entries.indexOfFirst { it.entryId == entryId })
        }
    }

    fun playNext(track: Track) {
        if (!track.playable && !(resolver != null && track.needsResolution)) return
        val state = _state.value
        val entryId = nextEntryId(track, QueueOrigin.MANUAL)
        val entries = QueuePolicy.insertManual(
            entries = state.entries,
            track = track,
            currentIndex = state.index,
            entryId = entryId,
            playNext = true,
        )
        val sourceCurrent = currentEntryId?.let { id -> sourceEntries.indexOfFirst { it.entryId == id } } ?: -1
        sourceEntries = QueuePolicy.insertManual(sourceEntries, track, sourceCurrent, entryId, playNext = true)
        _state.update { it.copy(entries = entries) }
        if (state.current == null) playAt(entries.indexOfFirst { it.entryId == entryId })
    }

    fun removeFromQueue(index: Int) {
        val state = _state.value
        if (index !in state.entries.indices) return
        val removed = state.entries[index]
        val removingCurrent = index == state.index
        val entries = state.entries.toMutableList().apply { removeAt(index) }
        sourceEntries = sourceEntries.filterNot { it.entryId == removed.entryId }
        val newIndex = when {
            entries.isEmpty() -> -1
            index < state.index -> state.index - 1
            else -> state.index.coerceAtMost(entries.lastIndex)
        }
        _state.update { it.copy(entries = entries, index = newIndex) }
        if (removingCurrent) {
            if (entries.isEmpty()) {
                engine.stop()
                _state.update { it.copy(isPlaying = false, positionMs = 0, durationMs = 0, current = null) }
            } else {
                loadCurrent(autoPlay = true)
            }
        }
    }

    fun moveInQueue(from: Int, to: Int) {
        val state = _state.value
        if (from !in state.entries.indices || to !in state.entries.indices) return
        val entries = state.entries.toMutableList()
        val item = entries.removeAt(from)
        entries.add(to, item)
        val index = currentEntryId?.let { id -> entries.indexOfFirst { it.entryId == id } } ?: state.index
        sourceEntries = sourceEntries.sortedBy { entry -> entries.indexOfFirst { it.entryId == entry.entryId }.let { if (it < 0) Int.MAX_VALUE else it } }
        _state.update { it.copy(entries = entries, index = index) }
    }

    fun clearQueue() {
        contextGeneration++
        refillJob?.cancel()
        awaitingEndRefill = false
        sourceEntries = emptyList()
        engine.stop()
        _state.update {
            it.copy(
                entries = emptyList(),
                index = -1,
                current = null,
                isPlaying = false,
                positionMs = 0,
                durationMs = 0,
                autoplayState = if (it.autoplayEnabled) AutoplayState.IDLE else AutoplayState.OFF,
                autoplayMessage = null,
            )
        }
    }

    fun stop() {
        engine.stop()
        _state.update { it.copy(isPlaying = false, positionMs = 0) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun release() {
        loadGeneration++
        contextGeneration++
        refillJob?.cancel()
        engine.release()
    }

    // --------------------------------------------------------------- refill

    private fun autoplayStateFor(state: PlayerState): AutoplayState = when {
        !state.autoplayEnabled -> AutoplayState.OFF
        autoplay == null -> AutoplayState.OFF
        state.autoplayState == AutoplayState.REFILLING -> AutoplayState.REFILLING
        else -> AutoplayState.IDLE
    }

    private fun maybeRefill() {
        val state = _state.value
        if (!state.autoplayEnabled || autoplay == null || state.current == null) return
        if (!QueuePolicy.needsRefill(
                entries = state.entries,
                index = state.index,
                enabled = state.autoplayEnabled,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
            )
        ) {
            return
        }
        val currentEntry = state.entries.getOrNull(state.index) ?: return
        if (nativeQueueEnded) return
        if (lastRefillEntryId == currentEntry.entryId) return
        requestRefill()
    }

    private fun requestRefill(force: Boolean = false) {
        val service = autoplay ?: return
        val state = _state.value
        if (!state.autoplayEnabled || state.current == null) return
        if (nativeQueueEnded) {
            awaitingEndRefill = false
            _state.update { it.copy(autoplayState = AutoplayState.EXHAUSTED, autoplayMessage = "No more Up next tracks") }
            return
        }
        if (refillJob?.isActive == true) return
        val currentEntry = state.entries.getOrNull(state.index) ?: return
        val ahead = state.entries.size - state.index - 1
        if (!force && ahead >= QueuePolicy.TARGET_AHEAD) return
        if (lastRefillEntryId == currentEntry.entryId && !force) return

        val generation = contextGeneration
        val seed = state.current
        val nativeSeedForRequest = nativeSeed ?: seed
        val excludeIds = state.entries.mapTo(mutableSetOf()) { it.track.id }
        val excludeKeys = state.entries.mapTo(mutableSetOf()) { it.track.matchKey() }
        lastRefillEntryId = currentEntry.entryId

        refillJob = scope.launch {
            _state.update { it.copy(autoplayState = AutoplayState.REFILLING, autoplayMessage = null) }
            val result = runCatching {
                service.next(
                    seed = nativeSeedForRequest,
                    continuation = nativeContinuation,
                    limit = QueuePolicy.TARGET_AHEAD,
                )
            }
            if (generation != contextGeneration) return@launch
            result.onSuccess { page ->
                if (nativeSeed == null) nativeSeed = nativeSeedForRequest
                nativeContinuation = page.continuation
                if (page.continuation == null) nativeQueueEnded = true
                val latest = _state.value
                val candidates = page.tracks.mapIndexed { index, track ->
                    QueueEntry(
                        entryId = "native-${track.id}-${index}-${Random.nextLong()}",
                        track = track,
                        origin = QueueOrigin.AUTO,
                        reason = "Up next",
                        seedTrackId = seed.id,
                    )
                }
                // Provider order is preserved exactly; appendAuto only rejects duplicates.
                val appended = QueuePolicy.appendAuto(latest.entries, candidates)
                if (appended.size == latest.entries.size) {
                    _state.update {
                        it.copy(
                            autoplayState = if (nativeQueueEnded) AutoplayState.EXHAUSTED else AutoplayState.READY,
                            autoplayMessage = if (nativeQueueEnded) "No more Up next tracks" else null,
                        )
                    }
                    awaitingEndRefill = false
                    return@launch
                }
                sourceEntries = sourceEntries + appended.drop(latest.entries.size)
                _state.update {
                    it.copy(
                        entries = appended,
                        autoplayState = AutoplayState.READY,
                        autoplayMessage = null,
                    )
                }
                if (awaitingEndRefill) {
                    awaitingEndRefill = false
                    val nextIndex = latest.index + 1
                    if (nextIndex in appended.indices) {
                        _state.update { it.copy(index = nextIndex) }
                        loadCurrent(autoPlay = true)
                    }
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        autoplayState = AutoplayState.FAILED,
                        autoplayMessage = (error.message ?: "The Up next queue is unavailable").take(140)
                    )
                }
                awaitingEndRefill = false
            }
        }
    }

    // --------------------------------------------------------------- loading

    private fun loadCurrent(autoPlay: Boolean, forceRefresh: Boolean = false) {
        val state = _state.value
        val entry = state.entries.getOrNull(state.index) ?: return
        val track = entry.track
        val generation = ++loadGeneration
        if (currentEntryId != entry.entryId) {
            currentEntryId = entry.entryId
            retryUsedFor = null
            playRecordedEntryId = null
            completedEntryId = null
            skipRecordedEntryId = null
        }
        _state.update {
            it.copy(
                current = track,
                positionMs = 0,
                durationMs = track.durationMs,
                isBuffering = true,
                error = null,
            )
        }
        scope.launch {
            val resolved = if (track.needsResolution && resolver != null) {
                resolver.resolve(track, forceRefresh)
            } else {
                ResolvedStream(track.streamUrl)
            }
            if (generation != loadGeneration) return@launch
            val uri = resolved.uri
            if (uri.isNullOrBlank()) {
                _state.update {
                    it.copy(
                        isBuffering = false,
                        isPlaying = false,
                        error = resolved.message ?: "\"${track.title}\" has no playable source",
                    )
                }
                return@launch
            }
            engine.load(uri, autoPlay)
        }
        maybeRefill()
    }

    private fun recordSkipIfNeeded(state: PlayerState, index: Int) {
        val entry = state.entries.getOrNull(index) ?: return
        if (skipRecordedEntryId == entry.entryId) return
        skipRecordedEntryId = entry.entryId
        val duration = if (state.durationMs > 0) state.durationMs else entry.track.durationMs
        val short = state.positionMs < 30_000 || (duration > 0 && state.positionMs < duration * 0.2)
        if (short && state.positionMs > 1_000) {
            scope.launch { runCatching { library.recordSkip(entry.track, state.positionMs) } }
        }
    }

    private fun recordPlayAtThreshold(state: PlayerState) {
        val entry = state.entries.getOrNull(state.index) ?: return
        if (playRecordedEntryId == entry.entryId) return
        val duration = if (state.durationMs > 0) state.durationMs else entry.track.durationMs
        val threshold = if (duration > 0) maxOf(30_000L, duration / 2) else 30_000L
        if (state.positionMs < threshold) return
        playRecordedEntryId = entry.entryId
        scope.launch { runCatching { library.recordPlay(entry.track) } }
    }

    private fun recordCompleted(state: PlayerState) {
        val entry = state.entries.getOrNull(state.index) ?: return
        if (completedEntryId == entry.entryId) return
        completedEntryId = entry.entryId
        playRecordedEntryId = entry.entryId
        val playedMs = maxOf(state.positionMs, state.durationMs, entry.track.durationMs)
        scope.launch {
            runCatching { library.recordCompletion(entry.track, playedMs) }
        }
    }

    private fun resetSessionGuards() {
        currentEntryId = null
        retryUsedFor = null
        playRecordedEntryId = null
        completedEntryId = null
        skipRecordedEntryId = null
        lastRefillEntryId = null
        awaitingEndRefill = false
        nativeContinuation = null
        nativeSeed = null
        nativeQueueEnded = false
    }

    private fun nextEntryId(track: Track, origin: QueueOrigin): String =
        "${origin.name.lowercase()}-${track.id}-${++entrySequence}-${Random.nextInt(1_000_000)}"

    // -------------------------------------------------------- engine callbacks

    override fun onReady(durationMs: Long) {
        _state.update {
            it.copy(
                durationMs = if (durationMs > 0) durationMs else it.durationMs,
                isBuffering = false,
            )
        }
    }

    override fun onProgress(positionMs: Long) {
        _state.update { it.copy(positionMs = positionMs) }
        val state = _state.value
        recordPlayAtThreshold(state)
        maybeRefill()
    }

    override fun onPlayingChanged(isPlaying: Boolean) {
        _state.update { it.copy(isPlaying = isPlaying, isBuffering = false) }
    }

    override fun onBuffering(isBuffering: Boolean) {
        _state.update { it.copy(isBuffering = isBuffering) }
    }

    override fun onEnded() {
        val state = _state.value
        recordCompleted(state)
        advance(auto = true, fromEnd = true)
    }

    override fun onError(message: String) {
        val current = _state.value.current
        // Signed YouTube URLs expire. Re-resolve once before surfacing the error.
        if (current?.needsResolution == true && resolver != null && retryUsedFor != current.id) {
            retryUsedFor = current.id
            _state.update { it.copy(isBuffering = true, error = null) }
            loadCurrent(autoPlay = true, forceRefresh = true)
            return
        }
        _state.update { it.copy(error = message, isPlaying = false, isBuffering = false) }
    }
}
