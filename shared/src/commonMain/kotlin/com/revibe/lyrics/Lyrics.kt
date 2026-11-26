package com.revibe.lyrics

import com.revibe.library.LibraryStore
import com.revibe.model.Track
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

data class LyricWord(val timeMs: Long, val text: String)

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
)

data class Lyrics(
    val lines: List<LyricLine>,
    val plain: String?,
    val hasTimestamps: Boolean,
    val wordSynced: Boolean,
    val source: String,
    val syncedText: String? = null,
) {
    val isEmpty: Boolean get() = lines.isEmpty() && plain.isNullOrBlank()
}

/**
 * LRC parser supporting standard `[mm:ss.xx]` lines, multiple timestamps per
 * line and enhanced word-level `<mm:ss.xx>` tags used by word-synced lyrics.
 */
object LrcParser {
    private val timestampRegex = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val wordTagRegex = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")

    fun parse(synced: String?): List<LyricLine> {
        if (synced.isNullOrBlank()) return emptyList()
        val result = mutableListOf<LyricLine>()

        synced.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach

            val stamps = timestampRegex.findAll(line).toList()
            if (stamps.isEmpty()) return@forEach

            val contentStart = stamps.last().range.last + 1
            val content = line.substring(contentStart).trim()
            if (content.isEmpty()) {
                // instrumental break marker
                stamps.forEach { match -> result += LyricLine(match.toMillis(), "♪", emptyList()) }
                return@forEach
            }

            val words = parseWords(content)
            val displayText = if (words.isNotEmpty()) words.joinToString("") { it.text } else content
            val baseTime = words.firstOrNull()?.timeMs
            stamps.forEach { match ->
                result += LyricLine(
                    timeMs = baseTime ?: match.toMillis(),
                    text = displayText,
                    words = words,
                )
            }
        }

        return result.sortedBy { it.timeMs }
            .distinctBy { it.timeMs to it.text }
    }

    private fun parseWords(content: String): List<LyricWord> {
        val matches = wordTagRegex.findAll(content).toList()
        if (matches.isEmpty()) return emptyList()
        val words = mutableListOf<LyricWord>()
        matches.forEachIndexed { index, match ->
            val textStart = match.range.last + 1
            val textEnd = if (index + 1 < matches.size) matches[index + 1].range.first else content.length
            val wordText = content.substring(textStart, textEnd)
            if (wordText.isNotEmpty()) {
                words += LyricWord(match.toMillis(), wordText)
            }
        }
        return words
    }

    private fun MatchResult.toMillis(): Long {
        val minutes = groupValues[1].toLongOrNull() ?: 0
        val seconds = groupValues[2].toLongOrNull() ?: 0
        val fractionRaw = groupValues[3]
        val fraction = when (fractionRaw.length) {
            0 -> 0
            1 -> fractionRaw.toLongOrNull()?.times(100) ?: 0
            2 -> fractionRaw.toLongOrNull()?.times(10) ?: 0
            else -> fractionRaw.take(3).toLongOrNull() ?: 0
        }
        return minutes * 60_000 + seconds * 1000 + fraction
    }
}

/**
 * LRCLIB is a free, community lyrics database with an open API and no key.
 * Docs: https://lrclib.net/docs
 */
class LyricsRepository(
    private val client: HttpClient,
    private val store: LibraryStore,
) {
    private val memory = mutableMapOf<String, Lyrics?>()

    suspend fun lyricsFor(track: Track): Lyrics? {
        memory[track.id]?.let { return it }
        if (track.id in memory) return null

        store.cachedLyrics(track.id)?.let { cached ->
            val parsed = build(track, cached.synced, cached.plain, cached.source)
            memory[track.id] = parsed
            return parsed
        }

        val fetched = fetch(track)
        memory[track.id] = fetched
        fetched?.let {
            store.putLyrics(
                trackId = track.id,
                plain = it.plain,
                synced = it.syncedText,
                source = it.source,
            )
        }
        return fetched
    }

    private suspend fun fetch(track: Track): Lyrics? {
        val direct = runCatching {
            val response: HttpResponse = client.get("https://lrclib.net/api/get") {
                parameter("artist_name", track.artist)
                parameter("track_name", track.title)
                parameter("album_name", track.album)
                if (track.durationMs > 0) parameter("duration", track.durationMs / 1000)
            }
            if (!response.status.isSuccess()) return@runCatching null
            response.body<LrclibLyrics>()
        }.getOrNull()

        direct?.let { return build(track, it.syncedLyrics, it.plainLyrics, "LRCLIB") }

        val results = runCatching {
            val response: HttpResponse = client.get("https://lrclib.net/api/search") {
                parameter("q", "${track.artist} ${track.title}")
            }
            if (!response.status.isSuccess()) return@runCatching emptyList()
            response.body<List<LrclibLyrics>>()
        }.getOrDefault(emptyList())

        val best = results
            .filter { !it.instrumental && (!it.plainLyrics.isNullOrBlank() || !it.syncedLyrics.isNullOrBlank()) }
            .minByOrNull { candidate ->
                val durationDelta = if (track.durationMs > 0 && candidate.duration != null) {
                    kotlin.math.abs(candidate.duration - track.durationMs / 1000.0)
                } else {
                    60.0
                }
                durationDelta
            }
            ?: return null

        return build(track, best.syncedLyrics, best.plainLyrics, "LRCLIB")
    }

    private fun build(track: Track, synced: String?, plain: String?, source: String): Lyrics? {
        val lines = LrcParser.parse(synced)
        if (lines.isEmpty() && plain.isNullOrBlank()) return null
        return Lyrics(
            lines = lines,
            plain = plain,
            hasTimestamps = lines.isNotEmpty(),
            wordSynced = lines.any { it.words.isNotEmpty() },
            source = source,
            syncedText = synced,
        )
    }
}

@Serializable
private data class LrclibLyrics(
    val id: Long? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)
