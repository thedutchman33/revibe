package com.revibe.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revibe.lyrics.LyricLine
import com.revibe.model.LayoutSpec
import com.revibe.model.Track
import com.revibe.model.formatDuration
import com.revibe.platform.currentTimeMillis
import com.revibe.platform.relativeTime
import com.revibe.playback.AutoplayState
import com.revibe.playback.QueueOrigin
import com.revibe.ui.AppViewModel
import com.revibe.ui.RightTab
import com.revibe.ui.theme.RevibeColors
import com.revibe.ui.theme.RevibeType
import kotlin.math.abs

@Composable
fun RightPanel(
    vm: AppViewModel,
    width: Dp = LayoutSpec.RIGHT_DEFAULT.dp,
    onClose: () -> Unit = vm::closeRightPanel,
) {
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(RevibeColors.Surface),
    ) {
        if (vm.addToPlaylistPlaylistId != null) {
            AddToPlaylistPanel(vm, onClose)
        } else {
            when (vm.rightTab) {
                RightTab.NOW_PLAYING -> NowPlayingTab(vm, onClose)
                RightTab.LYRICS -> LyricsTab(vm, onClose)
                RightTab.QUEUE -> QueueTab(vm, onClose)
            }
        }
    }
}

@Composable
private fun PanelHeader(
    title: String,
    onClose: () -> Unit,
    onTitleClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = RevibeType.title.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
            color = RevibeColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onTitleClick != null) {
                        Modifier
                            .clickable(onClick = onTitleClick)
                            .pointerHoverIcon(PointerIcon.Hand)
                    } else {
                        Modifier
                    },
                ),
        )
        trailing()
        IconAction(
            Icons.Rounded.Close,
            contentDescription = "Close panel",
            onClick = onClose,
            size = 30.dp,
            iconSize = 17.dp,
            tint = RevibeColors.TextSecondary,
        )
    }
}

