package com.revibe.playback

import com.revibe.model.MusicSourceId
import com.revibe.model.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QueuePolicyTest {

    private fun track(id: String) = Track(
        id = id,
        title = "Title $id",
        artist = "Artist",
        album = "Album",
        durationMs = 200_000,
        source = MusicSourceId.LOCAL,
        streamUrl = "https://example.test/$id",
    )

    private fun entry(id: String, origin: QueueOrigin) = QueueEntry(id, track("track-$id"), origin)

    @Test
    fun manualEntriesInsertBeforeAutoplayRows() {
        val context = listOf(entry("c0", QueueOrigin.CONTEXT), entry("c1", QueueOrigin.CONTEXT))
        val next = QueuePolicy.insertManual(context, track("manual-1"), 0, "m1", playNext = true)
        assertEquals(QueueOrigin.MANUAL, next[1].origin)

        val queued = QueuePolicy.insertManual(next, track("manual-2"), 0, "m2", playNext = false)
        assertEquals(listOf("c0", "m1", "m2", "c1"), queued.map { it.entryId })

        val withAuto = QueuePolicy.appendAuto(
            queued,
            listOf(QueueEntry("a1", track("auto"), QueueOrigin.AUTO)),
        )
        assertEquals(QueueOrigin.AUTO, withAuto.last().origin)
        assertTrue(withAuto.indexOfFirst { it.origin == QueueOrigin.MANUAL } < withAuto.indexOfFirst { it.origin == QueueOrigin.AUTO })
    }

    @Test
    fun autoplayRejectsDuplicateIdsAndNormalizedTitles() {
        val existing = listOf(entry("c0", QueueOrigin.CONTEXT))
        val duplicateId = QueueEntry("a1", track("track-c0"), QueueOrigin.AUTO)
        val sameSongDifferentId = QueueEntry(
            "a2",
            track("other").copy(title = "Title track-c0", artist = "Artist"),
            QueueOrigin.AUTO,
        )
        val fresh = QueueEntry("a3", track("fresh"), QueueOrigin.AUTO)
        val appended = QueuePolicy.appendAuto(existing, listOf(duplicateId, sameSongDifferentId, fresh))
        assertEquals(listOf("c0", "a3"), appended.map { it.entryId })
    }

    @Test
    fun refillThresholdAndDisabledAutoplay() {
        val entries = (0..9).map { entry("c$it", QueueOrigin.CONTEXT) }
        assertTrue(!QueuePolicy.needsRefill(entries, 0, enabled = true, positionMs = 0, durationMs = 200_000))
        assertTrue(QueuePolicy.needsRefill(entries, 7, enabled = true, positionMs = 0, durationMs = 200_000))
        assertTrue(!QueuePolicy.needsRefill(entries, 7, enabled = false, positionMs = 0, durationMs = 200_000))
        assertTrue(QueuePolicy.needsRefill(entries, 5, enabled = true, positionMs = 185_000, durationMs = 200_000))
    }
}
