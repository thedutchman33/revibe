package com.revibe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.revibe.lyrics.LyricLine
import com.revibe.model.formatDuration
import com.revibe.playback.PlayerState
import com.revibe.ui.AppViewModel
import com.revibe.ui.RightTab
import com.revibe.ui.theme.RevibeColors
import com.revibe.ui.theme.RevibeType
import kotlin.math.abs

@Composable
fun RightPanel(vm: AppViewModel) {
    Column(
        Modifier
            .width(344.dp)
            .fillMaxHeight()
            .background(RevibeColors.Surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            RightTab.entries.forEach { tab ->
                val selected = vm.rightTab == tab
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        tab.label,
                        style = RevibeType.caption.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        ),
                        color = if (selected) RevibeColors.TextPrimary else RevibeColors.TextTertiary,
                        modifier = Modifier
                            .clickable { vm.selectRightTab(tab) }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .padding(bottom = 5.dp),
                    )
                    Box(
                        Modifier
                            .width(if (selected) 18.dp else 0.dp)
                            .height(2.dp)
                            .clip(CircleShape)
                            .background(if (selected) RevibeColors.Accent else Color.Transparent),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            IconAction(
                Icons.Rounded.QueueMusic,
                contentDescription = "Queue",
                onClick = { vm.selectRightTab(RightTab.QUEUE) },
                size = 28.dp,
                iconSize = 17.dp,
            )
        }
        HDivider()
        when (vm.rightTab) {
            RightTab.NOW_PLAYING -> NowPlayingTab(vm)
            RightTab.LYRICS -> LyricsTab(vm)
            RightTab.QUEUE -> QueueTab(vm)
        }
    }
}

@Composable
private fun NowPlayingTab(vm: AppViewModel) {
    val state by vm.playerState.collectAsState()
    val track = state.current

    if (track == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                title = "Nothing playing",
                message = "Start a track and its artwork, credits and artist story will live here.",
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Artwork(
                url = track.artworkUrl ?: track.localPath,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                cornerRadius = 10.dp,
            )
        }
        item {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        style = RevibeType.display.copy(fontSize = 24.sp, lineHeight = 28.sp),
                        color = RevibeColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        track.artist,
                        style = RevibeType.bodyStrong,
                        color = RevibeColors.TextSecondary,
                        modifier = Modifier
                            .clickable { vm.openArtist(track.artistId) }
                            .pointerHoverIcon(if (track.artistId != null) PointerIcon.Hand else PointerIcon.Default),
                    )
                }
                IconAction(
                    Icons.Rounded.Add,
                    contentDescription = "Add to playlist",
                    onClick = { vm.addToPlaylistTrack = track },
                    size = 32.dp,
                    iconSize = 18.dp,
                )
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(RevibeColors.SurfaceRaised)
                        .clickable { vm.toggleLike(track) }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = if (vm.isLiked(track.id)) RevibeColors.Accent else RevibeColors.TextSecondary,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            if (vm.isLiked(track.id)) "Liked" else "Like",
                            style = RevibeType.caption.copy(fontWeight = FontWeight.Bold),
                            color = RevibeColors.TextPrimary,
                        )
                    }
                }
                if (state.previewOnly) {
                    MiniBadge("30-second catalog preview", color = RevibeColors.TextSecondary)
                }
            }
        }
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(RevibeColors.SurfaceRaised)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CaptionLabel("Credits")
                MetaRow("Album", track.album, onClick = { vm.navigateToAlbumId(track.albumId) })
                MetaRow("Genre", track.genre ?: "Not provided")
                MetaRow("Released", track.year?.toString() ?: "Not provided")
                MetaRow("Source", track.source.label)
                MetaRow("Length", formatDuration(if (state.durationMs > 0) state.durationMs else track.durationMs))
            }
        }
        vm.artistKnowledge?.let { knowledge ->
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(RevibeColors.SurfaceRaised)
                        .padding(14.dp),
                ) {
                    CaptionLabel("About the artist")
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ArtistAvatar(knowledge.imageUrl ?: track.artworkUrl, size = 40.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            track.artist,
                            style = RevibeType.bodyStrong,
                            color = RevibeColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        knowledge.summary,
                        style = RevibeType.body,
                        color = RevibeColors.TextSecondary,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        knowledge.sourceLabel,
                        style = RevibeType.caption,
                        color = RevibeColors.TextTertiary,
                    )
                }
            }
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
private fun LyricsTab(vm: AppViewModel) {
    val state by vm.playerState.collectAsState()
    val lyrics = vm.lyrics
    val track = state.current

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

            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
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
                                append(lyrics.source)
                                if (lyrics.wordSynced) append(" · word sync")
                                else if (lyrics.hasTimestamps) append(" · line sync")
                                else append(" · plain text")
                                if (abs(vm.lyricsOffsetMs) > 0) append(" · ${vm.lyricsOffsetMs / 1000.0}s offset")
                            },
                            style = RevibeType.caption,
                            color = RevibeColors.TextTertiary,
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
            Text(buildAnnotatedWords(line, positionMs), style = style, color = baseColor)
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
private fun QueueTab(vm: AppViewModel) {
    val state by vm.playerState.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Queue", style = RevibeType.heading, color = RevibeColors.TextPrimary)
                Text(
                    if (state.contextName.isBlank()) "${state.queue.size} tracks" else state.contextName,
                    style = RevibeType.caption,
                    color = RevibeColors.TextSecondary,
                )
            }
            Spacer(Modifier.weight(1f))
            IconAction(
                Icons.Rounded.PlaylistAdd,
                contentDescription = "Add to queue",
                onClick = { vm.showToast("Choose a track and use Add to queue") },
                size = 30.dp,
                iconSize = 17.dp,
            )
        }
        if (state.queue.isEmpty()) {
            EmptyState("The queue is empty", "Play an album, playlist or mix to fill it up.")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(state.queue) { index, track ->
                    QueueRow(
                        track = track,
                        index = index,
                        isCurrent = index == state.index,
                        onPlay = { vm.playQueueAt(index) },
                        onRemove = { vm.removeQueueAt(index) },
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}
