package com.revibe.provider

/**
 * Shared text matching for artwork providers. Catalogue services format the
 * same song differently — "Song (From \"Film\")", "Song | Official Video",
 * "Artist A, Artist B & Artist C" — so matching compares folded text rather
 * than raw strings. A wrong cover is worse than no cover, so title and artist
 * must both survive these checks.
 */
internal fun normalizeArtworkText(value: String): String = value
    .lowercase()
    .replace(COMBINING_MARKS, "")
    .replace(NON_ALPHANUMERIC, " ")
    .replace(WHITESPACE, " ")
    .trim()

/** Titles drop video/edition qualifiers that catalogues add inconsistently. */
internal fun normalizeArtworkTitle(value: String): String {
    var text = value.substringBefore(" | ")
    text = text.replace(TITLE_QUALIFIERS, " ")
    return normalizeArtworkText(text)
}

internal fun artworkTitlesMatch(wanted: String, returned: String): Boolean {
    val a = normalizeArtworkTitle(wanted)
    val b = normalizeArtworkTitle(returned)
    if (a.isBlank() || b.isBlank()) return false
    // Exact after folding. Containment would accept a remix or a different
    // edition that merely shares the base title, which is the mismatch this
    // matcher exists to prevent.
    return a == b
}

internal fun splitArtworkArtists(raw: String): List<String> = raw
    .split(ARTIST_SEPARATORS)
    .map(::normalizeArtworkText)
    .filter { it.isNotBlank() }
    .distinct()

/**
 * Every artist we asked for must appear in the returned credit. A provider
 * returning a different featured artist is still the same release; a provider
 * returning only someone else is not.
 */
internal fun artworkArtistsMatch(wanted: String, returned: String): Boolean {
    val wantedArtists = splitArtworkArtists(wanted)
    val returnedArtists = splitArtworkArtists(returned)
    if (wantedArtists.isEmpty() || returnedArtists.isEmpty()) return false
    return wantedArtists.all { want -> returnedArtists.any { it == want } }
}

internal fun artworkAlbumsMatch(wanted: String?, returned: String?): Boolean {
    if (wanted.isNullOrBlank() || returned.isNullOrBlank()) return false
    val a = normalizeArtworkText(wanted)
    val b = normalizeArtworkText(returned)
    return a == b || a.contains(b) || b.contains(a)
}

internal fun hasUsableArtworkMetadata(query: ArtworkQuery): Boolean =
    query.artist.isNotBlank() &&
        !query.artist.equals("Unknown artist", ignoreCase = true) &&
        query.title.isNotBlank()

private val COMBINING_MARKS = Regex("\\p{M}+")
private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
private val WHITESPACE = Regex("\\s+")

private val TITLE_QUALIFIERS = Regex(
    """\s*[\(\[](?:from|official|lyrical|video|audio|music video|full song|feat\.?|ft\.?|featuring|with)\b[^)\]]*[\)\]]""",
    RegexOption.IGNORE_CASE,
)

private val ARTIST_SEPARATORS = Regex(
    """(?:\s*,\s*|\s*&\s*|\s+×\s+|\s+x\s+|\bfeat\.?\b|\bft\.?\b|\bfeaturing\b|\bwith\b)""",
    RegexOption.IGNORE_CASE,
)
