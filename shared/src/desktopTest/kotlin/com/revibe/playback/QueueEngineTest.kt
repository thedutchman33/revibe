package com.revibe.playback

import com.revibe.data.JdbcLibraryStore
import com.revibe.library.LibraryRepository
import com.revibe.model.MusicSourceId
import com.revibe.model.Track
import com.revibe.recommendation.AutoplayService
import com.revibe.recommendation.NativeQueueProvider
import com.revibe.youtube.NativeQueuePage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The controller must consume InnerTubeX's native queue order unchanged. */
class QueueEngineTest {

    @Test
    fun nativeQueueOrderIsPreservedAndContinuationAppends() = runBlocking {
        val provider = PagedProvider()
        withHarness(provider = provider) { controller, _, _ ->
            controller.playQueue(listOf(track("context")), 0, "Native")
            delay(500)
            assertEquals(
                listOf("native-1", "native-2", "native-3"),
                controller.state.value.entries.drop(1).map { it.track.title },
            )
            assertEquals(AutoplayState.READY, controller.state.value.autoplayState)

            controller.playAt(1)
            delay(700)
            assertEquals(
                listOf("native-1", "native-2", "native-3", "next-4", "next-5"),
                controller.state.value.entries.drop(1).map { it.track.title },
            )
            assertEquals(listOf(null, "cont-1"), provider.continuations)
        }
    }

    @Test
    fun manualRowsAlwaysStayAheadOfNativeRows() = runBlocking {
        withHarness(provider = PagedProvider(delayMs = 250)) { controller, _, _ ->
            controller.playQueue(listOf(track("context-a"), track("context-b")), 0, "Test")
            val manualNext = track("manual-next")
            controller.playNext(manualNext)
            delay(900)
            val entries = controller.state.value.entries
            assertEquals(manualNext.id, entries[1].track.id)
            val manualIndex = entries.indexOfFirst { it.track.id == manualNext.id }
            val firstNative = entries.indexOfFirst { it.origin == QueueOrigin.AUTO }
            assertTrue(firstNative > manualIndex, "Manual Play next was not ahead of native Up next")

            val added = track("manual-added")
            controller.addToQueue(added)
            val after = controller.state.value.entries
            val addedIndex = after.indexOfFirst { it.track.id == added.id }
            val nativeAfter = after.indexOfFirst { it.origin == QueueOrigin.AUTO }
            assertTrue(addedIndex in 0 until nativeAfter, "Add to queue landed after a native row")
            assertTrue(after.filter { it.origin == QueueOrigin.AUTO }.all { it.reason == "Up next" })
        }
    }

    @Test
    fun autoplayOffNeverCallsTheNativeQueue() = runBlocking {
        withHarness(autoplayEnabled = false) { controller, _, provider ->
            controller.playQueue(listOf(track("one"), track("two")), 0, "Off")
            delay(500)
            assertEquals(2, controller.state.value.entries.size)
            assertEquals(AutoplayState.OFF, controller.state.value.autoplayState)
            assertEquals(0, provider.calls, "Autoplay called InnerTubeX while disabled")
        }
    }

    @Test
    fun nativeQueueFailureKeepsManualPlaybackWorking() = runBlocking {
        withHarness(fail = true) { controller, _, _ ->
            controller.playQueue(listOf(track("one"), track("two")), 0, "Failure")
            delay(600)
            val state = controller.state.value
            assertEquals(AutoplayState.FAILED, state.autoplayState)
            assertNotNull(state.current)
            assertEquals(2, state.entries.size)
            controller.next()
            delay(150)
            assertEquals("two", controller.state.value.current?.title)
        }
    }

    @Test
    fun staleNativeQueueFromPreviousContextIsDiscarded() = runBlocking {
        withHarness(provider = SeedAwareProvider(delayMs = 300)) { controller, _, _ ->
            controller.playQueue(listOf(track("A-context")), 0, "A")
            delay(50)
            controller.playQueue(listOf(track("B-context")), 0, "B")
            delay(900)
            val entries = controller.state.value.entries
            assertTrue(entries.any { it.track.title.startsWith("B-native") }, "No native rows for B")
            assertTrue(entries.none { it.track.title.startsWith("A-native") }, "Stale A rows leaked into B")
        }
    }

    @Test
    fun endOfTrackAdvancesAutomatically() = runBlocking {
        withHarness(autoplayEnabled = false) { controller, engine, _ ->
            controller.playQueue(listOf(track("one"), track("two")), 0, "End")
            delay(150)
            assertEquals("one", controller.state.value.current?.title)
            engine.lastListener?.onEnded()
            delay(250)
            assertEquals("two", controller.state.value.current?.title)
            assertEquals(1, controller.state.value.index)
        }
    }

