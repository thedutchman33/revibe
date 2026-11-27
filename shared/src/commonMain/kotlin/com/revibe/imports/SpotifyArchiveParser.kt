package com.revibe.imports

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Parses the music-related JSON files from a Spotify data export. Unrelated personal data is
 * never inspected: only files whose names/structure are recognized are read by the caller and
 * only music fields are extracted here.
 */
fun parseSpotifyArchive(archive: SpotifyArchiveEntries): SpotifyArchivePreview {
    var readme: String? = null
    val playlists = mutableListOf<SourcePlaylist>()
    val liked = mutableListOf<SourceTrack>()
    val albums = mutableListOf<SourceAlbum>()
    val artists = mutableListOf<SourceArtist>()
    val history = LinkedHashMap<String, SourceHistoryEvent>()
    val historyOverlap = HashMap<String, String>()
    val searches = mutableListOf<SourceSearch>()
    var tasteSummary: String? = null
    var wrappedSummary: String? = null
    val ignored = mutableListOf<String>()

    archive.files.forEach { file ->
        val name = file.name.substringAfterLast('/').lowercase()
        val text = file.text
        when {
            name.endsWith(".md") || (name.endsWith(".txt") && text.contains("spotify", ignoreCase = true)) -> {
                if (readme == null && (name.contains("read") || text.contains("read me", ignoreCase = true))) {
                    readme = text.take(4_000)
                }
            }
            name.endsWith(".json") && name.contains("playlist") -> {
                parsePlaylistFile(text)?.let { playlists += it }
            }
            name.endsWith(".json") && (name.contains("yourlibrary") || name.contains("liked")) -> {
                parseLibraryFile(text, liked, albums, artists)
            }
            name.endsWith(".json") && name.contains("follow") -> {
                parseFollowFile(text, artists)
            }
            name.endsWith(".json") && (name.contains("streaminghistory") || name.contains("streaming_history")) -> {
                val packageType = if (
                    name.contains("extended") || name.contains("streaming_history") || name.contains("streaming-history")
                ) "extended" else "account"
                parseHistoryFile(text, history, historyOverlap, packageType)
            }
            name.endsWith(".json") && (name.contains("searchqueries") || name.contains("search_queries")) -> {
                parseSearchFile(text, searches)
            }
            name.endsWith(".json") && name.contains("taste") -> {
                tasteSummary = summarizeJson(text, "Taste profile")
            }
            name.endsWith(".json") && name.contains("wrapped") -> {
                wrappedSummary = summarizeJson(text, "Wrapped")
            }
            name.endsWith(".json") || name.endsWith(".csv") || name.endsWith(".txt") -> {
                ignored += file.name
            }
        }
    }

    val sortedHistory = history.values.sortedBy { it.timestampMs }
    return SpotifyArchivePreview(
        fingerprint = archive.fingerprint,
        readme = readme,
        playlists = playlists,
        likedTracks = dedupeTracks(liked),
        savedAlbums = albums.distinctBy { it.sourceId },
        followedArtists = artists.distinctBy { it.sourceId },
        history = sortedHistory,
        searches = searches.distinctBy { it.sourceKey },
        tasteProfileSummary = tasteSummary,
        wrappedSummary = wrappedSummary,
        ignoredFiles = ignored.distinct(),
    )
}

private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

private fun readJson(text: String): JsonElement? = runCatching { json.parseToJsonElement(text) }.getOrNull()

private fun parsePlaylistFile(text: String): List<SourcePlaylist>? {
    val root = readJson(text) ?: return null
    val array = when (root) {
        is JsonArray -> root
        is JsonObject -> root.array("playlists") ?: root.array("items") ?: return null
        else -> return null
    }
    return array.mapIndexedNotNull { index, element ->
        val obj = element as? JsonObject ?: return@mapIndexedNotNull null
        val name = obj.string("name", "title") ?: return@mapIndexedNotNull null
        val sourceId = obj.string("uri", "id", "playlistUri") ?: "$name|$index"
        val items = obj.array("items", "tracks") ?: JsonArray(emptyList())
        val tracks = items.mapIndexedNotNull { trackIndex, item ->
            val itemObj = item as? JsonObject ?: return@mapIndexedNotNull null
            val trackObj = (itemObj["track"] as? JsonObject) ?: itemObj
            sourceTrack(trackObj, trackIndex + 1)
        }
        SourcePlaylist(
            sourceId = sourceId,
            name = name,
            description = obj.string("description").orEmpty(),
            tracks = tracks,
        )
    }.filter { it.tracks.isNotEmpty() }
}

