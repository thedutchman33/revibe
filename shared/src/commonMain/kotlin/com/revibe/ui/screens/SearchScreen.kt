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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revibe.model.formatDuration
import com.revibe.ui.AppViewModel
import com.revibe.ui.components.Artwork
import com.revibe.ui.components.Chip
import com.revibe.ui.components.EmptyState
import com.revibe.ui.components.MediaCard
import com.revibe.ui.components.SectionHeader
import com.revibe.ui.components.SkeletonBlock
import com.revibe.ui.components.TrackRow
import com.revibe.ui.components.TrackTableHeader
import com.revibe.ui.components.gradientFor
import com.revibe.ui.toMediaItem
import com.revibe.ui.theme.RevibeColors
import com.revibe.ui.theme.RevibeType

private val browseGenres = listOf(
    "Bollywood", "Punjabi", "Pop", "Hip-hop", "Indie", "Lo-fi",
    "R&B", "Rock", "Electronic", "Classical", "Jazz", "Devotional",
)

@Composable
fun SearchScreen(vm: AppViewModel) {
    val state by vm.playerState.collectAsState()
    val results = vm.searchResults
    val browsing = vm.searchQuery.trim().length < 2 && results == null

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(Brush.verticalGradient(listOf(vm.accent.copy(alpha = 0.08f), androidx.compose.ui.graphics.Color.Transparent))),
        )

        when {
            browsing -> BrowseAll(vm)
            vm.searchLoading -> SearchSkeleton()
            vm.searchError != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "Search failed",
                    message = vm.searchError ?: "",
                    action = "Try again",
                    onAction = { vm.submitSearch() },
                )
            }
            results != null && results.isEmpty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "No results for \"${results.query}\"",
                    message = "Try a different spelling, or search for an artist, album or song.",
                )
            }
            results != null -> {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    item {
                        Text(
                            "Results for \"${results.query}\"",
                            style = RevibeType.display.copy(fontSize = 28.sp),
                            color = RevibeColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Top", "Songs", "Albums", "Artists").forEachIndexed { index, label ->
                                Chip(label, vm.searchTab == index, onClick = { vm.selectSearchTab(index) })
                            }
                        }
                    }
                    when (vm.searchTab) {
                        0 -> {
                            item {
                                Column {
                                    SectionHeader("Songs", subtitle = "Top matches")
                                    Spacer(Modifier.height(8.dp))
                                    results.tracks.take(4).forEachIndexed { index, track ->
                                        SearchTrackRow(vm, track, index, results.tracks.take(4), state.current?.id, state.isPlaying)
                                    }
                                }
                            }
                            if (results.albums.isNotEmpty()) {
                                item {
                                    Column {
                                        SectionHeader("Albums")
                                        Spacer(Modifier.height(12.dp))
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            items(results.albums, key = { it.id }) { album ->
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
                            if (results.artists.isNotEmpty()) {
                                item {
                                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        results.artists.take(4).forEach { artist ->
                                            Row(
                                                Modifier
                                                    .clip(RoundedCornerShape(24.dp))
                                                    .background(RevibeColors.Surface)
                                                    .clickable { vm.openArtist(artist.id) }
                                                    .pointerHoverIcon(PointerIcon.Hand)
                                                    .padding(end = 16.dp, top = 6.dp, bottom = 6.dp, start = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            ) {
                                                Artwork(artist.imageUrl, Modifier.size(38.dp), cornerRadius = 19.dp)
                                                Column {
                                                    Text(artist.name, style = RevibeType.caption.copy(fontWeight = FontWeight.Bold), color = RevibeColors.TextPrimary)
                                                    Text("Artist", style = RevibeType.caption, color = RevibeColors.TextSecondary)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        1 -> itemsIndexed(results.tracks, key = { _, t -> t.id }) { index, track ->
                            SearchTrackRow(vm, track, index, results.tracks, state.current?.id, state.isPlaying)
                        }
                        2 -> items(results.albums, key = { it.id }) { album ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { vm.openAlbum(album) }
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Artwork(album.artworkUrl, Modifier.size(56.dp), cornerRadius = 4.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        album.title,
                                        style = RevibeType.bodyStrong,
                                        color = RevibeColors.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${album.artist} · ${album.year ?: "—"} · ${album.trackCount} songs",
                                        style = RevibeType.caption,
                                        color = RevibeColors.TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = RevibeColors.TextTertiary)
                            }
                        }
                        3 -> itemsIndexed(results.artists, key = { _, a -> a.id }) { _, artist ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { vm.openArtist(artist.id) }
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Artwork(artist.imageUrl, Modifier.size(52.dp), cornerRadius = 26.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(artist.name, style = RevibeType.bodyStrong, color = RevibeColors.TextPrimary)
                                    Text(artist.genre ?: "Artist", style = RevibeType.caption, color = RevibeColors.TextSecondary)
                                }
                                Text("View artist", style = RevibeType.caption, color = RevibeColors.TextTertiary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTrackRow(
    vm: AppViewModel,
    track: com.revibe.model.Track,
    index: Int,
    context: List<com.revibe.model.Track>,
    currentId: String?,
    isPlaying: Boolean,
) {
    TrackRow(
        track = track,
        index = index + 1,
        isCurrent = track.id == currentId,
        isPlaying = isPlaying,
        liked = vm.isLiked(track.id),
        onPlay = { vm.play(track, context, "Search") },
        onToggleLike = { vm.toggleLike(track) },
        onAddToPlaylist = { vm.addToPlaylistTrack = track },
        onAddToQueue = { vm.addToQueue(track) },
        onPlayNext = { vm.playNext(track) },
        onOpenArtist = { vm.openArtist(track.artistId) },
        onOpenAlbum = { vm.navigateToAlbumId(track.albumId) },
        showArtwork = true,
        showAlbum = true,
    )
}

@Composable
private fun BrowseAll(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().padding(start = 24.dp, end = 16.dp, top = 14.dp),
    ) {
        Text("Browse all", style = RevibeType.display.copy(fontSize = 30.sp), color = RevibeColors.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text("Search the open catalog, or start with a genre.", style = RevibeType.body, color = RevibeColors.TextSecondary)
        if (vm.recentSearches.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.recentSearches.take(5).forEach { term ->
                    Chip(term, false, onClick = { vm.submitSearch(term) })
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(190.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            items(browseGenres) { genre ->
                Box(
                    Modifier
                        .height(104.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(gradientFor(genre))
                        .clickable { vm.searchGenre(genre) }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(14.dp),
                ) {
                    Text(
                        genre,
                        style = RevibeType.heading.copy(fontSize = 18.sp),
                        color = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.28f),
                        modifier = Modifier.size(52.dp).align(Alignment.BottomEnd).padding(end = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSkeleton() {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SkeletonBlock(Modifier.width(280.dp).height(30.dp))
        Spacer(Modifier.height(8.dp))
        repeat(6) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SkeletonBlock(Modifier.size(44.dp))
                Column(Modifier.weight(1f)) {
                    SkeletonBlock(Modifier.width(220.dp).height(13.dp))
                    Spacer(Modifier.height(6.dp))
                    SkeletonBlock(Modifier.width(140.dp).height(11.dp))
                }
                SkeletonBlock(Modifier.width(40.dp).height(11.dp))
            }
        }
    }
}
