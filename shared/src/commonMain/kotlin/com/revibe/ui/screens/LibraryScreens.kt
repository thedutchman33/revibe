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
import androidx.compose.material.icons.rounded.MusicNote
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revibe.model.MusicSourceId
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
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(4.dp)).background(gradientFor(playlist.id)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(playlist.name, style = RevibeType.bodyStrong, color = RevibeColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${playlist.trackCount} songs", style = RevibeType.caption, color = RevibeColors.TextSecondary)
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
                    "MP3, M4A/AAC, WAV and AIFF play through the JavaFX engine. FLAC, OGG and OPUS are scanned, tagged and indexed, but full playback needs the native engine planned for the next milestone.",
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
    var showAbout by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 16.dp, top = 10.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column {
                    Text("Settings", style = RevibeType.display.copy(fontSize = 32.sp), color = RevibeColors.TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text("Playback, catalog and integrations", style = RevibeType.caption, color = RevibeColors.TextSecondary)
                }
            }
            item {
                SettingsCard("Playback") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Volume", style = RevibeType.body, color = RevibeColors.TextPrimary, modifier = Modifier.width(90.dp))
                        SeekBar(settings.volume, { vm.setVolume(it) }, modifier = Modifier.weight(1f))
                        Text(
                            "${(settings.volume * 100).toInt()}%",
                            style = RevibeType.caption,
                            color = RevibeColors.TextSecondary,
                            modifier = Modifier.width(44.dp).padding(start = 10.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Catalog tracks play 30-second previews. Local files play in full, gapless and crossfade arrive with the native engine.",
                        style = RevibeType.caption,
                        color = RevibeColors.TextTertiary,
                    )
                }
            }
            item {
                SettingsCard("Catalog") {
                    Text("Storefront", style = RevibeType.body, color = RevibeColors.TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Apple's catalog uses one storefront at a time. Changing it reloads search and home.",
                        style = RevibeType.caption,
                        color = RevibeColors.TextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("IN", "US", "GB", "DE", "AU", "JP", "BR", "FR")) { code ->
                            Chip(code, settings.market == code, onClick = { vm.changeMarket(code) })
                        }
                    }
                }
            }
            item {
                SettingsCard("Appearance") {
                    ToggleRow(
                        "Right panel",
                        "Now playing, lyrics and queue beside the main view",
                        vm.rightPanelVisible,
                        vm::toggleRightPanel,
                    )
                    ToggleRow(
                        "Compact sidebar",
                        "Icon-only navigation to free up width",
                        vm.compactSidebar,
                        vm::toggleCompactSidebar,
                    )
                }
            }
            item {
                SettingsCard("Sources & integrations") {
                    MusicSourceId.entries.forEach { source ->
                        SourceRow(source)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Revibe ships only integrations that are permitted by the provider. YouTube Music is not implemented because its terms do not allow third-party clients to stream its catalog.",
                        style = RevibeType.caption,
                        color = RevibeColors.TextTertiary,
                    )
                }
            }
            item {
                SettingsCard("Data") {
                    InfoRow("Platform", vm.platformName)
                    InfoRow("Version", vm.appVersion)
                    InfoRow("Data folder", vm.dataDirectory)
                    InfoRow("Library", "SQLite, stored locally")
                    InfoRow("Lyrics cache", "LRCLIB, cached per track")
                }
            }
            item {
                SettingsCard("About Revibe") {
                    Text(
                        "Revibe is an independent music player. Your music. Your vibe.",
                        style = RevibeType.body,
                        color = RevibeColors.TextPrimary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Metadata and artwork: iTunes Search API. Lyrics: LRCLIB. Artist biographies: Wikipedia, CC BY-SA 4.0. Revibe is not affiliated with Spotify, Apple or any other service.",
                        style = RevibeType.caption,
                        color = RevibeColors.TextSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    RevibeButton("Licensing & compliance", { showAbout = true }, kind = ButtonKind.Outline)
                }
            }
        }

        if (showAbout) {
            Modal(onDismiss = { showAbout = false }) {
                Column {
                    Text("Licensing & compliance", style = RevibeType.heading, color = RevibeColors.TextPrimary)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Revibe's own code is independent. The reference projects studied during design (Metrolist, BitChord, VIVI Music, InnerTubeX) are GPL-3.0; no source code from them is included in this build. Catalog metadata, artwork and previews come from Apple's public search API under its terms. Lyrics come from LRCLIB. Biographies come from Wikipedia under CC BY-SA 4.0 and are attributed in the interface.",
                        style = RevibeType.body,
                        color = RevibeColors.TextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "No DRM is bypassed. No proprietary Spotify assets, binaries or source code are used or redistributed.",
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
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(RevibeColors.Surface)
            .padding(18.dp),
    ) {
        Text(title, style = RevibeType.heading.copy(fontSize = 17.sp), color = RevibeColors.TextPrimary)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = RevibeType.caption, color = RevibeColors.TextTertiary, modifier = Modifier.width(110.dp))
        Text(
            value,
            style = RevibeType.caption,
            color = RevibeColors.TextSecondary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SourceRow(source: MusicSourceId) {
    val dotColor = when (source.availability) {
        com.revibe.model.SourceAvailability.ACTIVE -> RevibeColors.Accent
        com.revibe.model.SourceAvailability.EXPERIMENTAL -> Color(0xFFFFC857)
        com.revibe.model.SourceAvailability.INACTIVE -> RevibeColors.TextTertiary
        com.revibe.model.SourceAvailability.BLOCKED -> RevibeColors.Danger
    }
    val note = when (source) {
        MusicSourceId.ITUNES -> "Search, artwork, metadata and 30-second previews are live."
        MusicSourceId.LOCAL -> "Folder scanning, tag reading and full playback are live."
        MusicSourceId.YOUTUBE_MUSIC -> "Not shipped: streaming its catalog from a third-party client is outside YouTube's terms."
        MusicSourceId.SPOTIFY -> "Metadata import via official OAuth is planned; audio streaming is not part of Spotify's public API."
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 5.dp).size(7.dp).clip(CircleShape).background(dotColor))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(source.label, style = RevibeType.bodyStrong, color = RevibeColors.TextPrimary)
                Spacer(Modifier.width(8.dp))
                Text(
                    source.availability.label,
                    style = RevibeType.caption,
                    color = dotColor,
                )
            }
            Text(note, style = RevibeType.caption, color = RevibeColors.TextSecondary)
        }
    }
}