private fun parseLibraryFile(
    text: String,
    liked: MutableList<SourceTrack>,
    albums: MutableList<SourceAlbum>,
    artists: MutableList<SourceArtist>,
) {
    val root = readJson(text) ?: return
    val obj = root as? JsonObject
    if (obj == null) {
        // A bare liked-songs array is also accepted.
        (root as? JsonArray)?.forEachIndexed { index, item ->
            (item as? JsonObject)?.let { sourceTrack(it, index + 1) }?.let(liked::add)
        }
        return
    }
    obj.array("tracks", "songs")?.forEachIndexed { index, item ->
        (item as? JsonObject)?.let { sourceTrack(it, index + 1) }?.let(liked::add)
    }
    obj.array("albums")?.forEachIndexed { index, item ->
        val album = item as? JsonObject ?: return@forEachIndexed
        val name = album.string("album", "albumName", "name") ?: return@forEachIndexed
        val artist = album.string("artist", "artistName", "albumArtist") ?: album.artistFromArray().orEmpty()
        albums += SourceAlbum(
            sourceId = album.string("uri", "id") ?: "$name|$artist|$index",
            name = name,
            artist = artist,
            year = album.string("year")?.toIntOrNull(),
        )
    }
    obj.array("artists")?.forEachIndexed { index, item ->
        val artist = item as? JsonObject ?: return@forEachIndexed
        val name = artist.string("name", "artist", "artistName") ?: return@forEachIndexed
        artists += SourceArtist(artist.string("uri", "id") ?: "$name|$index", name)
    }
}

private fun parseFollowFile(text: String, artists: MutableList<SourceArtist>) {
    val root = readJson(text) ?: return
    val array = when (root) {
        is JsonArray -> root
        is JsonObject -> root.array("following", "artists", "followedArtists") ?: return
        else -> return
    }
    array.forEachIndexed { index, item ->
        val obj = item as? JsonObject ?: return@forEachIndexed
        val name = obj.string("name", "artist", "artistName") ?: return@forEachIndexed
        artists += SourceArtist(obj.string("uri", "id") ?: "$name|$index", name)
    }
}

