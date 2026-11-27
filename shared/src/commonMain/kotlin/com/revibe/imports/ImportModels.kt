package com.revibe.imports

import com.revibe.model.Track

enum class ImportSource(val label: String) {
    TUNE_MYMUSIC_FILE("Exported playlist file"),
    SPOTIFY_ACCOUNT("Spotify account data"),
}

enum class MatchStatus(val label: String) {
    MATCHED("Matched"),
    NEEDS_REVIEW("Needs review"),
    UNMATCHED("Unmatched"),
    UNSUPPORTED("Unsupported"),
    ALREADY_IMPORTED("Already imported"),
}

/** One source row, before catalog matching. */
data class SourceTrack(
    val position: Int,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val isrc: String? = null,
    val spotifyId: String? = null,
    val spotifyUri: String? = null,
    val durationMs: Long = 0,
    val sourceKey: String? = null,
) {
    val displayName: String get() = if (artist.isBlank()) title else "$title — $artist"
}

data class MatchedTrack(
    val source: SourceTrack,
    val track: Track?,
    val status: MatchStatus,
    /** 0..1 confidence derived from title, artist, album, duration and version markers. */
    val confidence: Float,
    val note: String = "",
    val selected: Boolean = true,
)

data class SourcePlaylist(
    val sourceId: String,
    val name: String,
    val description: String = "",
    val tracks: List<SourceTrack> = emptyList(),
)

data class SourceAlbum(
    val sourceId: String,
    val name: String,
    val artist: String,
    val year: Int? = null,
)

data class SourceArtist(
    val sourceId: String,
    val name: String,
)

data class SourceHistoryEvent(
    val sourceKey: String,
    val timestampMs: Long,
    val track: SourceTrack,
    val playedMs: Long,
    val completed: Boolean,
    val skipped: Boolean,
    val shuffle: Boolean? = null,
    val platform: String? = null,
    val reasonStart: String? = null,
    val reasonEnd: String? = null,
    /** Original Spotify event/play id when the export provides one. */
    val sourceEventId: String? = null,
    /** "account" or "extended" — used only to pair overlapping exports. */
    val sourcePackage: String = "account",
)

data class SourceSearch(
    val sourceKey: String,
    val query: String,
    val timestampMs: Long,
    val platform: String? = null,
    val resultUris: List<String> = emptyList(),
)

data class PlaylistFilePreview(
    val fileName: String,
    val fingerprint: String,
    val format: String,
    val playlistName: String,
    val tracks: List<MatchedTrack>,
    val duplicateCount: Int,
    val destinationPlaylistId: String? = null,
) {
    val matchedCount: Int get() = tracks.count { it.status == MatchStatus.MATCHED || it.status == MatchStatus.NEEDS_REVIEW }
    val unmatchedCount: Int get() = tracks.size - matchedCount
    val selectedCount: Int get() = tracks.count { it.selected && it.track != null }
}

data class SpotifyArchivePreview(
    val fingerprint: String,
    val readme: String? = null,
    val playlists: List<SourcePlaylist> = emptyList(),
    val likedTracks: List<SourceTrack> = emptyList(),
    val savedAlbums: List<SourceAlbum> = emptyList(),
    val followedArtists: List<SourceArtist> = emptyList(),
    val history: List<SourceHistoryEvent> = emptyList(),
    val searches: List<SourceSearch> = emptyList(),
    val tasteProfileSummary: String? = null,
    val wrappedSummary: String? = null,
    val ignoredFiles: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = playlists.isEmpty() && likedTracks.isEmpty() && savedAlbums.isEmpty() &&
            followedArtists.isEmpty() && history.isEmpty() && searches.isEmpty() &&
            tasteProfileSummary.isNullOrBlank() && wrappedSummary.isNullOrBlank()
}

data class MatchedPlaylist(val source: SourcePlaylist, val tracks: List<MatchedTrack>)
data class MatchedHistory(val event: SourceHistoryEvent, val match: MatchedTrack)
data class MatchedAlbum(val source: SourceAlbum, val album: com.revibe.model.Album?)
data class MatchedArtist(val source: SourceArtist, val artist: com.revibe.model.Artist?)

data class SpotifyMatchedArchive(
    val archive: SpotifyArchivePreview,
    val playlists: List<MatchedPlaylist> = emptyList(),
    val likedTracks: List<MatchedTrack> = emptyList(),
    val history: List<MatchedHistory> = emptyList(),
    val albums: List<MatchedAlbum> = emptyList(),
    val artists: List<MatchedArtist> = emptyList(),
) {
    val unmatched: List<MatchedTrack>
        get() = (playlists.flatMap { it.tracks } + likedTracks + history.map { it.match })
            .filter { it.status == MatchStatus.UNMATCHED || it.status == MatchStatus.NEEDS_REVIEW }
            .distinctBy { it.source.title.lowercase() + "|" + it.source.artist.lowercase() }
    val unmatchedCount: Int get() = unmatched.size
}

data class ImportCategorySelection(
    val playlists: Boolean = true,
    val likedTracks: Boolean = true,
    val savedAlbums: Boolean = true,
    val followedArtists: Boolean = true,
    val history: Boolean = true,
    val searches: Boolean = false,
    val tasteProfile: Boolean = true,
)

data class ImportProgress(
    val stage: String,
    val current: Int = 0,
    val total: Int = 0,
) {
    val fraction: Float get() = if (total <= 0) 0f else (current.toFloat() / total).coerceIn(0f, 1f)
}

data class ImportSummary(
    val primaryPlaylistId: String? = null,
    /** Name of the playlist this import wrote to, when one was created or appended to. */
    val playlistName: String? = null,
    val playlistsCreated: Int = 0,
    val playlistsSkipped: Int = 0,
    val tracksAdded: Int = 0,
    /** Source rows whose catalog match was close but not exact, selected for import or not. */
    val needsReview: Int = 0,
    val likedTracksAdded: Int = 0,
    val albumsAdded: Int = 0,
    val artistsAdded: Int = 0,
    val historyAdded: Int = 0,
    val historySkipped: Int = 0,
    val searchesAdded: Int = 0,
    val unmatched: List<SourceTrack> = emptyList(),
    val alreadyImported: Int = 0,
    val notes: List<String> = emptyList(),
)

/** Reads chosen files on the desktop; no archive contents are uploaded. */
data class PickedTextFile(
    val name: String,
    val text: String,
)

data class SpotifyArchiveEntries(
    val files: List<PickedTextFile>,
    val fingerprint: String,
)
