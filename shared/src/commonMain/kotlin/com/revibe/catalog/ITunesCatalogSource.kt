package com.revibe.catalog

import com.revibe.model.Album
import com.revibe.model.AlbumDetail
import com.revibe.model.Artist
import com.revibe.model.ArtistDetail
import com.revibe.model.MusicSourceId
import com.revibe.model.Track
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/**
 * The iTunes Search API is a public, keyless catalog that returns real metadata,
 * artwork and 30 second previews. It is used for discovery; it does not grant
 * access to full tracks, and the UI labels catalog playback as a preview.
 *
 * Terms: https://www.apple.com/legal/internet-services/itunes/dev/stdeula/
 */
class ITunesCatalogSource(private val client: HttpClient) : CatalogSource {
    override val id: MusicSourceId = MusicSourceId.ITUNES
    override val displayName: String = "iTunes Catalog"

    override suspend fun searchTracks(query: String, market: String, limit: Int): List<Track> =
        search(query, "song", market, limit).mapNotNull { it.toTrack() }

    override suspend fun searchAlbums(query: String, market: String, limit: Int): List<Album> =
        search(query, "album", market, limit)
            .filter { it.wrapperType == "collection" }
            .mapNotNull { it.toAlbum() }

    override suspend fun searchArtists(query: String, market: String, limit: Int): List<Artist> =
        search(query, "musicArtist", market, limit)
            .filter { it.wrapperType == "artist" || it.artistType != null }
            .mapNotNull { it.toArtist() }

    override suspend fun album(albumId: String, market: String): AlbumDetail {
        val items = lookup(albumId, "song", market, limit = 200)
        val collection = items.firstOrNull { it.wrapperType == "collection" }
        val tracks = items.filter { it.wrapperType == "track" }.sortedBy { it.trackNumber ?: 0 }.mapNotNull { it.toTrack() }
        val album = collection?.toAlbum() ?: tracks.firstOrNull()?.let { track ->
            Album(
                id = albumId,
                title = track.album,
                artist = track.artist,
                artworkUrl = track.artworkUrl,
                year = track.year,
                trackCount = tracks.size,
                artistId = track.artistId,
                genre = track.genre,
            )
        } ?: Album(id = albumId, title = "Unknown album", artist = "Unknown artist")
        return AlbumDetail(album = album.copy(trackCount = maxOf(album.trackCount, tracks.size)), tracks = tracks)
    }

    override suspend fun artist(artistId: String, market: String): ArtistDetail {
        val albums = albumsForArtist(artistId, market, limit = 40)
        val name = albums.firstOrNull()?.artist
        val artist = Artist(
            id = artistId,
            name = name ?: "Unknown artist",
            imageUrl = albums.firstOrNull()?.artworkUrl,
            genre = albums.firstOrNull()?.genre,
        )
        val tracks = if (name != null) {
            searchTracks(name, market, limit = 10)
        } else {
            emptyList()
        }
        return ArtistDetail(artist = artist, topTracks = tracks, albums = albums)
    }

    override suspend fun albumsForArtist(artistId: String, market: String, limit: Int): List<Album> =
        lookup(artistId, "album", market, limit)
            .filter { it.wrapperType == "collection" }
            .mapNotNull { it.toAlbum() }
            .distinctBy { it.id }

    /**
     * Lookup by artist returns the albums, which is the reliable way to get a
     * discography. Song search by name provides the "popular" ordering iTunes uses.
     */
    override suspend fun tracksForArtist(
        artistId: String,
        artistName: String,
        market: String,
        limit: Int,
    ): List<Track> = searchTracks(artistName, market, limit).filter { it.artistId == artistId || it.artist.contains(artistName, ignoreCase = true) }