private fun parseHistoryFile(
    text: String,
    history: MutableMap<String, SourceHistoryEvent>,
    overlapIndex: MutableMap<String, String>,
    sourcePackage: String,
) {
    val root = readJson(text) as? JsonArray ?: return
    root.forEachIndexed { index, item ->
        val obj = item as? JsonObject ?: return@forEachIndexed
        if (obj.string("episodeName", "episode_name") != null) return@forEachIndexed
        val title = obj.string(
            "master_metadata_track_name", "trackName", "track_name", "title",
        ) ?: return@forEachIndexed
        val artist = obj.string(
            "master_metadata_album_artist_name", "artistName", "artist_name", "artist",
        ).orEmpty()
        val album = obj.string(
            "master_metadata_album_album_name", "albumName", "album_name", "album",
        ).orEmpty()
        val timestamp = parseSpotifyTimestamp(
            obj.string("ts", "endTime", "end_time", "timestamp"),
        ) ?: return@forEachIndexed
        val playedMs = obj.long("ms_played", "msPlayed", "playedMs") ?: 0
        val uri = obj.string("spotify_track_uri", "trackUri", "track_uri", "uri")
        val reasonEnd = obj.string("reason_end", "reasonEnd")
        val reasonStart = obj.string("reason_start", "reasonStart")
        val shuffle = obj.boolean("shuffle")
        val platform = obj.string("platform")
        val eventId = obj.string(
            "play_id", "playId", "event_id", "eventId", "spotify_play_id", "spotifyPlayId",
        )?.takeIf { it.isNotBlank() }
        val skipped = obj.boolean("skipped") ?: (reasonEnd == "fwdbtn" || reasonEnd == "backbtn")
        // Account data does not include a completion reason, so absence of one is not
        // treated as a completed play. Skip and completion flags are only set from evidence.
        val completed = obj.boolean("completed") ?: (reasonEnd == "trackdone")

        // The fallback fingerprint uses the exact timestamp, so two genuinely different
        // plays in the same minute stay distinct. A separate overlap index pairs an account
        // export (minute precision) with an extended export only when the track and played
        // duration match and the rows come from different export packages.
        val overlapKey = buildString {
            append(timestamp / 60_000).append('|')
            append(title.lowercase().trim()).append('|')
            append(artist.lowercase().trim()).append('|')
            append(playedMs)
        }
        val fullKey = buildString {
            append(eventId?.let { "event|$it" } ?: overlapKey).append('|')
            append(album.lowercase().trim()).append('|')
            append(reasonStart.orEmpty()).append('|')
            append(reasonEnd.orEmpty()).append('|')
            append(shuffle?.toString().orEmpty()).append('|')
            append(platform.orEmpty())
        }
        val key = eventId?.let { "event|$it" } ?: fullKey
        val event = SourceHistoryEvent(
            sourceKey = key,
            timestampMs = timestamp,
            track = SourceTrack(
                position = index + 1,
                title = title,
                artist = artist,
                album = album,
                spotifyUri = uri,
                spotifyId = uri?.substringAfterLast(':')?.takeIf { it.isNotBlank() },
                durationMs = 0,
                sourceKey = key,
            ),
            playedMs = playedMs,
            completed = completed,
            skipped = skipped,
            shuffle = shuffle,
            platform = platform,
            reasonStart = reasonStart,
            reasonEnd = reasonEnd,
            sourceEventId = eventId,
            sourcePackage = sourcePackage,
        )

        // Account and extended exports describe the same play with different field sets. Pair
        // them only on an exact timestamp/track/duration match, and only when at least one row
        // has no original event id (or both ids agree). Never pair by minute alone.
        val indexedKey = overlapIndex[overlapKey]
        val indexed = indexedKey?.let(history::get)
        val previousKey = when {
            history[key] != null -> key
            indexed != null &&
                indexed.sourcePackage != sourcePackage &&
                (indexed.sourceEventId == null || eventId == null || indexed.sourceEventId == eventId) -> indexedKey
            else -> null
        }
        val previous = previousKey?.let(history::remove)
        val merged = if (previous == null) event else previous.copy(
            completed = previous.completed || event.completed,
            skipped = previous.skipped || event.skipped,
            shuffle = previous.shuffle ?: event.shuffle,
            platform = previous.platform ?: event.platform,
            reasonStart = previous.reasonStart ?: event.reasonStart,
            reasonEnd = previous.reasonEnd ?: event.reasonEnd,
            sourceEventId = previous.sourceEventId ?: event.sourceEventId,
            track = if (previous.track.spotifyUri == null && event.track.spotifyUri != null) event.track else previous.track,
        )
        val storedKey = when {
            previous == null -> key
            previousKey == key -> key
            !previous.sourceKey.startsWith("event|") && key.startsWith("event|") -> key
            else -> previous.sourceKey
        }
        history[storedKey] = merged.copy(sourceKey = storedKey, track = merged.track.copy(sourceKey = storedKey))
        overlapIndex[overlapKey] = storedKey
    }
}

private fun parseSearchFile(text: String, searches: MutableList<SourceSearch>) {
    val root = readJson(text) ?: return
    val array = when (root) {
        is JsonArray -> root
        is JsonObject -> root.array("searchQueries", "searches", "queries") ?: return
        else -> return
    }
    array.forEachIndexed { index, item ->
        val obj = item as? JsonObject ?: return@forEachIndexed
        val query = obj.string("searchQuery", "query", "search_query", "text")?.trim() ?: return@forEachIndexed
        val timestamp = parseSpotifyTimestamp(obj.string("searchTime", "timestamp", "ts")) ?: 0L
        val uris = obj.array("interactedResultUris", "resultUris", "uris")
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .orEmpty()
        searches += SourceSearch(
            sourceKey = "$timestamp|${query.lowercase()}|$index",
            query = query,
            timestampMs = timestamp,
            platform = obj.string("platform", "device"),
            resultUris = uris,
        )
    }
}

private fun sourceTrack(obj: JsonObject, position: Int): SourceTrack? {
    val title = obj.string("trackName", "track_name", "name", "title", "track") ?: return null
    if (title.isBlank()) return null
    val uri = obj.string("trackUri", "track_uri", "uri", "spotify_track_uri", "spotifyTrackUri")
    val artist = obj.string("artistName", "artist_name", "artist")
        ?: obj.artistFromArray().orEmpty()
    val duration = obj.long("durationMs", "duration_ms", "duration_ms_played")
        ?: obj.string("duration")?.let(::durationMsFromText) ?: 0L
    return SourceTrack(
        position = position,
        title = title,
        artist = artist,
        album = obj.string("albumName", "album_name", "album").orEmpty(),
        isrc = obj.string("isrc"),
        spotifyUri = uri,
        spotifyId = uri?.substringAfterLast(':')?.takeIf { it.isNotBlank() },
        durationMs = duration,
    )
}

