package com.revibe.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revibe.library.PlaylistRecency
import com.revibe.library.RecentAlbums
import com.revibe.model.Track
import com.revibe.personalization.HomepageSectionKind
import com.revibe.personalization.UserMaturity
import com.revibe.ui.AppViewModel
import com.revibe.ui.HomeCategory
import com.revibe.ui.MediaItem
import com.revibe.ui.Shelf
import com.revibe.ui.components.Artwork
import com.revibe.ui.components.Chip
import com.revibe.ui.components.EmptyState
import com.revibe.ui.components.IconAction
import com.revibe.ui.components.MediaCard
import com.revibe.ui.components.ShelfRow
import com.revibe.ui.components.SectionHeader
import com.revibe.ui.components.SkeletonBlock
import com.revibe.ui.toMediaItem
import com.revibe.ui.theme.RevibeColors
import com.revibe.ui.theme.RevibeType

/**
 * Dynamic home page. Sections are chosen by [com.revibe.personalization.HomepageSectionManager]
 * from real SQLite activity. Recently played is one mixed shelf: songs, albums and
 * playlists share a single row and are ordered by their latest real play. All catalog
 * content is live YouTube Music data.
 */
@Composable
fun HomeScreen(vm: AppViewModel) {
    val state by vm.playerState.collectAsState()
    val recentAlbums = remember(vm.recentHistory) { RecentAlbums.from(vm.recentHistory) }
    val playlistRecents = remember(vm.playlists, vm.recentlyPlayedPlaylists) {
        PlaylistRecency.order(vm.playlists, vm.recentlyPlayedPlaylists)
    }
    val libraryPlaylistCards = remember(
        vm.playlists,
        vm.recentlyPlayedPlaylists,
        vm.likedTracks,
        vm.likedIds,
        vm.recentHistory,
    ) {
        val playedIds = vm.recentlyPlayedPlaylists.map { it.playlist.id }.toSet()
        buildList {
            val likedPlayedAt = vm.recentHistory.firstOrNull { it.track.id in vm.likedIds }?.playedAt ?: 0L
            if (vm.likedTracks.isNotEmpty() || playlistRecents.isNotEmpty()) {
                add(
                    LibraryPlaylistCard(
                        key = "liked",
                        name = "Liked Songs",
                        subtitle = "Playlist",
                        artworkUrl = vm.likedTracks.firstOrNull()?.artworkUrl,
                        playedAt = likedPlayedAt,
                        onClick = { vm.openLiked() },
                    ),
                )
            }
            playlistRecents.forEach { played ->
                val playlist = played.playlist
                add(
                    LibraryPlaylistCard(
                        key = playlist.id,
                        name = playlist.name,
                        subtitle = if (playlist.trackCount == 1) "Playlist · 1 song" else "Playlist · ${playlist.trackCount} songs",
                        artworkUrl = played.artworkUrl ?: playlist.artworkUrl,
                        playedAt = if (playlist.id in playedIds) played.playedAt else 0L,
                        onClick = { vm.openPlaylist(playlist.id) },
                    ),
                )
            }
        }
            .sortedByDescending { it.playedAt }
            .take(8)
    }
    val recentRow = remember(
        vm.recentHistory,
        vm.recentTracks,
        vm.likedIds,
        vm.likedTracks,
        playlistRecents,
        recentAlbums,
    ) {
        val playedPlaylistIds = vm.recentlyPlayedPlaylists.map { it.playlist.id }.toSet()
        val songs = if (vm.recentHistory.isNotEmpty()) {
            vm.recentHistory.map { it.track to it.playedAt }
        } else {
            vm.recentTracks.mapIndexed { index, track -> track to (Long.MAX_VALUE - index) }
        }
        // A played track also produces an inferred album card. Keep the more
        // specific song card when both would show the same release.
        val seenMedia = mutableSetOf<String>()
        fun mediaKey(title: String, artist: String) =
            "${title.trim().lowercase()}\u0000${artist.trim().lowercase()}"
        val entries = buildList {
            songs.take(10).forEach { (track, playedAt) ->
                if (seenMedia.add(mediaKey(track.title, track.artist))) {
                    add(
                        RecentRowEntry(
                            key = "song:${track.id}",
                            playedAt = playedAt,
                            item = track.toMediaItem().copy(subtitle = "Song · ${track.artist}"),
                            kind = RecentKind.SONG,
                        ),
                    )
                }
            }
            recentAlbums.take(6).forEach { recent ->
                if (seenMedia.add(mediaKey(recent.album.title, recent.album.artist))) {
                    add(
                        RecentRowEntry(
                            key = "album:${recent.album.id}",
                            playedAt = recent.playedAt,
                            item = recent.album.toMediaItem().copy(subtitle = "Album · ${recent.album.artist}"),
                            kind = RecentKind.ALBUM,
                        ),
                    )
                }
            }
            val likedPlayedAt = vm.recentHistory.firstOrNull { it.track.id in vm.likedIds }?.playedAt ?: 0L
            if (vm.likedTracks.isNotEmpty() || playlistRecents.isNotEmpty()) {
                add(
                    RecentRowEntry(
                        key = "liked",
                        playedAt = likedPlayedAt,
                        item = MediaItem(
                            id = "liked",
                            title = "Liked Songs",
                            subtitle = "Playlist",
                            imageUrl = vm.likedTracks.firstOrNull()?.artworkUrl,
                        ),
                        kind = RecentKind.LIKED,
                    ),
                )
            }
            playlistRecents.take(8).forEach { played ->
                val playlist = played.playlist
                add(
                    RecentRowEntry(
                        key = "playlist:${playlist.id}",
                        playedAt = if (playlist.id in playedPlaylistIds) played.playedAt else 0L,
                        item = MediaItem(
                            id = "playlist:${playlist.id}",
                            title = playlist.name,
                            subtitle = if (playlist.trackCount == 1) "Playlist · 1 song" else "Playlist · ${playlist.trackCount} songs",
                            imageUrl = played.artworkUrl ?: playlist.artworkUrl,
                        ),
                        kind = RecentKind.PLAYLIST,
                        playlistId = playlist.id,
                    ),
                )
            }
        }
        entries.sortedByDescending { it.playedAt }
    }
    var showAll by remember { mutableStateOf<HomeShowAll?>(null) }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(360.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(vm.accent.copy(alpha = 0.24f), Color.Transparent),
                    ),
                ),
        )

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(30.dp),
        ) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(HomeCategory.entries, key = { it.name }) { option ->
                        Chip(
                            text = option.label,
                            selected = vm.homeCategory == option,
                            onClick = { vm.selectHomeCategory(option) },
                        )
                    }
                }
            }

            if (vm.homeCategory != HomeCategory.ALL) {
                if (vm.categoryLoading) {
                    item("category-loading") { HomeSkeleton() }
                } else {
                    vm.categoryError?.let { message ->
                        item("category-error") {
                            EmptyState(
                                title = "Could not load ${vm.homeCategory.label}",
                                message = message,
                                action = "Retry",
                                onAction = vm::refreshHome,
                            )
                        }
                    }
                    vm.categoryShelves.forEach { shelf ->
                        item("category-${shelf.id}") { DiscoveryShelf(vm, shelf) }
                    }
                }
            } else {
                if (vm.homeLoading && vm.shelves.isEmpty()) {
                    items(2) { HomeSkeleton() }
                } else {
                    if (libraryPlaylistCards.isNotEmpty()) {
                        item("library-playlists") {
                            LibraryPlaylistGrid(libraryPlaylistCards)
                        }
                    }
                    vm.homePlan.sections.forEach { section ->
                        when (section) {
                            HomepageSectionKind.RECENTLY_PLAYED -> {
                                if (recentRow.isNotEmpty()) {
                                    item("recently-played") {
                                        Column {
                                            SectionHeader(
                                                title = "Recently played",
                                                subtitle = "Songs, playlists and albums, most recent first",
                                                action = "Show all",
                                                onAction = {
                                                    showAll = HomeShowAll(
                                                        title = "Recently played",
                                                        subtitle = "Songs, playlists and albums, most recent first",
                                                        cards = recentRow.map { entry ->
                                                            ShowAllCard(
                                                                key = "recent:${entry.key}",
                                                                item = entry.item,
                                                                onOpen = { showAll = null; openRecent(vm, entry) },
                                                                onPlay = { showAll = null; playRecent(vm, entry) },
                                                            )
                                                        },
                                                    )
                                                },
                                            )
                                            Spacer(Modifier.height(16.dp))
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                                contentPadding = PaddingValues(end = 24.dp),
                                            ) {
                                                items(recentRow, key = { it.key }) { entry ->
                                                    MediaCard(
                                                        item = entry.item,
                                                        onOpen = { openRecent(vm, entry) },
                                                        onPlay = { playRecent(vm, entry) },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            HomepageSectionKind.MADE_FOR_YOU -> {
                                val shelf = vm.personalShelves.firstOrNull { it.id == "made-for-you" }
                                if (shelf != null) {
                                    item("made-for-you") {
                                        PersonalShelf(
                                            vm = vm,
                                            shelf = shelf,
                                            eyebrow = "Made For",
                                            onShowAll = {
                                                val tracks = shelf.items.mapNotNull { it.track }
                                                showAll = HomeShowAll(
                                                    title = shelf.title,
                                                    subtitle = shelf.subtitle,
                                                    cards = shelf.items.map { item ->
                                                        ShowAllCard(
                                                            key = "shelf:${shelf.id}:${item.id}",
                                                            item = item,
                                                            onOpen = {
                                                                showAll = null
                                                                openShelfItem(vm, item, tracks, shelf.title)
                                                            },
                                                            onPlay = {
                                                                showAll = null
                                                                playShelfItem(vm, item, tracks, shelf.title)
                                                            },
                                                        )
                                                    },
                                                )
                                            },
                                        )
                                    }
                                }
                            }

                            HomepageSectionKind.TOP_MIXES -> {
                                vm.personalShelves
                                    .filter { it.id.startsWith("artist:") }
                                    .forEach { shelf ->
                                        item("mix-${shelf.id}") {
                                            PersonalShelf(vm, shelf, eyebrow = "Your mix")
                                        }
                                    }
                            }

                            HomepageSectionKind.FOLLOWED_ARTISTS -> if (vm.followedArtistCards.isNotEmpty()) {
                                item("followed-artists") {
                                    ShelfRow(
                                        shelfId = "artists",
                                        title = "Artists you follow",
                                        items = vm.followedArtistCards.map { it.toMediaItem() },
                                        onOpenItem = { item -> vm.openArtist(item.artist?.id) },
                                    )
                                }
                            }

                            HomepageSectionKind.DISCOVERY -> {
                                vm.shelves.forEach { shelf ->
                                    item("discovery-${shelf.id}") {
                                        DiscoveryShelf(vm, shelf)
                                    }
                                }
                            }
                        }
                    }

                    vm.homeError?.let { message ->
                        item("home-error") {
                            EmptyState(
                                title = "Could not load your home feed",
                                message = message,
                                action = "Retry",
                                onAction = vm::refreshHome,
                            )
                        }
                    }
                }
            }

            if (state.current == null && !vm.homeLoading && vm.shelves.isNotEmpty()) {
                item {
                    Text(
                        "Catalog tracks stream online through InnerTubeX. Tracks in your local library play in full.",
                        style = RevibeType.caption,
                        color = RevibeColors.TextTertiary,
                    )
                }
            }
        }
        showAll?.let { page ->
            HomeShowAllPage(page, onClose = { showAll = null })
        }
    }
}

private data class ShowAllCard(
    val key: String,
    val item: MediaItem,
    val onOpen: () -> Unit,
    val onPlay: (() -> Unit)?,
)

private data class HomeShowAll(
    val title: String,
    val subtitle: String?,
    val cards: List<ShowAllCard>,
)

@Composable
private fun HomeShowAllPage(page: HomeShowAll, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().background(RevibeColors.Surface)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 24.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(
                Icons.Rounded.ArrowBack,
                contentDescription = "Back to home",
                onClick = onClose,
                size = 36.dp,
                iconSize = 20.dp,
                tint = RevibeColors.TextSecondary,
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    page.title,
                    style = RevibeType.heading.copy(fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold),
                    color = RevibeColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                page.subtitle?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        it,
                        style = RevibeType.caption,
                        color = RevibeColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            gridItems(page.cards, key = { it.key }) { card ->
                MediaCard(
                    item = card.item,
                    onOpen = card.onOpen,
                    onPlay = card.onPlay,
                    cardWidth = 150,
                )
            }
        }
    }
}

private data class LibraryPlaylistCard(
    val key: String,
    val name: String,
    val subtitle: String,
    val artworkUrl: String?,
    val playedAt: Long,
    val onClick: () -> Unit,
)

@Composable
private fun LibraryPlaylistGrid(cards: List<LibraryPlaylistCard>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 700.dp) 4 else 2
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            cards.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { card -> LibraryPlaylistCardView(card) }
                }
            }
        }
    }
}

