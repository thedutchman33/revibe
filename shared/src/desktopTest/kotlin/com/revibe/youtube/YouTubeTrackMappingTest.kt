package com.revibe.youtube

import com.revibe.model.MusicSourceId
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class YouTubeTrackMappingTest {
    @Test
    fun songMetadataMapsToDomain() {
        val track = YouTubeTestHarness.firstTrack()
        println("[mapping] ${track.title} | ${track.artist} | ${track.album} | ${track.artworkUrl} | ${track.artistId}")
        assertTrue(track.title.isNotBlank())
        assertTrue(track.artist.isNotBlank())
        assertTrue(track.album.isNotBlank())
        assertTrue(track.durationMs > 0, "Duration was not parsed")
        assertNotNull(track.artworkUrl, "Artwork missing")
        assertTrue(track.artworkUrl!!.startsWith("https://"))
        assertEquals(MusicSourceId.YOUTUBE_MUSIC, track.source)
        assertTrue(track.id.startsWith("ytm:track:"))
        assertTrue(track.artistId?.startsWith("ytm:artist:") == true)
        assertTrue(track.needsResolution)
        assertTrue(!track.isArchived)
    }

    @Test
    fun albumAndArtistDetailsResolve() = runBlocking {
        val source = YouTubeTestHarness.source
        val album = YouTubeTestHarness.firstAlbum()
        val albumDetail = source.album(album.id, "IN")
        println("[mapping] album=${albumDetail.album.title} tracks=${albumDetail.tracks.size} artwork=${albumDetail.album.artworkUrl != null}")
        assertTrue(albumDetail.album.title.isNotBlank())
        assertTrue(albumDetail.tracks.isNotEmpty(), "Album browse returned no tracks")
        assertTrue(albumDetail.tracks.all { it.source == MusicSourceId.YOUTUBE_MUSIC })

        val artist = YouTubeTestHarness.firstArtist()
        val artistDetail = source.artist(artist.id, "IN")
        println("[mapping] artist=${artistDetail.artist.name} top=${artistDetail.topTracks.size} albums=${artistDetail.albums.size}")
        assertTrue(artistDetail.artist.name.isNotBlank())
        assertTrue(artistDetail.topTracks.isNotEmpty() || artistDetail.albums.isNotEmpty())
    }
}
