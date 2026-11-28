package com.revibe.playback

import com.revibe.library.LibraryStore
import com.revibe.model.Track
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The player state Revibe reopens with: the queue, the row that was loaded and how
 * far into it the user got. Play/pause itself is deliberately not restored — the
 * app always comes back paused at the saved position instead of starting audio on
 * launch.
 */
@Serializable
data class PlaybackSession(
    val queue: List<Track>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val contextName: String = "",
)

/** Stores the session JSON in the existing SQLite settings table. */
class PlaybackSessionRepository(private val store: LibraryStore) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun load(): PlaybackSession? {
        val raw = store.getSetting(KEY)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { json.decodeFromString(PlaybackSession.serializer(), raw) }.getOrNull()
    }

    suspend fun save(session: PlaybackSession) {
        store.setSetting(KEY, json.encodeToString(PlaybackSession.serializer(), session))
    }

    suspend fun clear() {
        store.setSetting(KEY, "")
    }

    private companion object {
        const val KEY = "playbackSession"
    }
}
