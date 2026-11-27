package com.revibe.provider

import com.revibe.model.Track

/**
 * Resolves artwork in priority order:
 *
 * 1. The catalogue's own artwork (YouTube Music / InnerTubeX) always wins.
 * 2. MusicBrainz + Cover Art Archive for a matched release.
 * 3. Deezer's public search as a keyless square-cover fallback.
 *
 * Providers are only consulted for a missing cover. Every provider failure is
 * treated as a miss so artwork can never stop playback or navigation.
 */
class ArtworkResolver(
    private val providers: List<ArtworkProvider?>,
) {
    constructor(provider: ArtworkProvider?) : this(listOf(provider))

    suspend fun resolve(track: Track): String? = resolveCandidate(track)?.url

    suspend fun resolveCandidate(track: Track): ArtworkCandidate? {
        if (track.isArchived) return null
        if (!track.artworkUrl.isNullOrBlank()) {
            return ArtworkCandidate(
                url = track.artworkUrl,
                provider = "YouTube Music",
                matchedBy = "catalog",
            )
        }
        if (track.artist.isBlank() ||
            track.artist.equals("Unknown artist", ignoreCase = true) ||
            track.title.isBlank()
        ) {
            return null
        }

        val query = ArtworkQuery(
            artist = track.artist,
            album = track.album,
            title = track.title,
            durationMs = track.durationMs,
        )
        for (provider in providers) {
            val candidate = runCatching { provider?.artwork(query) }.getOrNull() ?: continue
            if (candidate.url.isBlank()) continue
            return candidate
        }
        return null
    }
}
