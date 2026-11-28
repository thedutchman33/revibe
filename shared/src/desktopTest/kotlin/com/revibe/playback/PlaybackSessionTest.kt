package com.revibe.playback

import com.revibe.data.JdbcLibraryStore
import com.revibe.library.LibraryRepository
import com.revibe.model.MusicSourceId
import com.revibe.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The session saved on close must round-trip and reopen paused at the same second. */
class PlaybackSessionTest {

    @Test
    fun repositoryRoundTripsQueuePositionAndModes() = runBlocking {
        val dir = tempDir()
        val store = JdbcLibraryStore(File(dir, "session.db"), Dispatchers.IO)
        val repository = PlaybackSessionRepository(store)
        try {
            assertNull(repository.load(), "A fresh database must not restore anything")

            val session = PlaybackSession(
                queue = listOf(track("one"), track("two", local = true)),
                index = 1,
                positionMs = 61_000,
                shuffle = true,
                repeat = RepeatMode.ALL,
                contextName = "Evening mix",
            )
            repository.save(session)
            assertEquals(session, repository.load())

            repository.clear()
            assertNull(repository.load(), "Clearing the queue must forget the session")
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun restoreOpensPausedAndSeeksToTheSavedPosition() = runBlocking {
        val dir = tempDir()
        val store = JdbcLibraryStore(File(dir, "restore.db"), Dispatchers.IO)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val engine = RecordingEngine()
        val controller = PlayerController(
            engine = engine,
            library = LibraryRepository(store),
            scope = scope,
            initialAutoplayEnabled = false,
        )
        try {
            val first = track("first")
            val second = track("second")
            controller.restoreSession(
                PlaybackSession(
                    queue = listOf(first, second),
                    index = 1,
                    positionMs = 61_000,
                    contextName = "Saved queue",
                ),
            )
            delay(200)

            val restored = controller.state.value
            assertEquals(second.id, restored.current?.id)
            assertEquals(2, restored.entries.size)
            assertEquals(1, restored.index)
            assertFalse(restored.isPlaying, "Restoring must not start audio")
            assertEquals(61_000, restored.positionMs)
            assertTrue(engine.loadedUris.isNotEmpty())

            // The FFmpeg engine reports the decoded duration before it is seekable.
            engine.lastListener?.onReady(200_000)
            assertEquals(61_000, engine.lastSeekMs, "The restored row was not seeked to the saved point")
            assertEquals(61_000, controller.state.value.positionMs)
        } finally {
            controller.release()
            scope.cancel()
            dir.deleteRecursively()
        }
    }

    private var sequence = 0

    private fun track(name: String, local: Boolean = false): Track {
        val id = "session-${name}-${++sequence}"
        return Track(
            id = id,
            title = name,
            artist = "Artist",
            album = "Album",
            durationMs = 200_000,
            artworkUrl = "https://example.test/$id.jpg",
            streamUrl = if (local) null else "https://example.test/$id",
            source = if (local) MusicSourceId.LOCAL else MusicSourceId.YOUTUBE_MUSIC,
            localPath = if (local) "C:/music/$id.flac" else null,
        )
    }

    private fun tempDir(): File =
        File(System.getProperty("java.io.tmpdir"), "revibe-session-${System.nanoTime()}").apply { mkdirs() }

    private class RecordingEngine : PlaybackEngine {
        var lastListener: PlaybackEngine.Listener? = null
        val loadedUris = mutableListOf<String>()
        var lastSeekMs: Long? = null

        override fun setListener(listener: PlaybackEngine.Listener) {
            lastListener = listener
        }

        override fun load(uri: String, autoPlay: Boolean) {
            loadedUris += uri
        }

        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMs: Long) {
            lastSeekMs = positionMs
        }

        override fun setVolume(volume: Float) = Unit
        override fun stop() = Unit
        override fun release() = Unit
    }
}
