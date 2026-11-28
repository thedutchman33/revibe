package com.revibe.personalization

import com.revibe.catalog.ArtistKnowledgeSource
import com.revibe.catalog.CatalogRepository
import com.revibe.library.RecommendationCacheRow
import com.revibe.library.RecommendationCacheStore
import com.revibe.model.ArtistAffinity
import com.revibe.model.PlayedTrack
import com.revibe.model.UserActivitySnapshot
import com.revibe.youtube.YouTubeTestHarness
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Network test against real InnerTubeX data. It intentionally uses the same
 * service the app uses; the fake cache only supplies the SQLite contract.
 */
class MadeForYouLiveTest {

    private object NoKnowledge : ArtistKnowledgeSource {
        override suspend fun biography(artistName: String) = null
    }

    private class MemoryCache : RecommendationCacheStore {
        var row: RecommendationCacheRow? = null
        override suspend fun recommendationCache(): RecommendationCacheRow? = row
        override suspend fun saveRecommendationCache(row: RecommendationCacheRow) {
            this.row = row
        }
    }

    @Test
    fun `real recommendations are multi-track and artist-diverse`() = runBlocking {
        val source = YouTubeTestHarness.source
        val hamza = source.searchArtists("Hamza Malik", "IN", 1).first()
        val arijit = source.searchArtists("Arijit Singh", "IN", 1).first()
        val hamzaTrack = source.searchTracks("Hamza Malik", "IN", 1).first()
        val arijitTrack = source.searchTracks("Arijit Singh", "IN", 1).first()

        val activity = UserActivitySnapshot(
            playCount = 40,
            distinctArtists = 2,
            topArtists = listOf(
                ArtistAffinity("Hamza Malik", 24, hamza.id),
                ArtistAffinity("Arijit Singh", 16, arijit.id),
            ),
        )
        val recent = listOf(
            PlayedTrack(hamzaTrack, playedAt = 2),
            PlayedTrack(arijitTrack, playedAt = 1),
        )
        val cache = MemoryCache()
        val service = MadeForYouService(CatalogRepository(source, NoKnowledge), cache)

        val first = service.recommendations(activity, recent, "IN")
        println(
            "LIVE_RECOMMENDATIONS " + first.joinToString(" | ") { item ->
                "${item.type.name}:${item.album.title} / ${item.album.artist} (tracks=${item.album.trackCount}, id=${item.album.id})"
            },
        )

        assertTrue(first.isNotEmpty(), "Real InnerTubeX recommendations came back empty")
        assertEquals(first.map { it.id }.distinct().size, first.size, "Duplicate recommendation ids")
        assertTrue(first.all { it.album.source.name == "YOUTUBE_MUSIC" }, "Non-YouTube Music content")
        // Provider mixes do not expose a track count on the home shelf; albums and
        // playlists must all have been verified as genuinely multi-track.
        assertTrue(
            first.filter { it.type != RecommendedContentType.MIX }.all { it.album.trackCount >= 2 },
            "A single-track album or playlist was recommended",
        )
        assertTrue(first.size <= MADE_FOR_YOU_LIMIT)
        assertTrue(first.any { it.type == RecommendedContentType.ALBUM }, "No multi-track album")
        assertTrue(
            first.filter { it.type == RecommendedContentType.ALBUM }
                .map { it.album.artist.lowercase() }
                .let { it.distinct().size == it.size },
            "Multiple albums from one artist",
        )
        assertTrue(cache.row != null, "A successful live refresh was not cached")

        // The second call inside the window must be the cached set.
        val second = service.recommendations(activity, recent, "IN")
        assertEquals(first.map { it.id }, second.map { it.id })
    }
}