@Composable
private fun AddToPlaylistPanel(vm: AppViewModel, onClose: () -> Unit) {
    val query = vm.addToPlaylistQuery
    val showingSuggestions = query.isBlank()
    val tracks = if (showingSuggestions) vm.addToPlaylistSuggestions else vm.addToPlaylistResults
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(Modifier.fillMaxSize()) {
        PanelHeader("Add to playlist", onClose)
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(RevibeColors.SurfaceRaised)
                    .border(
                        1.dp,
                        if (focused) RevibeColors.TextPrimary.copy(alpha = 0.55f) else Color.Transparent,
                        RoundedCornerShape(21.dp),
                    )
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = RevibeColors.TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
                BasicTextField(
                    value = query,
                    onValueChange = vm::onAddToPlaylistQueryChange,
                    singleLine = true,
                    textStyle = RevibeType.body.copy(fontSize = 14.sp, color = RevibeColors.TextPrimary),
                    cursorBrush = SolidColor(RevibeColors.Accent),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    "Search",
                                    style = RevibeType.body.copy(fontSize = 14.sp),
                                    color = RevibeColors.TextSecondary,
                                )
                            }
                            inner()
                        }
                    },
                )
                if (query.isNotEmpty()) {
                    IconAction(
                        Icons.Rounded.Close,
                        contentDescription = "Clear search",
                        onClick = vm::clearAddToPlaylistQuery,
                        size = 24.dp,
                        iconSize = 14.dp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (showingSuggestions) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(RevibeColors.TextPrimary)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        "Suggested songs",
                        style = RevibeType.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        color = RevibeColors.Base,
                    )
                }
            } else {
                Text(
                    "Tracks matching \"${query.trim()}\"",
                    style = RevibeType.caption.copy(fontSize = 11.sp),
                    color = RevibeColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                vm.addToPlaylistLoading && tracks.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(26.dp),
                        color = RevibeColors.Accent,
                        strokeWidth = 2.dp,
                    )
                }
                vm.addToPlaylistError != null -> {
                    Column(
                        Modifier.align(Alignment.Center).padding(horizontal = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Couldn't search songs",
                            style = RevibeType.bodyStrong,
                            color = RevibeColors.TextPrimary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            vm.addToPlaylistError.orEmpty(),
                            style = RevibeType.caption,
                            color = RevibeColors.TextSecondary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                tracks.isEmpty() -> {
                    Column(
                        Modifier.align(Alignment.Center).padding(horizontal = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (showingSuggestions) "No suggestions yet" else "No songs found",
                            style = RevibeType.bodyStrong,
                            color = RevibeColors.TextPrimary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (showingSuggestions) "Search for a song to add." else "Try a different search.",
                            style = RevibeType.caption,
                            color = RevibeColors.TextSecondary,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 20.dp),
                    ) {
                        items(tracks, key = { it.id }) { track ->
                            AddTrackRow(
                                track = track,
                                added = track.id in vm.addToPlaylistAddedIds,
                                onAdd = { vm.addTrackToOpenPlaylist(track) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddTrackRow(track: Track, added: Boolean, onAdd: () -> Unit) {
    HoverSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Artwork(
                url = track.artworkUrl ?: track.localPath,
                modifier = Modifier.size(44.dp),
                cornerRadius = 4.dp,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = RevibeType.bodyStrong.copy(fontSize = 13.sp),
                    color = RevibeColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    track.artist,
                    style = RevibeType.caption.copy(fontSize = 12.sp),
                    color = RevibeColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (added) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = "Added to playlist",
                    tint = RevibeColors.Accent,
                    modifier = Modifier.size(21.dp),
                )
            } else {
                IconAction(
                    Icons.Rounded.Add,
                    contentDescription = "Add ${track.title} to playlist",
                    onClick = onAdd,
                    size = 30.dp,
                    iconSize = 20.dp,
                )
            }
        }
    }
}

@Composable
private fun NowPlayingTab(vm: AppViewModel, onClose: () -> Unit) {
    val state by vm.playerState.collectAsState()
    val track = state.current

    if (track == null) {
        Column(Modifier.fillMaxSize()) {
            PanelHeader("Now playing", onClose)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "Nothing playing",
                    message = "Start a track and its artwork, lyrics and artist story will live here.",
                )
            }
        }
        return
    }

    // The hero image is the song's own poster. The artist photo belongs to the
    // "About the artist" card and must never stand in for missing track art.
    val songPoster = vm.currentArtworkUrl ?: track.artworkUrl ?: track.localPath
    // Match the hero to the actual image instead of forcing it into a square.
    // A 16:9 video still therefore stays complete, while square album art
    // keeps its square shape. The initial square is only the pre-load state.
    var artworkAspect by remember(songPoster) { mutableFloatStateOf(1f) }
    val heroAspect by animateFloatAsState(
        targetValue = artworkAspect,
        animationSpec = tween(durationMillis = 220),
        label = "now-playing-artwork-aspect",
    )
    val lyricPreview = remember(vm.lyrics) {
        vm.lyrics?.lines
            ?.map { it.text }
            ?.filter { it.isNotBlank() }
            ?.take(3)
            .orEmpty()
    }

    Column(Modifier.fillMaxSize()) {
        PanelHeader(track.title, onClose, onTitleClick = vm::openPlayingContext)
        LazyColumn(Modifier.weight(1f)) {
        item {
            Artwork(
                url = songPoster,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .aspectRatio(heroAspect.coerceIn(0.45f, 2.2f)),
                cornerRadius = 8.dp,
                contentScale = ContentScale.Fit,
                onArtworkLoaded = { loaded ->
                    val width = loaded.image.width
                    val height = loaded.image.height
                    if (width > 0 && height > 0) {
                        artworkAspect = width.toFloat() / height
                    }
                },
            )
        }
        item {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp)) {
                Text(
                    track.title,
                    style = RevibeType.heading.copy(fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold),
                    color = RevibeColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clickable { vm.openPlayingContext() }
                        .pointerHoverIcon(PointerIcon.Hand),
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        track.artist,
                        style = RevibeType.body.copy(fontSize = 13.sp),
                        color = RevibeColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { vm.openArtist(track.artistId) }
                            .pointerHoverIcon(if (track.artistId != null) PointerIcon.Hand else PointerIcon.Default),
                    )
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable { vm.toggleLike(track) }
                            .pointerHoverIcon(PointerIcon.Hand),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (vm.isLiked(track.id)) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline,
                            contentDescription = if (vm.isLiked(track.id)) "Liked" else "Add to Liked Songs",
                            tint = if (vm.isLiked(track.id)) RevibeColors.Accent else RevibeColors.TextSecondary,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
            }
        }

        if (lyricPreview.isNotEmpty()) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF7A4A1E), Color(0xFF563414)),
                            ),
                        )
                        .clickable { vm.selectRightTab(RightTab.LYRICS) }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Lyrics",
                            style = RevibeType.bodyStrong.copy(fontSize = 15.sp),
                            color = RevibeColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Text("Open", style = RevibeType.caption, color = RevibeColors.TextPrimary.copy(alpha = 0.7f))
                    }
                    Spacer(Modifier.height(10.dp))
                    lyricPreview.forEach { line ->
                        Text(
                            line,
                            style = RevibeType.title.copy(fontSize = 16.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
                            color = RevibeColors.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(2.dp).background(RevibeColors.TextPrimary.copy(alpha = 0.30f))) {
                        Box(Modifier.fillMaxWidth(0.35f).height(2.dp).background(RevibeColors.TextPrimary))
                    }
                }
            }
        }

        vm.artistKnowledge?.let { knowledge ->
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(RevibeColors.SurfaceRaised)
                        .padding(14.dp),
                ) {
                    Text(
                        "About the artist",
                        style = RevibeType.bodyStrong.copy(fontSize = 13.sp),
                        color = RevibeColors.TextPrimary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ArtistAvatar(knowledge.imageUrl ?: vm.currentArtworkUrl, size = 46.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                track.artist,
                                style = RevibeType.bodyStrong,
                                color = RevibeColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(knowledge.sourceLabel, style = RevibeType.caption.copy(fontSize = 10.sp), color = RevibeColors.TextTertiary)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        knowledge.summary,
                        style = RevibeType.body.copy(fontSize = 12.5.sp, lineHeight = 19.sp),
                        color = RevibeColors.TextSecondary,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(RevibeColors.SurfaceRaised)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CaptionLabel("Credits")
                MetaRow("Album", track.album, onClick = { vm.navigateToAlbumId(track.albumId) })
                MetaRow("Genre", track.genre ?: "Not provided")
                MetaRow("Released", track.year?.toString() ?: "Not provided")
                MetaRow("Source", track.source.label)
                MetaRow("Length", formatDuration(if (state.durationMs > 0) state.durationMs else track.durationMs))
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = RevibeType.caption, color = RevibeColors.TextTertiary, modifier = Modifier.width(72.dp))
        Text(
            value,
            style = RevibeType.caption.copy(fontWeight = FontWeight.Medium),
            color = if (onClick != null) RevibeColors.TextPrimary else RevibeColors.TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand)
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

@Composable
private fun LyricsTab(vm: AppViewModel, onClose: () -> Unit) {
    val state by vm.playerState.collectAsState()
    val lyrics = vm.lyrics
    val track = state.current

    Column(Modifier.fillMaxSize()) {
        PanelHeader("Lyrics", onClose)
        when {
            track == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState("No track playing", "Lyrics appear here, synced to the music when the provider has timings.")
            }
            vm.lyricsLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = RevibeColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
            }
            lyrics == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "No lyrics found",
                    message = "LRCLIB does not have a match for this track yet.",
                    action = "Try again",
                    onAction = vm::retryLyrics,
                )
            }
            else -> {
                val lines = if (lyrics.hasTimestamps) {
                    lyrics.lines
                } else {
                    lyrics.plain?.lineSequence()?.filter { it.isNotBlank() }?.map { LyricLine(0, it) }?.toList().orEmpty()
                }
                val position = state.positionMs + vm.lyricsOffsetMs
                val activeIndex = if (lyrics.hasTimestamps) {
                    lines.indexOfLast { it.timeMs <= position }
                } else {
                    -1
                }
                val listState = rememberLazyListState()

                LaunchedEffect(activeIndex, lyrics) {
                    if (activeIndex >= 0 && state.isPlaying) {
                        runCatching { listState.animateScrollToItem(activeIndex.coerceAtLeast(0)) }
                    }
                }

                val creditUrl = when {
                    lyrics.source.startsWith("lrc.red") -> "https://lrc.red"
                    lyrics.source.startsWith("Unison") -> "https://unison.betterlyrics.org"
                    else -> null
                }
                val uriHandler = LocalUriHandler.current

                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.title,
                                style = RevibeType.bodyStrong,
                                color = RevibeColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                buildString {
                                    append("Lyrics from ")
                                    append(lyrics.source)
                                    if (lyrics.wordSynced) append(" · word sync")
                                    else if (lyrics.hasTimestamps) append(" · line sync")
                                    else append(" · plain text")
                                    if (abs(vm.lyricsOffsetMs) > 0) append(" · ${vm.lyricsOffsetMs / 1000.0}s offset")
                                },
                                style = RevibeType.caption,
                                color = if (creditUrl != null) RevibeColors.TextSecondary else RevibeColors.TextTertiary,
                                modifier = if (creditUrl != null) {
                                    Modifier
                                        .clickable { uriHandler.openUri(creditUrl) }
                                        .pointerHoverIcon(PointerIcon.Hand)
                                } else {
                                    Modifier
                                },
                            )
                        }
                        Text(
                            "−",
                            style = RevibeType.title,
                            color = RevibeColors.TextSecondary,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { vm.shiftLyricsOffset(-500) }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                        Text(
                            "+",
                            style = RevibeType.title,
                            color = RevibeColors.TextSecondary,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { vm.shiftLyricsOffset(500) }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                        IconAction(
                            Icons.Rounded.Refresh,
                            contentDescription = "Reload lyrics",
                            onClick = vm::retryLyrics,
                            size = 26.dp,
                            iconSize = 14.dp,
                        )
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        itemsIndexed(lines) { index, line ->
                            val isActive = index == activeIndex
                            val passed = lyrics.hasTimestamps && index < activeIndex
                            LyricLineView(
                                line = line,
                                isActive = isActive,
                                passed = passed,
                                positionMs = position,
                                wordSynced = lyrics.wordSynced,
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricLineView(
    line: LyricLine,
    isActive: Boolean,
    passed: Boolean,
    positionMs: Long,
    wordSynced: Boolean,
) {
    val baseColor = when {
        isActive -> RevibeColors.TextPrimary
        passed -> RevibeColors.TextTertiary.copy(alpha = 0.7f)
        else -> RevibeColors.TextSecondary.copy(alpha = 0.55f)
    }
    val style = RevibeType.body.copy(
        fontSize = if (isActive) 19.sp else 17.sp,
        lineHeight = if (isActive) 28.sp else 25.sp,
        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
    )
    if (wordSynced && isActive && line.words.isNotEmpty()) {
        Row {
            Text(buildAnnotatedWords(line, positionMs), style = style.copy(color = baseColor))
        }
    } else {
        Text(line.text, style = style, color = baseColor)
    }
}

private fun buildAnnotatedWords(line: LyricLine, positionMs: Long): androidx.compose.ui.text.AnnotatedString {
    val builder = androidx.compose.ui.text.AnnotatedString.Builder()
    line.words.forEach { word ->
        val span = if (word.timeMs <= positionMs) {
            androidx.compose.ui.text.SpanStyle(color = RevibeColors.Accent)
        } else {
            androidx.compose.ui.text.SpanStyle(color = RevibeColors.TextPrimary)
        }
        builder.pushStyle(span)
        builder.append(word.text)
        builder.pop()
    }
    return builder.toAnnotatedString()
}

@Composable
private fun QueueTab(vm: AppViewModel, onClose: () -> Unit) {
    val state by vm.playerState.collectAsState()
    var showRecent by remember { mutableStateOf(false) }
    var queueMenu by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refreshHistory() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                QueueHeaderTab("Queue", !showRecent) { showRecent = false }
                QueueHeaderTab("Recently played", showRecent) { showRecent = true }
            }
            IconAction(
                Icons.Rounded.Close,
                contentDescription = "Close panel",
                onClick = onClose,
                size = 30.dp,
                iconSize = 20.dp,
                tint = RevibeColors.TextSecondary,
            )
        }

        if (showRecent) {
            val history = vm.recentHistory
            if (history.isEmpty()) {
                EmptyState("No listening history yet", "Tracks you actually play will appear here.")
            } else {
                val now = currentTimeMillis()
                LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 40.dp)) {
                    items(history, key = { "recent-${it.track.id}" }) { played ->
                        Column {
                            QueueRow(
                                track = played.track,
                                index = 0,
                                isCurrent = false,
                                onPlay = { vm.play(played.track, history.map { it.track }, "Recently played") },
                                onRemove = null,
                            )
                            Text(
                                relativeTime(played.playedAt, now),
                                style = RevibeType.caption.copy(fontSize = 10.5.sp),
                                color = RevibeColors.TextTertiary,
                                modifier = Modifier.padding(start = 58.dp, bottom = 3.dp),
                            )
                        }
                    }
                }
            }
            return@Column
        }

        if (state.entries.isEmpty()) {
            EmptyState("The queue is empty", "Play an album, playlist or mix to fill it up.")
            return@Column
        }

        val currentEntry = state.entries.getOrNull(state.index)
        val upcoming = state.entries.drop(state.index + 1)
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (currentEntry != null) {
                item("queue-current-header") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 8.dp, end = 2.dp, top = 14.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Now playing",
                            style = RevibeType.title.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                            color = RevibeColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Box {
                            IconAction(
                                Icons.Rounded.MoreHoriz,
                                contentDescription = "Queue options",
                                onClick = { queueMenu = true },
                                size = 26.dp,
                                iconSize = 18.dp,
                                tint = RevibeColors.TextSecondary,
                            )
                            DropdownMenu(
                                expanded = queueMenu,
                                onDismissRequest = { queueMenu = false },
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(RevibeColors.SurfaceRaised),
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (vm.autoplayEnabled) "Turn autoplay off" else "Turn autoplay on",
                                            style = RevibeType.label,
                                            color = RevibeColors.TextPrimary,
                                        )
                                    },
                                    onClick = { queueMenu = false; vm.toggleAutoplay() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Save queue as playlist", style = RevibeType.label, color = RevibeColors.TextPrimary) },
                                    onClick = { queueMenu = false; vm.saveQueueAsPlaylist() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear queue", style = RevibeType.label, color = RevibeColors.Danger) },
                                    onClick = { queueMenu = false; vm.clearQueue() },
                                )
                            }
                        }
                    }
                }
                item("queue-current") {
                    QueueRow(
                        track = currentEntry.track,
                        index = state.index,
                        isCurrent = true,
                        onPlay = { vm.playQueueAt(state.index) },
                        onRemove = null,
                        reason = null,
                    )
                }
            }

            if (upcoming.isNotEmpty()) {
                item("queue-next-header") {
                    Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 18.dp, bottom = 6.dp)) {
                        Text(
                            "Next from: ${state.contextName.ifBlank { "Queue" }}",
                            style = RevibeType.title.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                            color = RevibeColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        when (state.autoplayState) {
                            AutoplayState.REFILLING -> Text(
                                "Finding more music…",
                                style = RevibeType.caption.copy(fontSize = 11.sp),
                                color = RevibeColors.TextTertiary,
                            )
                            AutoplayState.FAILED -> Text(
                                state.autoplayMessage ?: "Recommendations are unavailable",
                                style = RevibeType.caption.copy(fontSize = 11.sp),
                                color = RevibeColors.Danger,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            else -> Unit
                        }
                    }
                }
                itemsIndexed(upcoming, key = { _, entry -> entry.entryId }) { offset, entry ->
                    val index = state.index + 1 + offset
                    val drag = remember(entry.entryId) { mutableFloatStateOf(0f) }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .pointerInput(entry.entryId, state.entries.size) {
                                detectVerticalDragGestures(
                                    onVerticalDrag = { change, amount ->
                                        change.consume()
                                        drag.floatValue += amount
                                    },
                                    onDragEnd = {
                                        val steps = (drag.floatValue / 56f).toInt()
                                        val target = (index + steps).coerceIn(0, state.entries.lastIndex)
                                        if (steps != 0 && target != index && target != state.index) {
                                            vm.moveQueueItem(index, target)
                                        }
                                        drag.floatValue = 0f
                                    },
                                    onDragCancel = { drag.floatValue = 0f },
                                )
                            },
                    ) {
                        QueueRow(
                            track = entry.track,
                            index = index,
                            isCurrent = false,
                            onPlay = { vm.playQueueAt(index) },
                            onRemove = { vm.removeQueueAt(index) },
                            reason = entry.reason,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueHeaderTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val underline by animateDpAsState(if (selected) 24.dp else 0.dp, label = "queueTabUnderline")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = RevibeType.title.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = if (selected) RevibeColors.TextPrimary else RevibeColors.TextSecondary,
            modifier = Modifier
                .clickable(onClick = onClick)
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(bottom = 8.dp),
        )
        Box(
            Modifier
                .width(underline)
                .height(2.dp)
                .clip(CircleShape)
                .background(if (selected) RevibeColors.Accent else Color.Transparent),
        )
    }
}
