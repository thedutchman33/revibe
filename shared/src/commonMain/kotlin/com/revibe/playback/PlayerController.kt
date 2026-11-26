package com.revibe.playback

import com.revibe.library.LibraryRepository
import com.revibe.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Single source of truth for playback. The engine only decodes audio; every
 * queue decision lives here so the logic is platform independent and testable.
 */
class PlayerController(
    private val engine: PlaybackEngine,
    private val library: LibraryRepository,
    private val scope: CoroutineScope,
    initialVolume: Float = 0.85f,
) : PlaybackEngine.Listener {

    private val _state = MutableStateFlow(PlayerState(volume = initialVolume))
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /** The unshuffled order, kept so shuffle can be turned off without losing order. */
    private var sourceQueue: List<Track> = emptyList()

    init {
        engine.setListener(this)
        engine.setVolume(initialVolume)
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0, contextName: String = "") {
        val playable = tracks.filter { !it.streamUrl.isNullOrBlank() }
        if (playable.isEmpty()) return
        val originalIndex = startIndex.coerceIn(tracks.indices)
        val anchor = tracks[originalIndex]
        sourceQueue = tracks

        val queue = if (_state.value.shuffle) {
            listOf(anchor) + tracks.filter { it.id != anchor.id }.shuffled()
        } else {
            tracks
        }
        val index = queue.indexOfFirst { it.id == anchor.id }.coerceAtLeast(0)
        _state.update {
            it.copy(queue = queue, index = index, contextName = contextName, error = null)
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
            if (state.queue.isNotEmpty()) {
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

    fun next(auto: Boolean = false) {
        val state = _state.value
        if (state.queue.isEmpty()) return
        if (auto && state.repeat == RepeatMode.ONE) {
            engine.seekTo(0)
            engine.play()
            return
        }
        when {
            state.index < state.queue.lastIndex -> {
                _state.update { it.copy(index = it.index + 1, positionMs = 0) }
                loadCurrent(autoPlay = true)
            }
            state.repeat == RepeatMode.ALL -> {
                _state.update { it.copy(index = 0, positionMs = 0) }
                loadCurrent(autoPlay = true)
            }
            auto -> {
                engine.stop()
                _state.update { it.copy(isPlaying = false, positionMs = 0) }
            }
        }
    }

    fun previous() {
        val state = _state.value
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

    fun toggleShuffle() {
        val state = _state.value
        if (sourceQueue.isEmpty()) {
            _state.update { it.copy(shuffle = !it.shuffle) }
            return
        }
        val current = state.current
        val shuffled = !state.shuffle
        val queue = if (shuffled) {
            if (current != null) {
                listOf(current) + sourceQueue.filter { it.id != current.id }.shuffled()
            } else {
                sourceQueue.shuffled()
            }
        } else {
            sourceQueue
        }
        val index = current?.let { c -> queue.indexOfFirst { it.id == c.id } }?.takeIf { it >= 0 } ?: 0
        _state.update { it.copy(shuffle = shuffled, queue = queue, index = index) }
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
        if (index !in state.queue.indices) return
        _state.update { it.copy(index = index, positionMs = 0) }
        loadCurrent(autoPlay = true)
    }

    fun addToQueue(track: Track) {
        if (track.streamUrl.isNullOrBlank()) return
        val state = _state.value
        val queue = state.queue + track
        sourceQueue = sourceQueue + track
        _state.update { it.copy(queue = queue) }
        if (state.current == null) {
            playAt(queue.lastIndex)
        }
    }

    fun playNext(track: Track) {
        if (track.streamUrl.isNullOrBlank()) return
        val state = _state.value
        val insertAt = (state.index + 1).coerceIn(0, state.queue.size)
        val queue = state.queue.toMutableList().apply { add(insertAt, track) }
        sourceQueue = sourceQueue.toMutableList().apply { add(insertAt, track) }
        _state.update { it.copy(queue = queue) }
        if (state.current == null) playAt(insertAt)
    }

    fun removeFromQueue(index: Int) {
        val state = _state.value
        if (index !in state.queue.indices) return
        val removingCurrent = index == state.index
        val queue = state.queue.toMutableList().apply { removeAt(index) }
        sourceQueue = sourceQueue.filterNot { it.id == state.queue[index].id }
        val newIndex = when {
            queue.isEmpty() -> -1
            index < state.index -> state.index - 1
            else -> state.index.coerceAtMost(queue.lastIndex)
        }
        _state.update { it.copy(queue = queue, index = newIndex) }
        if (removingCurrent) {
            if (queue.isEmpty()) {
                engine.stop()
                _state.update { it.copy(isPlaying = false, positionMs = 0, durationMs = 0) }
            } else {
                loadCurrent(autoPlay = true)
            }
        }
    }

    fun moveInQueue(from: Int, to: Int) {
        val state = _state.value
        if (from !in state.queue.indices || to !in state.queue.indices) return
        val queue = state.queue.toMutableList()
        val item = queue.removeAt(from)
        queue.add(to, item)
        val index = state.current?.let { c -> queue.indexOfFirst { it.id == c.id } } ?: state.index
        _state.update { it.copy(queue = queue, index = index) }
    }

    fun stop() {
        engine.stop()
        _state.update { it.copy(isPlaying = false, positionMs = 0) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun release() {
        engine.release()
    }

    private fun loadCurrent(autoPlay: Boolean) {
        val state = _state.value
        val track = state.queue.getOrNull(state.index) ?: return
        val uri = track.streamUrl
        if (uri.isNullOrBlank()) {
            _state.update {
                it.copy(
                    current = track,
                    isBuffering = false,
                    isPlaying = false,
                    error = "\"${track.title}\" has no playable source",
                )
            }
            return
        }
        _state.update {
            it.copy(
                current = track,
                positionMs = 0,
                durationMs = track.durationMs,
                isBuffering = true,
                error = null,
                previewOnly = track.isPreview,
            )
        }
        engine.load(uri, autoPlay)
        scope.launch { runCatching { library.recordPlay(track) } }
    }

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
    }

    override fun onPlayingChanged(isPlaying: Boolean) {
        _state.update { it.copy(isPlaying = isPlaying, isBuffering = false) }
    }

    override fun onBuffering(isBuffering: Boolean) {
        _state.update { it.copy(isBuffering = isBuffering) }
    }

    override fun onEnded() {
        next(auto = true)
    }

    override fun onError(message: String) {
        _state.update { it.copy(error = message, isPlaying = false, isBuffering = false) }
    }
}
