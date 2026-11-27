package com.revibe.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revibe.model.formatDuration
import com.revibe.playback.PlayerState
import com.revibe.playback.RepeatMode
import com.revibe.ui.AppViewModel
import com.revibe.ui.components.Artwork
import com.revibe.ui.components.IconAction
import com.revibe.ui.components.SeekBar
import com.revibe.ui.theme.RevibeColors
import com.revibe.ui.theme.RevibeType
import java.awt.Cursor
import java.awt.Window

/**
 * Spotify-style miniplayer: a compact always-on-top card with the album art as the
 * hero, transport controls that fade in over it, a seekable progress line and the
 * track identity below. It is the same AppViewModel and engine as the main window.
 */
@Composable
fun MiniPlayer(vm: AppViewModel, window: Window) {
    val state by vm.playerState.collectAsState()
    val track = state.current
    val liked = track != null && vm.isLiked(track.id)
    val shape = RoundedCornerShape(12.dp)

    Box(Modifier.fillMaxSize().padding(10.dp)) {
        Column(
            Modifier
                .fillMaxSize()
                .shadow(18.dp, shape, clip = false)
                .clip(shape)
                .background(Color(0xFF121212))
                .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
                .moveWindowOnDrag(window),
        ) {
            MiniHeader(onClose = vm::exitMiniplayer)

            BoxWithConstraints(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 18.dp),
            ) {
                val artSize = minOf(maxWidth * 0.56f, maxHeight - 92.dp).coerceAtLeast(120.dp)
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(4.dp))
                    ArtworkStage(vm = vm, state = state, artSize = artSize)
                    Spacer(Modifier.height(4.dp))
                    ProgressBlock(state = state, onSeek = vm::seekToFraction)
                    Spacer(Modifier.height(4.dp))
                    TrackInfo(
                        title = track?.title,
                        artist = track?.artist,
                        liked = liked,
                        onLike = { track?.let(vm::toggleLike) },
                        onClose = vm::exitMiniplayer,
                    )
                }
            }
        }

        ResizeGrip(window, Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun MiniHeader(onClose: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(26.dp).padding(horizontal = 6.dp)) {
        // Spotify's two-row grab handle. Dragging anywhere on the card moves the window.
        Canvas(Modifier.align(Alignment.Center).size(16.dp, 8.dp)) {
            val color = RevibeColors.TextTertiary.copy(alpha = 0.8f)
            val radius = 1.25.dp.toPx()
            val gapX = 6.dp.toPx()
            val stepY = 4.dp.toPx()
            for (row in 0..1) {
                for (column in 0..2) {
                    drawCircle(
                        color = color,
                        radius = radius,
                        center = Offset(2.dp.toPx() + column * gapX, 2.dp.toPx() + row * stepY),
                    )
                }
            }
        }
        IconAction(
            Icons.Rounded.Close,
            contentDescription = "Close miniplayer",
            onClick = onClose,
            modifier = Modifier.align(Alignment.CenterEnd),
            size = 26.dp,
            iconSize = 14.dp,
            tint = RevibeColors.TextTertiary,
            hoverTint = RevibeColors.TextPrimary,
        )
    }
}

