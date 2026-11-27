package com.revibe.imports

import com.revibe.catalog.ArtistKnowledge
import com.revibe.catalog.ArtistKnowledgeSource
import com.revibe.catalog.CatalogRepository
import com.revibe.catalog.CatalogSource
import com.revibe.data.JdbcLibraryStore
import com.revibe.library.LibraryRepository
import com.revibe.model.Album
import com.revibe.model.AlbumDetail
import com.revibe.model.Artist
import com.revibe.model.ArtistDetail
import com.revibe.model.MusicSourceId
import com.revibe.model.SearchResults
import com.revibe.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The desktop flow after the file dialog: parse a TuneMyMusic CSV, match against
 * the catalog, show a preview and commit through the existing library store.
 */
class PlaylistImportWorkflowTest {

    private val csv = """
        Track name,Artist name,Album name,Duration,ISRC
        Kesariya,Arijit Singh,Brahmastra,4:28,USSM12101721
        Kesariya,Arijit Singh,Brahmastra,4:28,USSM12101721
        Blinding Lights,The Weeknd,After Hours,3:20,
        Kesariya (Live),Arijit Singh,Brahmastra,4:28,
        Missing Song,Nobody,Unknown,2:00,
    """.trimIndent()

    @Test
    fun `preview preserves order and confirmation creates the playlist without touching existing ones`() = runBlocking {
        val store = JdbcLibraryStore(
            File(Files.createTempDirectory("revibe-import-flow").toFile(), "library.db"),
            Dispatchers.IO,
        )
        val library = LibraryRepository(store)
        val existing = library.createPlaylist("Existing Mix")
        library.addToPlaylist(existing.id, track("existing", "An Existing Song", "Native Artist"))
        val repository = ImportRepository(
            CatalogRepository(
                FakeCatalog(
                    listOf(
                        track("kesariya", "Kesariya", "Arijit Singh", "Brahmastra", 268_000),
                        track("blinding", "Blinding Lights", "The Weeknd", "After Hours", 200_000),
                    ),
                ),
                EmptyKnowledge,
            ),
            library,
            store,
        )

        val preview = repository.matchPlaylistFile(PickedTextFile("Taare Radio.csv", csv), "IN")

        assertEquals("Taare Radio", preview.playlistName)
        assertEquals(5, preview.tracks.size)
        assertEquals(listOf(1, 2, 3, 4, 5), preview.tracks.map { it.source.position })
        assertEquals(1, preview.duplicateCount)
        assertEquals(3, preview.selectedCount, "a close match starts unselected")
        assertEquals(4, preview.matchedCount)
        assertEquals(1, preview.unmatchedCount)
        assertEquals(MatchStatus.NEEDS_REVIEW, preview.tracks[3].status)
        assertEquals(MatchStatus.UNMATCHED, preview.tracks[4].status)

        val summary = repository.commitPlaylistFile(
            preview = preview,
            destinationPlaylistId = null,
            newPlaylistName = preview.playlistName,
            market = "IN",
        )

        assertEquals("Taare Radio", summary.playlistName)
        assertEquals(3, summary.tracksAdded, "the repeated source row stays a separate playlist entry")
        assertEquals(1, summary.needsReview, "the close match is reported even though it was not added")
        assertEquals(1, summary.unmatched.size)
        assertEquals(2, library.playlists().size, "the existing playlist survives")

        val created = library.playlists().single { it.name == "Taare Radio" }
        val entries = store.playlistTrackEntries(created.id)
        assertEquals(3, entries.size)
        assertEquals(listOf(1, 2, 3), entries.map { it.position })
        assertEquals(
            listOf("ytm:track:kesariya", "ytm:track:kesariya", "ytm:track:blinding"),
            entries.map { it.track.id },
        )
        assertEquals(1, library.playlistTracks(existing.id).size)

        val appended = repository.commitPlaylistFile(
            preview = preview,
            destinationPlaylistId = existing.id,
            newPlaylistName = "",
            market = "IN",
        )
        assertEquals(3, appended.tracksAdded)
        assertEquals(existing.id, appended.primaryPlaylistId)
        assertEquals("Existing Mix", appended.playlistName, "the summary names the playlist that received the tracks")
        assertEquals(4, library.playlistTracks(existing.id).size)
        assertEquals(2, library.playlists().size)
    }

    private fun track(
        id: String,
        title: String,
        artist: String,
        album: String = "Album",
        durationMs: Long = 200_000,
    ) = Track(
        id = "ytm:track:$id",
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        source = MusicSourceId.YOUTUBE_MUSIC,
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
