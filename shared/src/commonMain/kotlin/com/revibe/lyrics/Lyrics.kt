package com.revibe.lyrics

import com.revibe.library.LibraryStore
import com.revibe.model.Track
import io.ktor.client.HttpClient

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
 * Central lyrics resolver with a cache. LRCLIB remains primary; Unison is a
 * video-id-exact fallback used when LRCLIB has no timed lyrics. Both return the
 * same [Lyrics] domain model, so the UI never sees provider payloads.
 */
class LyricsRepository(
    client: HttpClient,
    private val store: LibraryStore,
    private val resolver: LyricsProviderResolver = LyricsProviderResolver(
        LrclibLyricsSource(client),
        LrcRedLyricsSource(client),
        UnisonLyricsSource(client),
    ),
) {
    private val memory = mutableMapOf<String, Lyrics?>()

    suspend fun lyricsFor(track: Track): Lyrics? {
        memory[track.id]?.let { return it }
        if (track.id in memory) return null

        store.cachedLyrics(track.id)?.let { cached ->
            val parsed = build(cached.synced, cached.plain, cached.source)
            memory[track.id] = parsed
            return parsed
        }

        val fetched = resolver.resolve(track)
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

    private fun build(synced: String?, plain: String?, source: String): Lyrics? {
        // Both Unison and lrc.red return TTML; cached LRC from LRCLIB must keep
        // using the LRC parser. Sniffing the payload avoids coupling cache
        // validity to a provider display name.
        val lines = if (synced?.trimStart()?.startsWith("<") == true) {
            TtmlParser.parse(synced)
        } else {
            LrcParser.parse(synced)
        }
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
