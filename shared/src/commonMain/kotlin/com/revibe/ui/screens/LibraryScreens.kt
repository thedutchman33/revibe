package com.revibe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revibe.account.AccountState
import com.revibe.ui.AppViewModel
import com.revibe.ui.components.Artwork
import com.revibe.ui.components.ButtonKind
import com.revibe.ui.components.Chip
import com.revibe.ui.components.EmptyState
import com.revibe.ui.components.HoverSurface
import androidx.compose.material3.HorizontalDivider
import com.revibe.ui.components.MediaCard
import com.revibe.ui.components.Modal
import com.revibe.ui.components.RevibeButton
import com.revibe.ui.components.RevibeSwitch
import com.revibe.ui.components.SeekBar
import com.revibe.ui.components.SectionHeader
import com.revibe.ui.components.ToggleRow
import com.revibe.ui.components.TrackRow
import com.revibe.ui.components.TrackTableHeader
import com.revibe.ui.components.gradientFor
import com.revibe.ui.toMediaItem
import com.revibe.ui.theme.RevibeColors
import com.revibe.ui.theme.RevibeType
import com.revibe.update.UpdatePhase
import com.revibe.update.UpdateState

@Composable
fun LibraryScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 16.dp, top = 10.dp)) {
        Text("Your Library", style = RevibeType.display.copy(fontSize = 32.sp), color = RevibeColors.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "${vm.playlists.size} playlists · ${vm.likedAlbums.size} albums · ${vm.followedArtists.size} artists",
            style = RevibeType.caption,
            color = RevibeColors.TextSecondary,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Playlists", "Albums", "Artists").forEachIndexed { index, label ->
                Chip(label, vm.libraryTab == index, onClick = { vm.selectLibraryTab(index) })
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.weight(1f)) {
            when (vm.libraryTab) {
                0 -> LibraryAll(vm)
                1 -> PlaylistList(vm)
                2 -> AlbumGrid(vm)
                3 -> ArtistList(vm)
            }
        }
    }
}