private fun JsonObject.artistFromArray(): String? =
    array("artists")
        ?.mapNotNull { (it as? JsonObject)?.string("name") }
        ?.joinToString(", ")
        ?.takeIf { it.isNotBlank() }

private fun dedupeTracks(tracks: List<SourceTrack>): List<SourceTrack> {
    val seen = mutableSetOf<String>()
    return tracks.mapIndexedNotNull { index, track ->
        val key = track.spotifyUri ?: "${track.title.lowercase()}|${track.artist.lowercase()}"
        if (!seen.add(key)) null else track.copy(position = index + 1)
    }
}

private fun summarizeJson(text: String, label: String): String? {
    val root = readJson(text) as? JsonObject ?: return null
    val pieces = root.entries.take(8).mapNotNull { (key, value) ->
        when (value) {
            is JsonArray -> "$key: ${value.size} records".takeIf { value.isNotEmpty() }
            is JsonObject -> "$key: ${value.size} fields".takeIf { value.isNotEmpty() }
            is JsonPrimitive -> value.contentOrNull?.take(80)?.let { "$key: $it" }
            else -> null
        }
    }
    return if (pieces.isEmpty()) null else "$label — ${pieces.joinToString(" · ").take(2_000)}"
}

private fun JsonObject.string(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key -> (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() } }

private fun JsonObject.long(vararg keys: String): Long? =
    keys.firstNotNullOfOrNull { key -> (this[key] as? JsonPrimitive)?.longOrNull }

private fun JsonObject.boolean(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.booleanOrNull

private fun JsonObject.array(vararg keys: String): JsonArray? =
    keys.firstNotNullOfOrNull { key -> this[key] as? JsonArray }

private fun durationMsFromText(value: String): Long {
    val parts = value.split(':').mapNotNull { it.trim().toLongOrNull() }
    return when (parts.size) {
        2 -> (parts[0] * 60 + parts[1]) * 1000
        3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
        else -> value.toLongOrNull()?.let { if (it < 100_000) it * 1000 else it } ?: 0
    }
}

fun parseSpotifyTimestamp(value: String?): Long? {
    val raw = value?.trim()?.takeIf { it.length >= 10 } ?: return null
    val normalized = raw.replace(' ', 'T')
    val date = normalized.substring(0, 10).split('-').mapNotNull { it.toIntOrNull() }
    if (date.size != 3) return null
    val timePart = normalized.substringAfter('T', "00:00:00")
        .substringBefore('Z')
        .substringBefore('+')
        .substringBefore('-')
    val time = timePart.split(':').mapNotNull { it.toIntOrNull() }
    val year = date[0]
    val month = date[1]
    val day = date[2]
    val hour = time.getOrNull(0) ?: 0
    val minute = time.getOrNull(1) ?: 0
    val second = time.getOrNull(2) ?: 0
    if (month !in 1..12 || day !in 1..31 || hour !in 0..23 || minute !in 0..59 || second !in 0..59) return null
    var millis = daysFromCivil(year, month, day) * 86_400_000L +
        hour * 3_600_000L + minute * 60_000L + second * 1_000L
    val offset = Regex("([+-])(\\d{2}):?(\\d{2})$").find(normalized)
    if (offset != null) {
        val sign = if (offset.groupValues[1] == "-") -1 else 1
        val offsetMinutes = offset.groupValues[2].toInt() * 60 + offset.groupValues[3].toInt()
        millis -= sign * offsetMinutes * 60_000L
    }
    return millis
}

private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    var y = year
    val m = month
    y -= if (m <= 2) 1 else 0
    val era = if (y >= 0) y / 400 else (y - 399) / 400
    val yoe = y - era * 400
    val mp = (m + 9) % 12
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146_097L + doe - 719_468L
}

internal fun archiveFingerprint(files: List<PickedTextFile>): String {
    var hash = -0x340d631b7bdddcdbL // FNV-1a offset basis
    files.sortedBy { it.name }.forEach { file ->
        val text = "${file.name}\u0000${file.text.length}\u0000${file.text.take(8_000)}"
        text.forEach { char ->
            hash = hash xor char.code.toLong()
            hash *= 0x100000001b3L
        }
    }
    return hash.toULong().toString(16).padStart(16, '0')
}
