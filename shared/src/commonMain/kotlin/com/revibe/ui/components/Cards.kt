package com.revibe.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.revibe.model.Track
import com.revibe.model.formatDuration
import com.revibe.ui.MediaItem
import com.revibe.ui.theme.RevibeColors
import com.revibe.ui.theme.RevibeType

private val cardGradients = listOf(
    listOf(Color(0xFF4457C8), Color(0xFF7C4DFF)),
    listOf(Color(0xFF1E7A5C), Color(0xFF3AE0A8)),
    listOf(Color(0xFF8C3B2E), Color(0xFFE2683C)),
    listOf(Color(0xFF7A3D75), Color(0xFFD05CA8)),
    listOf(Color(0xFF2D5C7A), Color(0xFF57B6D6)),
    listOf(Color(0xFF6B5B1F), Color(0xFFD7B840)),
)

fun gradientFor(seed: String): Brush {
    if (seed.isEmpty()) return Brush.linearGradient(cardGradients[0])
    var hash = 0
    seed.forEach { char -> hash = hash * 31 + char.code }
    val index = ((hash % cardGradients.size) + cardGradients.size) % cardGradients.size
    return Brush.linearGradient(cardGradients[index])
}

@Composable
fun MediaCard(
    item: MediaItem,
    onOpen: () -> Unit,
    onPlay: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    cardWidth: Int = 168,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) 1.025f else 1f, label = "cardScale")

    Column(
        modifier
            .width(cardWidth.dp)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen)
            .pointerHoverIcon(PointerIcon.Hand),
    ) {
        Box {
            Artwork(
                url = item.imageUrl,
                modifier = Modifier
                    .size(cardWidth.dp)
                    .scale(scale),
                cornerRadius = if (item.round) (cardWidth / 2).dp else 6.dp,
            )
            if (hovered && onPlay != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-8).dp, y = (-8).dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(RevibeColors.Accent)
                        .clickable(onClick = onPlay)
                        .pointerHoverIcon(PointerIcon.Hand),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        tint = RevibeColors.OnAccent,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = item.title,
            style = RevibeType.title,
            color = RevibeColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 4.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = item.subtitle,
            style = RevibeType.caption,
            color = RevibeColors.TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 8.dp),
        )
    }
}