@Composable
private fun LibraryAll(vm: AppViewModel) {
    if (vm.playlists.isEmpty() && vm.likedAlbums.isEmpty() && vm.followedArtists.isEmpty()) {
        EmptyState(
            title = "Your library is waiting",
            message = "Save albums, follow artists, or create a playlist to get started.",
            action = "Browse the catalog",
            onAction = vm::openSearch,
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (vm.playlists.isNotEmpty()) {
            item {
                SectionHeader(
                    "Playlists",
                    action = "New playlist",
                    onAction = { vm.showCreatePlaylist = true },
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }
            items(vm.playlists, key = { it.id }) { playlist -> PlaylistRow(vm, playlist) }
        }
        if (vm.likedAlbums.isNotEmpty()) {
            item {
                Spacer(Modifier.height(18.dp))
                SectionHeader("Liked albums", modifier = Modifier.padding(vertical = 10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(end = 20.dp)) {
                    items(vm.likedAlbums, key = { it.id }) { album ->
                        MediaCard(album.toMediaItem(), onOpen = { vm.openAlbum(album) })
                    }
                }
            }
        }
        if (vm.followedArtists.isNotEmpty()) {
            item {
                Spacer(Modifier.height(18.dp))
                SectionHeader("Artists you follow", modifier = Modifier.padding(vertical = 10.dp))
            }
            items(vm.followedArtists, key = { it.id }) { artist ->
                HoverSurface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    onClick = { vm.openArtist(artist.id) },
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Artwork(artist.imageUrl, Modifier.size(52.dp), cornerRadius = 26.dp)
                        Column(Modifier.weight(1f)) {
                            Text(artist.name, style = RevibeType.bodyStrong, color = RevibeColors.TextPrimary)
                            Text(artist.genre ?: "Artist", style = RevibeType.caption, color = RevibeColors.TextSecondary)
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = RevibeColors.TextTertiary)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistList(vm: AppViewModel) {
    if (vm.playlists.isEmpty()) {
        EmptyState(
            title = "No playlists yet",
            message = "Create your first playlist and fill it with anything you find.",
            action = "Create playlist",
            onAction = { vm.showCreatePlaylist = true },
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        items(vm.playlists, key = { it.id }) { playlist -> PlaylistRow(vm, playlist) }
    }
}

@Composable
private fun PlaylistRow(vm: AppViewModel, playlist: com.revibe.model.Playlist) {
    HoverSurface(
        modifier = Modifier.fillMaxWidth(),
        onClick = { vm.openPlaylist(playlist.id) },
        shape = RoundedCornerShape(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!playlist.artworkUrl.isNullOrBlank()) {
                Artwork(playlist.artworkUrl, Modifier.size(52.dp), cornerRadius = 4.dp)
            } else {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(4.dp)).background(gradientFor(playlist.id)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                }
            }
            val playing = vm.playingPlaylistId == playlist.id
            Column(Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    style = RevibeType.bodyStrong,
                    color = if (playing) RevibeColors.Accent else RevibeColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${playlist.trackCount} songs", style = RevibeType.caption, color = RevibeColors.TextSecondary)
            }
            if (playing) {
                Icon(
                    Icons.Rounded.GraphicEq,
                    contentDescription = "Currently playing",
                    tint = RevibeColors.Accent,
                    modifier = Modifier.size(17.dp),
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = RevibeColors.TextTertiary)
        }
    }
}

@Composable
private fun AlbumGrid(vm: AppViewModel) {
    if (vm.likedAlbums.isEmpty()) {
        EmptyState("No saved albums", "Tap the heart on an album page to keep it here.", action = "Browse", onAction = vm::openSearch)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        contentPadding = PaddingValues(bottom = 40.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(vm.likedAlbums, key = { it.id }) { album ->
            MediaCard(album.toMediaItem(), onOpen = { vm.openAlbum(album) }, cardWidth = 150)
        }
    }
}

@Composable
private fun ArtistList(vm: AppViewModel) {
    if (vm.followedArtists.isEmpty()) {
        EmptyState("No followed artists", "Follow an artist to see their releases here.", action = "Browse", onAction = vm::openSearch)
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        items(vm.followedArtists, key = { it.id }) { artist ->
            HoverSurface(
                modifier = Modifier.fillMaxWidth(),
                onClick = { vm.openArtist(artist.id) },
                shape = RoundedCornerShape(6.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Artwork(artist.imageUrl, Modifier.size(52.dp), cornerRadius = 26.dp)
                    Column(Modifier.weight(1f)) {
                        Text(artist.name, style = RevibeType.bodyStrong, color = RevibeColors.TextPrimary)
                        Text(artist.genre ?: "Artist", style = RevibeType.caption, color = RevibeColors.TextSecondary)
                    }
                    Chip("Following", true, onClick = { vm.toggleFollowArtist(artist) })
                }
            }
        }
    }
}

@Composable
fun LocalScreen(vm: AppViewModel) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 16.dp, top = 10.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text("Local files", style = RevibeType.display.copy(fontSize = 32.sp), color = RevibeColors.TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Your own music, read straight from disk. Works offline.",
                    style = RevibeType.caption,
                    color = RevibeColors.TextSecondary,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    RevibeButton("Add folder", vm::addLocalFolder, icon = Icons.Rounded.Add)
                    RevibeButton(
                        if (vm.scanning) "Scanning…" else "Scan library",
                        vm::scanLocal,
                        kind = ButtonKind.Outline,
                        icon = Icons.Rounded.Refresh,
                    )
                    if (vm.scanning) {
                        CircularProgressIndicator(
                            color = RevibeColors.Accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            if (vm.scanProgress.second > 0) "${vm.scanProgress.first} / ${vm.scanProgress.second}" else "Finding files…",
                            style = RevibeType.caption,
                            color = RevibeColors.TextSecondary,
                        )
                    }
                }
            }
        }

        if (vm.scanFolders.isNotEmpty()) {
            item {
                Column {
                    Text("Folders", style = RevibeType.label, color = RevibeColors.TextSecondary, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(vm.scanFolders) { folder ->
                            Row(
                                Modifier
                                    .clip(CircleShape)
                                    .background(RevibeColors.SurfaceRaised)
                                    .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    folder,
                                    style = RevibeType.caption,
                                    color = RevibeColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(240.dp),
                                )
                                Box(
                                    Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .clickable { vm.removeLocalFolder(folder) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Remove folder", tint = RevibeColors.TextSecondary, modifier = Modifier.size(13.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        vm.scanReport?.let { report ->
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(RevibeColors.Surface)
                        .padding(14.dp),
                ) {
                    Text("Last scan", style = RevibeType.bodyStrong, color = RevibeColors.TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${report.added} added · ${report.updated} updated · ${report.removed} removed · ${report.skipped} skipped",
                        style = RevibeType.caption,
                        color = RevibeColors.TextSecondary,
                    )
                    report.errors.take(3).forEach { error ->
                        Text("• $error", style = RevibeType.caption, color = RevibeColors.Danger, maxLines = 2)
                    }
                }
            }
        }

        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(RevibeColors.Surface)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = RevibeColors.TextSecondary, modifier = Modifier.size(16.dp))
                    Text("Supported formats", style = RevibeType.bodyStrong, color = RevibeColors.TextPrimary)
                }
                Text(
                    "MP3, M4A/AAC, WAV, AIFF, FLAC, OGG and OPUS play through the bundled FFmpeg engine.",
                    style = RevibeType.caption,
                    color = RevibeColors.TextSecondary,
                )
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            SectionHeader("Tracks", subtitle = "${vm.localTracks.size} indexed")
            if (vm.localTracks.isNotEmpty()) Spacer(Modifier.height(6.dp))
            if (vm.localTracks.isNotEmpty()) TrackTableHeader(showAlbum = true)
        }

        if (vm.localTracks.isEmpty()) {
            item {
                EmptyState(
                    title = "No local music yet",
                    message = "Add a folder with your music files, then run a scan. Tags and embedded artwork are read automatically.",
                    action = "Add a folder",
                    onAction = vm::addLocalFolder,
                )
            }
        } else {
            itemsIndexed(vm.localTracks, key = { _, track -> track.id }) { index, track ->
                TrackRow(
                    track = track,
                    index = index + 1,
                    isCurrent = vm.playerState.value.current?.id == track.id,
                    isPlaying = vm.playerState.value.isPlaying,
                    liked = vm.isLiked(track.id),
                    onPlay = { vm.play(track, vm.localTracks, "Local files") },
                    onToggleLike = { vm.toggleLike(track) },
                    onAddToPlaylist = { vm.addToPlaylistTrack = track },
                    onAddToQueue = { vm.addToQueue(track) },
                    onPlayNext = { vm.playNext(track) },
                    onOpenArtist = {},
                    onOpenAlbum = {},
                    showArtwork = true,
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsState()
    val account by vm.accountState.collectAsState()
    val sync by vm.syncStatus.collectAsState()
    val update by vm.updateState.collectAsState()
    var showAbout by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 16.dp, top = 10.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Settings",
                        style = RevibeType.display.copy(fontSize = 32.sp),
                        color = RevibeColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "v${vm.appVersion}",
                        style = RevibeType.caption,
                        color = RevibeColors.TextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(RevibeColors.Surface)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            item {
                SettingsCard("Playback", Icons.Rounded.GraphicEq) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Volume", style = RevibeType.bodyStrong, color = RevibeColors.TextPrimary, modifier = Modifier.width(82.dp))
                        SeekBar(settings.volume, { vm.setVolume(it) }, modifier = Modifier.weight(1f))
                        Text(
                            "${(settings.volume * 100).toInt()}%",
                            style = RevibeType.caption,
                            color = RevibeColors.TextSecondary,
                            modifier = Modifier.width(44.dp).padding(start = 10.dp),
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = RevibeColors.OutlineSoft)
                    ToggleRow(
                        "Autoplay",
                        null,
                        vm.autoplayEnabled,
                        vm::toggleAutoplay,
                    )
                }
            }
            item {
                SettingsCard("Appearance", Icons.Rounded.Palette) {
                    ToggleRow(
                        "Right panel",
                        null,
                        vm.rightPanelVisible,
                        vm::toggleRightPanel,
                    )
                    HorizontalDivider(color = RevibeColors.OutlineSoft)
                    ToggleRow(
                        "Compact sidebar",
                        null,
                        vm.compactSidebar,
                        vm::toggleCompactSidebar,
                    )
                }
            }
            item {
                SettingsCard("Catalog", Icons.Rounded.Public) {
                    Text("Storefront", style = RevibeType.bodyStrong, color = RevibeColors.TextPrimary)
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("IN", "US", "GB", "DE", "AU", "JP", "BR", "FR")) { code ->
                            Chip(code, settings.market == code, onClick = { vm.changeMarket(code) })
                        }
                    }
                }
            }
            item {
                SettingsCard("Account", Icons.Rounded.Person) {
                    val signedIn = account as? AccountState.SignedIn
                    val detail = when {
                        signedIn != null -> signedIn.user.email
                        account is AccountState.AwaitingConfirmation -> "Confirm your email"
                        account is AccountState.Restoring -> "Restoring session"
                        account is AccountState.NotConfigured -> "Local library only"
                        else -> "Not signed in"
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Account & sync", style = RevibeType.bodyStrong, color = RevibeColors.TextPrimary)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    detail,
                                    style = RevibeType.caption,
                                    color = RevibeColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (sync.pendingChanges > 0) {
                                    Text(
                                        " · ${sync.pendingChanges} pending",
                                        style = RevibeType.caption,
                                        color = RevibeColors.TextTertiary,
                                    )
                                }
                            }
                        }
                        RevibeButton(
                            if (signedIn != null || account is AccountState.AwaitingConfirmation) "Manage" else "Log in",
                            if (signedIn != null || account is AccountState.AwaitingConfirmation) vm::openAccount else vm::openAuthModal,
                            kind = ButtonKind.Outline,
                        )
                    }
                }
            }
            item {
                SettingsCard("About Revibe", Icons.Rounded.Info) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Version", style = RevibeType.bodyStrong, color = RevibeColors.TextPrimary, modifier = Modifier.weight(1f))
                        Text(vm.appVersion, style = RevibeType.body, color = RevibeColors.TextSecondary)
                    }
                    if (update.phase != UpdatePhase.DISABLED) {
                        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = RevibeColors.OutlineSoft)
                        SettingsUpdateRow(vm, update)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = RevibeColors.OutlineSoft)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showAbout = true }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Licensing & compliance", style = RevibeType.bodyStrong, color = RevibeColors.TextPrimary, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.ChevronRight, null, tint = RevibeColors.TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        if (showAbout) {
            Modal(onDismiss = { showAbout = false }) {
                Column {
                    Text("Licensing & compliance", style = RevibeType.heading, color = RevibeColors.TextPrimary)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Revibe links InnerTubeX v0.13.1 and QuackJS (both GPL-3.0), so this combined application is distributed under GPL-3.0. Corresponding source is available with the project. Catalog metadata and artwork come from the online catalog; extraction is subject to the service's terms. Lyrics come from LRCLIB. Biographies come from Wikipedia under CC BY-SA 4.0 and are attributed in the interface.",
                        style = RevibeType.body,
                        color = RevibeColors.TextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "No DRM or paid access is bypassed. No cookies, tokens or credentials are extracted. No proprietary Spotify assets, binaries or source code are used or redistributed.",
                        style = RevibeType.caption,
                        color = RevibeColors.TextTertiary,
                    )
                    Spacer(Modifier.height(16.dp))
                    RevibeButton("Close", { showAbout = false }, modifier = Modifier.align(Alignment.End))
                }
            }
        }
    }
}

