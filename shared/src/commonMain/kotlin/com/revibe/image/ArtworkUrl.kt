package com.revibe.image

/**
 * YouTube Music serves artwork as Google image URLs (`=w544-h544-l90-rj`) and
 * video thumbnails as fixed renditions. Both need a canonical high-resolution
 * form so cards, the player and the right rail never ask for a thumbnail-sized
 * image.
 */
fun highResolutionArtworkUrl(url: String): String = when {
    isYouTubeVideoArtwork(url) -> YOUTUBE_VIDEO_ART.replace(url) {
        "${it.groupValues[1]}maxresdefault${it.groupValues[3]}"
    }
    GOOGLE_SIZE.containsMatchIn(url) -> GOOGLE_SIZE.replaceFirst(url, "=w1200-h1200")
    else -> url
}

/**
 * Palette extraction wants a small, bar-free image. `mqdefault` is the YouTube
 * rendition without baked-in letterboxing; `s256` is enough for a colour seed.
 */
fun artworkColorSourceUrl(url: String): String = when {
    isYouTubeVideoArtwork(url) -> YOUTUBE_VIDEO_ART.replace(url) {
        "${it.groupValues[1]}mqdefault${it.groupValues[3]}"
    }
    GOOGLE_SIZE.containsMatchIn(url) -> GOOGLE_SIZE.replaceFirst(url, "=w256-h256")
    else -> url
}

/** True when the URL is a fixed YouTube video frame rather than release art. */
fun isYouTubeVideoArtwork(url: String): Boolean = YOUTUBE_VIDEO_ART.containsMatchIn(url)

/**
 * Fallback renditions to try when a promoted `maxresdefault` image does not
 * exist. `mqdefault` is deliberately last: it is small but has no black bars.
 */
fun artworkLoadCandidates(url: String): List<String> = buildList {
    add(url)
    if (isYouTubeVideoArtwork(url)) {
        add(YOUTUBE_VIDEO_ART.replace(url) { "${it.groupValues[1]}hqdefault${it.groupValues[3]}" })
        add(YOUTUBE_VIDEO_ART.replace(url) { "${it.groupValues[1]}mqdefault${it.groupValues[3]}" })
    }
}.distinct()

private val YOUTUBE_VIDEO_ART = Regex(
    "(/vi(?:_webp)?/[^/?#]+/)(mqdefault|hqdefault|sddefault|hq720|maxresdefault)(\\.(?:jpg|webp))",
    RegexOption.IGNORE_CASE,
)

private val GOOGLE_SIZE = Regex("=(?:s\\d+|w\\d+-h\\d+)(?:-[^?#=]+)*")