@Composable
fun ShelfRow(
    shelfId: String,
    title: String,
    items: List<MediaItem>,
    subtitle: String? = null,
    onOpenItem: (MediaItem) -> Unit,
    onPlayItem: ((MediaItem) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(end = 12.dp),
) {
    Column {
        SectionHeader(title = title, subtitle = subtitle)
        Spacer(Modifier.height(14.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = contentPadding,
        ) {
            items(items, key = { "$shelfId-${it.id}" }) { item ->
                MediaCard(
                    item = item,
                    onOpen = { onOpenItem(item) },
                    onPlay = onPlayItem?.let { play -> { play(item) } },
                )
            }
        }
    }
}

@Composable
fun TrackTableHeader(
    showAlbum: Boolean,
    modifier: Modifier = Modifier,
    endPadding: Int = 92,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("#", style = RevibeType.caption, color = RevibeColors.TextTertiary, modifier = Modifier.width(30.dp))
        Text("Title", style = RevibeType.caption, color = RevibeColors.TextTertiary, modifier = Modifier.weight(1f))
        if (showAlbum) {
            Text(
                "Album",
                style = RevibeType.caption,
                color = RevibeColors.TextTertiary,
                modifier = Modifier.weight(0.7f).padding(start = 16.dp),
            )
        }
        Text(
            "Time",
            style = RevibeType.caption,
            color = RevibeColors.TextTertiary,
            modifier = Modifier.width(endPadding.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
    HDivider(Modifier.padding(horizontal = 10.dp), RevibeColors.OutlineSoft)
}

@Composable
fun TrackRow(
    track: Track,
    index: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    liked: Boolean,
    onPlay: () -> Unit,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onOpenArtist: () -> Unit,
    onOpenAlbum: () -> Unit,
    modifier: Modifier = Modifier,
    showAlbum: Boolean = true,
    showArtwork: Boolean = false,
    onRemove: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(if (hovered) RevibeColors.SurfaceRaised else Color.Transparent)
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null, onClick = onPlay)
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(30.dp), contentAlignment = Alignment.CenterStart) {
                if (isCurrent && isPlaying) {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        contentDescription = "Now playing",
                        tint = RevibeColors.Accent,
                        modifier = Modifier.size(17.dp),
                    )
                } else {
                    Text(
                        text = index.toString(),
                        style = RevibeType.label,
                        color = if (isCurrent) RevibeColors.Accent else RevibeColors.TextTertiary,
                    )
                }
            }

            if (showArtwork) {
                Artwork(
                    url = track.artworkUrl ?: track.localPath,
                    modifier = Modifier.size(40.dp).padding(end = 12.dp),
                    cornerRadius = 4.dp,
                )
            }

            Column(Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = RevibeType.bodyStrong,
                    color = if (isCurrent) RevibeColors.Accent else RevibeColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = track.artist,
                        style = RevibeType.caption,
                        color = RevibeColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .clickable {
                                if (track.artistId != null) onOpenArtist()
                            }
                            .pointerHoverIcon(
                                if (track.artistId != null) PointerIcon.Hand else PointerIcon.Default,
                            ),
                    )
                    if (track.explicit) MiniBadge("E")
                    if (track.isPreview) MiniBadge("30s")
                }
            }

            if (showAlbum) {
                Text(
                    text = track.album,
                    style = RevibeType.caption,
                    color = RevibeColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(0.7f)
                        .padding(start = 16.dp, end = 12.dp)
                        .clickable { if (track.albumId != null) onOpenAlbum() }
                        .pointerHoverIcon(if (track.albumId != null) PointerIcon.Hand else PointerIcon.Default),
                )
            }

            Text(
                text = formatDuration(track.durationMs),
                style = RevibeType.caption,
                color = RevibeColors.TextTertiary,
                modifier = Modifier.width(46.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )

            Spacer(Modifier.width(4.dp))
            IconAction(
                icon = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (liked) "Remove from Liked Songs" else "Add to Liked Songs",
                onClick = onToggleLike,
                size = 30.dp,
                iconSize = 16.dp,
                tint = if (liked) RevibeColors.Accent else RevibeColors.TextTertiary,
                hoverTint = RevibeColors.TextPrimary,
            )
            Box {
                IconAction(
                    icon = Icons.Rounded.MoreHoriz,
                    contentDescription = "More options",
                    onClick = { menuOpen = true },
                    size = 30.dp,
                    iconSize = 18.dp,
                    tint = RevibeColors.TextTertiary,
                )
                TrackMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    liked = liked,
                    showGoToAlbum = track.albumId != null,
                    showGoToArtist = track.artistId != null,
                    onToggleLike = { menuOpen = false; onToggleLike() },
                    onAddToQueue = { menuOpen = false; onAddToQueue() },
                    onPlayNext = { menuOpen = false; onPlayNext() },
                    onAddToPlaylist = { menuOpen = false; onAddToPlaylist() },
                    onOpenAlbum = { menuOpen = false; onOpenAlbum() },
                    onOpenArtist = { menuOpen = false; onOpenArtist() },
                    onRemove = onRemove?.let { remove -> { menuOpen = false; remove() } },
                )
            }
        }
    }
}

@Composable
fun TrackMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    liked: Boolean,
    showGoToAlbum: Boolean,
    showGoToArtist: Boolean,
    onToggleLike: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenAlbum: () -> Unit,
    onOpenArtist: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(RevibeColors.SurfaceRaised),
    ) {
        MenuRow(if (liked) "Remove from Liked Songs" else "Save to Liked Songs", onToggleLike)
        MenuRow("Add to playlist", onAddToPlaylist, Icons.Rounded.PlaylistAdd)
        MenuRow("Add to queue", onAddToQueue, Icons.Rounded.AddCircleOutline)
        MenuRow("Play next", onPlayNext, Icons.Rounded.QueueMusic)
        if (showGoToAlbum) MenuRow("Go to album", onOpenAlbum)
        if (showGoToArtist) MenuRow("Go to artist", onOpenArtist)
        onRemove?.let { MenuRow("Remove from playlist", it) }
    }
}

@Composable
private fun MenuRow(
    label: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    DropdownMenuItem(
        text = { Text(label, style = RevibeType.label, color = RevibeColors.TextPrimary) },
        leadingIcon = icon?.let { vector ->
            { Icon(vector, contentDescription = null, tint = RevibeColors.TextSecondary, modifier = Modifier.size(18.dp)) }
        },
        onClick = onClick,
    )
}

/** Compact row used by the queue panel. */
@Composable
fun QueueRow(
    track: Track,
    index: Int,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onPlay)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Artwork(track.artworkUrl ?: track.localPath, Modifier.size(38.dp), cornerRadius = 4.dp)
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = RevibeType.caption.copy(fontWeight = FontWeight.SemiBold),
                color = if (isCurrent) RevibeColors.Accent else RevibeColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                style = RevibeType.caption,
                color = RevibeColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(formatDuration(track.durationMs), style = RevibeType.caption, color = RevibeColors.TextTertiary)
        IconAction(
            icon = Icons.Rounded.Close,
            contentDescription = "Remove from queue",
            onClick = onRemove,
            size = 26.dp,
            iconSize = 14.dp,
            tint = RevibeColors.TextTertiary,
        )
    }
}
