package com.revibe.youtube

import com.revibe.data.JdbcLibraryStore
import com.revibe.library.LibraryRepository
import com.revibe.model.MusicSourceId
import com.revibe.model.Track
import com.revibe.playback.PlaybackEngine
import com.revibe.playback.PlayerController
import com.revibe.playback.ResolvedStream
import com.revibe.playback.TrackStreamResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YouTubeQueueTest {
    @Test
    fun resolvesOnlyTheCurrentQueueItemAndAdvances() = runBlocking {
        val dir = File(System.getProperty("java.io.tmpdir"), "revibe-yt-queue-${System.nanoTime()}").apply { mkdirs() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = JdbcLibraryStore(File(dir, "queue.db"), Dispatchers.IO)
        val engine = FakeEngine()
        val resolved = mutableListOf<String>()
        val resolver = object : TrackStreamResolver {
            override suspend fun resolve(track: Track, forceRefresh: Boolean): ResolvedStream {
                resolved += track.id
                return ResolvedStream("https://example.test/${track.id.substringAfterLast(':')}")
            }
        }
        val controller = PlayerController(
            engine = engine,
            library = LibraryRepository(store),
            scope = scope,
            resolver = resolver,
        )
        val first = ytTrack("ytm:track:aaaaaaaaaaa", "First")
        val second = ytTrack("ytm:track:bbbbbbbbbbb", "Second")
        controller.playQueue(listOf(first, second), 0, "Test queue")
        delay(250)
        assertEquals(first.id, controller.state.value.current?.id)
        assertTrue(engine.lastUri?.contains("aaaaaaaaaaa") == true, "First item was not resolved")
        assertEquals(1, resolved.size, "Queue should resolve only the current item")

        controller.next()
        delay(250)
        assertEquals(second.id, controller.state.value.current?.id)
        assertTrue(engine.lastUri?.contains("bbbbbbbbbbb") == true, "Next item was not resolved")
        assertEquals(listOf(first.id, second.id), resolved)

        controller.release()
        scope.cancel()
        dir.deleteRecursively()
        Unit
    }

    private fun ytTrack(id: String, title: String) = Track(
        id = id,
        title = title,
        artist = "Test artist",
        album = "Test album",
        durationMs = 200_000,
        source = MusicSourceId.YOUTUBE_MUSIC,
    )

    private class FakeEngine : PlaybackEngine {
        var lastUri: String? = null
        override fun setListener(listener: PlaybackEngine.Listener) = Unit
        override fun load(uri: String, autoPlay: Boolean) { lastUri = uri }
        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun setVolume(volume: Float) = Unit
        override fun stop() = Unit
        override fun release() = Unit
    }
}
