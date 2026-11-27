package com.revibe.imports

private data class ParsedPlaylistFile(
    val format: String,
    val playlistName: String,
    val tracks: List<SourceTrack>,
    val duplicateCount: Int,
)

private object PlaylistFileParser {
    fun parse(fileName: String, text: String): ParsedPlaylistFile {
        val cleaned = text.removePrefix("\uFEFF")
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val trimmed = cleaned.trimStart()
        return when {
            extension in setOf("m3u", "m3u8") || trimmed.startsWith("#EXTM3U") -> parseM3u(fileName, cleaned)
            extension == "xspf" || trimmed.startsWith("<?xml") || trimmed.contains("<playlist", ignoreCase = true) ->
                parseXspf(fileName, cleaned)
            extension == "csv" || looksDelimited(cleaned) -> parseDelimited(fileName, cleaned, "CSV")
            extension == "tsv" -> parseDelimited(fileName, cleaned, "TSV")
            else -> parseText(fileName, cleaned)
        }
    }

    private fun baseName(fileName: String): String =
        fileName.substringBeforeLast('.').ifBlank { "Imported playlist" }

    private fun looksDelimited(text: String): Boolean {
        val first = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return false
        val comma = first.count { it == ',' }
        val tab = first.count { it == '\t' }
        val semicolon = first.count { it == ';' }
        return maxOf(comma, tab, semicolon) > 0
    }

    private fun parseDelimited(fileName: String, text: String, format: String): ParsedPlaylistFile {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        val delimiter = when {
            firstLine.count { it == '\t' } >= firstLine.count { it == ',' } &&
                firstLine.count { it == '\t' } >= firstLine.count { it == ';' } -> '\t'
            firstLine.count { it == ';' } > firstLine.count { it == ',' } -> ';'
            else -> ','
        }
        val rows = parseRows(text, delimiter)
        val header = rows.firstOrNull()?.map(::normalizeHeader).orEmpty()
        val hasHeader = header.any { it in TRACK_HEADERS } || header.any { it in ARTIST_HEADERS }
        val dataRows = if (hasHeader) rows.drop(1) else rows
        val column = header.withIndex().associate { it.value to it.index }
        fun field(row: List<String>, aliases: Set<String>): String {
            val index = aliases.firstNotNullOfOrNull { column[it] } ?: return ""
            return row.getOrNull(index)?.trim().orEmpty()
        }

        var playlistName = ""
        var fallbackPosition = 0
        val tracks = dataRows.mapNotNull { row ->
            if (row.all { it.isBlank() }) return@mapNotNull null
            val title = if (hasHeader) {
                field(row, TRACK_HEADERS)
            } else {
                row.getOrNull(0)?.trim().orEmpty()
            }
            if (title.isBlank()) return@mapNotNull null
            playlistName = playlistName.ifBlank { field(row, PLAYLIST_HEADERS) }
            val duration = parseDurationMs(field(row, DURATION_HEADERS))
            val spotifyUri = field(row, SPOTIFY_URI_HEADERS).takeIf { it.isNotBlank() }
            val isrc = field(row, ISRC_HEADERS).takeIf { it.isNotBlank() }
            fallbackPosition++
            SourceTrack(
                position = field(row, POSITION_HEADERS).toIntOrNull()?.takeIf { it > 0 } ?: fallbackPosition,
                title = title,
                artist = if (hasHeader) field(row, ARTIST_HEADERS) else row.getOrNull(1)?.trim().orEmpty(),
                album = if (hasHeader) field(row, ALBUM_HEADERS) else row.getOrNull(2)?.trim().orEmpty(),
                isrc = isrc,
                spotifyId = spotifyUri?.let(::spotifyId),
                spotifyUri = spotifyUri,
                durationMs = duration,
            )
        }
        return finished(
            fileName,
            format,
            playlistName.ifBlank { baseName(fileName) },
            tracks,
        )
    }

