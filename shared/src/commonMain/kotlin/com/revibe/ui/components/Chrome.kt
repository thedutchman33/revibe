package com.revibe.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revibe.model.Playlist
import com.revibe.model.Track
import com.revibe.model.formatDuration
import com.revibe.playback.PlayerState
import com.revibe.playback.RepeatMode
import com.revibe.ui.AppViewModel
import com.revibe.ui.RightTab
import com.revibe.ui.Route
import com.revibe.ui.theme.RevibeColors
import com.revibe.ui.theme.RevibeType

@Composable
fun RevibeLogo(
    size: Dp = 30.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Brush.linearGradient(listOf(RevibeColors.Accent, Color(0xFF6FE3B0)))),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(size * 0.09f), verticalAlignment = Alignment.CenterVertically) {
            listOf(0.36f, 0.6f, 0.46f).forEach { fraction ->
                Box(
                    Modifier
                        .width(size * 0.11f)
                        .height(size * fraction)
                        .clip(RoundedCornerShape(size * 0.06f))
                        .background(RevibeColors.OnAccent),
                )
            }
        }
    }
}

@Composable
fun TopBar(
    vm: AppViewModel,
    windowControls: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(RevibeColors.Base)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconAction(
                Icons.Rounded.KeyboardArrowLeft,
                contentDescription = "Back",
                onClick = vm::goBack,
                enabled = vm.backStack.isNotEmpty(),
                size = 32.dp,
                iconSize = 22.dp,
            )
            IconAction(
                Icons.Rounded.KeyboardArrowRight,
                contentDescription = "Forward",
                onClick = vm::goForward,
                enabled = vm.forwardStack.isNotEmpty(),
                size = 32.dp,
                iconSize = 22.dp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (vm.route == Route.Home) RevibeColors.SurfaceRaised else RevibeColors.Surface)
                .clickable { vm.openHome() }
                .pointerHoverIcon(PointerIcon.Hand),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Home,
                contentDescription = "Home",
                tint = if (vm.route == Route.Home) RevibeColors.TextPrimary else RevibeColors.TextSecondary,
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.width(18.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            SearchField(
                value = vm.searchQuery,
                onValueChange = vm::onSearchQueryChange,
                onSubmit = { vm.submitSearch() },
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(16.dp))
        IconAction(
            Icons.Rounded.Settings,
            contentDescription = "Settings",
            onClick = vm::openSettings,
            size = 34.dp,
            iconSize = 19.dp,
        )
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(RevibeColors.SurfaceRaised)
                .clickable { vm.openSettings() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Person, contentDescription = "Profile", tint = RevibeColors.TextSecondary, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(10.dp))
        windowControls()
    }
}

@Composable
fun Sidebar(vm: AppViewModel) {
    val compact = vm.compactSidebar
    val width = if (compact) 76.dp else 288.dp
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(RevibeColors.Sidebar)
            .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RevibeLogo(size = 28.dp)
            if (!compact) {
                Spacer(Modifier.width(10.dp))
                Text(
                    "REVIBE",
                    style = RevibeType.micro.copy(fontSize = 13.sp, letterSpacing = 2.6.sp),
                    color = RevibeColors.TextPrimary,
                )
            }
            Spacer(Modifier.weight(1f))
            IconAction(
                icon = if (compact) Icons.Rounded.KeyboardArrowRight else Icons.Rounded.KeyboardArrowLeft,
                contentDescription = if (compact) "Expand sidebar" else "Collapse sidebar",
                onClick = vm::toggleCompactSidebar,
                size = 30.dp,
                iconSize = 18.dp,
            )
        }

        Spacer(Modifier.height(10.dp))
        SidebarNavItem(
            icon = Icons.Rounded.Home,
            label = "Home",
            selected = vm.route == Route.Home,
            compact = compact,
            onClick = vm::openHome,
        )
        Spacer(Modifier.height(2.dp))
        SidebarNavItem(
            icon = Icons.Rounded.Search,
            label = "Search",
            selected = vm.route == Route.Search,
            compact = compact,
            onClick = { vm.openSearch() },
        )

        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth().height(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!compact) {
                Text(
                    "Your Library",
                    style = RevibeType.title,
                    color = RevibeColors.TextPrimary,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            IconAction(
                Icons.Rounded.Add,
                contentDescription = "Create playlist",
                onClick = { vm.showCreatePlaylist = true },
                size = 30.dp,
                iconSize = 19.dp,
            )
        }

        if (!compact) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("All", "Playlists", "Albums", "Artists")) { label ->
                    Chip(
                        text = label,
                        selected = libraryTabFor(vm.libraryTab) == label,
                        onClick = { vm.selectLibraryTab(labelToTab(label)) },
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            item {
                LikedSongsRow(
                    selected = vm.route == Route.Liked,
                    compact = compact,
                    onClick = vm::openLiked,
                )
            }
            if (vm.libraryTab == 0 || vm.libraryTab == 1) {
                items(vm.playlists, key = { "pl-${it.id}" }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        selected = vm.route is Route.PlaylistRoute && (vm.route as Route.PlaylistRoute).id == playlist.id,
                        compact = compact,
                        onClick = { vm.openPlaylist(playlist.id) },
                    )
                }
            }
            if (vm.libraryTab == 0 || vm.libraryTab == 2) {
                items(vm.likedAlbums, key = { "al-${it.id}" }) { album ->
                    LibraryRow(
                        title = album.title,
                        subtitle = "Album · ${album.artist}",
                        compact = compact,
                        onClick = { vm.openAlbum(album) },
                    ) {
                        Artwork(album.artworkUrl, Modifier.size(44.dp), cornerRadius = 4.dp)
                    }
                }
            }
            if (vm.libraryTab == 0 || vm.libraryTab == 3) {
                items(vm.followedArtists, key = { "ar-${it.id}" }) { artist ->
                    LibraryRow(
                        title = artist.name,
                        subtitle = "Artist",
                        compact = compact,
                        onClick = { vm.openArtist(artist.id) },
                    ) {
                        ArtistAvatar(artist.imageUrl, size = 44.dp)
                    }
                }
            }
            if (!compact) {
                item {
                    Spacer(Modifier.height(6.dp))
                    LibraryRow(
                        title = "Local files",
                        subtitle = "${vm.localTracks.size} tracks on this device",
                        compact = compact,
                        onClick = vm::openLocal,
                    ) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)).background(RevibeColors.SurfaceRaised),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = RevibeColors.TextSecondary, modifier = Modifier.size(19.dp))
                        }
                    }
                }
            }
        }

        HDivider(Modifier.padding(vertical = 8.dp))
        SidebarNavItem(
            icon = Icons.Rounded.Settings,
            label = "Settings",
            selected = vm.route == Route.Settings,
            compact = compact,
            onClick = vm::openSettings,
        )
    }
}

