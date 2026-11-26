package com.revibe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revibe.model.AlbumDetail
import com.revibe.model.ArtistDetail
import com.revibe.model.Track
import com.revibe.playback.PlayerState
import com.revibe.ui.AppViewModel
import com.revibe.ui.PlaylistDetail
import com.revibe.ui.components.Artwork
import com.revibe.ui.components.ButtonKind
import com.revibe.ui.components.CaptionLabel
import com.revibe.ui.components.EmptyState
import com.revibe.ui.components.HoverSurface
import com.revibe.ui.components.IconAction
import com.revibe.ui.components.MediaCard
import com.revibe.ui.components.Modal
import com.revibe.ui.components.RevibeButton
import com.revibe.ui.components.RevibeTextField
import com.revibe.ui.components.SectionHeader
import com.revibe.ui.components.SkeletonBlock
import com.revibe.ui.components.TrackRow
import com.revibe.ui.components.TrackTableHeader
import com.revibe.ui.components.gradientFor
import com.revibe.ui.toMediaItem
import com.revibe.ui.theme.RevibeColors
import com.revibe.ui.theme.RevibeType
import com.revibe.ui.theme.LocalAccent

@Composable
fun AlbumScreen(vm: AppViewModel) {
    val state by vm.playerState.collectAsState()
    val detail = vm.albumDetail
    when {
        vm.detailLoading -> DetailSkeleton()
        detail == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                title = "Album unavailable",
                message = vm.detailError ?: "This album could not be loaded.",
                action = "Go back",
                onAction = vm::goBack,
            )
        }
        else -> AlbumBody(vm, detail, state)
    }
}

@Composable
private fun AlbumBody(vm: AppViewModel, detail: AlbumDetail, state: PlayerState) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 16.dp, top = 6.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(LocalAccent.current.copy(alpha = 0.12f), Color.Transparent),
                        ),
                    )
                    .padding(bottom = 18.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Artwork(
                        url = detail.album.artworkUrl,
                        modifier = Modifier.size(196.dp),
                        cornerRadius = 8.dp,
                    )
                    Spacer(Modifier.width(24.dp))
                    Column(Modifier.weight(1f)) {
                        CaptionLabel("Album")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            detail.album.title,
                            style = RevibeType.hero.copy(fontSize = 38.sp, lineHeight = 42.sp),
                            color = RevibeColors.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                detail.album.artist,
                                style = RevibeType.bodyStrong,
                                color = RevibeColors.TextPrimary,
                                modifier = Modifier
                                    .clickable { vm.openArtist(detail.album.artistId) }
                                    .pointerHoverIcon(PointerIcon.Hand),
                            )
                            Text("•", style = RevibeType.caption, color = RevibeColors.TextTertiary)
                            Text(detail.album.year?.toString() ?: "—", style = RevibeType.caption, color = RevibeColors.TextSecondary)
                            Text("•", style = RevibeType.caption, color = RevibeColors.TextTertiary)
                            Text("${detail.tracks.size} songs", style = RevibeType.caption, color = RevibeColors.TextSecondary)
                            detail.album.genre?.let {
                                Text("•", style = RevibeType.caption, color = RevibeColors.TextTertiary)
                                Text(it, style = RevibeType.caption, color = RevibeColors.TextSecondary)
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            RevibeButton("Play", { vm.playAlbum(detail) }, icon = Icons.Rounded.PlayArrow)
                            RevibeButton(
                                "Shuffle",
                                { vm.playAlbum(detail, shuffled = true) },
                                kind = ButtonKind.Outline,
                                icon = Icons.Rounded.Shuffle,
                            )
                            IconAction(
                                Icons.Rounded.Favorite,
                                contentDescription = "Save album",
                                onClick = { vm.toggleLikedAlbum(detail.album) },
                                size = 38.dp,
                                iconSize = 20.dp,
                                tint = if (vm.isAlbumLiked(detail.album.id)) RevibeColors.Accent else RevibeColors.TextSecondary,
                            )
                        }
                    }
                }
            }
        }
        item {
            if (detail.tracks.any { it.isPreview }) {
                Text(
                    "Catalog tracks play as 30-second previews. Add the album's files to your local library for full playback.",
                    style = RevibeType.caption,
                    color = RevibeColors.TextTertiary,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            TrackTableHeader(showAlbum = false)
        }
        itemsIndexed(detail.tracks, key = { _, track -> track.id }) { index, track ->
            TrackRow(
                track = track,
                index = index + 1,
                isCurrent = state.current?.id == track.id,
                isPlaying = state.isPlaying,
                liked = vm.isLiked(track.id),
                onPlay = { vm.play(track, detail.tracks, detail.album.title) },
                onToggleLike = { vm.toggleLike(track) },
                onAddToPlaylist = { vm.addToPlaylistTrack = track },
                onAddToQueue = { vm.addToQueue(track) },
                onPlayNext = { vm.playNext(track) },
                onOpenArtist = { vm.openArtist(track.artistId) },
                onOpenAlbum = { vm.navigateToAlbumId(track.albumId) },
                showAlbum = false,
            )
        }
    }
}

