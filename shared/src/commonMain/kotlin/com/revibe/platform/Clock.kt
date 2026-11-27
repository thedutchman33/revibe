package com.revibe.platform

expect fun currentTimeMillis(): Long

/** Calendar date for playlist "Date added" columns; platform locale formatting stays in the actual. */
expect fun formatAddedDate(epochMillis: Long): String

/** Parses a Postgres/PostgREST timestamp; null when the value is malformed. */
expect fun isoToMillis(value: String): Long?

/** Formats an epoch millisecond value for PostgREST timestamptz columns. */
expect fun millisToIso(value: Long): String

/** Human-friendly age for real history rows; testable with a fixed [now]. */
fun relativeTime(playedAt: Long, now: Long = currentTimeMillis()): String {
    val delta = (now - playedAt).coerceAtLeast(0)
    val minutes = delta / 60_000
    val hours = delta / 3_600_000
    val days = delta / 86_400_000
    return when {
        delta < 60_000 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> "${days / 7}w ago"
    }
}
