package com.revibe.imports

import com.revibe.catalog.CatalogRepository
import com.revibe.model.Track
import kotlin.math.abs
import kotlin.math.max

/**
 * Resolves imported metadata against the live catalog. A Spotify URI is never treated as a
 * playable video id; matching is metadata/duration based and ambiguous rows stay reviewable.
 */
class TrackMatcher(private val catalog: CatalogRepository) {
    private val cache = mutableMapOf<String, MatchedTrack>()

    suspend fun match(source: SourceTrack, market: String): MatchedTrack {
        val key = cacheKey(source)
        cache[key]?.let { cached ->
            return cached.copy(source = source, note = appendNote(cached.note, source))
        }
        val query = listOf(source.title, source.artist)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { source.title }
        val results = runCatching { catalog.search(query, market).tracks }.getOrDefault(emptyList())
        val scored = results
            .map { track -> track to score(source, track) }
            .sortedByDescending { it.second.total }
        val best = scored.firstOrNull()
        val chosen = best?.takeIf { it.second.total >= REVIEW_THRESHOLD }?.first
        val confidence = best?.second?.total ?: 0f
        val status = when {
            chosen == null -> MatchStatus.UNMATCHED
            confidence >= MATCH_THRESHOLD && best.second.title >= 0.78 -> MatchStatus.MATCHED
            else -> MatchStatus.NEEDS_REVIEW
        }
        val note = when {
            chosen == null -> "No catalog result met the title/artist threshold."
            status == MatchStatus.NEEDS_REVIEW -> "Close result; confirm before importing."
            best.second.versionPenalty > 0f -> "Version wording differs; confirm the recording."
            else -> ""
        }
        val matched = MatchedTrack(
            source = source,
            track = chosen,
            status = status,
            confidence = confidence,
            note = note,
            selected = status == MatchStatus.MATCHED,
        )
        cache[key] = matched
        return matched
    }

    fun matchAll(
        source: SourceTrack,
        candidates: List<Track>,
    ): List<Pair<Track, Float>> = candidates
        .map { it to score(source, it).total }
        .sortedByDescending { it.second }

    private fun cacheKey(source: SourceTrack): String = listOf(
        normalize(source.title),
        normalize(source.artist),
        normalize(source.album),
        source.durationMs / 1000,
        versionTokens(source.title).sorted().joinToString(","),
    ).joinToString("|")

    private fun appendNote(note: String, source: SourceTrack): String = buildString {
        append(note)
        if (!source.isrc.isNullOrBlank()) {
            if (isNotEmpty()) append(" ")
            append("ISRC retained as source metadata only.")
        }
    }

    private data class Score(
        val total: Float,
        val title: Float,
        val artist: Float,
        val versionPenalty: Float,
    )

    private fun score(source: SourceTrack, candidate: Track): Score {
        val sourceTitle = normalize(source.title)
        val candidateTitle = normalize(candidate.title)
        val title = titleSimilarity(sourceTitle, candidateTitle)
        val artist = artistSimilarity(source.artist, candidate.artist)
        val album = if (source.album.isBlank() || candidate.album.isBlank()) {
            0.5f
        } else {
            similarity(normalize(source.album), normalize(candidate.album))
        }
        val duration = when {
            source.durationMs <= 0 || candidate.durationMs <= 0 -> 0.5f
            abs(source.durationMs - candidate.durationMs) <= 2_000 -> 1f
            abs(source.durationMs - candidate.durationMs) <= 8_000 -> 0.7f
            abs(source.durationMs - candidate.durationMs) <= 20_000 -> 0.3f
            else -> 0f
        }
        val versionPenalty = versionPenalty(source.title, candidate.title)
        val total = if (source.artist.isBlank()) {
            (title * 0.68f + album * 0.12f + duration * 0.20f - versionPenalty)
        } else {
            (title * 0.50f + artist * 0.30f + album * 0.10f + duration * 0.10f - versionPenalty)
        }.coerceIn(0f, 1f)
        return Score(total, title, artist, versionPenalty)
    }

    private fun versionPenalty(source: String, candidate: String): Float {
        val sourceTokens = versionTokens(source)
        val candidateTokens = versionTokens(candidate)
        if (sourceTokens == candidateTokens) return 0f
        if (sourceTokens.isEmpty() && candidateTokens.isNotEmpty()) return 0.16f
        if (sourceTokens.isNotEmpty() && candidateTokens.isEmpty()) return 0.20f
        return if (sourceTokens.intersect(candidateTokens).isEmpty()) 0.22f else 0.06f
    }

    private val versionWords = setOf(
        "remix", "remixed", "live", "acoustic", "instrumental", "karaoke", "demo",
        "remaster", "remastered", "edit", "version", "extended", "radio", "cover",
        "sped", "slowed", "reverb", "nightcore", "8d",
    )

    private fun versionTokens(title: String): Set<String> {
        val lower = title.lowercase()
        return versionWords.filter { marker -> lower.contains(marker) }.toSet()
    }

    private fun titleSimilarity(source: String, candidate: String): Float {
        if (source == candidate) return 1f
        val sourceWords = meaningfulWords(source)
        val candidateWords = meaningfulWords(candidate)
        if (sourceWords.isEmpty() || candidateWords.isEmpty()) return 0f
        if (candidateWords.joinToString(" ").contains(sourceWords.joinToString(" "))) {
            val ratio = sourceWords.size.toFloat() / candidateWords.size
            return (0.82f + 0.18f * ratio).coerceAtMost(0.98f)
        }
        val intersection = sourceWords.intersect(candidateWords).size.toFloat()
        val union = sourceWords.union(candidateWords).size.toFloat()
        return (intersection / max(union, 1f)) * 0.92f
    }

    private fun artistSimilarity(source: String, candidate: String): Float {
        if (source.isBlank() || candidate.isBlank()) return 0.5f
        val sourceNames = splitArtists(source)
        val candidateNames = splitArtists(candidate)
        if (sourceNames.any { it == candidateNames.joinToString(" ") }) return 1f
        var best = 0f
        sourceNames.forEach { left ->
            candidateNames.forEach { right ->
                best = max(best, similarity(left, right))
            }
        }
        return best
    }

    private fun splitArtists(value: String): List<String> = normalize(value)
        .split(Regex("\\s*(?:,|&|feat\\.?|ft\\.?|with|x)\\s*"))
        .map { it.trim() }
        .filter { it.isNotBlank() }

    private fun similarity(left: String, right: String): Float {
        if (left == right) return 1f
        if (left.isBlank() || right.isBlank()) return 0f
        if (left.contains(right) || right.contains(left)) return 0.86f
        val leftWords = meaningfulWords(left)
        val rightWords = meaningfulWords(right)
        if (leftWords.isEmpty() || rightWords.isEmpty()) return 0f
        val intersection = leftWords.intersect(rightWords).size.toFloat()
        val union = leftWords.union(rightWords).size.toFloat()
        return intersection / max(union, 1f)
    }

    private fun meaningfulWords(value: String): Set<String> =
        value.split(' ')
            .map { it.trim() }
            .filter { it.isNotBlank() && it !in STOP_WORDS }
            .toSet()

    private fun normalize(value: String): String = value
        .lowercase()
        .replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private companion object {
        const val MATCH_THRESHOLD = 0.82f
        const val REVIEW_THRESHOLD = 0.62f
        val STOP_WORDS = setOf("the", "a", "an", "and", "of", "feat", "ft", "with", "from")
    }
}