private fun libraryTabFor(tab: Int): String = when (tab) {
    1 -> "Playlists"
    2 -> "Albums"
    3 -> "Artists"
    else -> "All"
}

private fun labelToTab(label: String): Int = when (label) {
    "Playlists" -> 1
    "Albums" -> 2
    "Artists" -> 3
    else -> 0
}

@Composable
private fun SidebarNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(if (selected) RevibeColors.TextPrimary else RevibeColors.TextSecondary, label = "navTint")
    HoverSurface(
        modifier = Modifier.fillMaxWidth().height(42.dp),
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
    ) { hovered ->
        Row(
            Modifier
                .fillMaxSize()
                .background(if (selected) RevibeColors.SurfaceRaised else Color.Transparent)
                .padding(horizontal = if (compact) 12.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) RevibeColors.Accent else if (hovered) RevibeColors.TextPrimary else tint,
                modifier = Modifier.size(21.dp),
            )
            if (!compact) {
                Text(
                    label,
                    style = RevibeType.body.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                    color = tint,
                )
            }
        }
    }
}

@Composable
private fun LikedSongsRow(selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    HoverSurface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (selected) RevibeColors.SurfaceRaised else Color.Transparent)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF6C63FF), RevibeColors.Accent))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Favorite, contentDescription = null, tint = RevibeColors.OnAccent, modifier = Modifier.size(20.dp))
            }
            if (!compact) {
                Column {
                    Text("Liked Songs", style = RevibeType.bodyStrong, color = RevibeColors.TextPrimary)
                    Text("Playlist", style = RevibeType.caption, color = RevibeColors.TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(playlist: Playlist, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    LibraryRow(
        title = playlist.name,
        subtitle = "Playlist · ${playlist.trackCount} songs",
        compact = compact,
        selected = selected,
        onClick = onClick,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)).background(gradientFor(playlist.id)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = RevibeColors.OnAccent.copy(alpha = 0.85f), modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun LibraryRow(
    title: String,
    subtitle: String,
    compact: Boolean,
    onClick: () -> Unit,
    selected: Boolean = false,
    leading: @Composable () -> Unit,
) {
    HoverSurface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (selected) RevibeColors.SurfaceRaised else Color.Transparent)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading()
            if (!compact) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = RevibeType.bodyStrong, color = RevibeColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = RevibeType.caption, color = RevibeColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun PlayerBar(vm: AppViewModel) {
    val state by vm.playerState.collectAsState()
    val track = state.current
    val fraction = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f

    Column(
        Modifier
            .fillMaxWidth()
            .height(94.dp)
            .background(RevibeColors.PlayerBar),
    ) {
        HDivider(color = RevibeColors.OutlineSoft)
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.width(280.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(
                    url = track?.artworkUrl ?: track?.localPath,
                    modifier = Modifier.size(54.dp),
                    cornerRadius = 6.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    if (track != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                track.title,
                                style = RevibeType.bodyStrong,
                                color = RevibeColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (state.previewOnly) MiniBadge("Preview")
                        }
                        Text(
                            track.artist,
                            style = RevibeType.caption,
                            color = RevibeColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clickable { vm.openArtist(track.artistId) }
                                .pointerHoverIcon(if (track.artistId != null) PointerIcon.Hand else PointerIcon.Default),
                        )
                    } else {
                        Text("Nothing playing", style = RevibeType.bodyStrong, color = RevibeColors.TextPrimary)
                        Text("Pick something from your library", style = RevibeType.caption, color = RevibeColors.TextTertiary)
                    }
                }
                if (track != null) {
                    IconAction(
                        icon = if (vm.isLiked(track.id)) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Like",
                        onClick = { vm.toggleLike(track) },
                        size = 30.dp,
                        iconSize = 16.dp,
                        tint = if (vm.isLiked(track.id)) RevibeColors.Accent else RevibeColors.TextTertiary,
                    )
                }
            }

            Column(
                Modifier.weight(1f).padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconAction(
                        Icons.Rounded.Shuffle,
                        "Shuffle",
                        onClick = { vm.toggleShuffle() },
                        size = 30.dp,
                        iconSize = 18.dp,
                        tint = if (state.shuffle) RevibeColors.Accent else RevibeColors.TextSecondary,
                        enabled = state.queue.isNotEmpty(),
                    )
                    IconAction(
                        Icons.Rounded.SkipPrevious,
                        "Previous",
                        onClick = { vm.previousTrack() },
                        size = 34.dp,
                        iconSize = 21.dp,
                        tint = RevibeColors.TextPrimary,
                        enabled = state.current != null,
                    )
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (track != null) RevibeColors.Accent else RevibeColors.SurfaceRaised)
                            .clickable(enabled = track != null) { vm.togglePlayPause() }
                            .pointerHoverIcon(PointerIcon.Hand),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.isBuffering && track != null) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = RevibeColors.OnAccent,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = if (track != null) RevibeColors.OnAccent else RevibeColors.TextTertiary,
                                modifier = Modifier.size(25.dp),
                            )
                        }
                    }
                    IconAction(
                        Icons.Rounded.SkipNext,
                        "Next",
                        onClick = { vm.nextTrack() },
                        size = 34.dp,
                        iconSize = 21.dp,
                        tint = RevibeColors.TextPrimary,
                        enabled = state.hasNext,
                    )
                    IconAction(
                        icon = if (state.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = "Repeat",
                        onClick = { vm.cycleRepeatMode() },
                        size = 30.dp,
                        iconSize = 18.dp,
                        tint = if (state.repeat != RepeatMode.OFF) RevibeColors.Accent else RevibeColors.TextSecondary,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatDuration(state.positionMs),
                        style = RevibeType.caption,
                        color = RevibeColors.TextTertiary,
                        modifier = Modifier.width(44.dp),
                    )
                    SeekBar(
                        value = fraction,
                        onValueChange = { vm.seekToFraction(it) },
                        modifier = Modifier.weight(1f),
                        enabled = track != null && state.durationMs > 0,
                    )
                    Text(
                        formatDuration(state.durationMs),
                        style = RevibeType.caption,
                        color = RevibeColors.TextTertiary,
                        modifier = Modifier.width(44.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }
            }

            Row(
                Modifier.width(280.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                IconAction(
                    Icons.Rounded.Mic,
                    contentDescription = "Lyrics",
                    onClick = { vm.selectRightTab(RightTab.LYRICS) },
                    size = 32.dp,
                    iconSize = 17.dp,
                    tint = if (vm.rightTab == RightTab.LYRICS && vm.rightPanelVisible) RevibeColors.Accent else RevibeColors.TextSecondary,
                )
                IconAction(
                    Icons.Rounded.QueueMusic,
                    contentDescription = "Queue",
                    onClick = { vm.selectRightTab(RightTab.QUEUE) },
                    size = 32.dp,
                    iconSize = 18.dp,
                    tint = if (vm.rightTab == RightTab.QUEUE && vm.rightPanelVisible) RevibeColors.Accent else RevibeColors.TextSecondary,
                )
                Spacer(Modifier.width(6.dp))
                IconAction(
                    if (state.volume == 0f) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                    contentDescription = "Mute",
                    onClick = { vm.setVolume(if (state.volume == 0f) 0.7f else 0f) },
                    size = 30.dp,
                    iconSize = 17.dp,
                )
                SeekBar(
                    value = state.volume,
                    onValueChange = { vm.setVolume(it) },
                    modifier = Modifier.width(96.dp),
                    trackHeight = 3.dp,
                )
                Spacer(Modifier.width(8.dp))
                IconAction(
                    Icons.Rounded.PlaylistAdd,
                    contentDescription = "Add to playlist",
                    onClick = { track?.let { vm.addToPlaylistTrack = it } },
                    size = 30.dp,
                    iconSize = 17.dp,
                    enabled = track != null,
                )
            }
        }
    }
}