    private suspend fun search(query: String, entity: String, market: String, limit: Int): List<ITunesItem> {
        if (query.isBlank()) return emptyList()
        return runCatching {
            val response: HttpResponse = client.get("https://itunes.apple.com/search") {
                parameter("term", query)
                parameter("entity", entity)
                parameter("limit", limit.coerceIn(1, 200))
                parameter("country", market)
            }
            if (!response.status.isSuccess()) {
                println("[itunes] search '$query' ($entity) returned ${response.status}")
                return@runCatching emptyList()
            }
            response.body<ITunesResponse>().results
        }.onFailure { error ->
            println("[itunes] search '$query' ($entity) failed: ${error::class.simpleName}: ${error.message}")
        }.getOrDefault(emptyList())
    }

    private suspend fun lookup(id: String, entity: String, market: String, limit: Int): List<ITunesItem> =
        runCatching {
            val response: HttpResponse = client.get("https://itunes.apple.com/lookup") {
                parameter("id", id)
                parameter("entity", entity)
                parameter("limit", limit.coerceIn(1, 200))
                parameter("country", market)
            }
            if (!response.status.isSuccess()) {
                println("[itunes] lookup '$id' ($entity) returned ${response.status}")
                return@runCatching emptyList()
            }
            response.body<ITunesResponse>().results
        }.onFailure { error ->
            println("[itunes] lookup '$id' ($entity) failed: ${error::class.simpleName}: ${error.message}")
        }.getOrDefault(emptyList())
}

@Serializable
internal data class ITunesResponse(
    val resultCount: Int = 0,
    val results: List<ITunesItem> = emptyList(),
)

@Serializable
internal data class ITunesItem(
    val wrapperType: String? = null,
    val kind: String? = null,
    val artistType: String? = null,
    val collectionType: String? = null,
    val trackId: Long? = null,
    val collectionId: Long? = null,
    val artistId: Long? = null,
    val trackName: String? = null,
    val collectionName: String? = null,
    val artistName: String? = null,
    val artworkUrl100: String? = null,
    val previewUrl: String? = null,
    val trackTimeMillis: Long? = null,
    val releaseDate: String? = null,
    val primaryGenreName: String? = null,
    val trackNumber: Int? = null,
    val trackCount: Int? = null,
    val trackExplicitness: String? = null,
    val artworkUrl60: String? = null,
) {
    private fun highResArtwork(): String? = artworkUrl100
        ?.replace("/100x100bb", "/600x600bb")
        ?.replace("/100x100", "/600x600")

    fun toTrack(): Track? {
        val id = trackId ?: return null
        val title = trackName ?: return null
        return Track(
            id = "itunes:track:$id",
            title = title,
            artist = artistName ?: "Unknown artist",
            album = collectionName ?: title,
            durationMs = trackTimeMillis ?: 0L,
            artworkUrl = highResArtwork(),
            streamUrl = previewUrl,
            source = MusicSourceId.ITUNES,
            artistId = artistId?.let { "itunes:artist:$it" },
            albumId = collectionId?.let { "itunes:album:$it" },
            year = releaseDate?.take(4)?.toIntOrNull(),
            trackNumber = trackNumber,
            genre = primaryGenreName,
            explicit = trackExplicitness.equals("explicit", ignoreCase = true),
        )
    }

    fun toAlbum(): Album? {
        val id = collectionId ?: return null
        return Album(
            id = "itunes:album:$id",
            title = collectionName ?: return null,
            artist = artistName ?: "Unknown artist",
            artworkUrl = highResArtwork(),
            year = releaseDate?.take(4)?.toIntOrNull(),
            trackCount = trackCount ?: 0,
            genre = primaryGenreName,
            artistId = artistId?.let { "itunes:artist:$it" },
        )
    }

    fun toArtist(): Artist? {
        val id = artistId ?: return null
        val name = artistName ?: return null
        return Artist(
            id = "itunes:artist:$id",
            name = name,
            imageUrl = highResArtwork(),
            genre = primaryGenreName,
        )
    }
}

internal fun catalogNumericId(prefixed: String): String = prefixed.substringAfterLast(':')
