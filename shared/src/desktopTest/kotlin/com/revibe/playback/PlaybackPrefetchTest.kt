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

class PlaybackPrefetchTest {
    @Test
    fun `queue load warms next stream and rapid next never plays an obsolete track`() = runBlocking {
        val dir = File(System.getProperty("java.io.tmpdir"), "revibe-prefetch-${System.nanoTime()}").apply { mkdirs() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val engine = RecordingEngine()
        val tracks = listOf(track("one"), track("two"), track("three"))
        val resolver = RecordingResolver(delayedTrackId = tracks[1].id)
        val controller = PlayerController(
            engine = engine,
            library = LibraryRepository(JdbcLibraryStore(File(dir, "library.db"), Dispatchers.IO)),
            scope = scope,
            resolver = resolver,
        )
        try {
            controller.playQueue(tracks, 0, "Prefetch")
            waitUntil { engine.loaded == listOf(tracks[0].id) }
            waitUntil { tracks[1].id in resolver.prefetched }

            // Both clicks happen before track two's deliberately delayed resolve
            // finishes. Only the final row may reach the audio engine.
            controller.next()
            controller.next()
            waitUntil { tracks[2].id in engine.loaded }
            delay(650)

            assertEquals(listOf(tracks[0].id, tracks[2].id), engine.loaded)
            assertTrue(engine.loaded.none { it == tracks[1].id }, "Obsolete Next row was played")
        } finally {
            controller.release()
            scope.cancel()
            dir.deleteRecursively()
        }
    }

    @Test
    fun `next click while prefetch is in flight keeps the shared resolve alive`() = runBlocking {
        val dir = File(System.getProperty("java.io.tmpdir"), "revibe-prefetch-join-${System.nanoTime()}").apply { mkdirs() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val engine = RecordingEngine()
        val tracks = listOf(track("one"), track("two"))
        val resolver = SharedPrefetchResolver()
        val controller = PlayerController(
            engine = engine,
            library = LibraryRepository(JdbcLibraryStore(File(dir, "library.db"), Dispatchers.IO)),
            scope = scope,
            resolver = resolver,
        )
        try {
            controller.playQueue(tracks, 0, "Join")
            waitUntil { engine.loaded == listOf(tracks[0].id) }
            waitUntil { resolver.prefetchStarted }
            controller.next()
            waitUntil { tracks[1].id in engine.loaded }
            assertEquals(listOf(tracks[0].id, tracks[1].id), engine.loaded)
        } finally {
            controller.release()
            scope.cancel()
            dir.deleteRecursively()
        }
    }

    private suspend fun waitUntil(timeoutMs: Long = 3_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out waiting for playback state" }
            delay(20)
        }
    }

    private fun track(name: String) = Track(
        id = "ytm:track:$name",
        title = name,
        artist = "Artist",
        album = "Album",
        durationMs = 200_000,
        source = MusicSourceId.YOUTUBE_MUSIC,
    )

    private class RecordingResolver(private val delayedTrackId: String) : TrackStreamResolver {
        val prefetched = CopyOnWriteArrayList<String>()
        val resolved = CopyOnWriteArrayList<String>()

        override suspend fun resolve(track: Track, forceRefresh: Boolean): ResolvedStream {
            resolved += track.id
            if (track.id == delayedTrackId) delay(500)
            return ResolvedStream("https://stream.test/${track.id}")
        }

        override suspend fun prefetch(track: Track): ResolvedStream? {
            prefetched += track.id
            return ResolvedStream("https://stream.test/${track.id}")
        }
    }

    /** Resolve joins the prefetch's deferred, exactly like the real resolver's in-flight map. */
    private class SharedPrefetchResolver : TrackStreamResolver {
        @Volatile
        var prefetchStarted = false
        private val shared = kotlinx.coroutines.CompletableDeferred<ResolvedStream>()

        override suspend fun resolve(track: Track, forceRefresh: Boolean): ResolvedStream =
            if (track.id.endsWith("two")) shared.await() else ResolvedStream("https://stream.test/${track.id}")

        override suspend fun prefetch(track: Track): ResolvedStream {
            prefetchStarted = true
            delay(350)
            return ResolvedStream("https://stream.test/${track.id}").also { shared.complete(it) }
        }
    }

    private class RecordingEngine : PlaybackEngine {
        val loaded = CopyOnWriteArrayList<String>()

        override fun setListener(listener: PlaybackEngine.Listener) = Unit

        override fun load(uri: String, autoPlay: Boolean) {
            loaded += uri.substringAfterLast('/')
        }

        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun setVolume(volume: Float) = Unit
        override fun stop() = Unit
        override fun release() = Unit
    }
}