@Composable
private fun ArtworkStage(vm: AppViewModel, state: PlayerState, artSize: androidx.compose.ui.unit.Dp) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val track = state.current

    Box(
        Modifier.fillMaxWidth().height(artSize).hoverable(interaction),
        contentAlignment = Alignment.Center,
    ) {
        Artwork(
            url = vm.currentArtworkUrl ?: track?.localPath,
            modifier = Modifier.size(artSize),
            cornerRadius = 8.dp,
        )

        if (hovered) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.38f)),
                        ),
                    ),
            )

            // Controls use the gutters on both sides of the artwork like Spotify does.
            Row(
                Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconAction(
                    if (state.volume == 0f) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                    contentDescription = if (state.volume == 0f) "Unmute" else "Mute",
                    onClick = { vm.setVolume(if (state.volume == 0f) 0.7f else 0f) },
                    size = 26.dp,
                    iconSize = 16.dp,
                    tint = Color.White,
                    hoverTint = Color.White,
                )
                SeekBar(
                    value = state.volume,
                    onValueChange = { vm.setVolume(it) },
                    modifier = Modifier.width(64.dp),
                    accent = RevibeColors.Accent,
                    trackHeight = 3.dp,
                    trackColor = Color.White.copy(alpha = 0.22f),
                )
            }

            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(enabled = track != null) { vm.togglePlayPause() }
                    .pointerHoverIcon(PointerIcon.Hand),
                contentAlignment = Alignment.Center,
            ) {
                if (state.isBuffering && track != null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color(0xFF0A0A0A),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = Color(0xFF0A0A0A),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Row(
                Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                IconAction(
                    Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    onClick = vm::nextTrack,
                    size = 28.dp,
                    iconSize = 20.dp,
                    tint = Color.White,
                    hoverTint = Color.White,
                )
                IconAction(
                    if (state.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    contentDescription = "Repeat",
                    onClick = vm::cycleRepeatMode,
                    size = 28.dp,
                    iconSize = 18.dp,
                    tint = if (state.repeat != RepeatMode.OFF) RevibeColors.Accent else Color.White,
                    hoverTint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ProgressBlock(state: PlayerState, onSeek: (Float) -> Unit) {
    val fraction = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(Modifier.fillMaxWidth()) {
        SeekBar(
            value = fraction,
            onValueChange = onSeek,
            modifier = Modifier.fillMaxWidth(),
            accent = Color.White,
            trackHeight = 3.dp,
            trackColor = Color.White.copy(alpha = 0.24f),
            enabled = state.durationMs > 0,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(state.positionMs), style = RevibeType.playerTime, color = RevibeColors.PlayerTime)
            Text(formatDuration(state.durationMs), style = RevibeType.playerTime, color = RevibeColors.PlayerTime)
        }
    }
}

@Composable
private fun TrackInfo(
    title: String?,
    artist: String?,
    liked: Boolean,
    onLike: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title ?: "Nothing playing",
                style = RevibeType.title.copy(
                    fontSize = 21.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                ),
                color = RevibeColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                artist ?: "Choose a track in the main window",
                style = RevibeType.caption.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = RevibeColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconAction(
            Icons.Rounded.Close,
            contentDescription = "Close miniplayer",
            onClick = onClose,
            size = 28.dp,
            iconSize = 17.dp,
            tint = RevibeColors.TextSecondary,
            hoverTint = RevibeColors.TextPrimary,
        )
        IconAction(
            if (liked) Icons.Outlined.CheckCircle else Icons.Outlined.AddCircleOutline,
            contentDescription = if (liked) "Remove from Liked Songs" else "Add to Liked Songs",
            onClick = onLike,
            size = 28.dp,
            iconSize = 20.dp,
            tint = if (liked) RevibeColors.Accent else RevibeColors.TextSecondary,
            hoverTint = RevibeColors.TextPrimary,
        )
    }
}

@Composable
private fun ResizeGrip(window: Window, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(6.dp)
            .size(14.dp)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.SE_RESIZE_CURSOR)))
            .resizeWindowOnDrag(
                window,
                WindowResizeEdge.BottomRight,
                minWidth = 356,
                minHeight = 300,
                maxWidth = 680,
                maxHeight = 620,
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val color = Color(0xFF5A5A5A)
            drawLine(
                color,
                Offset(size.width * 0.45f, size.height),
                Offset(size.width, size.height * 0.45f),
                strokeWidth = 1.dp.toPx(),
            )
            drawLine(
                color,
                Offset(size.width * 0.75f, size.height),
                Offset(size.width, size.height * 0.75f),
                strokeWidth = 1.dp.toPx(),
            )
        }
    }
}
