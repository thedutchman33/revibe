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
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExpiredStreamRecoveryTest {
    @Test
    fun reResolvesOnceAfterPlaybackError() = runBlocking {
        val dir = File(System.getProperty("java.io.tmpdir"), "revibe-expiry-${System.nanoTime()}").apply { mkdirs() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val engine = FakeEngine()
        val calls = CopyOnWriteArrayList<Boolean>()
        val resolver = object : TrackStreamResolver {
            override suspend fun resolve(track: Track, forceRefresh: Boolean): ResolvedStream {
                calls += forceRefresh
                return if (forceRefresh) {
                    ResolvedStream("https://refreshed.test/stream.m4a")
                } else {
                    ResolvedStream("https://expired.test/stream.m4a")
                }
            }
        }
        val controller = PlayerController(
            engine = engine,
            library = LibraryRepository(JdbcLibraryStore(File(dir, "library.db"), Dispatchers.IO)),
            scope = scope,
            resolver = resolver,
        )
        val track = Track(
            id = "ytm:track:expired0001",
            title = "Expiry test",
            artist = "Test",
            album = "Test",
            durationMs = 200_000,
            source = MusicSourceId.YOUTUBE_MUSIC,
        )
        controller.playQueue(listOf(track), 0, "Expiry")
        delay(400)
        assertEquals("https://expired.test/stream.m4a", engine.lastUri)
        assertEquals(listOf(false), calls)

        engine.eventListener?.onError("HTTP 403: signed URL expired")
        delay(500)
        assertEquals(listOf(false, true), calls, "The expired URL should be refreshed exactly once")
        assertEquals("https://refreshed.test/stream.m4a", engine.lastUri)
        assertTrue(controller.state.value.error == null, "Recovery should not surface an error yet")

        controller.release()
        scope.cancel()
        dir.deleteRecursively()
        Unit
    }

    private class FakeEngine : PlaybackEngine {
        var eventListener: PlaybackEngine.Listener? = null
        var lastUri: String? = null

        override fun setListener(listener: PlaybackEngine.Listener) {
            eventListener = listener
        }

        override fun load(uri: String, autoPlay: Boolean) {
            lastUri = uri
        }

        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun setVolume(volume: Float) = Unit
        override fun stop() = Unit
        override fun release() = Unit
    }
}
