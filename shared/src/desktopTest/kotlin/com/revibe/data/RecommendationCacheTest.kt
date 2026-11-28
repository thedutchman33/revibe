package com.revibe.data

import com.revibe.library.RecommendationCacheRow
import com.revibe.model.MusicSourceId
import com.revibe.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecommendationCacheTest {

    private fun newStore(name: String): JdbcLibraryStore {
        val dir = Files.createTempDirectory("revibe-$name").toFile()
        return JdbcLibraryStore(File(dir, "library.db"), Dispatchers.IO)
    }

    @Test
    fun `recommendation cache stores one timestamped payload in sqlite`() = runBlocking {
        val store = newStore("recommendation-cache")
        assertNull(store.recommendationCache())

        store.saveRecommendationCache(RecommendationCacheRow(1_000L, """{"items":[]}"""))
        assertEquals(1_000L, store.recommendationCache()?.refreshedAt)
        assertEquals("""{"items":[]}""", store.recommendationCache()?.payload)

        store.saveRecommendationCache(RecommendationCacheRow(2_000L, """{"items":[1]}"""))
        assertEquals(2_000L, store.recommendationCache()?.refreshedAt)
        assertEquals("""{"items":[1]}""", store.recommendationCache()?.payload)
    }

    @Test
    fun `activity exposes artist ids and frequently played albums`() = runBlocking {
        val store = newStore("recommendation-signals")
        val track = Track(
            id = "ytm:track:one",
            title = "Song",
            artist = "Arijit Singh",
            album = "Real Album",
            durationMs = 200_000,
            source = MusicSourceId.YOUTUBE_MUSIC,
            artistId = "ytm:artist:arijit",
            albumId = "ytm:album:real",
        )
        repeat(3) { store.recordPlay(track) }

        val activity = store.userActivity()

        assertEquals("ytm:artist:arijit", activity.topArtists.single().artistId)
        assertEquals(3, activity.topArtists.single().plays)
        assertEquals("ytm:album:real", activity.topAlbums.single().albumId)
        assertEquals("Arijit Singh", activity.topAlbums.single().artist)
        assertEquals(3, activity.topAlbums.single().plays)
        assertTrue(activity.topAlbums.single().albumId.startsWith("ytm:album:"))
    }
}
