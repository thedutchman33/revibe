package com.revibe.model

/**
 * Every catalog item Revibe can show carries the source it came from, so the UI can
 * be honest about what is playable and where the metadata came from.
 */
enum class MusicSourceId(val label: String, val availability: SourceAvailability) {
    ITUNES("iTunes Catalog", SourceAvailability.ACTIVE),
    LOCAL("Local Files", SourceAvailability.ACTIVE),
    YOUTUBE_MUSIC("YouTube Music", SourceAvailability.BLOCKED),
    SPOTIFY("Spotify Metadata", SourceAvailability.INACTIVE),
}

enum class SourceAvailability(val label: String) {
    ACTIVE("Active"),
    INACTIVE("Not configured"),
    EXPERIMENTAL("Experimental"),
    BLOCKED("Blocked: terms of service"),
}

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String? = null,
    val streamUrl: String? = null,
    val source: MusicSourceId = MusicSourceId.ITUNES,
    val artistId: String? = null,
    val albumId: String? = null,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val genre: String? = null,
    val explicit: Boolean = false,
    val localPath: String? = null,
) {
    val isLocal: Boolean get() = source == MusicSourceId.LOCAL
    val isPreview: Boolean get() = !isLocal && source == MusicSourceId.ITUNES
    val playable: Boolean get() = !streamUrl.isNullOrBlank()
}

data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val year: Int? = null,
    val trackCount: Int = 0,
    val genre: String? = null,
    val artistId: String? = null,
    val source: MusicSourceId = MusicSourceId.ITUNES,
)

data class Artist(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
    val genre: String? = null,
    val source: MusicSourceId = MusicSourceId.ITUNES,
)

data class Playlist(
    val id: String,
    val name: String,
    val description: String = "",
    val trackCount: Int = 0,
    val createdAt: Long = 0,
)

data class AlbumDetail(
    val album: Album,
    val tracks: List<Track>,
)

data class ArtistDetail(
    val artist: Artist,
    val bio: String? = null,
    val topTracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
)

data class SearchResults(
    val query: String,
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
) {
    val isEmpty: Boolean get() = tracks.isEmpty() && albums.isEmpty() && artists.isEmpty()
}

data class CachedLyrics(
    val plain: String?,
    val synced: String?,
    val source: String,
)

data class AppSettings(
    val market: String = "IN",
    val volume: Float = 0.85f,
    val showRightPanel: Boolean = true,
    val compactSidebar: Boolean = false,
    val lastRoute: String = "home",
)

data class ScanReport(
    val added: Int = 0,
    val updated: Int = 0,
    val removed: Int = 0,
    val skipped: Int = 0,
    val errors: List<String> = emptyList(),
)

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
