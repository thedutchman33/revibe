package com.revibe.playback

import com.revibe.model.Track

/** Where a queue row came from. Manual rows always outrank generated rows. */
enum class QueueOrigin { CONTEXT, MANUAL, AUTO }

data class QueueEntry(
    val entryId: String,
    val track: Track,
    val origin: QueueOrigin,
    /** Short, real explanation for AUTO rows. Null for context and manual rows. */
    val reason: String? = null,
    val seedTrackId: String? = null,
)

enum class AutoplayState { OFF, IDLE, REFILLING, READY, EXHAUSTED, FAILED }

/** Case/punctuation-insensitive identity used to suppress duplicate recommendations. */
fun Track.matchKey(): String {
    fun String.normalize() = lowercase()
        .replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
    return "${title.normalize()}|${artist.normalize()}"
}
