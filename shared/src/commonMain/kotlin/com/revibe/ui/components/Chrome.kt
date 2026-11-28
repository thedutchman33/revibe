package com.revibe.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.AccountCircle
import com.revibe.account.AccountState
import com.revibe.sync.SyncPhase
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revibe.library.PlaylistRecency
import com.revibe.model.LayoutSpec
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
import com.revibe.update.UpdatePhase
import com.revibe.update.UpdateState

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

/**
 * Spotify's global bar: history arrows sit over the library, the home button and
 * search pill are centred on the window, account actions sit over the right rail.
 */
@Composable
fun TopBar(
    vm: AppViewModel,
    windowControls: @Composable RowScope.() -> Unit,
) {
    var profileMenu by remember { mutableStateOf(false) }
    val account by vm.accountState.collectAsState()
    val sync by vm.syncStatus.collectAsState()
    val update by vm.updateState.collectAsState()
    val signedInUser = (account as? AccountState.SignedIn)?.user
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(LayoutSpec.TOP_BAR_HEIGHT.dp)
            .background(RevibeColors.Base),
    ) {
    // The fixed centre cluster must not squeeze the window controls on a narrow
    // window, so the search pill gives up width first.
    val searchWidth = if (maxWidth < 1280.dp) 360.dp else 474.dp
    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            IconAction(
                Icons.Rounded.KeyboardArrowLeft,
                contentDescription = "Back",
                onClick = vm::goBack,
                enabled = vm.backStack.isNotEmpty(),
                size = 32.dp,
                iconSize = 22.dp,
                tint = RevibeColors.TextSecondary,
                hoverTint = RevibeColors.TextPrimary,
            )
            IconAction(
                Icons.Rounded.KeyboardArrowRight,
                contentDescription = "Forward",
                onClick = vm::goForward,
                enabled = vm.forwardStack.isNotEmpty(),
                size = 32.dp,
                iconSize = 22.dp,
                tint = RevibeColors.TextSecondary,
                hoverTint = RevibeColors.TextPrimary,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReelHouseTopBarAction()
            UpdateTopBarAction(vm, update)
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
                    modifier = Modifier.size(22.dp),
                )
            }
            SearchField(
                value = vm.searchQuery,
                onValueChange = vm::onSearchQueryChange,
                onSubmit = { vm.submitSearch() },
                modifier = Modifier.widthIn(max = searchWidth).width(searchWidth),
                height = 40.dp,
                trailing = {
                    IconAction(
                        Icons.Rounded.LibraryMusic,
                        contentDescription = "Browse",
                        onClick = vm::openSearch,
                        size = 30.dp,
                        iconSize = 18.dp,
                    )
                },
            )
        }

        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            IconAction(
                Icons.Rounded.Notifications,
                contentDescription = "Notifications",
                onClick = { vm.showToast("No new notifications") },
                size = 32.dp,
                iconSize = 18.dp,
                tint = RevibeColors.TextSecondary,
            )
            Spacer(Modifier.width(6.dp))
            Box {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (signedInUser != null) RevibeColors.Accent else RevibeColors.TextPrimary)
                        .clickable { profileMenu = true }
                        .pointerHoverIcon(PointerIcon.Hand),
                    contentAlignment = Alignment.Center,
                ) {
                    if (signedInUser != null) {
                        Text(
                            signedInUser.initials,
                            style = RevibeType.micro.copy(letterSpacing = 0.sp),
                            color = RevibeColors.OnAccent,
                        )
                    } else {
                        Icon(Icons.Rounded.Person, contentDescription = "Profile", tint = RevibeColors.Base, modifier = Modifier.size(18.dp))
                    }
                }
                if (signedInUser != null) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(syncDotColor(sync.phase)),
                    )
                }
                DropdownMenu(
                    expanded = profileMenu,
                    onDismissRequest = { profileMenu = false },
                    modifier = Modifier
                        .width(286.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(RevibeColors.SurfaceRaised)
                        .border(1.dp, RevibeColors.OutlineSoft, RoundedCornerShape(12.dp)),
                ) {
                    Column(Modifier.padding(6.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (signedInUser != null) RevibeColors.Accent else RevibeColors.Surface),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (signedInUser != null) {
                                    Text(
                                        signedInUser.initials,
                                        style = RevibeType.micro.copy(letterSpacing = 0.sp),
                                        color = RevibeColors.OnAccent,
                                    )
                                } else {
                                    Icon(
                                        Icons.Rounded.Person,
                                        contentDescription = null,
                                        tint = RevibeColors.TextSecondary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    signedInUser?.email ?: "Not signed in",
                                    style = RevibeType.bodyStrong.copy(fontSize = 12.5.sp),
                                    color = RevibeColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    if (signedInUser != null) {
                                        Box(
                                            Modifier.size(6.dp).clip(CircleShape).background(syncDotColor(sync.phase)),
                                        )
                                    }
                                    Text(
                                        if (signedInUser != null) {
                                            syncMenuLabel(sync.phase)
                                        } else {
                                            "Your library stays on this device"
                                        },
                                        style = RevibeType.caption.copy(fontSize = 11.sp),
                                        color = RevibeColors.TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        HDivider(Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                        if (signedInUser == null) {
                            ProfileMenuRow(
                                icon = Icons.Rounded.Person,
                                title = "Log in",
                                subtitle = "Sign in or create an account",
                                accent = true,
                                onClick = {
                                    profileMenu = false
                                    vm.openAuthModal()
                                },
                            )
                        }
                        ProfileMenuRow(
                            icon = Icons.Rounded.AccountCircle,
                            title = "Account & sync",
                            subtitle = "Manage account and cloud sync",
                            onClick = {
                                profileMenu = false
                                vm.openAccount()
                            },
                        )
                        ProfileMenuRow(
                            icon = Icons.Rounded.Settings,
                            title = "Settings",
                            subtitle = "Playback, appearance and data",
                            onClick = {
                                profileMenu = false
                                vm.openSettings()
                            },
                        )
                        ProfileMenuRow(
                            icon = Icons.Rounded.FolderOpen,
                            title = "Local files",
                            subtitle = if (vm.localTracks.size == 1) "1 track on this device" else "${vm.localTracks.size} tracks on this device",
                            onClick = {
                                profileMenu = false
                                vm.openLocal()
                            },
                        )
                        if (signedInUser != null) {
                            ProfileMenuRow(
                                icon = Icons.Rounded.Logout,
                                title = "Log out",
                                subtitle = "Sign out on this device",
                                onClick = {
                                    profileMenu = false
                                    vm.signOut()
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            windowControls()
        }
    }
    }
}

private const val REELHOUSE_URL = "https://reelhouse1.vercel.app/"
private val ReelHouseRed = Color(0xFFFF4D4D)

/** Prominent partner link in the global bar, immediately left of the update pill. */
@Composable
private fun ReelHouseTopBarAction() {
    val uriHandler = LocalUriHandler.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(17.dp)
    Row(
        Modifier
            .height(34.dp)
            .clip(shape)
            .background(if (hovered) RevibeColors.SurfaceHover else RevibeColors.Surface)
            .border(1.dp, RevibeColors.Outline, shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) {
                runCatching { uriHandler.openUri(REELHOUSE_URL) }
            }
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Icons.Rounded.OpenInNew,
            contentDescription = null,
            tint = RevibeColors.TextSecondary,
            modifier = Modifier.size(14.dp),
        )
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = RevibeColors.TextPrimary)) { append("Visit Reel") }
                withStyle(SpanStyle(color = ReelHouseRed)) { append("House") }
            },
            style = RevibeType.caption,
            maxLines = 1,
        )
    }
}

@Composable
private fun UpdateTopBarAction(vm: AppViewModel, state: UpdateState) {
    val shape = RoundedCornerShape(17.dp)
    val emphasized = state.phase == UpdatePhase.AVAILABLE
    val failed = state.phase == UpdatePhase.ERROR
    val enabled = !state.isBusy
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background = when {
        emphasized && hovered -> RevibeColors.AccentHover
        emphasized -> RevibeColors.Accent
        hovered && enabled -> RevibeColors.SurfaceHover
        state.isBusy -> RevibeColors.SurfaceRaised
        else -> RevibeColors.Surface
    }
    val foreground = when {
        emphasized -> RevibeColors.OnAccent
        failed -> RevibeColors.Danger
        else -> RevibeColors.TextSecondary
    }
    val label = when (state.phase) {
        UpdatePhase.DISABLED, UpdatePhase.IDLE -> "Check updates"
        UpdatePhase.UP_TO_DATE -> "Up to date"
        UpdatePhase.CHECKING -> "Checking…"
        UpdatePhase.AVAILABLE -> "Update available"
        UpdatePhase.DOWNLOADING -> state.progress
            ?.let { "Updating ${(it.coerceIn(0f, 1f) * 100).toInt()}%" }
            ?: "Updating…"
        UpdatePhase.VERIFYING -> "Verifying…"
        UpdatePhase.INSTALLING -> "Installing…"
        UpdatePhase.INSTALLED -> "Updated"
        UpdatePhase.ERROR -> "Retry update"
    }
    Row(
        Modifier
            .height(34.dp)
            .clip(shape)
            .background(background)
            .border(
                1.dp,
                when {
                    emphasized -> Color.Transparent
                    failed -> RevibeColors.Danger.copy(alpha = 0.45f)
                    else -> RevibeColors.Outline
                },
                shape,
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                when (state.phase) {
                    UpdatePhase.AVAILABLE -> vm.installUpdate()
                    UpdatePhase.ERROR -> vm.retryUpdate()
                    UpdatePhase.INSTALLED -> vm.dismissUpdateNotice()
                    UpdatePhase.DISABLED -> vm.showToast("This development build can't install updates")
                    else -> vm.checkForUpdates()
                }
            }
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (state.isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(13.dp),
                strokeWidth = 2.dp,
                color = foreground,
            )
        } else {
            Icon(
                Icons.Rounded.SystemUpdate,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            label,
            style = RevibeType.caption,
            color = foreground,
            maxLines = 1,
        )
    }
}

@Composable
private fun ProfileMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    HoverSurface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        backgroundColor = if (accent) RevibeColors.Accent.copy(alpha = 0.08f) else Color.Transparent,
        hoverColor = if (accent) RevibeColors.Accent.copy(alpha = 0.15f) else RevibeColors.SurfaceHover,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (accent) RevibeColors.Accent.copy(alpha = 0.16f) else RevibeColors.Surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (accent) RevibeColors.Accent else RevibeColors.TextSecondary,
                    modifier = Modifier.size(17.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = RevibeType.bodyStrong.copy(fontSize = 13.sp),
                    color = if (accent) RevibeColors.Accent else RevibeColors.TextPrimary,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = RevibeType.caption.copy(fontSize = 11.sp),
                    color = RevibeColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun syncDotColor(phase: SyncPhase): Color = when (phase) {
    SyncPhase.IDLE -> RevibeColors.Accent
    SyncPhase.SYNCING -> RevibeColors.TextPrimary
    SyncPhase.OFFLINE -> RevibeColors.TextSecondary
    SyncPhase.ERROR -> RevibeColors.Danger
    else -> RevibeColors.TextTertiary
}

private fun syncMenuLabel(phase: SyncPhase): String = when (phase) {
    SyncPhase.IDLE -> "Up to date"
    SyncPhase.SYNCING -> "Syncing…"
    SyncPhase.OFFLINE -> "Offline — queued"
    SyncPhase.ERROR -> "Sync needs attention"
    SyncPhase.NOT_CONFIGURED -> "Cloud not configured"
    SyncPhase.SIGNED_OUT -> "Not signed in"
    SyncPhase.RESTORING -> "Restoring session…"
}

@Composable
fun Sidebar(
    vm: AppViewModel,
    width: Dp = LayoutSpec.LEFT_DEFAULT.dp,
) {
    val compact = vm.compactSidebar
    var librarySearch by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var alphabetical by remember { mutableStateOf(false) }

    val playlists = remember(vm.playlists, vm.recentlyPlayedPlaylists, librarySearch, alphabetical) {
        PlaylistRecency.order(vm.playlists, vm.recentlyPlayedPlaylists)
            .map { it.playlist }
            .filter { it.name.contains(librarySearch, ignoreCase = true) }
            .let { if (alphabetical) it.sortedBy { p -> p.name.lowercase() } else it }
    }
    val albums = remember(vm.likedAlbums, librarySearch, alphabetical) {
        vm.likedAlbums
            .filter { it.title.contains(librarySearch, ignoreCase = true) || it.artist.contains(librarySearch, ignoreCase = true) }
            .let { if (alphabetical) it.sortedBy { a -> a.title.lowercase() } else it }
    }
    val artists = remember(vm.followedArtists, librarySearch, alphabetical) {
        vm.followedArtists
            .filter { it.name.contains(librarySearch, ignoreCase = true) }
            .let { if (alphabetical) it.sortedBy { a -> a.name.lowercase() } else it }
    }

    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(RevibeColors.Surface)
            .padding(horizontal = if (compact) 6.dp else 12.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!compact) {
                Row(
                    Modifier.padding(start = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = RevibeColors.TextSecondary, modifier = Modifier.size(22.dp))
                    Text(
                        "Your Library",
                        style = RevibeType.title.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        color = RevibeColors.TextPrimary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (!compact) {
                Row(
                    Modifier
                        .height(32.dp)
                        .widthIn(min = 84.dp)
                        .clip(CircleShape)
                        .background(RevibeColors.SurfaceRaised)
                        .clickable { vm.showCreatePlaylist = true }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = RevibeColors.TextPrimary, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(
                        "Create",
                        style = RevibeType.caption.copy(fontWeight = FontWeight.Bold, fontSize = 11.5.sp),
                        color = RevibeColors.TextPrimary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Spacer(Modifier.width(2.dp))
            }
            IconAction(
                icon = Icons.Rounded.OpenInFull,
                contentDescription = if (compact) "Expand library" else "Collapse library",
                onClick = vm::toggleCompactSidebar,
                size = 32.dp,
                iconSize = 18.dp,
                tint = RevibeColors.TextSecondary,
            )
        }

        if (!compact) {
            Spacer(Modifier.height(10.dp))
            LibraryChipRow(
                labels = listOf("All", "Playlists", "Albums", "Artists"),
                selected = libraryTabLabel(vm.libraryTab),
                onSelect = { label -> vm.selectLibraryTab(labelToTab(label)) },
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                if (searching) {
                    RevibeTextField(
                        value = librarySearch,
                        onValueChange = { librarySearch = it },
                        placeholder = "Search in Your Library",
                        modifier = Modifier.weight(1f).height(30.dp),
                    )
                    IconAction(
                        Icons.Rounded.Close,
                        contentDescription = "Close library search",
                        onClick = { searching = false; librarySearch = "" },
                        size = 26.dp,
                        iconSize = 14.dp,
                    )
                } else {
                    IconAction(
                        Icons.Rounded.Search,
                        contentDescription = "Search in Your Library",
                        onClick = { searching = true },
                        size = 26.dp,
                        iconSize = 16.dp,
                    )
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { alphabetical = !alphabetical }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            if (alphabetical) "A–Z" else "Recents",
                            style = RevibeType.caption.copy(fontWeight = FontWeight.Bold),
                            color = RevibeColors.TextSecondary,
                        )
                        Icon(Icons.Rounded.List, contentDescription = null, tint = RevibeColors.TextSecondary, modifier = Modifier.size(15.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item {
                LikedSongsRow(
                    selected = vm.route == Route.Liked,
                    compact = compact,
                    onClick = vm::openLiked,
                )
            }
            if (vm.libraryTab == 0 || vm.libraryTab == 1) {
                items(playlists, key = { "pl-${it.id}" }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        selected = vm.route is Route.PlaylistRoute && (vm.route as Route.PlaylistRoute).id == playlist.id,
                        playing = vm.playingPlaylistId == playlist.id,
                        compact = compact,
                        onClick = { vm.openPlaylist(playlist.id) },
                    )
                }
            }
            if (vm.libraryTab == 0 || vm.libraryTab == 2) {
                items(albums, key = { "al-${it.id}" }) { album ->
                    LibraryRow(
                        title = album.title,
                        subtitle = albumSubtitle(album.artist),
                        compact = compact,
                        playing = vm.playingAlbumId == album.id,
                        onClick = { vm.openAlbum(album) },
                    ) {
                        Artwork(album.artworkUrl, Modifier.size(48.dp), cornerRadius = 4.dp)
                    }
                }
            }
            if (vm.libraryTab == 0 || vm.libraryTab == 3) {
                items(artists, key = { "ar-${it.id}" }) { artist ->
                    LibraryRow(
                        title = artist.name,
                        subtitle = "Artist",
                        compact = compact,
                        onClick = { vm.openArtist(artist.id) },
                    ) {
                        ArtistAvatar(artist.imageUrl, size = 48.dp)
                    }
                }
            }
            if (!compact) {
                item {
                    LibraryRow(
                        title = "Local files",
                        subtitle = "${vm.localTracks.size} tracks on this device",
                        compact = compact,
                        onClick = vm::openLocal,
                    ) {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)).background(RevibeColors.SurfaceRaised),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = RevibeColors.TextSecondary, modifier = Modifier.size(19.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryChipRow(
    labels: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(labels) { label ->
            Chip(
                text = label,
                selected = label == selected,
                onClick = { onSelect(label) },
                horizontalPadding = 10.dp,
            )
        }
    }
}

private fun albumSubtitle(artist: String): String {
    val name = artist.trim()
    val generic = name.isBlank() || name.equals("unknown artist", ignoreCase = true) ||
        name.lowercase() in setOf("album", "single", "ep", "playlist")
    return if (generic) "Album" else "Album · $name"
}

private fun libraryTabFor(tab: Int): String = when (tab) {
    1 -> "Playlists"
    2 -> "Albums"
    3 -> "Artists"
    else -> "All"
}

private fun libraryTabLabel(tab: Int): String = libraryTabFor(tab)

private fun labelToTab(label: String): Int = when (label) {
    "Playlists" -> 1
    "Albums" -> 2
    "Artists" -> 3
    else -> 0
}

@Composable
private fun LikedSongsRow(selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    HoverSurface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (selected) RevibeColors.SurfaceRaised else Color.Transparent)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF6C63FF), RevibeColors.Accent))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Favorite, contentDescription = null, tint = RevibeColors.OnAccent, modifier = Modifier.size(21.dp))
            }
            if (!compact) {
                Column {
                    Text("Liked Songs", style = RevibeType.bodyStrong, color = RevibeColors.TextPrimary, maxLines = 1)
                    Text("Playlist", style = RevibeType.caption, color = RevibeColors.TextSecondary, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    selected: Boolean,
    playing: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    LibraryRow(
        title = playlist.name,
        subtitle = "Playlist · ${playlist.trackCount} songs",
        compact = compact,
        selected = selected,
        playing = playing,
        onClick = onClick,
    ) {
        if (!playlist.artworkUrl.isNullOrBlank()) {
            Artwork(playlist.artworkUrl, Modifier.size(48.dp), cornerRadius = 4.dp)
        } else {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)).background(gradientFor(playlist.id)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = RevibeColors.OnAccent.copy(alpha = 0.85f), modifier = Modifier.size(19.dp))
            }
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
    playing: Boolean = false,
    leading: @Composable () -> Unit,
) {
    HoverSurface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (selected) RevibeColors.SurfaceRaised else Color.Transparent)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            leading()
            if (!compact) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = RevibeType.bodyStrong.copy(fontSize = 14.5.sp),
                        color = if (playing) RevibeColors.Accent else RevibeColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(subtitle, style = RevibeType.caption.copy(fontSize = 12.sp), color = RevibeColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (playing) {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        contentDescription = "Currently playing",
                        tint = RevibeColors.Accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
            }
        }
    }
}

@Composable
fun PlayerBar(vm: AppViewModel) {
    val state by vm.playerState.collectAsState()
    val track = state.current
    val liked = track != null && vm.isLiked(track.id)
    val fraction = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(LayoutSpec.PLAYER_HEIGHT.dp)
            .background(RevibeColors.PlayerBar),
    ) {
        // Spotify's desktop grid: control centre is 40% of the bar, capped at 573dp.
        val centerWidth = (maxWidth * 0.4f).coerceIn(320.dp, 573.dp)

        Row(
            Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: 56dp art, 14dp gap, bold title, regular artist, then queue controls.
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(
                    url = if (track == null) null else vm.currentArtworkUrl ?: track.localPath,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(enabled = track != null) { vm.selectRightTab(RightTab.NOW_PLAYING) }
                        .pointerHoverIcon(if (track != null) PointerIcon.Hand else PointerIcon.Default),
                    cornerRadius = 4.dp,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.widthIn(max = 205.dp)) {
                    if (track != null) {
                        Text(
                            track.title,
                            style = RevibeType.playerTitle,
                            color = RevibeColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clickable { vm.openPlayingContext() }
                                .pointerHoverIcon(PointerIcon.Hand),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            track.artist,
                            style = RevibeType.playerArtist,
                            color = RevibeColors.PlayerMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clickable { vm.openArtist(track.artistId) }
                                .pointerHoverIcon(if (track.artistId != null) PointerIcon.Hand else PointerIcon.Default),
                        )
                    } else {
                        Text("Nothing playing", style = RevibeType.playerTitle, color = RevibeColors.TextPrimary)
                        Spacer(Modifier.height(2.dp))
                        Text("Pick something from your library", style = RevibeType.playerArtist, color = RevibeColors.PlayerTime)
                    }
                }
                if (track != null) {
                    Spacer(Modifier.width(8.dp))
                    IconAction(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Remove from queue",
                        onClick = { if (state.index >= 0) vm.removeQueueAt(state.index) },
                        size = 32.dp,
                        iconSize = 18.dp,
                        tint = RevibeColors.PlayerMuted,
                    )
                    IconAction(
                        icon = if (liked) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline,
                        contentDescription = if (liked) "Remove from Liked Songs" else "Add to Liked Songs",
                        onClick = { vm.toggleLike(track) },
                        size = 32.dp,
                        iconSize = 18.dp,
                        tint = if (liked) RevibeColors.Accent else RevibeColors.PlayerMuted,
                    )
                }
            }

            // Centre: 32dp transport buttons, 16dp glyphs, white 32dp play disc.
            Column(
                Modifier.width(centerWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconAction(
                        Icons.Rounded.Shuffle,
                        "Shuffle",
                        onClick = { vm.toggleShuffle() },
                        size = 32.dp,
                        iconSize = 18.dp,
                        tint = if (state.shuffle) RevibeColors.Accent else RevibeColors.PlayerMuted,
                        enabled = state.queue.isNotEmpty(),
                    )
                    IconAction(
                        Icons.Rounded.SkipPrevious,
                        "Previous",
                        onClick = { vm.previousTrack() },
                        size = 32.dp,
                        iconSize = 18.dp,
                        tint = RevibeColors.PlayerMuted,
                        enabled = state.current != null,
                    )
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(RevibeColors.TextPrimary)
                            .clickable(enabled = track != null) { vm.togglePlayPause() }
                            .pointerHoverIcon(PointerIcon.Hand),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.isBuffering && track != null) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(15.dp),
                                color = RevibeColors.OnAccent,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = if (track != null) RevibeColors.OnAccent else RevibeColors.OnAccent.copy(alpha = 0.55f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    IconAction(
                        Icons.Rounded.SkipNext,
                        "Next",
                        onClick = { vm.nextTrack() },
                        size = 32.dp,
                        iconSize = 18.dp,
                        tint = RevibeColors.PlayerMuted,
                        enabled = state.hasNext,
                    )
                    IconAction(
                        icon = if (state.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = "Repeat",
                        onClick = { vm.cycleRepeatMode() },
                        size = 32.dp,
                        iconSize = 18.dp,
                        tint = if (state.repeat != RepeatMode.OFF) RevibeColors.Accent else RevibeColors.PlayerMuted,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        formatDuration(state.positionMs),
                        style = RevibeType.playerTime,
                        color = RevibeColors.PlayerTime,
                        modifier = Modifier.width(44.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                    Spacer(Modifier.width(8.dp))
                    SeekBar(
                        value = fraction,
                        onValueChange = { vm.seekToFraction(it) },
                        modifier = Modifier.weight(1f),
                        accent = Color.White,
                        enabled = track != null && state.durationMs > 0,
                        trackHeight = 4.dp,
                        trackColor = RevibeColors.PlayerTrack,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatDuration(state.durationMs),
                        style = RevibeType.playerTime,
                        color = RevibeColors.PlayerTime,
                        modifier = Modifier.width(44.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                    )
                }
            }

            // Right: lyrics, queue, volume, miniplayer, fullscreen. The device icon is
            // deliberately absent until device selection exists.
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            ) {
                IconAction(
                    PlayerLyricsIcon,
                    contentDescription = "Lyrics",
                    onClick = { vm.selectRightTab(RightTab.LYRICS) },
                    size = 36.dp,
                    iconSize = 22.dp,
                    tint = if (vm.rightTab == RightTab.LYRICS && vm.rightPanelVisible) RevibeColors.Accent else RevibeColors.PlayerMuted,
                )
                IconAction(
                    PlayerQueueIcon,
                    contentDescription = "Queue",
                    onClick = { vm.selectRightTab(RightTab.QUEUE) },
                    size = 36.dp,
                    iconSize = 22.dp,
                    tint = if (vm.rightTab == RightTab.QUEUE && vm.rightPanelVisible) RevibeColors.Accent else RevibeColors.PlayerMuted,
                )
                IconAction(
                    if (state.volume == 0f) PlayerVolumeMutedIcon else PlayerVolumeIcon,
                    contentDescription = "Mute",
                    onClick = { vm.setVolume(if (state.volume == 0f) 0.7f else 0f) },
                    size = 36.dp,
                    iconSize = 22.dp,
                    tint = RevibeColors.PlayerMuted,
                )
                SeekBar(
                    value = state.volume,
                    onValueChange = { vm.setVolume(it) },
                    modifier = Modifier.width(100.dp),
                    accent = Color.White,
                    trackHeight = 4.dp,
                    trackColor = RevibeColors.PlayerTrack,
                )
                IconAction(
                    PlayerMiniplayerIcon,
                    contentDescription = "Miniplayer",
                    onClick = vm::toggleMiniplayer,
                    size = 36.dp,
                    iconSize = 22.dp,
                    tint = RevibeColors.PlayerMuted,
                )
                IconAction(
                    PlayerFullscreenIcon,
                    contentDescription = "Full screen",
                    onClick = { if (track != null) vm.nowPlayingExpanded = true },
                    size = 36.dp,
                    iconSize = 22.dp,
                    tint = RevibeColors.PlayerMuted,
                    enabled = track != null,
                )
            }
        }
    }
}
