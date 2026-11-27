package com.revibe.imports

import com.revibe.catalog.ArtistKnowledge
import com.revibe.catalog.ArtistKnowledgeSource
import com.revibe.catalog.CatalogRepository
import com.revibe.catalog.CatalogSource
import com.revibe.model.Album
import com.revibe.model.AlbumDetail
import com.revibe.model.Artist
import com.revibe.model.ArtistDetail
import com.revibe.model.MusicSourceId
import com.revibe.model.SearchResults
import com.revibe.model.Track
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ImportParsingTest {

    @Test
    fun `csv aliases quoted titles and order are parsed`() {
        val csv = """
            Playlist Name,Track Name,Artist Name,Album Name,ISRC,Spotify URI,Duration,Position
            Road Trip,"Stay (with Justin Bieber)",The Kid LAROI,"F*ck Love",USSM12101721,spotify:track:abc,2:21,2
            Road Trip,Kesariya,Arijit Singh,Brahmastra,,spotify:track:def,4:28,1
            Road Trip,Kesariya,Arijit Singh,Brahmastra,,spotify:track:def,4:28,3
        """.trimIndent()

        val preview = parsePlaylistFile("roadtrip.csv", csv)

        assertEquals("Road Trip", preview.playlistName)
        assertEquals(3, preview.tracks.size)
        assertEquals(1, preview.duplicateCount)
        assertEquals("Stay (with Justin Bieber)", preview.tracks[0].source.title)
        assertEquals("The Kid LAROI", preview.tracks[0].source.artist)
        assertEquals("USSM12101721", preview.tracks[0].source.isrc)
        assertEquals("abc", preview.tracks[0].source.spotifyId)
        assertEquals(141_000, preview.tracks[0].source.durationMs)
        assertEquals(listOf(2, 1, 3), preview.tracks.map { it.source.position })
    }

    @Test
    fun `tunemymusic csv export keeps order repeated rows and metadata`() {
        val csv = """
            Track name,Artist name,Album name,Duration,ISRC
            Kesariya,Arijit Singh,Brahmastra,4:28,USSM12101721
            Kesariya,Arijit Singh,Brahmastra,4:28,USSM12101721
            Blinding Lights,The Weeknd,After Hours,3:20,
        """.trimIndent()

        val preview = parsePlaylistFile("Taare Radio.csv", csv)

        assertEquals("Taare Radio", preview.playlistName, "TuneMyMusic exports have no playlist column; the file name is the title")
        assertEquals("CSV", preview.format)
        assertEquals(3, preview.tracks.size, "repeated source rows stay as separate playlist entries")
        assertEquals(1, preview.duplicateCount)
        assertEquals(listOf(1, 2, 3), preview.tracks.map { it.source.position })
        assertEquals("Kesariya", preview.tracks[0].source.title)
        assertEquals("Arijit Singh", preview.tracks[0].source.artist)
        assertEquals("Brahmastra", preview.tracks[0].source.album)
        assertEquals("USSM12101721", preview.tracks[0].source.isrc)
        assertEquals(268_000, preview.tracks[0].source.durationMs)
        assertEquals("The Weeknd", preview.tracks[2].source.artist)
    }

    @Test
    fun `m3u xspf and hindi text retain playlist order`() {
        val m3u = """
            #EXTM3U
            #PLAYLIST:Evening
            #EXTINF:201,Artist One - Song One
            C:\Music\one.mp3
            #EXTINF:180,के.के. - कल हो न हो
            C:\Music\two.mp3
        """.trimIndent()
        val m3uPreview = parsePlaylistFile("evening.m3u", m3u)
        assertEquals("Evening", m3uPreview.playlistName)
        assertEquals(listOf("Song One", "कल हो न हो"), m3uPreview.tracks.map { it.source.title })
        assertEquals("के.के.", m3uPreview.tracks[1].source.artist)

        val xspf = """
            <?xml version="1.0"?>
            <playlist version="1"><title>XSPF Mix</title><trackList>
              <track><title>Blinding Lights</title><creator>The Weeknd</creator><album>After Hours</album><duration>200000</duration></track>
            </trackList></playlist>
        """.trimIndent()
        val xspfPreview = parsePlaylistFile("mix.xspf", xspf)
        assertEquals("XSPF Mix", xspfPreview.playlistName)
        assertEquals("The Weeknd", xspfPreview.tracks.single().source.artist)
        assertEquals(200_000, xspfPreview.tracks.single().source.durationMs)
    }

    @Test
    fun `spotify archive keeps music categories deduplicates history and ignores unrelated files`() {
        val readme = "Read Me First: this folder contains your Spotify account data."
        val playlist = """
            {"playlists":[{"name":"Hindi Mix","description":"Evening","items":[
              {"track":{"trackName":"Kesariya","artistName":"Arijit Singh","albumName":"Brahmastra","trackUri":"spotify:track:hindi1"}},
              {"track":{"trackName":"Blinding Lights","artistName":"The Weeknd","albumName":"After Hours","trackUri":"spotify:track:eng1"}}
            ]}]}
        """.trimIndent()
        val library = """
            {"tracks":[{"track":"Kesariya","artist":"Arijit Singh","album":"Brahmastra","uri":"spotify:track:hindi1"}],
             "albums":[{"album":"After Hours","artist":"The Weeknd","uri":"spotify:album:1"}],
             "artists":[{"name":"Arijit Singh","uri":"spotify:artist:1"}]}
        """.trimIndent()
        val accountHistory = """
            [{"endTime":"2024-01-01 12:34","artistName":"Arijit Singh","trackName":"Kesariya","msPlayed":150000},
             {"endTime":"2024-01-01 12:35","artistName":"The Weeknd","trackName":"Blinding Lights","msPlayed":5000}]
        """.trimIndent()
        val extendedHistory = """
            [{"ts":"2024-01-01T12:34:10Z","ms_played":150000,"master_metadata_track_name":"Kesariya",
              "master_metadata_album_artist_name":"Arijit Singh","master_metadata_album_album_name":"Brahmastra",
              "spotify_track_uri":"spotify:track:hindi1","reason_end":"trackdone","shuffle":false,"platform":"Windows"},
             {"ts":"2024-01-02T00:00:00Z","ms_played":60000,"master_metadata_track_name":"Tum Hi Ho",
              "master_metadata_album_artist_name":"Arijit Singh","spotify_track_uri":"spotify:track:hindi2","reason_end":"fwdbtn","skipped":true}]
        """.trimIndent()
        val files = listOf(
            PickedTextFile("ReadMeFirst.md", readme),
            PickedTextFile("Playlist1.json", playlist),
            PickedTextFile("YourLibrary.json", library),
            PickedTextFile("StreamingHistory_music_0.json", accountHistory),
            PickedTextFile("Streaming_History_Audio_2024_0.json", extendedHistory),
            PickedTextFile("SearchQueries.json", """[{"searchTime":"2024-01-01T12:00:00Z","searchQuery":"arijit singh"}]"""),
            PickedTextFile("Follow.json", """{"following":[{"name":"Arijit Singh","uri":"spotify:artist:1"}]}"""),
            PickedTextFile("Payment.json", """{"card":"should be ignored"}"""),
        )
        val parsed = parseSpotifyArchive(SpotifyArchiveEntries(files, archiveFingerprint(files)))

        assertEquals(1, parsed.playlists.size)
        assertEquals(2, parsed.playlists.single().tracks.size)
        assertEquals(1, parsed.likedTracks.size)
        assertEquals(1, parsed.savedAlbums.size)
        assertEquals(1, parsed.followedArtists.size)
        assertEquals(3, parsed.history.size, "four rows collapse to three distinct plays")
        assertTrue(parsed.history.first().completed)
        assertTrue(parsed.history.last().skipped)
        assertEquals(1, parsed.searches.size)
        assertTrue(parsed.readme!!.contains("Read Me"))
        assertTrue(parsed.ignoredFiles.contains("Payment.json"))
        assertEquals(1_704_067_200_000L, parseSpotifyTimestamp("2024-01-01T00:00:00Z"))
    }

    @Test
    fun `overlapping account and extended exports pair one play without collapsing same-minute plays`() {
        val account = """
            [{"endTime":"2024-01-01 12:34","artistName":"Arijit Singh","trackName":"Kesariya","msPlayed":150000},
             {"endTime":"2024-01-01 12:34","artistName":"Arijit Singh","trackName":"Kesariya","msPlayed":61000},
             {"endTime":"2024-01-01 12:35","artistName":"Arijit Singh","trackName":"Kesariya","msPlayed":150000}]
        """.trimIndent()
        val extended = """
            [{"ts":"2024-01-01T12:34:12Z","ms_played":150000,"master_metadata_track_name":"Kesariya",
              "master_metadata_album_artist_name":"Arijit Singh","spotify_track_uri":"spotify:track:hindi1",
              "reason_end":"trackdone"}]
        """.trimIndent()
        val files = listOf(
            PickedTextFile("StreamingHistory_music_0.json", account),
            PickedTextFile("Streaming_History_Audio_2024_0.json", extended),
        )

        val parsed = parseSpotifyArchive(SpotifyArchiveEntries(files, archiveFingerprint(files)))

        // The 12:34:12 extended row pairs with the one account row that has the same
        // track and played duration. The other 12:34 play and the 12:35 repeat stay.
        assertEquals(3, parsed.history.size)
        assertEquals(2, parsed.history.count { it.playedMs == 150_000L })
        assertEquals(1, parsed.history.count { it.playedMs == 61_000L })
        assertEquals(1, parsed.history.count { it.track.spotifyUri == "spotify:track:hindi1" })
    }

    @Test
    fun `a large extended history keeps every distinct play`() {
        val rows = (0 until 5_000).joinToString(",") { index ->
            val halfMinute = index / 2
            val day = 1 + halfMinute / 1_440
            val minuteOfDay = halfMinute % 1_440
            val hour = minuteOfDay / 60
            val minute = minuteOfDay % 60
            val second = if (index % 2 == 0) 0 else 30
            """{"ts":"2024-01-%02dT%02d:%02d:%02dZ","ms_played":%d,"master_metadata_track_name":"Track %d","master_metadata_album_artist_name":"Artist","spotify_track_uri":"spotify:track:%d"}"""
                .format(day, hour, minute, second, 60_000 + (index % 5) * 1_000, index % 200, index % 200)
        }
        val files = listOf(PickedTextFile("Streaming_History_Audio_2024_0.json", "[$rows]"))
        val parsed = parseSpotifyArchive(SpotifyArchiveEntries(files, archiveFingerprint(files)))
        assertEquals(5_000, parsed.history.size)
    }

    @Test
    fun `malformed optional files do not abort a valid archive`() {
        val files = listOf(
            PickedTextFile("Playlist1.json", "{broken"),
            PickedTextFile("YourLibrary.json", "not json"),
            PickedTextFile("StreamingHistory_music_0.json", """[{"endTime":"2024-01-01 12:00","trackName":"Kesariya","artistName":"Arijit Singh","msPlayed":1000}]"""),
            PickedTextFile("Payments.json", """{"amount":"ignored"}"""),
        )
        val parsed = parseSpotifyArchive(SpotifyArchiveEntries(files, archiveFingerprint(files)))
        assertTrue(parsed.playlists.isEmpty())
        assertTrue(parsed.likedTracks.isEmpty())
        assertEquals(1, parsed.history.size)
        assertTrue(parsed.ignoredFiles.contains("Payments.json"))
    }

    @Test
    fun `matcher rejects random first results and respects recording versions`() = runBlocking {
        val tracks = listOf(
            track("unrelated", "Unrelated Song", "Other Artist"),
            track("live", "Kesariya (Live)", "Arijit Singh"),
            track("studio", "Kesariya", "Arijit Singh"),
            track("blinding", "Blinding Lights", "The Weeknd"),
            track("hindi", "तुम ही हो", "Arijit Singh"),
        )
        val catalog = CatalogRepository(FakeCatalog(tracks), EmptyKnowledge)
        val matcher = TrackMatcher(catalog)

        val exact = matcher.match(SourceTrack(1, "Blinding Lights", "The Weeknd"), "IN")
        assertEquals(MatchStatus.MATCHED, exact.status)
        assertEquals("ytm:track:blinding", exact.track?.id)

        val hindi = matcher.match(SourceTrack(1, "तुम ही हो", "Arijit Singh"), "IN")
        assertEquals(MatchStatus.MATCHED, hindi.status)

        val live = matcher.match(SourceTrack(1, "Kesariya (Live)", "Arijit Singh"), "IN")
        assertEquals("ytm:track:live", live.track?.id)
        assertTrue(live.status == MatchStatus.MATCHED || live.status == MatchStatus.NEEDS_REVIEW)

        val missing = matcher.match(SourceTrack(1, "Completely Different", "Nobody"), "IN")
        assertEquals(MatchStatus.UNMATCHED, missing.status)
        assertEquals(null, missing.track)
    }

    private fun track(id: String, title: String, artist: String) = Track(
        id = "ytm:track:$id",
        title = title,
        artist = artist,
        album = if (id == "blinding") "After Hours" else "Album",
        durationMs = 200_000,
    )

    private class FakeCatalog(private val tracks: List<Track>) : CatalogSource {
        override val id = MusicSourceId.YOUTUBE_MUSIC
        override val displayName = "Test catalog"
        override val idPrefix = "ytm:"
        override suspend fun searchTracks(query: String, market: String, limit: Int): List<Track> = tracks
        override suspend fun searchAlbums(query: String, market: String, limit: Int): List<Album> = emptyList()
        override suspend fun searchArtists(query: String, market: String, limit: Int) = emptyList<Artist>()
        override suspend fun searchAll(query: String, market: String, limit: Int) =
            SearchResults(query, tracks = tracks)
        override suspend fun album(albumId: String, market: String): AlbumDetail = error("unused")
        override suspend fun artist(artistId: String, market: String): ArtistDetail = error("unused")
        override suspend fun albumsForArtist(artistId: String, market: String, limit: Int): List<Album> = emptyList()
        override suspend fun tracksForArtist(artistId: String, artistName: String, market: String, limit: Int): List<Track> = emptyList()
    }

    private object EmptyKnowledge : ArtistKnowledgeSource {
        override suspend fun biography(artistName: String): ArtistKnowledge? = null
    }
}
