package com.revibe.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.revibe.model.Track
import com.revibe.ui.components.Artwork
import com.revibe.ui.components.ButtonKind
import com.revibe.ui.components.HoverSurface
import com.revibe.ui.components.LocalArtworkLoader
import com.revibe.ui.components.Modal
import com.revibe.ui.components.PlayerBar
import com.revibe.ui.components.RevibeButton
import com.revibe.ui.components.RevibeTextField
import com.revibe.ui.components.RightPanel
import com.revibe.ui.components.Sidebar
import com.revibe.ui.components.TopBar
import com.revibe.ui.components.gradientFor
import com.revibe.ui.screens.AlbumScreen
import com.revibe.ui.screens.ArtistScreen
import com.revibe.ui.screens.HomeScreen
import com.revibe.ui.screens.LibraryScreen
import com.revibe.ui.screens.LikedScreen
import com.revibe.ui.screens.LocalScreen
import com.revibe.ui.screens.PlaylistScreen
import com.revibe.ui.screens.SearchScreen
import com.revibe.ui.screens.SettingsScreen
import com.revibe.ui.theme.LocalAccent
import com.revibe.ui.theme.RevibeColors
import com.revibe.ui.theme.RevibeTheme
import com.revibe.ui.theme.RevibeType

@Composable
fun RevibeApp(
    vm: AppViewModel,
    topBar: @Composable () -> Unit = { TopBar(vm) { } },
) {
    val accent by animateColorAsState(vm.accent, animationSpec = tween(700), label = "accent")
    val player by vm.playerState.collectAsState()

    CompositionLocalProvider(
        LocalArtworkLoader provides vm.artworkLoader,
        LocalAccent provides accent,
    ) {
        RevibeTheme {
            Box(Modifier.fillMaxSize().background(RevibeColors.Base)) {
                Column(Modifier.fillMaxSize()) {
                    topBar()
                    Row(Modifier.weight(1f)) {
                        Sidebar(vm)
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            Crossfade(targetState = vm.route, label = "route") { route ->
                                when (route) {
                                    Route.Home -> HomeScreen(vm)
                                    Route.Search -> SearchScreen(vm)
                                    Route.Library -> LibraryScreen(vm)
                                    Route.Liked -> LikedScreen(vm)
                                    Route.Local -> LocalScreen(vm)
                                    Route.Settings -> SettingsScreen(vm)
                                    is Route.AlbumRoute -> AlbumScreen(vm)
                                    is Route.ArtistRoute -> ArtistScreen(vm)
                                    is Route.PlaylistRoute -> PlaylistScreen(vm)
                                }
                            }
                        }
                        if (vm.rightPanelVisible) RightPanel(vm)
                    }
                    PlayerBar(vm)
                }

                AnimatedVisibility(
                    visible = vm.toast != null,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 112.dp),
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(RevibeColors.SurfaceRaised)
                            .padding(horizontal = 18.dp, vertical = 11.dp),
                    ) {
                        Text(
                            vm.toast.orEmpty(),
                            style = RevibeType.label,
                            color = RevibeColors.TextPrimary,
                        )
                    }
                }

                if (vm.showCreatePlaylist) {
                    CreatePlaylistModal(vm)
                }
                vm.addToPlaylistTrack?.let { track ->
                    AddToPlaylistModal(vm, track)
                }

                LaunchedEffect(player.error) {
                    player.error?.let { message ->
                        vm.showToast(message)
                        vm.clearPlayerError()
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatePlaylistModal(vm: AppViewModel) {
    var name by remember { mutableStateOf("") }
    Modal(onDismiss = { vm.showCreatePlaylist = false }) {
        Column {
            Text("Create playlist", style = RevibeType.heading, color = RevibeColors.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("Give it a name. You can add tracks from any menu.", style = RevibeType.caption, color = RevibeColors.TextSecondary)
            Spacer(Modifier.height(14.dp))
            RevibeTextField(name, { name = it }, placeholder = "My playlist")
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.align(Alignment.End)) {
                RevibeButton("Cancel", { vm.showCreatePlaylist = false }, kind = ButtonKind.Ghost)
                RevibeButton("Create", { vm.createPlaylist(name) })
            }
        }
    }
}

@Composable
private fun AddToPlaylistModal(vm: AppViewModel, track: Track) {
    Modal(onDismiss = { vm.addToPlaylistTrack = null }) {
        Column {
            Text("Add to playlist", style = RevibeType.heading, color = RevibeColors.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                track.title,
                style = RevibeType.caption,
                color = RevibeColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            if (vm.playlists.isEmpty()) {
                Text(
                    "You have no playlists yet.",
                    style = RevibeType.body,
                    color = RevibeColors.TextSecondary,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                Column(Modifier.height(260.dp)) {
                    androidx.compose.foundation.lazy.LazyColumn {
                        items(vm.playlists, key = { it.id }) { playlist ->
                            HoverSurface(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { vm.addToPlaylist(playlist.id, track) },
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        Modifier.size(36.dp).clip(RoundedCornerShape(4.dp)).background(gradientFor(playlist.id)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(playlist.name, style = RevibeType.bodyStrong, color = RevibeColors.TextPrimary, maxLines = 1)
                                        Text("${playlist.trackCount} songs", style = RevibeType.caption, color = RevibeColors.TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.align(Alignment.End)) {
                RevibeButton(
                    "New playlist",
                    {
                        vm.addToPlaylistTrack = null
                        vm.showCreatePlaylist = true
                    },
                    kind = ButtonKind.Outline,
                )
                RevibeButton("Close", { vm.addToPlaylistTrack = null }, kind = ButtonKind.Ghost)
            }
        }
    }
}
