package com.revibe.personalization

import com.revibe.catalog.ArtistKnowledgeSource
import com.revibe.catalog.ArtistRecommendationSeed
import com.revibe.catalog.CatalogRepository
import com.revibe.catalog.CatalogSource
import com.revibe.library.RecommendationCacheRow
import com.revibe.library.RecommendationCacheStore
import com.revibe.model.Album
import com.revibe.model.AlbumDetail
import com.revibe.model.Artist
import com.revibe.model.ArtistAffinity
import com.revibe.model.MusicSourceId
import com.revibe.model.PlayedTrack
import com.revibe.model.Track
import com.revibe.model.UserActivitySnapshot
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MadeForYouServiceTest {

    private val now = 1_700_000_000_000L
    private val sixHours = MADE_FOR_YOU_REFRESH_INTERVAL_MS

    private class FakeSource : CatalogSource {
        override val id = MusicSourceId.YOUTUBE_MUSIC
        override val displayName = "Fake YouTube Music"
        override val idPrefix = "ytm:"

        val seeds = mutableMapOf<String, ArtistRecommendationSeed>()
        val details = mutableMapOf<String, AlbumDetail>()
        var fail = false
        var seedCalls = 0
        var albumCalls = 0
        var mixCalls = 0
        var searchCalls = 0

        fun album(
            id: String,
            artist: String,
            tracks: Int,
            title: String = "Album $id",
            playlist: Boolean = false,
        ) = Album(
            id = id,
            title = title,
            artist = artist,
            trackCount = tracks,
            source = MusicSourceId.YOUTUBE_MUSIC,
            isPlaylist = playlist,
        )

        fun verify(album: Album, tracks: Int) {
            details[album.id] = AlbumDetail(
                album = album.copy(trackCount = tracks),
                tracks = (1..tracks).map { index ->
                    Track(
                        id = "${album.id}:track:$index",
                        title = "Track $index",
                        artist = album.artist,
                        album = album.title,
                        durationMs = 180_000,
                        source = MusicSourceId.YOUTUBE_MUSIC,
                    )
                },
            )
        }

        override suspend fun recommendationSeed(artistId: String, market: String): ArtistRecommendationSeed {
            seedCalls += 1
            if (fail) error("provider offline")
            return seeds[artistId] ?: ArtistRecommendationSeed()
        }

        override suspend fun officialMixes(market: String): List<Album> {
            mixCalls += 1
            if (fail) error("provider offline")
            return emptyList()
        }

        override suspend fun album(albumId: String, market: String): AlbumDetail {
            albumCalls += 1
            if (fail) error("provider offline")
            return details[albumId] ?: error("no detail for $albumId")
        }

        override suspend fun searchTracks(query: String, market: String, limit: Int): List<Track> = emptyList()
        override suspend fun searchAlbums(query: String, market: String, limit: Int): List<Album> = emptyList()
        override suspend fun searchArtists(query: String, market: String, limit: Int): List<Artist> {
            searchCalls += 1
            return emptyList()
        }
        override suspend fun searchPlaylists(query: String, market: String, limit: Int): List<Album> = emptyList()
        override suspend fun artist(artistId: String, market: String): com.revibe.model.ArtistDetail =
            error("not used")
        override suspend fun albumsForArtist(artistId: String, market: String, limit: Int): List<Album> = emptyList()
        override suspend fun tracksForArtist(
            artistId: String,
            artistName: String,
            market: String,
            limit: Int,
        ): List<Track> = emptyList()
    }

    private object NoKnowledge : ArtistKnowledgeSource {
        override suspend fun biography(artistName: String) = null
    }

    private class FakeCacheStore : RecommendationCacheStore {
        var row: RecommendationCacheRow? = null
        var saveCalls = 0
        override suspend fun recommendationCache(): RecommendationCacheRow? = row
        override suspend fun saveRecommendationCache(row: RecommendationCacheRow) {
            this.row = row
            saveCalls += 1
        }
    }

    private fun track(
        artistId: String,
        artist: String,
        albumId: String,
    ) = Track(
        id = "ytm:track:$albumId",
        title = "A song",
        artist = artist,
        album = "An album",
        durationMs = 200_000,
        source = MusicSourceId.YOUTUBE_MUSIC,
        artistId = artistId,
        albumId = albumId,
    )

    private fun activity(artist: String = "Top Artist", artistId: String = "ytm:artist:top") =
        UserActivitySnapshot(
            playCount = 40,
            distinctArtists = 2,
            topArtists = listOf(ArtistAffinity(artist, 20, artistId)),
        )

    private fun recentHistory(artist: String = "Top Artist", artistId: String = "ytm:artist:top") =
        listOf(PlayedTrack(track(artistId, artist, "ytm:album:played"), playedAt = now - 60_000))

    private fun setupCatalog(source: FakeSource, includeSingle: Boolean = false) {
        val familiar = source.album("ytm:album:familiar", "Top Artist", tracks = 9, title = "Familiar Album")
        val playlist = source.album(
            id = "ytm:playlist:PLtop",
            artist = "YouTube Music",
            tracks = 30,
            title = "Bollywood Romance Hitlist",
            playlist = true,
        )
        val relatedAlbum = source.album("ytm:album:related", "Related Artist", tracks = 7, title = "Related Album")
        val single = source.album("ytm:album:single", "Top Artist", tracks = 1, title = "Sajnaa")
        source.verify(familiar, 9)
        source.verify(playlist, 30)
        source.verify(relatedAlbum, 7)
        source.verify(single, 1)
        source.seeds["ytm:artist:top"] = ArtistRecommendationSeed(
            albums = listOfNotNull(familiar, single.takeIf { includeSingle }),
            playlists = listOf(playlist),
            relatedArtists = listOf(Artist("ytm:artist:related", "Related Artist")),
        )
        source.seeds["ytm:artist:related"] = ArtistRecommendationSeed(
            albums = listOf(relatedAlbum),
        )
    }

    private fun cachedItems(source: FakeSource) = listOf(
        RecommendedAlbum(source.album("ytm:album:old", "Old Artist", tracks = 8), RecommendedContentType.ALBUM),
        RecommendedAlbum(
            source.album("ytm:playlist:old", "YouTube Music", tracks = 20, playlist = true),
            RecommendedContentType.PLAYLIST,
        ),
    )

    @Test
    fun `a fresh six-hour cache avoids every provider recommendation request`() = runBlocking {
        val source = FakeSource()
        val cache = FakeCacheStore()
        val cached = cachedItems(source)
        cache.row = RecommendationCacheRow(now - 60 * 60 * 1_000L, RecommendationCacheCodec.encode(cached))

        val result = MadeForYouService(
            CatalogRepository(source, NoKnowledge),
            cache,
            clock = { now },
        ).recommendations(activity(), recentHistory(), "IN")

        assertEquals(listOf("ytm:album:old", "ytm:playlist:old"), result.map { it.id })
        assertEquals(0, source.seedCalls)
        assertEquals(0, source.albumCalls)
        assertEquals(0, source.mixCalls)
    }

    @Test
    fun `six-hour cache is used after a restart for the full window`() = runBlocking {
        val source = FakeSource()
        val cache = FakeCacheStore()
        cache.row = RecommendationCacheRow(now - sixHours + 1_000L, RecommendationCacheCodec.encode(cachedItems(source)))
        val repository = CatalogRepository(source, NoKnowledge)

        val result = MadeForYouService(repository, cache, clock = { now })
            .recommendations(activity(), recentHistory(), "IN")

        assertTrue(result.isNotEmpty())
        assertEquals(0, source.seedCalls)
    }

    @Test
    fun `an expired cache refreshes real provider content and moves the timestamp`() = runBlocking {
        val source = FakeSource()
        setupCatalog(source)
        val cache = FakeCacheStore()
        cache.row = RecommendationCacheRow(now - sixHours, RecommendationCacheCodec.encode(cachedItems(source)))

        val result = MadeForYouService(
            CatalogRepository(source, NoKnowledge),
            cache,
            clock = { now },
        ).recommendations(activity(), recentHistory(), "IN")

        assertTrue(source.seedCalls > 0)
        assertEquals(now, cache.row?.refreshedAt)
        assertEquals(1, cache.saveCalls)
        assertTrue(result.any { it.id == "ytm:album:familiar" })
        assertTrue(result.any { it.id == "ytm:playlist:PLtop" })
        assertTrue(result.any { it.id == "ytm:album:related" })
    }

    @Test
    fun `a failed refresh keeps the previous set and does not restart the timer`() = runBlocking {
        val source = FakeSource()
        setupCatalog(source)
        val cache = FakeCacheStore()
        val oldRow = RecommendationCacheRow(now - sixHours - 60_000L, RecommendationCacheCodec.encode(cachedItems(source)))
        cache.row = oldRow
        source.fail = true
        var clockNow = now
        val service = MadeForYouService(
            CatalogRepository(source, NoKnowledge),
            cache,
            clock = { clockNow },
        )

        val result = service.recommendations(activity(), recentHistory(), "IN")

        assertEquals(listOf("ytm:album:old", "ytm:playlist:old"), result.map { it.id })
        assertEquals(oldRow.refreshedAt, cache.row?.refreshedAt)
        assertEquals(0, cache.saveCalls)

        // The next Home visit retries because the failed refresh did not extend the window.
        clockNow += 60_000L
        val before = source.seedCalls
        service.recommendations(activity(), recentHistory(), "IN")
        assertTrue(source.seedCalls > before)
        assertEquals(oldRow.refreshedAt, cache.row?.refreshedAt)
    }

    @Test
    fun `single-track releases are excluded from a real refresh`() = runBlocking {
        val source = FakeSource()
        setupCatalog(source, includeSingle = true)
        val cache = FakeCacheStore()

        val result = MadeForYouService(
            CatalogRepository(source, NoKnowledge),
            cache,
            clock = { now },
        ).recommendations(activity(), recentHistory(), "IN")

        assertFalse(result.any { it.id == "ytm:album:single" })
        assertTrue(result.all { it.album.trackCount != 1 })
    }

    @Test
    fun `an artist with only singles yields playlists and related albums instead`() = runBlocking {
        val source = FakeSource()
        val single = source.album("ytm:album:hamza", "Hamza Malik", tracks = 1, title = "Sajnaa")
        val playlist = source.album(
            "ytm:playlist:PLurdu",
            "YouTube Music",
            tracks = 36,
            title = "Urdu Jams",
            playlist = true,
        )
        val related = source.album("ytm:album:zeeshan", "Zeeshan Ali", tracks = 7, title = "Related Album")
        source.verify(single, 1)
        source.verify(playlist, 36)
        source.verify(related, 7)
        source.seeds["ytm:artist:hamza"] = ArtistRecommendationSeed(
            albums = listOf(single),
            playlists = listOf(playlist),
            relatedArtists = listOf(Artist("ytm:artist:zeeshan", "Zeeshan Ali")),
        )
        source.seeds["ytm:artist:zeeshan"] = ArtistRecommendationSeed(albums = listOf(related))
        val cache = FakeCacheStore()

        val result = MadeForYouService(
            CatalogRepository(source, NoKnowledge),
            cache,
            clock = { now },
        ).recommendations(
            activity(artist = "Hamza Malik", artistId = "ytm:artist:hamza"),
            recentHistory(artist = "Hamza Malik", artistId = "ytm:artist:hamza"),
            "IN",
        )

        assertFalse(result.any { it.id == "ytm:album:hamza" })
        assertTrue(result.any { it.id == "ytm:playlist:PLurdu" })
        assertTrue(result.any { it.id == "ytm:album:zeeshan" })
        assertTrue(result.map { it.id }.distinct().size == result.size)
    }

    @Test
    fun `a first run with no valid provider content stays empty and does not cache`() = runBlocking {
        val source = FakeSource()
        val cache = FakeCacheStore()

        val result = MadeForYouService(
            CatalogRepository(source, NoKnowledge),
            cache,
            clock = { now },
        ).recommendations(activity(), recentHistory(), "IN")

        assertTrue(result.isEmpty())
        assertEquals(0, cache.saveCalls)
        assertEquals(null, cache.row)
    }

    @Test
    fun `cache payload round-trips all three provider content types`() {
        val albums = listOf(
            RecommendedAlbum(
                Album("ytm:album:1", "Album", "Artist", trackCount = 8),
                RecommendedContentType.ALBUM,
            ),
            RecommendedAlbum(
                Album("ytm:playlist:PL1", "Playlist", "YouTube Music", trackCount = 30, isPlaylist = true),
                RecommendedContentType.PLAYLIST,
            ),
            RecommendedAlbum(
                Album("ytm:playlist:RD1", "Mix", "YouTube Music", trackCount = 20, isPlaylist = true),
                RecommendedContentType.MIX,
            ),
        )

        val decoded = RecommendationCacheCodec.decode(RecommendationCacheCodec.encode(albums))

        assertEquals(albums.map { it.id }, decoded?.map { it.id })
        assertEquals(albums.map { it.type }, decoded?.map { it.type })
        assertEquals(8, decoded?.first()?.album?.trackCount)
    }
}
