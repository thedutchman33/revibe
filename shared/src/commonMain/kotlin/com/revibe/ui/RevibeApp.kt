package com.revibe.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
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
import androidx.compose.ui.unit.sp
import com.revibe.model.LayoutSpec
import com.revibe.model.Track
import com.revibe.model.formatDuration
import com.revibe.ui.components.Artwork
import com.revibe.ui.components.ButtonKind
import com.revibe.ui.components.HoverSurface
import com.revibe.ui.components.LocalArtworkLoader
import com.revibe.ui.components.Modal
import com.revibe.ui.components.PlayerBar
import com.revibe.ui.components.RevibeButton
import com.revibe.ui.components.RevibeTextField
import com.revibe.ui.components.IconAction
import com.revibe.ui.components.RightPanel
import com.revibe.ui.components.SeekBar
import com.revibe.ui.components.Sidebar
import com.revibe.ui.components.TopBar
import com.revibe.ui.components.UpdateNotice
import com.revibe.ui.components.VerticalResizeHandle
import com.revibe.ui.components.gradientFor
import com.revibe.ui.screens.AccountAuthModal
import com.revibe.ui.screens.AccountScreen
import com.revibe.ui.screens.AlbumScreen
import com.revibe.ui.screens.ArtistScreen
import com.revibe.ui.screens.HomeScreen
import com.revibe.ui.screens.PlaylistImportScreen
import com.revibe.ui.screens.SpotifyImportScreen
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
            BoxWithConstraints(Modifier.fillMaxSize().background(RevibeColors.Base)) {
                val availableWidth = maxWidth.value
                var leftTarget = if (vm.compactSidebar) {
                    LayoutSpec.LEFT_COLLAPSED
                } else {
                    LayoutSpec.clampLeft(vm.leftPanelWidth)
                }
                val requestedRight = if (vm.rightPanelVisible) {
                    LayoutSpec.clampRight(vm.rightPanelWidth)
                } else {
                    0f
                }
                // Side panels yield before the centre drops below its usable width.
                var maxRight = (availableWidth - leftTarget - LayoutSpec.GUTTER * 2 - LayoutSpec.CENTER_MIN)
                    .coerceAtLeast(0f)
                // At the 1120dp minimum the requested panels are a few dp short. Shrink the
                // library to its own minimum before dropping the right rail, so Queue and Lyrics
                // stay reachable instead of opening into a hidden panel.
                if (!vm.compactSidebar && requestedRight > 0f && maxRight < LayoutSpec.RIGHT_MIN) {
                    val shrink = (LayoutSpec.RIGHT_MIN - maxRight)
                        .coerceAtMost((leftTarget - LayoutSpec.LEFT_MIN).coerceAtLeast(0f))
                    leftTarget -= shrink
                    maxRight += shrink
                }
                val rightTarget = when {
                    requestedRight <= 0f -> 0f
                    requestedRight <= maxRight -> requestedRight
                    maxRight >= LayoutSpec.RIGHT_MIN -> maxRight
                    else -> 0f
                }
                var draggingLeft by remember { mutableStateOf(false) }
                var draggingRight by remember { mutableStateOf(false) }
                val animatedLeft by animateDpAsState(
                    targetValue = leftTarget.dp,
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                    label = "leftPanelWidth",
                )
                val animatedRight by animateDpAsState(
                    targetValue = rightTarget.dp,
                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                    label = "rightPanelWidth",
                )
                val leftWidth = if (draggingLeft) leftTarget.dp else animatedLeft
                val rightWidth = if (draggingRight) rightTarget.dp else animatedRight

                Column(Modifier.fillMaxSize()) {
                    topBar()
                    Row(Modifier.weight(1f).fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                        Box(
                            Modifier
                                .width(leftWidth)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(LayoutSpec.CONTAINER_RADIUS.dp)),
                        ) {
                            Sidebar(vm, width = leftWidth)
                        }
                        if (!vm.compactSidebar) {
                            VerticalResizeHandle(
                                onDrag = { delta ->
                                    vm.resizeLeftPanel(vm.leftPanelWidth + delta)
                                },
                                onDragStart = { draggingLeft = true },
                                onDragEnd = { draggingLeft = false },
                            )
                        } else {
                            Spacer(Modifier.width(LayoutSpec.GUTTER.dp))
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(LayoutSpec.CONTAINER_RADIUS.dp))
                                .background(RevibeColors.Surface),
                        ) {
                            Crossfade(targetState = vm.route, label = "route") { route ->
                                when (route) {
                                    Route.Home -> HomeScreen(vm)
                                    Route.Search -> SearchScreen(vm)
                                    Route.Library -> LibraryScreen(vm)
                                    Route.Liked -> LikedScreen(vm)
                                    Route.Local -> LocalScreen(vm)
                                    Route.Settings -> SettingsScreen(vm)
                                    Route.Account -> AccountScreen(vm)
                                    Route.ImportPlaylist -> PlaylistImportScreen(vm)
                                    Route.SpotifyImport -> SpotifyImportScreen(vm)
                                    is Route.AlbumRoute -> AlbumScreen(vm)
                                    is Route.ArtistRoute -> ArtistScreen(vm)
                                    is Route.PlaylistRoute -> PlaylistScreen(vm)
                                }
                            }
                        }
                        if (rightTarget > 0f || rightWidth > 0.dp) {
                            if (rightTarget > 0f) {
                                VerticalResizeHandle(
                                    onDrag = { delta ->
                                        // The rail is anchored to the right edge, so moving the handle
                                        // right must narrow it for the edge to follow the cursor.
                                        vm.resizeRightPanel(vm.rightPanelWidth - delta)
                                    },
                                    onDragStart = { draggingRight = true },
                                    onDragEnd = { draggingRight = false },
                                )
                            }
                            Box(
                                Modifier
                                    .width(rightWidth)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(LayoutSpec.CONTAINER_RADIUS.dp)),
                            ) {
                                RightPanel(
                                    vm = vm,
                                    width = rightWidth,
                                    onClose = vm::closeRightPanel,
                                )
                            }
                        }
                    }
                    PlayerBar(vm)
                }

                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 24.dp, bottom = 112.dp),
                ) {
                    UpdateNotice(vm)
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
                AccountAuthModal(vm)
                if (vm.nowPlayingExpanded && player.current != null) {
                    NowPlayingFullscreen(vm)
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
private fun NowPlayingFullscreen(vm: AppViewModel) {
    val state by vm.playerState.collectAsState()
    val track = state.current ?: return
    val fraction = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    Box(Modifier.fillMaxSize().background(RevibeColors.Base)) {
        IconAction(
            Icons.Rounded.Close,
            contentDescription = "Exit full screen",
            onClick = { vm.nowPlayingExpanded = false },
            size = 40.dp,
            iconSize = 20.dp,
            modifier = Modifier.align(Alignment.TopEnd).padding(18.dp),
        )
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Artwork(
                url = track.artworkUrl ?: track.localPath,
                modifier = Modifier.size(380.dp),
                cornerRadius = 8.dp,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                track.title,
                style = RevibeType.display.copy(fontSize = 38.sp, lineHeight = 44.sp),
                color = RevibeColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(track.artist, style = RevibeType.body.copy(fontSize = 17.sp), color = RevibeColors.TextSecondary, maxLines = 1)
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                IconAction(Icons.Rounded.Shuffle, "Shuffle", { vm.toggleShuffle() }, size = 36.dp, iconSize = 19.dp, tint = if (state.shuffle) RevibeColors.Accent else RevibeColors.TextSecondary)
                IconAction(Icons.Rounded.SkipPrevious, "Previous", { vm.previousTrack() }, size = 42.dp, iconSize = 24.dp, tint = RevibeColors.TextPrimary)
                Box(
                    Modifier.size(68.dp).clip(CircleShape).background(RevibeColors.TextPrimary)
                        .clickable { vm.togglePlayPause() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = RevibeColors.Base,
                        modifier = Modifier.size(34.dp),
                    )
                }
                IconAction(Icons.Rounded.SkipNext, "Next", { vm.nextTrack() }, size = 42.dp, iconSize = 24.dp, tint = RevibeColors.TextPrimary)
                IconAction(
                    if (state.repeat == com.revibe.playback.RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    "Repeat",
                    { vm.cycleRepeatMode() },
                    size = 36.dp,
                    iconSize = 19.dp,
                    tint = if (state.repeat != com.revibe.playback.RepeatMode.OFF) RevibeColors.Accent else RevibeColors.TextSecondary,
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.width(520.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(state.positionMs), style = RevibeType.caption, color = RevibeColors.TextTertiary, modifier = Modifier.width(44.dp))
                SeekBar(fraction, { vm.seekToFraction(it) }, Modifier.weight(1f), enabled = state.durationMs > 0)
                Text(
                    formatDuration(state.durationMs),
                    style = RevibeType.caption,
                    color = RevibeColors.TextTertiary,
                    modifier = Modifier.width(44.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun CreatePlaylistModal(vm: AppViewModel) {
    var name by remember { mutableStateOf("") }
    var naming by remember { mutableStateOf(false) }
    Modal(onDismiss = { vm.showCreatePlaylist = false }) {
        Column {
            Text(if (naming) "Create playlist" else "New playlist", style = RevibeType.heading, color = RevibeColors.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                if (naming) "Give it a name. You can add tracks from any menu." else "Start a fresh playlist or bring one in from another service.",
                style = RevibeType.caption,
                color = RevibeColors.TextSecondary,
            )
            Spacer(Modifier.height(14.dp))
            if (!naming) {
                PlaylistChoice(
                    title = "Create playlist",
                    subtitle = "Start with an empty playlist and add tracks yourself.",
                ) { naming = true }
                Spacer(Modifier.height(8.dp))
                PlaylistChoice(
                    title = "Import playlist",
                    subtitle = "Use TuneMyMusic or import a CSV, TXT, M3U or XSPF export.",
                ) { vm.beginPlaylistImport() }
                Spacer(Modifier.height(18.dp))
                RevibeButton("Cancel", { vm.showCreatePlaylist = false }, kind = ButtonKind.Ghost, modifier = Modifier.align(Alignment.End))
            } else {
                RevibeTextField(name, { name = it }, placeholder = "My playlist")
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.align(Alignment.End)) {
                    RevibeButton("Back", { naming = false }, kind = ButtonKind.Ghost)
                    RevibeButton("Create", { vm.createPlaylist(name) })
                }
            }
        }
    }
}

@Composable
private fun PlaylistChoice(title: String, subtitle: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(RevibeColors.SurfaceRaised)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Column {
            Text(title, style = RevibeType.bodyStrong, color = RevibeColors.TextPrimary)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, style = RevibeType.caption, color = RevibeColors.TextSecondary)
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