@Composable
fun ArtistScreen(vm: AppViewModel) {
    val state by vm.playerState.collectAsState()
    val detail = vm.artistDetail
    when {
        vm.detailLoading -> DetailSkeleton()
        detail == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                title = "Artist unavailable",
                message = vm.detailError ?: "This artist could not be loaded.",
                action = "Go back",
                onAction = vm::goBack,
            )
        }
        else -> ArtistBody(vm, detail, state)
    }
}

@Composable
private fun ArtistBody(vm: AppViewModel, detail: ArtistDetail, state: PlayerState) {
    val followed = vm.isArtistFollowed(detail.artist.id)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 16.dp, top = 6.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(LocalAccent.current.copy(alpha = 0.12f), Color.Transparent)),
                    )
                    .padding(bottom = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Artwork(
                        detail.artist.imageUrl ?: detail.albums.firstOrNull()?.artworkUrl,
                        Modifier.size(188.dp),
                        cornerRadius = 94.dp,
                    )
                    Spacer(Modifier.width(26.dp))
                    Column(Modifier.weight(1f)) {
                        CaptionLabel("Artist")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            detail.artist.name,
                            style = RevibeType.hero.copy(fontSize = 44.sp, lineHeight = 48.sp),
                            color = RevibeColors.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        detail.artist.genre?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, style = RevibeType.caption, color = RevibeColors.TextSecondary)
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            RevibeButton(
                                text = if (followed) "Following" else "Follow",
                                onClick = { vm.toggleFollowArtist(detail.artist) },
                                kind = if (followed) ButtonKind.Outline else ButtonKind.Accent,
                            )
                            if (detail.topTracks.isNotEmpty()) {
                                RevibeButton(
                                    "Play",
                                    { vm.playArtistTop(detail.topTracks, detail.artist.name) },
                                    kind = ButtonKind.Outline,
                                    icon = Icons.Rounded.PlayArrow,
                                )
                            }
                        }
                    }
                }
            }
        }
        detail.bio?.let { bio ->
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(RevibeColors.Surface)
                        .padding(18.dp),
                ) {
                    CaptionLabel("About")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        bio,
                        style = RevibeType.body,
                        color = RevibeColors.TextSecondary,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Biography from Wikipedia, CC BY-SA 4.0", style = RevibeType.caption, color = RevibeColors.TextTertiary)
                }
            }
        }
        if (detail.topTracks.isNotEmpty()) {
            item {
                Column {
                    SectionHeader("Popular", subtitle = "Top matches in this storefront")
                    Spacer(Modifier.height(8.dp))
                    detail.topTracks.take(6).forEachIndexed { index, track ->
                        TrackRow(
                            track = track,
                            index = index + 1,
                            isCurrent = state.current?.id == track.id,
                            isPlaying = state.isPlaying,
                            liked = vm.isLiked(track.id),
                            onPlay = { vm.play(track, detail.topTracks, detail.artist.name) },
                            onToggleLike = { vm.toggleLike(track) },
                            onAddToPlaylist = { vm.addToPlaylistTrack = track },
                            onAddToQueue = { vm.addToQueue(track) },
                            onPlayNext = { vm.playNext(track) },
                            onOpenArtist = { vm.openArtist(track.artistId) },
                            onOpenAlbum = { vm.navigateToAlbumId(track.albumId) },
                            showArtwork = true,
                            showAlbum = false,
                        )
                    }
                }
            }
        }
        if (detail.albums.isNotEmpty()) {
            item {
                Column {
                    SectionHeader("Discography")
                    Spacer(Modifier.height(14.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(detail.albums, key = { it.id }) { album ->
                            MediaCard(
                                item = album.toMediaItem(),
                                onOpen = { vm.openAlbum(album) },
                                onPlay = { vm.playAlbumById(album.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistScreen(vm: AppViewModel) {
    val state by vm.playerState.collectAsState()
    val detail = vm.playlistDetail
    var menuOpen by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var newName by remember(detail?.playlist?.name) { mutableStateOf(detail?.playlist?.name.orEmpty()) }

    Box(Modifier.fillMaxSize()) {
        when {
            vm.detailLoading -> DetailSkeleton()
            detail == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "Playlist unavailable",
                    message = vm.detailError ?: "This playlist no longer exists.",
                    action = "Go back",
                    onAction = vm::goBack,
                )
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 16.dp, top = 6.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 20.dp)) {
                        Box(
                            Modifier
                                .size(196.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(gradientFor(detail.playlist.id)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.65f),
                                modifier = Modifier.size(64.dp),
                            )
                        }
                        Spacer(Modifier.width(24.dp))
                        Column(Modifier.weight(1f)) {
                            CaptionLabel("Playlist")
                            Spacer(Modifier.height(6.dp))
                            Text(
                                detail.playlist.name,
                                style = RevibeType.hero.copy(fontSize = 40.sp, lineHeight = 44.sp),
                                color = RevibeColors.TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${detail.tracks.size} songs · ${detail.playlist.description.ifBlank { "Made in Revibe" }}",
                                style = RevibeType.caption,
                                color = RevibeColors.TextSecondary,
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                RevibeButton("Play", { vm.playPlaylist(detail) }, icon = Icons.Rounded.PlayArrow)
                                RevibeButton(
                                    "Shuffle",
                                    { vm.playPlaylist(detail, shuffled = true) },
                                    kind = ButtonKind.Outline,
                                    icon = Icons.Rounded.Shuffle,
                                )
                                Box {
                                    IconAction(
                                        Icons.Rounded.MoreHoriz,
                                        contentDescription = "Playlist options",
                                        onClick = { menuOpen = true },
                                        size = 38.dp,
                                        iconSize = 20.dp,
                                    )
                                    DropdownMenu(
                                        expanded = menuOpen,
                                        onDismissRequest = { menuOpen = false },
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(RevibeColors.SurfaceRaised),
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Rename playlist", style = RevibeType.label, color = RevibeColors.TextPrimary) },
                                            onClick = { menuOpen = false; newName = detail.playlist.name; showRename = true },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete playlist", style = RevibeType.label, color = RevibeColors.Danger) },
                                            onClick = { menuOpen = false; showDelete = true },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (detail.tracks.isEmpty()) {
                    item {
                        EmptyState(
                            title = "This playlist is empty",
                            message = "Search for something you like, then use the track menu to add it here.",
                            action = "Find music",
                            onAction = { vm.openSearch() },
                        )
                    }
                } else {
                    item { TrackTableHeader(showAlbum = true) }
                    itemsIndexed(detail.tracks, key = { _, track -> track.id }) { index, track ->
                        TrackRow(
                            track = track,
                            index = index + 1,
                            isCurrent = state.current?.id == track.id,
                            isPlaying = state.isPlaying,
                            liked = vm.isLiked(track.id),
                            onPlay = { vm.play(track, detail.tracks, detail.playlist.name) },
                            onToggleLike = { vm.toggleLike(track) },
                            onAddToPlaylist = { vm.addToPlaylistTrack = track },
                            onAddToQueue = { vm.addToQueue(track) },
                            onPlayNext = { vm.playNext(track) },
                            onOpenArtist = { vm.openArtist(track.artistId) },
                            onOpenAlbum = { vm.navigateToAlbumId(track.albumId) },
                            onRemove = { vm.removeFromPlaylist(detail.playlist.id, track) },
                        )
                    }
                }
            }
        }

        if (showRename) {
            Modal(onDismiss = { showRename = false }) {
                Column {
                    Text("Rename playlist", style = RevibeType.heading, color = RevibeColors.TextPrimary)
                    Spacer(Modifier.height(14.dp))
                    RevibeTextField(newName, { newName = it }, placeholder = "Playlist name")
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.align(Alignment.End)) {
                        RevibeButton("Cancel", { showRename = false }, kind = ButtonKind.Ghost)
                        RevibeButton("Save", {
                            vm.renamePlaylist(detail?.playlist?.id.orEmpty(), newName)
                            showRename = false
                        })
                    }
                }
            }
        }
        if (showDelete) {
            Modal(onDismiss = { showDelete = false }) {
                Column {
                    Text("Delete this playlist?", style = RevibeType.heading, color = RevibeColors.TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "The playlist will be removed from your library. The music itself is not deleted.",
                        style = RevibeType.body,
                        color = RevibeColors.TextSecondary,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.align(Alignment.End)) {
                        RevibeButton("Cancel", { showDelete = false }, kind = ButtonKind.Ghost)
                        RevibeButton("Delete", { showDelete = false; detail?.let { vm.deletePlaylist(it.playlist.id) } })
                    }
                }
            }
        }
    }
}

@Composable
fun LikedScreen(vm: AppViewModel) {
    val state by vm.playerState.collectAsState()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 16.dp, top = 6.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 20.dp)) {
                Box(
                    Modifier
                        .size(196.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF6C63FF), LocalAccent.current))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = RevibeColors.OnAccent, modifier = Modifier.size(70.dp))
                }
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f)) {
                    CaptionLabel("Playlist")
                    Spacer(Modifier.height(6.dp))
                    Text("Liked Songs", style = RevibeType.hero.copy(fontSize = 42.sp, lineHeight = 46.sp), color = RevibeColors.TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text("${vm.likedTracks.size} songs", style = RevibeType.caption, color = RevibeColors.TextSecondary)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RevibeButton("Play", { vm.playLiked() }, icon = Icons.Rounded.PlayArrow)
                        RevibeButton("Shuffle", { vm.playLiked(shuffled = true) }, kind = ButtonKind.Outline, icon = Icons.Rounded.Shuffle)
                    }
                }
            }
        }
        if (vm.likedTracks.isEmpty()) {
            item {
                EmptyState(
                    title = "No liked songs yet",
                    message = "Tap the heart on any track and it will appear here.",
                    action = "Find music",
                    onAction = { vm.openSearch() },
                )
            }
        } else {
            item { TrackTableHeader(showAlbum = true) }
            itemsIndexed(vm.likedTracks, key = { _, track -> track.id }) { index, track ->
                TrackRow(
                    track = track,
                    index = index + 1,
                    isCurrent = state.current?.id == track.id,
                    isPlaying = state.isPlaying,
                    liked = true,
                    onPlay = { vm.play(track, vm.likedTracks, "Liked Songs") },
                    onToggleLike = { vm.toggleLike(track) },
                    onAddToPlaylist = { vm.addToPlaylistTrack = track },
                    onAddToQueue = { vm.addToQueue(track) },
                    onPlayNext = { vm.playNext(track) },
                    onOpenArtist = { vm.openArtist(track.artistId) },
                    onOpenAlbum = { vm.navigateToAlbumId(track.albumId) },
                )
            }
        }
    }
}

@Composable
fun DetailSkeleton() {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row {
            SkeletonBlock(Modifier.size(196.dp))
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonBlock(Modifier.width(60.dp).height(11.dp))
                SkeletonBlock(Modifier.width(320.dp).height(34.dp))
                SkeletonBlock(Modifier.width(180.dp).height(13.dp))
                Spacer(Modifier.height(10.dp))
                SkeletonBlock(Modifier.width(150.dp).height(38.dp))
            }
        }
        Spacer(Modifier.height(30.dp))
        repeat(7) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(Modifier.width(20.dp).height(11.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    SkeletonBlock(Modifier.width(200.dp).height(12.dp))
                    Spacer(Modifier.height(6.dp))
                    SkeletonBlock(Modifier.width(130.dp).height(10.dp))
                }
                SkeletonBlock(Modifier.width(36.dp).height(10.dp))
            }
        }
    }
}
