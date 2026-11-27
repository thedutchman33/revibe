package com.revibe.recommendation

import com.revibe.catalog.CatalogRepository
import com.revibe.model.MusicSourceId
import com.revibe.model.Track
import com.revibe.youtube.NativeQueuePage
import com.revibe.youtube.YouTubeMusicSource
import kotlinx.coroutines.withTimeout

/**
 * Provider contract for the native YouTube Music Up next queue. Tests inject a
 * fake page; production uses InnerTubeX's real `next`/continuation endpoint.
 * There is no local ranking or reordering here.
 */
interface NativeQueueProvider {
    suspend fun page(seed: Track, continuation: String?, limit: Int): NativeQueuePage
}

class AutoplayService(
    private val provider: NativeQueueProvider,
    private val timeoutMs: Long = 12_000,
) {
    suspend fun next(seed: Track, continuation: String?, limit: Int): NativeQueuePage =
        withTimeout(timeoutMs) {
            provider.page(seed, continuation, limit)
        }
}

/**
 * Resolves a Revibe seed to a YouTube Music video id and asks InnerTubeX for the
 * native queue. For a local seed we only map the *same* song to YouTube Music;
 * we never invent recommendations.
 */
class InnerTubeNativeQueueProvider(
    private val youtube: YouTubeMusicSource,
    private val catalog: CatalogRepository,
    private val market: () -> String,
) : NativeQueueProvider {

    override suspend fun page(seed: Track, continuation: String?, limit: Int): NativeQueuePage {
        val nativeSeed = when (seed.source) {
            MusicSourceId.YOUTUBE_MUSIC -> seed
            else -> catalog.search("${seed.title} ${seed.artist}", market())
                .tracks
                .firstOrNull { it.source == MusicSourceId.YOUTUBE_MUSIC }
        } ?: return NativeQueuePage("", emptyList(), null)
        return youtube.nativeQueue(nativeSeed, continuation, limit)
    }
}
