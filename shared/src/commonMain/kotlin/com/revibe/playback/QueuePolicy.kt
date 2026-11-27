package com.revibe.playback

import com.revibe.model.Track

/**
 * Pure queue rules. Keeping insertion, priority, dedupe and refill thresholds
 * here means the controller and tests share one definition instead of each
 * re-implementing "manual before autoplay".
 */
object QueuePolicy {
    const val TARGET_AHEAD = 8
    const val REFILL_AHEAD = 3
    const val REFILL_REMAINING_MS = 20_000L

    fun needsRefill(
        entries: List<QueueEntry>,
        index: Int,
        enabled: Boolean,
        positionMs: Long,
        durationMs: Long,
    ): Boolean {
        if (!enabled || entries.isEmpty() || index !in entries.indices) return false
        val ahead = entries.size - index - 1
        if (ahead >= TARGET_AHEAD) return false
        val nearEnd = durationMs > 0 && durationMs - positionMs <= REFILL_REMAINING_MS
        return ahead <= REFILL_AHEAD || nearEnd
    }

    /** Insert after the current track and after any manual rows, but before AUTO rows. */
    fun addToQueueIndex(entries: List<QueueEntry>, currentIndex: Int): Int {
        if (entries.isEmpty()) return 0
        val safeIndex = currentIndex.coerceIn(0, entries.lastIndex)
        val firstAutoAfter = entries.withIndex()
            .firstOrNull { indexed -> indexed.index > safeIndex && indexed.value.origin == QueueOrigin.AUTO }
            ?.index ?: -1
        if (firstAutoAfter >= 0) return firstAutoAfter
        val lastManual = entries.indexOfLast { it.origin == QueueOrigin.MANUAL }
        return if (lastManual > safeIndex) lastManual + 1 else (safeIndex + 1).coerceAtMost(entries.size)
    }

    fun insertManual(
        entries: List<QueueEntry>,
        track: Track,
        currentIndex: Int,
        entryId: String,
        playNext: Boolean,
    ): List<QueueEntry> {
        val at = if (playNext) {
            (currentIndex.coerceAtLeast(-1) + 1).coerceIn(0, entries.size)
        } else {
            addToQueueIndex(entries, currentIndex)
        }
        return entries.toMutableList().apply {
            add(at, QueueEntry(entryId = entryId, track = track, origin = QueueOrigin.MANUAL))
        }
    }

    /**
     * Append generated rows after every existing row while rejecting duplicates
     * against the live queue. Callers also pass history exclusions.
     */
    fun appendAuto(
        entries: List<QueueEntry>,
        candidates: List<QueueEntry>,
        excludedKeys: Set<String> = emptySet(),
    ): List<QueueEntry> {
        if (candidates.isEmpty()) return entries
        val seenIds = entries.mapTo(mutableSetOf()) { it.track.id }
        val seenKeys = entries.mapTo(mutableSetOf()) { it.track.matchKey() }.apply { addAll(excludedKeys) }
        val appended = ArrayList<QueueEntry>(entries.size + candidates.size)
        appended += entries
        candidates.forEach { candidate ->
            val id = candidate.track.id
            val key = candidate.track.matchKey()
            if (id in seenIds || key in seenKeys) return@forEach
            seenIds += id
            seenKeys += key
            appended += candidate.copy(origin = QueueOrigin.AUTO)
        }
        return appended
    }

    fun hasDuplicate(entries: List<QueueEntry>, track: Track): Boolean =
        entries.any { it.track.id == track.id || it.track.matchKey() == track.matchKey() }
}