@Composable
private fun SettingsUpdateRow(vm: AppViewModel, update: UpdateState) {
    val available = update.available
    val status = when (update.phase) {
        UpdatePhase.IDLE -> "Check for the latest release"
        UpdatePhase.UP_TO_DATE -> "You're up to date"
        UpdatePhase.CHECKING -> "Checking for updates…"
        UpdatePhase.AVAILABLE -> if (available?.mandatory == true) "Required update" else "Ready to install"
        UpdatePhase.DOWNLOADING -> update.message ?: "Downloading…"
        UpdatePhase.VERIFYING -> update.message ?: "Verifying download…"
        UpdatePhase.INSTALLING -> update.message ?: "Installing…"
        UpdatePhase.INSTALLED -> update.message ?: "Update complete"
        UpdatePhase.ERROR -> update.message ?: "Update check failed"
        UpdatePhase.DISABLED -> "Updates are available in packaged builds"
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Rounded.SystemUpdate,
            contentDescription = null,
            tint = if (available != null) RevibeColors.Accent else RevibeColors.TextSecondary,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                if (available != null) "Revibe ${available.version}" else "Software updates",
                style = RevibeType.bodyStrong,
                color = RevibeColors.TextPrimary,
            )
            Text(
                status,
                style = RevibeType.caption,
                color = if (update.phase == UpdatePhase.ERROR) RevibeColors.Danger else RevibeColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            update.isBusy -> CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = RevibeColors.Accent,
            )
            update.phase == UpdatePhase.AVAILABLE -> RevibeButton("Update", vm::installUpdate)
            update.phase == UpdatePhase.ERROR -> RevibeButton("Retry", vm::retryUpdate, kind = ButtonKind.Outline)
            update.phase == UpdatePhase.INSTALLED -> RevibeButton("Done", vm::dismissUpdateNotice, kind = ButtonKind.Ghost)
            else -> RevibeButton("Check", vm::checkForUpdates, kind = ButtonKind.Outline)
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(RevibeColors.SurfaceRaised)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(RevibeColors.Surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = RevibeColors.Accent, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(title, style = RevibeType.heading.copy(fontSize = 17.sp), color = RevibeColors.TextPrimary)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}
