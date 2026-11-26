package com.revibe.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.revibe.image.ArtworkLoader
import com.revibe.image.LoadedArtwork
import com.revibe.ui.theme.RevibeColors

val LocalArtworkLoader = staticCompositionLocalOf<ArtworkLoader> {
    error("ArtworkLoader was not provided")
}

private val placeholderGradient = Brush.linearGradient(
    listOf(Color(0xFF2A3031), Color(0xFF1A1E1F), Color(0xFF242A2B)),
)

@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    contentScale: ContentScale = ContentScale.Crop,
    onArtworkLoaded: (LoadedArtwork) -> Unit = {},
) {
    val loader = LocalArtworkLoader.current
    var artwork by remember(url) { mutableStateOf<LoadedArtwork?>(null) }

    LaunchedEffect(url) {
        artwork = if (url.isNullOrBlank()) null else loader.load(url)
        artwork?.let(onArtworkLoaded)
    }

    Box(
        modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(placeholderGradient),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = artwork, label = "artwork") { loaded ->
            if (loaded != null) {
                Image(
                    bitmap = loaded.image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                )
            } else {
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = RevibeColors.TextTertiary.copy(alpha = 0.55f),
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}

@Composable
fun ArtistAvatar(
    url: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    Box(modifier.size(size).clip(RoundedCornerShape(percent = 50))) {
        Artwork(url, Modifier.fillMaxSize(), cornerRadius = size / 2)
    }
}