    private fun parseText(fileName: String, text: String): ParsedPlaylistFile {
        var playlistName = baseName(fileName)
        val tracks = mutableListOf<SourceTrack>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isBlank() || line.startsWith("#")) return@forEach
            if (line.startsWith("Playlist:", ignoreCase = true)) {
                playlistName = line.substringAfter(':').trim().ifBlank { playlistName }
                return@forEach
            }
            val numbered = line.replace(Regex("^\\d+[.)]\\s+"), "")
            val parts = numbered.split(" - ", " – ", "\t", "|").map { it.trim() }.filter { it.isNotEmpty() }
            val title = parts.firstOrNull() ?: return@forEach
            if (title.equals("Track", ignoreCase = true) || title.equals("Title", ignoreCase = true)) return@forEach
            tracks += SourceTrack(
                position = tracks.size + 1,
                title = title,
                artist = parts.getOrNull(1).orEmpty(),
                album = parts.getOrNull(2).orEmpty(),
            )
        }
        return finished(fileName, "TXT", playlistName, tracks)
    }

    private fun parseM3u(fileName: String, text: String): ParsedPlaylistFile {
        var name = baseName(fileName)
        var pendingDuration = 0L
        var pendingArtist = ""
        var pendingTitle = ""
        val tracks = mutableListOf<SourceTrack>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> Unit
                line.startsWith("#PLAYLIST:", ignoreCase = true) -> {
                    name = line.substringAfter(':').trim().ifBlank { name }
                }
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val meta = line.substringAfter(':', "")
                    val duration = meta.substringBefore(',').trim().toIntOrNull() ?: -1
                    pendingDuration = if (duration > 0) duration * 1000L else 0
                    val display = meta.substringAfter(',', "").trim()
                    val split = display.split(" - ", " – ", "\t").map { it.trim() }
                    if (split.size >= 2) {
                        pendingArtist = split.first()
                        pendingTitle = split.drop(1).joinToString(" - ")
                    } else {
                        pendingTitle = display
                    }
                }
                line.startsWith("#") || line.isBlank() -> Unit
                else -> {
                    val fallback = line.substringAfterLast('/').substringBeforeLast('.').replace('_', ' ').trim()
                    val parts = fallback.split(" - ", " – ").map { it.trim() }
                    val title = pendingTitle.ifBlank {
                        if (parts.size >= 2) parts.drop(1).joinToString(" - ") else fallback
                    }
                    val artist = pendingArtist.ifBlank { if (parts.size >= 2) parts.first() else "" }
                    tracks += SourceTrack(
                        position = tracks.size + 1,
                        title = title.ifBlank { fallback },
                        artist = artist,
                        durationMs = pendingDuration,
                    )
                    pendingTitle = ""
                    pendingArtist = ""
                    pendingDuration = 0
                }
            }
        }
        return finished(fileName, "M3U", name, tracks)
    }

    private fun parseXspf(fileName: String, text: String): ParsedPlaylistFile {
        val playlistName = tag(text, "title").ifBlank { baseName(fileName) }
        val tracks = Regex("<track\\b[^>]*>(.*?)</track>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .findAll(text)
            .mapIndexedNotNull { index, match ->
                val block = match.groupValues[1]
                val title = tag(block, "title")
                if (title.isBlank()) return@mapIndexedNotNull null
                val duration = tag(block, "duration").toLongOrNull() ?: 0L
                SourceTrack(
                    position = index + 1,
                    title = title,
                    artist = tag(block, "creator"),
                    album = tag(block, "album"),
                    durationMs = if (duration in 1..86_400_000L) duration else 0,
                )
            }
            .toList()
        return finished(fileName, "XSPF", playlistName, tracks)
    }

    private fun finished(fileName: String, format: String, name: String, tracks: List<SourceTrack>): ParsedPlaylistFile {
        val seen = mutableSetOf<String>()
        var duplicates = 0
        val normalized = tracks.mapIndexed { index, track ->
            val key = listOf(track.title, track.artist, track.album, track.spotifyUri.orEmpty())
                .joinToString("|")
                .lowercase()
                .replace(Regex("\\s+"), " ")
            if (!seen.add(key)) duplicates++
            track
        }
        return ParsedPlaylistFile(format, name, normalized, duplicates)
    }

    private fun tag(xml: String, name: String): String =
        Regex("<$name\\b[^>]*>(.*?)</$name>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(xml)?.groupValues?.get(1)?.let(::xmlUnescape)?.trim().orEmpty()

    private fun xmlUnescape(value: String): String = value
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&amp;", "&")

    private fun spotifyId(uri: String): String? = uri
        .takeIf { it.contains(":track:") }
        ?.substringAfterLast(':')
        ?.takeIf { it.isNotBlank() }

    private fun normalizeHeader(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun parseDurationMs(value: String): Long {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return 0
        if (trimmed.matches(Regex("\\d+"))) {
            val number = trimmed.toLong()
            return if (number > 10_000 || number > 3_600) number else number * 1000
        }
        val parts = trimmed.split(':').mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60 + parts[1]) * 1000
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
            else -> 0
        }
    }

    private fun parseRows(text: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                char == '"' && quoted && index + 1 < text.length && text[index + 1] == '"' -> {
                    field.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == delimiter && !quoted -> {
                    row += field.toString()
                    field.clear()
                }
                (char == '\n' || char == '\r') && !quoted -> {
                    if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                    row += field.toString()
                    field.clear()
                    if (row.any { it.isNotBlank() }) rows += row
                    row = mutableListOf()
                }
                else -> field.append(char)
            }
            index++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString()
            if (row.any { it.isNotBlank() }) rows += row
        }
        return rows
    }

    private val TRACK_HEADERS = setOf("trackname", "title", "track", "song", "songname", "name")
    private val ARTIST_HEADERS = setOf("artistname", "artist", "artists", "albumartist", "creator")
    private val ALBUM_HEADERS = setOf("albumname", "album")
    private val ISRC_HEADERS = setOf("isrc", "isrcs", "isrcid")
    private val SPOTIFY_URI_HEADERS = setOf("spotifyuri", "spotifytrackuri", "uri", "trackuri", "spotifyid")
    private val DURATION_HEADERS = setOf("duration", "durationms", "durationinms", "length", "milliseconds")
    private val PLAYLIST_HEADERS = setOf("playlistname", "playlist")
    private val POSITION_HEADERS = setOf("position", "trackposition", "index", "tracknumber", "no")
}

enum class PlaylistFileFormat { CSV, TXT, M3U, M3U8, XSPF }

/** Public parser entry point. Throws [IllegalArgumentException] for unusable files. */
fun parsePlaylistFile(fileName: String, text: String): PlaylistFilePreview {
    require(text.isNotBlank()) { "The selected playlist file is empty." }
    val parsed = PlaylistFileParser.parse(fileName, text)
    require(parsed.tracks.isNotEmpty()) { "No tracks could be found in this file." }
    return PlaylistFilePreview(
        fileName = fileName,
        fingerprint = archiveFingerprint(listOf(PickedTextFile(fileName, text))),
        format = parsed.format,
        playlistName = parsed.playlistName,
        tracks = parsed.tracks.map { MatchedTrack(it, null, MatchStatus.UNMATCHED, 0f) },
        duplicateCount = parsed.duplicateCount,
    )
}