    @Test
    fun historyStoresSkipAndCompletionSignals() = runBlocking {
        val dir = tempDir()
        val store = JdbcLibraryStore(File(dir, "history.db"), Dispatchers.IO)
        val skipped = track("skipped")
        val completed = track("completed")
        store.recordSkip(skipped, 4_000)
        store.recordCompletion(completed, 200_000)
        assertEquals(listOf(skipped.id), store.skippedTrackIds(10))
        assertTrue(store.recentlyPlayed(10).any { it.id == completed.id })
        dir.deleteRecursively()
        Unit
    }

    // ------------------------------------------------------------------ harness

    private suspend fun withHarness(
        autoplayEnabled: Boolean = true,
        provider: NativeQueueProvider? = null,
        fail: Boolean = false,
        block: suspend (PlayerController, FakeEngine, CountingProvider) -> Unit,
    ) {
        val dir = tempDir()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = JdbcLibraryStore(File(dir, "queue.db"), Dispatchers.IO)
        val library = LibraryRepository(store)
        val engine = FakeEngine()
        val counting: CountingProvider = if (provider is CountingProvider) {
            provider
        } else {
            CallCountingProvider(provider ?: PagedProvider(fail = fail))
        }
        val controller = PlayerController(
            engine = engine,
            library = library,
            scope = scope,
            autoplay = AutoplayService(counting),
            initialAutoplayEnabled = autoplayEnabled,
        )
        try {
            block(controller, engine, counting)
        } finally {
            controller.release()
            scope.cancel()
            dir.deleteRecursively()
        }
    }

    private var sequence = 0

    private fun track(name: String): Track {
        val id = "test-${name}-${++sequence}"
        return Track(
            id = id,
            title = name,
            artist = "Artist",
            album = "Album",
            durationMs = 200_000,
            source = MusicSourceId.LOCAL,
            streamUrl = "https://example.test/$id",
        )
    }

    private fun nativeTrack(title: String): Track = Track(
        id = "ytm:track:$title",
        title = title,
        artist = "Native Artist",
        album = "Native Album",
        durationMs = 180_000,
        source = MusicSourceId.YOUTUBE_MUSIC,
        streamUrl = "https://example.test/$title",
    )

    private fun tempDir(): File =
        File(System.getProperty("java.io.tmpdir"), "revibe-queue-${System.nanoTime()}").apply { mkdirs() }

    private class FakeEngine : PlaybackEngine {
        var lastListener: PlaybackEngine.Listener? = null
        override fun setListener(listener: PlaybackEngine.Listener) { lastListener = listener }
        override fun load(uri: String, autoPlay: Boolean) = Unit
        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun setVolume(volume: Float) = Unit
        override fun stop() = Unit
        override fun release() = Unit
    }

    interface CountingProvider : NativeQueueProvider {
        val calls: Int
        val continuations: List<String?>
    }

    private class CallCountingProvider(private val delegate: NativeQueueProvider) : CountingProvider {
        override var calls: Int = 0
            private set
        override val continuations = mutableListOf<String?>()

        override suspend fun page(seed: Track, continuation: String?, limit: Int): NativeQueuePage {
            calls++
            continuations += continuation
            return delegate.page(seed, continuation, limit)
        }
    }

    private class PagedProvider(
        private val delayMs: Long = 0,
        private val fail: Boolean = false,
    ) : CountingProvider {
        override var calls: Int = 0
            private set
        override val continuations = mutableListOf<String?>()

        override suspend fun page(seed: Track, continuation: String?, limit: Int): NativeQueuePage {
            calls++
            continuations += continuation
            if (delayMs > 0) delay(delayMs)
            if (fail) error("InnerTubeX is offline")
            return when (continuation) {
                null -> NativeQueuePage(
                    seed.id,
                    listOf(native("native-1"), native("native-2"), native("native-3")),
                    "cont-1",
                )
                "cont-1" -> NativeQueuePage(
                    seed.id,
                    listOf(native("next-4"), native("next-5")),
                    null,
                )
                else -> NativeQueuePage(seed.id, emptyList(), null)
            }
        }

        private fun native(title: String) = Track(
            id = "ytm:track:$title",
            title = title,
            artist = "Native Artist",
            album = "Native Album",
            durationMs = 180_000,
            source = MusicSourceId.LOCAL,
            streamUrl = "https://example.test/$title",
        )
    }

    private class SeedAwareProvider(private val delayMs: Long) : CountingProvider {
        override var calls: Int = 0
            private set
        override val continuations = mutableListOf<String?>()

        override suspend fun page(seed: Track, continuation: String?, limit: Int): NativeQueuePage {
            calls++
            continuations += continuation
            delay(delayMs)
            val prefix = if (seed.title.startsWith("A-")) "A" else "B"
            return NativeQueuePage(
                seed.id,
                (1..3).map { index ->
                    Track(
                        id = "ytm:track:$prefix-native-$index",
                        title = "$prefix-native-$index",
                        artist = "Artist",
                        album = "Album",
                        durationMs = 180_000,
                        source = MusicSourceId.LOCAL,
                        streamUrl = "https://example.test/$prefix/$index",
                    )
                },
                null,
            )
        }
    }
}
