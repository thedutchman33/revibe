package com.revibe.personalization

import com.revibe.model.Album
import com.revibe.model.MusicSourceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MadeForYouSelectorTest {

    private fun album(
        id: String,
        artist: String,
        tracks: Int,
        title: String = "Album $id",
        source: MusicSourceId = MusicSourceId.YOUTUBE_MUSIC,
        playlist: Boolean = false,
    ) = Album(
        id = id,
        title = title,
        artist = artist,
        trackCount = tracks,
        source = source,
        isPlaylist = playlist,
    )

    private fun select(
        familiar: List<Album> = emptyList(),
        discovery: List<Album> = emptyList(),
        playlists: List<Album> = emptyList(),
        mixes: List<Album> = emptyList(),
        previous: Set<String> = emptySet(),
    ) = MadeForYouSelector.select(
        familiarAlbums = familiar,
        discoveryAlbums = discovery,
        playlists = playlists,
        mixes = mixes,
        affinity = mapOf("hamza malik" to 20),
        recentArtists = setOf("hamza malik"),
        recentlyPlayedAlbumIds = emptySet(),
        previousIds = previous,
        seed = 1,
    )

    @Test
    fun `single-track albums are excluded`() {
        val result = select(
            familiar = listOf(
                album("single", "Hamza Malik", tracks = 1, title = "Sajnaa"),
                album("multi", "Hamza Malik", tracks = 8, title = "Real album"),
            ),
        )

        assertEquals(listOf("multi"), result.map { it.id })
    }

    @Test
    fun `one album per artist even when the provider returns several`() {
        val result = select(
            familiar = listOf(
                album("one", "Hamza Malik", tracks = 8),
                album("two", "Hamza Malik", tracks = 9),
                album("three", "Hamza Malik", tracks = 10),
            ),
        )

        assertEquals(1, result.size)
        assertEquals(RecommendedContentType.ALBUM, result.single().type)
    }

    @Test
    fun `duplicate ids and duplicate titles are collapsed`() {
        val result = select(
            familiar = listOf(
                album("same", "Artist A", tracks = 5, title = "Same Record"),
                album("same", "Artist A", tracks = 5, title = "Same Record"),
                album("copy", "Artist B", tracks = 5, title = "Same Record"),
            ),
        )

        assertEquals(listOf("same"), result.map { it.id })
    }

    @Test
    fun `albums playlists and mixes are interleaved by content type`() {
        val result = select(
            familiar = listOf(album("f1", "Artist F", tracks = 9)),
            playlists = listOf(album("p1", "YouTube Music", tracks = 40, playlist = true)),
            discovery = listOf(album("d1", "Related Artist", tracks = 7)),
            mixes = listOf(album("m1", "YouTube Music", tracks = 20, playlist = true)),
        )

        assertEquals(listOf("f1", "p1", "d1", "m1"), result.map { it.id })
        assertEquals(
            listOf(
                RecommendedContentType.ALBUM,
                RecommendedContentType.PLAYLIST,
                RecommendedContentType.ALBUM,
                RecommendedContentType.MIX,
            ),
            result.map { it.type },
        )
    }

    @Test
    fun `previous refresh ids are avoided when alternatives exist`() {
        val result = select(
            familiar = listOf(
                album("old", "Artist A", tracks = 8),
                album("new", "Artist B", tracks = 8),
            ),
            playlists = listOf(album("old-playlist", "YouTube Music", tracks = 20, playlist = true)),
            previous = setOf("old", "old-playlist"),
        )

        assertEquals(listOf("new"), result.map { it.id })
    }

    @Test
    fun `a tiny provider pool repeats rather than disappearing`() {
        val result = select(
            familiar = listOf(album("only", "Artist A", tracks = 8)),
            previous = setOf("only"),
        )

        assertEquals(listOf("only"), result.map { it.id })
    }

    @Test
    fun `only YouTube Music content is selectable`() {
        val result = select(
            familiar = listOf(
                album("local", "Artist A", tracks = 8, source = MusicSourceId.LOCAL),
                album("spotify", "Artist B", tracks = 8, source = MusicSourceId.SPOTIFY),
                album("online", "Artist C", tracks = 8),
            ),
        )

        assertEquals(listOf("online"), result.map { it.id })
    }

    @Test
    fun `the row is capped at eight diverse cards`() {
        val result = select(
            familiar = (1..5).map { album("f$it", "Familiar $it", tracks = 8) },
            playlists = (1..5).map { album("p$it", "YouTube Music", tracks = 30, playlist = true, title = "List $it") },
            discovery = (1..5).map { album("d$it", "Discovery $it", tracks = 8) },
            mixes = (1..5).map { album("m$it", "YouTube Music", tracks = 20, playlist = true, title = "Mix $it") },
        )

        assertEquals(8, result.size)
        assertEquals(8, result.map { it.id }.distinct().size)
        assertTrue(result.count { it.type == RecommendedContentType.ALBUM } <= 5)
        assertTrue(result.count { it.type == RecommendedContentType.PLAYLIST } <= 3)
        assertTrue(result.count { it.type == RecommendedContentType.MIX } <= 2)
        // The album artists themselves are all distinct.
        val albumArtists = result.filter { it.type == RecommendedContentType.ALBUM }.map { it.album.artist }
        assertEquals(albumArtists.size, albumArtists.distinct().size)
    }

    @Test
    fun `a playlist with one track is not recommended`() {
        val result = select(
            playlists = listOf(
                album("one-song", "YouTube Music", tracks = 1, playlist = true, title = "One song"),
                album("many", "YouTube Music", tracks = 30, playlist = true, title = "Many songs"),
            ),
        )

        assertEquals(listOf("many"), result.map { it.id })
    }
}
