package com.revibe.image

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

data class LoadedArtwork(
    val image: ImageBitmap,
    val dominantColor: Color,
    val secondaryColor: Color,
)

/**
 * Image loading is platform specific (Skia on desktop, Coil on Android).
 * Returning the palette alongside the bitmap lets the UI theme itself from the
 * current artwork without a second decode.
 */
interface ArtworkLoader {
    suspend fun load(url: String?, sizePx: Int = 600): LoadedArtwork?
}

object ArtworkPaletteDefaults {
    val dominant = Color(0xFF2A3134)
    val secondary = Color(0xFF171B1D)
}
