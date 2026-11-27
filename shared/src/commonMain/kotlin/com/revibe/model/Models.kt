package com.revibe.model

/**
 * Every catalog item Revibe can show carries the source it came from, so the UI can
 * be honest about what is playable and where the metadata came from.
 */
enum class MusicSourceId(val label: String, val availability: SourceAvailability) {
    YOUTUBE_MUSIC("Online", SourceAvailability.ACTIVE),
    LOCAL("Local Files", SourceAvailability.ACTIVE),
    /**
     * Rows saved by builds that shipped a second, preview-only catalog. The
     * integration is gone; these rows are retained read-only so likes,
     * playlists and history keep their entries instead of silently losing them.
     */
    ARCHIVED("Archived", SourceAvailability.INACTIVE),
    SPOTIFY("Spotify Metadata", SourceAvailability.INACTIVE),
}

enum class SourceAvailability(val label: String) {
    ACTIVE("Active"),
    INACTIVE("Not configured"),
    EXPERIMENTAL("Experimental"),
}

enum class PlaybackKind(val label: String) {
    FULL("Full track"),
    LOCAL("Local file"),
    UNAVAILABLE("Unavailable"),
}

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String? = null,
    val streamUrl: String? = null,
    val source: MusicSourceId = MusicSourceId.YOUTUBE_MUSIC,
    val artistId: String? = null,
    val albumId: String? = null,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val genre: String? = null,
    val explicit: Boolean = false,
    val localPath: String? = null,
) {
    val isLocal: Boolean get() = source == MusicSourceId.LOCAL

    /** Saved by a retired catalog build; retained for history but never playable. */
    val isArchived: Boolean get() = source == MusicSourceId.ARCHIVED
    val playable: Boolean get() = !isArchived && !streamUrl.isNullOrBlank()

    /** YouTube Music tracks carry only a video id; the stream is resolved just before playback. */
    val needsResolution: Boolean get() = source == MusicSourceId.YOUTUBE_MUSIC
    val playbackKind: PlaybackKind
        get() = when {
            isLocal -> PlaybackKind.LOCAL
            isArchived -> PlaybackKind.UNAVAILABLE
            else -> PlaybackKind.FULL
        }
    val sourceLabel: String
        get() = when (source) {
            MusicSourceId.YOUTUBE_MUSIC -> "Online"
            MusicSourceId.LOCAL -> "Local"
            MusicSourceId.ARCHIVED -> "Archived"
            MusicSourceId.SPOTIFY -> "Spotify metadata"
        }
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
    val source: MusicSourceId = MusicSourceId.YOUTUBE_MUSIC,
    /** True for a YouTube Music playlist that should offer import into the local library. */
    val isPlaylist: Boolean = false,
)

data class Artist(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
    val genre: String? = null,
    val source: MusicSourceId = MusicSourceId.YOUTUBE_MUSIC,
)

data class Playlist(
    val id: String,
    val name: String,
    val description: String = "",
    val trackCount: Int = 0,
    val createdAt: Long = 0,
    /** First available track cover; playlists have no cover of their own. */
    val artworkUrl: String? = null,
)

/** A playlist row with a unique entry id, explicit order and real added-at timestamp. */
data class PlaylistTrackEntry(
    val track: Track,
    val addedAt: Long,
    val entryId: String = "",
    val position: Int = 0,
    val sourcePlatform: String? = null,
    val sourceImportId: String? = null,
    val importedAt: Long? = null,
) {
    val imported: Boolean get() = !sourceImportId.isNullOrBlank()
}

/** A playlist the user actually played, with its latest cover and play timestamp. */
data class PlayedPlaylist(
    val playlist: Playlist,
    val artworkUrl: String?,
    val playedAt: Long,
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
    val playlists: List<Album> = emptyList(),
) {
    val isEmpty: Boolean get() = tracks.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()
}

/**
 * Aggregated, local-only activity used by the homepage maturity system. Every
 * field is derived from real SQLite rows; there are no synthetic counts.
 */
data class UserActivitySnapshot(
    val playCount: Int = 0,
    /** Native Revibe plays and imported Spotify history remain distinguishable. */
    val nativePlayCount: Int = 0,
    val importedPlayCount: Int = 0,
    val importedLikedCount: Int = 0,
    val importedPlaylistCount: Int = 0,
    val completionCount: Int = 0,
    val skipCount: Int = 0,
    val distinctArtists: Int = 0,
    val distinctGenres: Int = 0,
    val likedTrackCount: Int = 0,
    val likedAlbumCount: Int = 0,
    val followedArtistCount: Int = 0,
    val playlistCount: Int = 0,
    val localTrackCount: Int = 0,
    val historySpanDays: Int = 0,
    val daysSinceLastPlay: Int? = null,
    val topArtists: List<ArtistAffinity> = emptyList(),
    val topGenres: List<GenreAffinity> = emptyList(),
)

data class ArtistAffinity(val artist: String, val plays: Int)
data class GenreAffinity(val genre: String, val plays: Int)

/** A real history row, for "played 2h ago" labels in the sidebar. */
data class PlayedTrack(val track: Track, val playedAt: Long)

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
    val leftPanelWidth: Float = LayoutSpec.LEFT_DEFAULT,
    val rightPanelWidth: Float = LayoutSpec.RIGHT_DEFAULT,
    val autoplayEnabled: Boolean = true,
    val syncSearchHistory: Boolean = false,
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
