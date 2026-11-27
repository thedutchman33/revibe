package com.revibe.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sort
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revibe.model.AlbumDetail
import com.revibe.model.ArtistDetail
import com.revibe.model.PlaylistTrackEntry
import com.revibe.model.Track
import com.revibe.model.formatDuration
import com.revibe.platform.formatAddedDate
import com.revibe.playback.PlayerState
import com.revibe.ui.AppViewModel
import com.revibe.ui.PlaylistDetail
import com.revibe.ui.components.Artwork
import com.revibe.ui.components.ButtonKind
import com.revibe.ui.components.CaptionLabel
import com.revibe.ui.components.EmptyState
import com.revibe.ui.components.HDivider
import com.revibe.ui.components.HoverSurface
import com.revibe.ui.components.IconAction
import com.revibe.ui.components.MediaCard
import com.revibe.ui.components.Modal
import com.revibe.ui.components.RevibeButton
import com.revibe.ui.components.RevibeTextField
import com.revibe.ui.components.SectionHeader
import com.revibe.ui.components.SkeletonBlock
import com.revibe.ui.components.TrackMenu
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
                        CaptionLabel(if (detail.album.isPlaylist) "Playlist" else "Album")
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
                            if (detail.album.isPlaylist) {
                                RevibeButton(
                                    if (vm.importingPlaylist) "Importing…" else "Import to library",
                                    { vm.importYouTubePlaylist(detail) },
                                    kind = ButtonKind.Outline,
                                )
                            }
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
            if (detail.tracks.any { it.isArchived }) {
                Text(
                    "Some tracks were saved by an older Revibe build from a retired catalog. They stay in your library but can't be played; search the catalog to add them again.",
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
                onPlay = { vm.play(track, detail.tracks, detail.album.title, albumId = detail.album.id) },
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

private enum class PlaylistSort(val label: String) {
    CUSTOM("Custom order"),
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    RECENTLY_ADDED("Recently added"),
}

@Composable
fun PlaylistScreen(vm: AppViewModel) {
    val detail = vm.playlistDetail
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var newName by remember(detail?.playlist?.name) { mutableStateOf(detail?.playlist?.name.orEmpty()) }
    var newDescription by remember(detail?.playlist?.description) { mutableStateOf(detail?.playlist?.description.orEmpty()) }

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
            else -> PlaylistBody(
                vm = vm,
                detail = detail,
                onRename = {
                    newName = detail.playlist.name
                    newDescription = detail.playlist.description
                    showRename = true
                },
                onDelete = { showDelete = true },
            )
        }

        if (showRename) {
            Modal(onDismiss = { showRename = false }) {
                Column {
                    Text("Name & details", style = RevibeType.heading, color = RevibeColors.TextPrimary)
                    Spacer(Modifier.height(14.dp))
                    RevibeTextField(newName, { newName = it }, placeholder = "Playlist name")
                    Spacer(Modifier.height(10.dp))
                    RevibeTextField(newDescription, { newDescription = it }, placeholder = "Add an optional description")
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.align(Alignment.End)) {
                        RevibeButton("Cancel", { showRename = false }, kind = ButtonKind.Ghost)
                        RevibeButton("Save", {
                            vm.renamePlaylist(detail?.playlist?.id.orEmpty(), newName)
                            vm.setPlaylistDescription(detail?.playlist?.id.orEmpty(), newDescription)
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
private fun PlaylistBody(
    vm: AppViewModel,
    detail: PlaylistDetail,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val state by vm.playerState.collectAsState()
    var query by remember(detail.playlist.id) { mutableStateOf("") }
    var searchOpen by remember(detail.playlist.id) { mutableStateOf(false) }
    var sort by remember(detail.playlist.id) { mutableStateOf(PlaylistSort.CUSTOM) }
    var sortOpen by remember { mutableStateOf(false) }

    val entries = remember(detail) {
        detail.entries.ifEmpty {
            detail.tracks.mapIndexed { index, track ->
                PlaylistTrackEntry(
                    track = track,
                    addedAt = detail.playlist.createdAt,
                    entryId = "legacy:$index",
                    position = index + 1,
                )
            }
        }
    }
    val visible = remember(entries, query, sort) {
        val filtered = if (query.isBlank()) {
            entries
        } else {
            entries.filter {
                it.track.title.contains(query, ignoreCase = true) ||
                    it.track.artist.contains(query, ignoreCase = true) ||
                    it.track.album.contains(query, ignoreCase = true)
            }
        }
        when (sort) {
            PlaylistSort.CUSTOM -> filtered
            PlaylistSort.TITLE -> filtered.sortedBy { it.track.title.lowercase() }
            PlaylistSort.ARTIST -> filtered.sortedBy { it.track.artist.lowercase() }
            PlaylistSort.ALBUM -> filtered.sortedBy { it.track.album.lowercase() }
            PlaylistSort.RECENTLY_ADDED -> filtered.sortedByDescending { it.addedAt }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val showAlbum = maxWidth >= 620.dp
        val showDate = maxWidth >= 660.dp
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 36.dp),
        ) {
            item("playlist-header") {
                PlaylistHeader(
                    detail = detail,
                    entries = entries,
                    query = query,
                    onQueryChange = { query = it },
                    searchOpen = searchOpen,
                    onToggleSearch = {
                        searchOpen = !searchOpen
                        if (!searchOpen) query = ""
                    },
                    sort = sort,
                    sortOpen = sortOpen,
                    onSortOpen = { sortOpen = true },
                    onSortDismiss = { sortOpen = false },
                    onSortSelect = { sort = it; sortOpen = false },
                    onPlay = { vm.playPlaylist(detail) },
                    onShuffle = { vm.playPlaylist(detail, shuffled = true) },
                    onAddSongs = { vm.openAddToPlaylist(detail.playlist.id) },
                    onRename = onRename,
                    onDelete = onDelete,
                )
            }

            if (entries.isEmpty()) {
                item("empty") {
                    EmptyState(
                        title = "This playlist is empty",
                        message = "Search for songs and add them right from the panel.",
                        action = "Add songs",
                        onAction = { vm.openAddToPlaylist(detail.playlist.id) },
                    )
                }
            } else {
                item("table-header") {
                    PlaylistTableHeader(showAlbum = showAlbum, showDate = showDate)
                }
                if (visible.isEmpty()) {
                    item("no-matches") {
                        Text(
                            "No songs match \"$query\" in this playlist.",
                            style = RevibeType.body,
                            color = RevibeColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                        )
                    }
                }
                itemsIndexed(visible, key = { _, entry -> entry.entryId }) { index, entry ->
                    PlaylistTrackRow(
                        entry = entry,
                        index = index + 1,
                        showAlbum = showAlbum,
                        showDate = showDate,
                        isCurrent = state.current?.id == entry.track.id,
                        isPlaying = state.isPlaying,
                        liked = vm.isLiked(entry.track.id),
                        onPlay = {
                            vm.play(
                                entry.track,
                                entries.map { it.track },
                                detail.playlist.name,
                                playlistId = detail.playlist.id,
                            )
                        },
                        onToggleLike = { vm.toggleLike(entry.track) },
                        onAddToPlaylist = { vm.addToPlaylistTrack = entry.track },
                        onAddToQueue = { vm.addToQueue(entry.track) },
                        onPlayNext = { vm.playNext(entry.track) },
                        onOpenArtist = { vm.openArtist(entry.track.artistId) },
                        onOpenAlbum = { vm.navigateToAlbumId(entry.track.albumId) },
                        onRemove = { vm.removeFromPlaylist(detail.playlist.id, entry.entryId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistHeader(
    detail: PlaylistDetail,
    entries: List<PlaylistTrackEntry>,
    query: String,
    onQueryChange: (String) -> Unit,
    searchOpen: Boolean,
    onToggleSearch: () -> Unit,
    sort: PlaylistSort,
    sortOpen: Boolean,
    onSortOpen: () -> Unit,
    onSortDismiss: () -> Unit,
    onSortSelect: (PlaylistSort) -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onAddSongs: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var optionsOpen by remember { mutableStateOf(false) }
    var tint by remember(detail.playlist.id) { mutableStateOf(Color(0xFF4B4B4B)) }
    val tracks = entries.map { it.track }
    val coverUrl = tracks.firstNotNullOfOrNull { it.artworkUrl ?: it.localPath }
    val totalDuration = tracks.sumOf { it.durationMs }
    val titleSize = when {
        detail.playlist.name.length <= 10 -> 72.sp
        detail.playlist.name.length <= 16 -> 58.sp
        detail.playlist.name.length <= 24 -> 44.sp
        else -> 36.sp
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        tint.copy(alpha = 0.94f),
                        tint.copy(alpha = 0.58f),
                        RevibeColors.Surface,
                    ),
                ),
            )
            .padding(top = 18.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            val compact = maxWidth < 620.dp
            val coverSize = if (compact) 160.dp else 216.dp
            val compactTitleSize = when {
                detail.playlist.name.length <= 10 -> 48.sp
                detail.playlist.name.length <= 16 -> 30.sp
                detail.playlist.name.length <= 24 -> 26.sp
                else -> 26.sp
            }
            val effectiveTitleSize = if (compact) compactTitleSize else titleSize
            Row {
                Box(
                    Modifier
                        .size(coverSize)
                        .clip(RoundedCornerShape(8.dp))
                        .background(gradientFor(detail.playlist.id))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (coverUrl != null) {
                        Artwork(
                            url = coverUrl,
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 8.dp,
                            onArtworkLoaded = { tint = it.dominantColor },
                        )
                    } else {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.size(64.dp),
                        )
                    }
                }
                Spacer(Modifier.width(if (compact) 20.dp else 24.dp))
                Column(
                    Modifier.height(coverSize).weight(1f),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        "Playlist",
                        style = RevibeType.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        color = RevibeColors.TextPrimary.copy(alpha = 0.9f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        detail.playlist.name,
                        style = RevibeType.hero.copy(
                            fontSize = effectiveTitleSize,
                            lineHeight = (effectiveTitleSize.value * 1.06f).sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (if (compact) -1.2 else -2.4).sp,
                        ),
                        color = RevibeColors.TextPrimary,
                        maxLines = if (compact) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(24.dp).clip(CircleShape).background(RevibeColors.Accent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "R",
                                style = RevibeType.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                color = RevibeColors.OnAccent,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Your Library",
                            style = RevibeType.caption.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                            color = RevibeColors.TextPrimary,
                        )
                        if (!compact) {
                            Text(
                                "  ·  ${tracks.size} songs, ${formatCollectionDuration(totalDuration)}",
                                style = RevibeType.caption.copy(fontSize = 14.sp),
                                color = RevibeColors.TextPrimary.copy(alpha = 0.72f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (compact) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${tracks.size} songs, ${formatCollectionDuration(totalDuration)}",
                            style = RevibeType.caption.copy(fontSize = 13.sp),
                            color = RevibeColors.TextPrimary.copy(alpha = 0.72f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(RevibeColors.Accent)
                    .clickable(onClick = onPlay)
                    .pointerHoverIcon(PointerIcon.Hand),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Play playlist",
                    tint = RevibeColors.OnAccent,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            PlaylistActionIcon(Icons.Rounded.Shuffle, "Shuffle playlist", onShuffle)
            PlaylistActionIcon(Icons.Rounded.PersonAdd, "Add songs", onAddSongs)
            Box {
                PlaylistActionIcon(Icons.Rounded.MoreHoriz, "More playlist options") { optionsOpen = true }
                DropdownMenu(
                    expanded = optionsOpen,
                    onDismissRequest = { optionsOpen = false },
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(RevibeColors.SurfaceRaised),
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit name & details", style = RevibeType.label, color = RevibeColors.TextPrimary) },
                        onClick = { optionsOpen = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete playlist", style = RevibeType.label, color = RevibeColors.Danger) },
                        onClick = { optionsOpen = false; onDelete() },
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            IconAction(
                Icons.Rounded.Search,
                contentDescription = "Search in playlist",
                onClick = onToggleSearch,
                size = 34.dp,
                iconSize = 20.dp,
                tint = if (searchOpen) RevibeColors.TextPrimary else RevibeColors.TextSecondary,
            )
            Box {
                Row(
                    Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onSortOpen)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        sort.label,
                        style = RevibeType.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        color = RevibeColors.TextPrimary,
                    )
                    Icon(Icons.Rounded.Sort, contentDescription = null, tint = RevibeColors.TextPrimary, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(
                    expanded = sortOpen,
                    onDismissRequest = onSortDismiss,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(RevibeColors.SurfaceRaised),
                ) {
                    PlaylistSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    option.label,
                                    style = RevibeType.label,
                                    color = if (option == sort) RevibeColors.Accent else RevibeColors.TextPrimary,
                                )
                            },
                            onClick = { onSortSelect(option) },
                        )
                    }
                }
            }
        }

        if (searchOpen) {
            Spacer(Modifier.height(6.dp))
            PlaylistSearchField(query, onQueryChange, Modifier.padding(horizontal = 24.dp))
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlaylistPill("Add", Icons.Rounded.Add, onAddSongs)
            PlaylistPill("Name & details", Icons.Rounded.Edit, onRename)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PlaylistActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconAction(
        icon = icon,
        contentDescription = description,
        onClick = onClick,
        size = 34.dp,
        iconSize = 20.dp,
        tint = RevibeColors.TextPrimary.copy(alpha = 0.72f),
        hoverTint = RevibeColors.TextPrimary,
    )
}

@Composable
private fun PlaylistPill(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .height(32.dp)
            .clip(CircleShape)
            .border(1.dp, RevibeColors.TextSecondary.copy(alpha = 0.65f), CircleShape)
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = RevibeColors.TextPrimary, modifier = Modifier.size(16.dp))
        Text(
            text,
            style = RevibeType.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            color = RevibeColors.TextPrimary,
        )
    }
}

@Composable
private fun PlaylistSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .width(320.dp)
            .height(34.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.22f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = RevibeColors.TextSecondary, modifier = Modifier.size(16.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = RevibeType.body.copy(fontSize = 13.sp, color = RevibeColors.TextPrimary),
            cursorBrush = SolidColor(RevibeColors.Accent),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text("Search in playlist", style = RevibeType.body.copy(fontSize = 13.sp), color = RevibeColors.TextTertiary)
                    }
                    inner()
                }
            },
        )
        if (value.isNotEmpty()) {
            IconAction(
                Icons.Rounded.Close,
                contentDescription = "Clear playlist search",
                onClick = { onValueChange("") },
                size = 22.dp,
                iconSize = 14.dp,
            )
        }
    }
}

@Composable
private fun PlaylistTableHeader(showAlbum: Boolean, showDate: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp).height(34.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "#",
            style = RevibeType.caption.copy(fontSize = 12.sp),
            color = RevibeColors.TextSecondary,
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.width(8.dp))
        Text("Title", style = RevibeType.caption.copy(fontSize = 12.sp), color = RevibeColors.TextSecondary, modifier = Modifier.weight(1f))
        if (showAlbum) {
            Text("Album", style = RevibeType.caption.copy(fontSize = 12.sp), color = RevibeColors.TextSecondary, modifier = Modifier.width(150.dp))
        }
        if (showDate) {
            Text("Date added", style = RevibeType.caption.copy(fontSize = 12.sp), color = RevibeColors.TextSecondary, modifier = Modifier.width(112.dp))
        }
        Box(Modifier.width(76.dp), contentAlignment = Alignment.CenterEnd) {
            Icon(Icons.Rounded.AccessTime, contentDescription = "Duration", tint = RevibeColors.TextSecondary, modifier = Modifier.size(16.dp))
        }
    }
    HDivider(Modifier.padding(horizontal = 24.dp), RevibeColors.OutlineSoft)
}

@Composable
private fun PlaylistTrackRow(
    entry: PlaylistTrackEntry,
    index: Int,
    showAlbum: Boolean,
    showDate: Boolean,
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
    onRemove: () -> Unit,
) {
    val track = entry.track
    var menuOpen by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background by animateColorAsState(
        if (hovered) Color.White.copy(alpha = 0.075f) else Color.Transparent,
        label = "playlistRowHover",
    )
    val actionAlpha by animateFloatAsState(if (hovered || menuOpen) 1f else 0f, label = "playlistRowActions")
    val showActions = hovered || menuOpen

    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(background)
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null, onClick = onPlay)
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                when {
                    isCurrent && isPlaying -> Icon(
                        Icons.Rounded.GraphicEq,
                        contentDescription = "Now playing",
                        tint = RevibeColors.Accent,
                        modifier = Modifier.size(17.dp),
                    )
                    hovered -> Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "Play ${track.title}",
                        tint = RevibeColors.TextPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                    else -> Text(
                        index.toString(),
                        style = RevibeType.caption.copy(fontSize = 14.sp),
                        color = if (isCurrent) RevibeColors.Accent else RevibeColors.TextSecondary,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Artwork(
                    url = track.artworkUrl ?: track.localPath,
                    modifier = Modifier.size(40.dp),
                    cornerRadius = 2.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        style = RevibeType.body.copy(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
                        color = if (isCurrent) RevibeColors.Accent else RevibeColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        track.artist,
                        style = RevibeType.body.copy(fontSize = 14.sp, lineHeight = 18.sp),
                        color = RevibeColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .clickable { if (track.artistId != null) onOpenArtist() }
                            .pointerHoverIcon(if (track.artistId != null) PointerIcon.Hand else PointerIcon.Default),
                    )
                }
            }
            if (showAlbum) {
                Text(
                    track.album,
                    style = RevibeType.body.copy(fontSize = 14.sp),
                    color = RevibeColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .width(150.dp)
                        .padding(end = 12.dp)
                        .clickable { if (track.albumId != null) onOpenAlbum() }
                        .pointerHoverIcon(if (track.albumId != null) PointerIcon.Hand else PointerIcon.Default),
                )
            }
            if (showDate) {
                Text(
                    if (entry.addedAt > 0) formatAddedDate(entry.addedAt) else "—",
                    style = RevibeType.body.copy(fontSize = 14.sp),
                    color = RevibeColors.TextSecondary,
                    maxLines = 1,
                    modifier = Modifier.width(112.dp),
                )
            }
            Box(Modifier.width(76.dp), contentAlignment = Alignment.CenterEnd) {
                if (showActions) {
                    Row(
                        Modifier
                            .fillMaxSize()
                            .background(background)
                            .alpha(actionAlpha),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        IconAction(
                            Icons.Rounded.Favorite,
                            contentDescription = if (liked) "Remove from Liked Songs" else "Save to Liked Songs",
                            onClick = onToggleLike,
                            size = 28.dp,
                            iconSize = 16.dp,
                            tint = if (liked) RevibeColors.Accent else RevibeColors.TextSecondary,
                        )
                        Box {
                            IconAction(
                                Icons.Rounded.MoreHoriz,
                                contentDescription = "More options",
                                onClick = { menuOpen = true },
                                size = 28.dp,
                                iconSize = 18.dp,
                                tint = RevibeColors.TextSecondary,
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
                                onRemove = { menuOpen = false; onRemove() },
                            )
                        }
                    }
                } else {
                    Text(
                        formatDuration(track.durationMs),
                        style = RevibeType.caption.copy(fontSize = 14.sp),
                        color = RevibeColors.TextSecondary,
                    )
                }
            }
        }
    }
}

private fun formatCollectionDuration(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes >= 60 -> "${minutes / 60} hr ${minutes % 60} min"
        minutes > 0 -> "$minutes min"
        else -> "0 min"
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
