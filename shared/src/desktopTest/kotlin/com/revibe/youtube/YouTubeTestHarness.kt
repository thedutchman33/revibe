package com.revibe.youtube

import com.revibe.model.Album
import com.revibe.model.Artist
import com.revibe.model.Track
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/**
 * One live source for the network tests. The tests use real YouTube Music
 * responses; if YouTube is unreachable they fail with the actual status, not a
 * synthetic pass.
 */
object YouTubeTestHarness {
    val http: HttpClient by lazy {
        HttpClient(CIO) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
    }

    val source: YouTubeMusicSource by lazy { YouTubeMusicSource(http) }

    val query: String = "arijit singh"

    val tracks: List<Track> by lazy {
        runBlocking { source.searchTracks(query, "IN", 8) }
    }

    val albums: List<Album> by lazy {
        runBlocking { source.searchAlbums(query, "IN", 6) }
    }

    val artists: List<Artist> by lazy {
        runBlocking { source.searchArtists(query, "IN", 4) }
    }

    fun firstTrack(): Track = tracks.firstOrNull()
        ?: error("YouTube Music returned no search results for '$query': ${source.diagnostic.value}")

    fun firstAlbum(): Album = albums.firstOrNull()
        ?: error("YouTube Music returned no albums for '$query': ${source.diagnostic.value}")

    fun firstArtist(): Artist = artists.firstOrNull()
        ?: error("YouTube Music returned no artists for '$query': ${source.diagnostic.value}")
}