@Composable
private fun LibraryPlaylistCardView(card: LibraryPlaylistCard) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background by animateColorAsState(
        targetValue = if (hovered) RevibeColors.SurfaceActive else RevibeColors.SurfaceHover,
        animationSpec = tween(110),
        label = "libraryPlaylistCardBackground",
    )
    Row(
        Modifier
            .width(160.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = card.onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            url = card.artworkUrl,
            modifier = Modifier.size(64.dp),
            cornerRadius = 0.dp,
        )
        Column(Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
            Text(
                card.name,
                style = RevibeType.bodyStrong.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold),
                color = RevibeColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                card.subtitle,
                style = RevibeType.caption.copy(fontSize = 11.sp),
                color = RevibeColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private enum class RecentKind { SONG, PLAYLIST, ALBUM, LIKED }

private data class RecentRowEntry(
    val key: String,
    val playedAt: Long,
    val item: MediaItem,
    val kind: RecentKind,
    val playlistId: String? = null,
)

private fun openRecent(vm: AppViewModel, entry: RecentRowEntry) {
    when (entry.kind) {
        RecentKind.SONG -> entry.item.track?.let {
            vm.play(it, vm.recentHistory.map { played -> played.track }.ifEmpty { vm.recentTracks }, "Recently played")
        }
        RecentKind.PLAYLIST -> entry.playlistId?.let(vm::openPlaylist)
        RecentKind.ALBUM -> entry.item.album?.let(vm::openAlbum)
        RecentKind.LIKED -> vm.openLiked()
    }
}

private fun playRecent(vm: AppViewModel, entry: RecentRowEntry) {
    when (entry.kind) {
        RecentKind.SONG -> entry.item.track?.let {
            vm.play(it, vm.recentHistory.map { played -> played.track }.ifEmpty { vm.recentTracks }, "Recently played")
        }
        RecentKind.PLAYLIST -> entry.playlistId?.let(vm::playPlaylistById)
        RecentKind.ALBUM -> entry.item.album?.let { vm.playAlbumById(it.id) }
        RecentKind.LIKED -> vm.playLiked()
    }
}

@Composable
private fun PersonalShelf(
    vm: AppViewModel,
    shelf: Shelf,
    eyebrow: String,
    onShowAll: (() -> Unit)? = null,
) {
    val tracks = remember(shelf) { shelf.items.mapNotNull { it.track } }
    ShelfRow(
        shelfId = shelf.id,
        title = shelf.title,
        subtitle = shelf.subtitle,
        eyebrow = eyebrow,
        action = if (onShowAll != null) "Show all" else null,
        onAction = onShowAll,
        items = shelf.items,
        onOpenItem = { item -> openShelfItem(vm, item, tracks, shelf.title) },
        onPlayItem = { item -> playShelfItem(vm, item, tracks, shelf.title) },
    )
}

private fun openShelfItem(vm: AppViewModel, item: MediaItem, tracks: List<Track>, title: String) {
    val track = item.track
    when {
        track != null -> vm.play(track, tracks, title)
        item.album != null -> vm.openAlbum(item.album)
        item.artist != null -> vm.openArtist(item.artist.id)
    }
}

private fun playShelfItem(vm: AppViewModel, item: MediaItem, tracks: List<Track>, title: String) {
    val album = item.album
    when {
        album != null -> vm.playAlbumById(album.id)
        item.track != null -> vm.play(item.track, tracks, title)
    }
}

@Composable
private fun DiscoveryShelf(vm: AppViewModel, shelf: Shelf) {
    ShelfRow(
        shelfId = shelf.id,
        title = shelf.title,
        subtitle = shelf.subtitle,
        eyebrow = "Discover",
        action = "Show all",
        onAction = { vm.searchGenre(shelf.id) },
        items = shelf.items,
        onOpenItem = { item -> item.album?.let(vm::openAlbum) },
        onPlayItem = { item -> item.album?.let { vm.playAlbumById(it.id) } },
    )
}

@Composable
private fun HomeSkeleton() {
    Column {
        SkeletonBlock(Modifier.width(240.dp).height(24.dp))
        Spacer(Modifier.height(16.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            items(6) {
                Column {
                    SkeletonBlock(Modifier.size(132.dp))
                    Spacer(Modifier.height(10.dp))
                    SkeletonBlock(Modifier.width(110.dp).height(13.dp))
                    Spacer(Modifier.height(6.dp))
                    SkeletonBlock(Modifier.width(78.dp).height(11.dp))
                }
            }
        }
    }
}
