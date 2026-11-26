package com.revibe.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revibe.ui.AppViewModel
import com.revibe.ui.Shelf
import com.revibe.ui.components.Artwork
import com.revibe.ui.components.Chip
import com.revibe.ui.components.EmptyState
import com.revibe.ui.components.MediaCard
import com.revibe.ui.components.RevibeButton
import com.revibe.ui.components.SectionHeader
import com.revibe.ui.components.ShelfRow
import com.revibe.ui.components.SkeletonBlock
import com.revibe.ui.toMediaItem
import com.revibe.ui.theme.RevibeColors
import com.revibe.ui.theme.RevibeType
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.foundation.clickable

@Composable
fun HomeScreen(vm: AppViewModel) {
    val state by vm.playerState.collectAsState()
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(vm.accent.copy(alpha = 0.10f), Color.Transparent),
                    ),
                ),
        )

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 16.dp, top = 10.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(30.dp),
        ) {
            item {
                Column {
                    Text(
                        "Your music. Your vibe.",
                        style = RevibeType.display.copy(fontSize = 34.sp, lineHeight = 38.sp),
                        color = RevibeColors.TextPrimary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Pick up where you left off, or find something new.",
                        style = RevibeType.body,
                        color = RevibeColors.TextSecondary,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Bollywood", "Punjabi", "Lo-fi", "Indie", "Hip-hop").forEach { genre ->
                            Chip(text = genre, selected = false, onClick = { vm.searchGenre(genre) })
                        }
                    }
                }
            }

            if (vm.recentTracks.isNotEmpty()) {
                item {
                    ShelfRow(
                        shelfId = "recent",
                        title = "Jump back in",
                        items = vm.recentTracks.map { it.toMediaItem() },
                        onOpenItem = { item ->
                            item.track?.let { track -> vm.play(track, vm.recentTracks, "Recently played") }
                        },
                        onPlayItem = { item ->
                            item.track?.let { track -> vm.play(track, vm.recentTracks, "Recently played") }
                        },
                    )
                }
            }

            if (vm.followedArtistCards.isNotEmpty()) {
                item {
                    ShelfRow(
                        shelfId = "artists",
                        title = "Artists you follow",
                        items = vm.followedArtistCards.map { it.toMediaItem() },
                        onOpenItem = { item -> vm.openArtist(item.artist?.id) },
                    )
                }
            }

            if (vm.homeLoading) {
                items(2) { index ->
                    Column {
                        SkeletonBlock(Modifier.width(220.dp).height(22.dp))
                        Spacer(Modifier.height(16.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(6) { tile ->
                                Column {
                                    SkeletonBlock(Modifier.size(168.dp))
                                    Spacer(Modifier.height(10.dp))
                                    SkeletonBlock(Modifier.width(120.dp).height(13.dp))
                                    Spacer(Modifier.height(6.dp))
                                    SkeletonBlock(Modifier.width(84.dp).height(11.dp))
                                }
                            }
                        }
                    }
                }
            }

            if (vm.homeError != null && !vm.homeLoading) {
                item {
                    EmptyState(
                        title = "Could not load your home feed",
                        message = vm.homeError ?: "",
                        action = "Retry",
                        onAction = vm::refreshHome,
                    )
                }
            }

            items(vm.shelves, key = { it.id }) { shelf ->
                ShelfRow(
                    shelfId = shelf.id,
                    title = shelf.title,
                    subtitle = shelf.subtitle,
                    items = shelf.items,
                    onOpenItem = { item -> item.album?.let(vm::openAlbum) },
                    onPlayItem = { item -> item.album?.let { vm.playAlbumById(it.id) } },
                )
            }

            if (!vm.homeLoading && vm.shelves.isNotEmpty()) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(RevibeColors.Surface)
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Bring your own library", style = RevibeType.title, color = RevibeColors.TextPrimary)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Scan a folder of MP3, FLAC, M4A, OGG or WAV files and Revibe will read their tags and artwork.",
                                style = RevibeType.caption,
                                color = RevibeColors.TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        RevibeButton("Open local files", { vm.openLocal() }, kind = com.revibe.ui.components.ButtonKind.Outline)
                    }
                }
            }

            if (state.current == null && !vm.homeLoading && vm.shelves.isNotEmpty()) {
                item {
                    Text(
                        "Catalog tracks stream as 30-second previews. Tracks in your local library play in full.",
                        style = RevibeType.caption,
                        color = RevibeColors.TextTertiary,
                    )
                }
            }
        }
    }
}
