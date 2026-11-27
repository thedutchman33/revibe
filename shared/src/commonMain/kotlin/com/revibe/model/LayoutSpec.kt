package com.revibe.model

/**
 * Width contract for the three-container desktop shell. Values are logical dp so
 * persisted settings survive DPI changes. The centre is not stored: it takes the
 * remaining space.
 */
object LayoutSpec {
    // Matches the measured Spotify window: 363 dp library, 353 dp right rail.
    const val LEFT_DEFAULT = 363f
    const val RIGHT_DEFAULT = 353f

    const val LEFT_MIN = 300f
    const val LEFT_MAX = 460f
    const val RIGHT_MIN = 280f
    const val RIGHT_MAX = 460f

    /** The collapsed library is still a usable icon rail, never zero-width. */
    const val LEFT_COLLAPSED = 72f

    /** Spotify's player band is 87 dp; the top bar is 63 dp. */
    const val PLAYER_HEIGHT = 88f
    const val TOP_BAR_HEIGHT = 64f
    const val CONTAINER_RADIUS = 8f

    /** Below this the centre route becomes unusable; side panels yield first. */
    const val CENTER_MIN = 480f

    /** Visual gutter and the full hit area for a drag handle. */
    const val GUTTER = 8f

    fun clampLeft(width: Float): Float = width.coerceIn(LEFT_MIN, LEFT_MAX)

    fun clampRight(width: Float): Float = width.coerceIn(RIGHT_MIN, RIGHT_MAX)
}
