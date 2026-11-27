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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revibe.imports.ImportProgress
import com.revibe.imports.ImportSummary
import com.revibe.imports.MatchStatus
import com.revibe.imports.SpotifyMatchedArchive
import com.revibe.ui.AppViewModel
import com.revibe.ui.components.Artwork
import com.revibe.ui.components.ButtonKind
import com.revibe.ui.components.Chip
import com.revibe.ui.components.HoverSurface
import com.revibe.ui.components.RevibeButton
import com.revibe.ui.components.RevibeTextField
import com.revibe.ui.components.ToggleRow
import com.revibe.ui.components.gradientFor
import com.revibe.ui.theme.RevibeColors
import com.revibe.ui.theme.RevibeType

private const val TUNE_MY_MUSIC_URL = "https://www.tunemymusic.com/"
private const val SPOTIFY_PRIVACY_URL = "https://www.spotify.com/us/account/privacy/"

@Composable
fun PlaylistImportScreen(vm: AppViewModel) {
    val uriHandler = LocalUriHandler.current
    val preview = vm.playlistImportPreview
    val summary = vm.playlistImportSummary

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 16.dp, top = 10.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ImportHeader(
                title = "Import playlist",
                subtitle = "Move a Spotify playlist into Revibe. Revibe is the destination.",
                onBack = vm::leaveImportScreens,
            )
        }
        when {
            summary != null -> item {
                PlaylistImportSummaryCard(
                    summary = summary,
                    onOpenPlaylist = if (summary.primaryPlaylistId != null) vm::openImportedPlaylist else null,
                    onImportAnother = vm::beginPlaylistImport,
                    onDone = vm::leaveImportScreens,
                )
            }
            preview == null -> item {
                PlaylistImportGuide(
                    busy = vm.playlistImportBusy,
                    selectedFileName = vm.playlistImportFileName,
                    progress = vm.playlistImportProgress,
                    error = vm.playlistImportError,
                    onOpenTuneMyMusic = { uriHandler.openUri(TUNE_MY_MUSIC_URL) },
                    onOpenDownloads = vm::openDownloadsFolder,
                    onImportFile = vm::choosePlaylistImportFile,
                )
            }
            else -> {
                item {
                    ImportCard("Review import") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(preview.playlistName, style = RevibeType.heading.copy(fontSize = 18.sp), color = RevibeColors.TextPrimary)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${preview.fileName} · ${preview.format}",
                                    style = RevibeType.caption,
                                    color = RevibeColors.TextTertiary,
                                )
                            }
                            if (preview.duplicateCount > 0) {
                                Text(
                                    "${preview.duplicateCount} repeated",
                                    style = RevibeType.caption,
                                    color = Color(0xFFFFC857),
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        ImportStat("Source tracks", preview.tracks.size, "order preserved")
                        ImportStat("Matched", preview.matchedCount, null)
                        ImportStat("Needs review", preview.tracks.count { it.status == MatchStatus.NEEDS_REVIEW }, "close matches start unselected")
                        ImportStat("Unmatched", preview.unmatchedCount, "kept in the report, never fabricated")
                        Spacer(Modifier.height(12.dp))
                        Text("Add to", style = RevibeType.label, color = RevibeColors.TextSecondary)
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                Chip("New playlist", vm.playlistImportDestination == null, onClick = { vm.playlistImportDestination = null })
                            }
                            items(vm.playlists, key = { it.id }) { playlist ->
                                Chip(
                                    playlist.name,
                                    vm.playlistImportDestination == playlist.id,
                                    onClick = { vm.playlistImportDestination = playlist.id },
                                    horizontalPadding = 12.dp,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (vm.playlistImportDestination == null) {
                            Text("Playlist name", style = RevibeType.label, color = RevibeColors.TextSecondary)
                            Spacer(Modifier.height(6.dp))
                            RevibeTextField(
                                vm.playlistImportName,
                                { vm.playlistImportName = it },
                                placeholder = preview.playlistName,
                                enabled = !vm.playlistImportBusy,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Review each row before adding it. Close matches start unselected; repeated source rows are kept as separate entries in their original order. Nothing is written until you confirm.",
                            style = RevibeType.caption,
                            color = RevibeColors.TextTertiary,
                        )
                    }
                }
                itemsIndexed(preview.tracks, key = { index, item -> "${index}-${item.source.position}-${item.source.title}" }) { index, item ->
                    PreviewTrackRow(
                        index = index,
                        title = item.source.title,
                        subtitle = listOf(item.source.artist, item.source.album).filter { it.isNotBlank() }.joinToString(" · "),
                        artwork = item.track?.artworkUrl,
                        status = item.status,
                        confidence = item.confidence,
                        selected = item.selected,
                        enabled = item.track != null && !vm.playlistImportBusy,
                        onClick = { vm.togglePlaylistImportTrack(index) },
                    )
                }
                item {
                    ImportProgressLine(vm.playlistImportProgress.stage, vm.playlistImportProgress.current, vm.playlistImportProgress.total)
                    vm.playlistImportError?.let { ImportError(it) }
                    RevibeButton(
                        text = "Add playlist to Revibe",
                        onClick = vm::confirmPlaylistImport,
                        enabled = !vm.playlistImportBusy && preview.selectedCount > 0,
                        icon = Icons.Rounded.CloudUpload,
                        modifier = Modifier.fillMaxWidth(),
                        centered = true,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${preview.selectedCount} of ${preview.tracks.size} tracks will be added. Unselected and unmatched rows stay in this report only.",
                        style = RevibeType.caption,
                        color = RevibeColors.TextTertiary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RevibeButton("Choose another file", vm::choosePlaylistImportFile, kind = ButtonKind.Outline, enabled = !vm.playlistImportBusy)
                        RevibeButton("Cancel", vm::leaveImportScreens, kind = ButtonKind.Ghost)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlaylistImportGuide(
    busy: Boolean,
    selectedFileName: String?,
    progress: ImportProgress,
    error: String?,
    onOpenTuneMyMusic: () -> Unit,
    onOpenDownloads: () -> Unit,
    onImportFile: () -> Unit,
) {
    ImportCard("Move a playlist from Spotify") {
        Text(
            "TuneMyMusic moves the playlist out of Spotify. Revibe then reads that file and matches every track against the InnerTubeX catalog — no TuneMyMusic destination has to be chosen.",
            style = RevibeType.caption,
            color = RevibeColors.TextSecondary,
        )
        Spacer(Modifier.height(12.dp))
        RevibeButton(
            text = if (busy) "Reading…" else "Import downloaded playlist",
            onClick = onImportFile,
            enabled = !busy,
            icon = Icons.Rounded.Description,
            modifier = Modifier.fillMaxWidth(),
            centered = true,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RevibeButton("Open Downloads folder", onOpenDownloads, kind = ButtonKind.Outline, icon = Icons.Rounded.FolderOpen, enabled = !busy)
            RevibeButton("Open TuneMyMusic in browser", onOpenTuneMyMusic, kind = ButtonKind.Ghost, icon = Icons.Rounded.OpenInNew, enabled = !busy)
        }
        if (selectedFileName != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(RevibeColors.SurfaceRaised)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.Description, null, tint = RevibeColors.TextSecondary, modifier = Modifier.size(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(selectedFileName, style = RevibeType.bodyStrong, color = RevibeColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (busy) progress.stage else "Ready to review",
                        style = RevibeType.caption,
                        color = RevibeColors.TextSecondary,
                    )
                }
                if (busy) CircularProgressIndicator(color = RevibeColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            }
        }
        if (busy) {
            Spacer(Modifier.height(8.dp))
            ImportProgressLine(progress.stage, progress.current, progress.total)
        }
        error?.let {
            Spacer(Modifier.height(10.dp))
            ImportError(it)
        }
        Spacer(Modifier.height(16.dp))
        Text("Step by step", style = RevibeType.bodyStrong, color = RevibeColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(RevibeColors.Surface)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            GuideStep("1", "Open TuneMyMusic", "Use the button above. It opens in your normal browser.")
            GuideStep("2", "Copy and paste the Spotify playlist URL", "Paste it into TuneMyMusic, or connect Spotify there if the playlist is private.")
            GuideStep("3", "Load playlist", "Wait for TuneMyMusic to show the playlist and its tracks.")
            GuideStep("4", "Choose Destination", "Revibe is not a TuneMyMusic destination, so pick Export to file instead of a streaming service.")
            GuideStep("5", "Export to file", "Choose CSV. Keep the downloaded file in your Downloads folder.")
            GuideStep("6", "Return to Revibe", "The playlist is added here, not on another service.")
            GuideStep("7", "Import the downloaded CSV", "Pick the file you just downloaded and review the matches.")
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Supported formats: CSV, TXT/TSV, M3U, M3U8 and XSPF/XML. Files are parsed locally. Revibe never imports anything from Downloads on its own — you always choose the exact file. A Spotify URI is never treated as a playable video id.",
            style = RevibeType.caption,
            color = RevibeColors.TextTertiary,
        )
    }
}

@Composable
private fun GuideStep(number: String, title: String, hint: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(RevibeColors.SurfaceHover),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, style = RevibeType.caption.copy(fontWeight = FontWeight.Bold), color = RevibeColors.TextPrimary)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = RevibeType.bodyStrong, color = RevibeColors.TextPrimary)
            Text(hint, style = RevibeType.caption, color = RevibeColors.TextSecondary)
        }
    }
}

@Composable
internal fun PlaylistImportSummaryCard(
    summary: ImportSummary,
    onOpenPlaylist: (() -> Unit)?,
    onImportAnother: () -> Unit,
    onDone: () -> Unit,
) {
    ImportCard("Playlist added") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.CloudUpload, null, tint = RevibeColors.Accent, modifier = Modifier.size(22.dp))
            Text(
                summary.playlistName ?: "Imported playlist",
                style = RevibeType.heading.copy(fontSize = 20.sp),
                color = RevibeColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(10.dp))
        ImportStat("Tracks added", summary.tracksAdded, null)
        ImportStat("Tracks requiring review", summary.needsReview, "close matches, selected or not")
        ImportStat("Unmatched tracks", summary.unmatched.size, "kept in the report, never fabricated")
        if (summary.unmatched.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            summary.unmatched.take(20).forEach { track ->
                Text(
                    "• ${track.displayName}",
                    style = RevibeType.caption,
                    color = RevibeColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (summary.unmatched.size > 20) {
                Text("and ${summary.unmatched.size - 20} more", style = RevibeType.caption, color = RevibeColors.TextTertiary)
            }
        }
        summary.notes.forEach { note ->
            Spacer(Modifier.height(6.dp))
            Text("• $note", style = RevibeType.caption, color = RevibeColors.TextTertiary)
        }
        Spacer(Modifier.height(14.dp))
        if (onOpenPlaylist != null) {
            RevibeButton("Open playlist", onOpenPlaylist, modifier = Modifier.fillMaxWidth(), centered = true)
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RevibeButton("Import another playlist", onImportAnother, kind = ButtonKind.Outline)
            RevibeButton("Done", onDone, kind = ButtonKind.Ghost)
        }
    }
}

@Composable
fun SpotifyImportScreen(vm: AppViewModel) {
    val uriHandler = LocalUriHandler.current
    val matched = vm.spotifyArchiveMatches
    val summary = vm.spotifyImportSummary

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 16.dp, top = 10.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ImportHeader(
                title = "Import your music",
                subtitle = "Spotify account data and extended streaming history.",
                onBack = vm::leaveImportScreens,
            )
        }
        if (summary != null) {
            item {
                ImportCompleteCard(summary, onOpenPlaylist = null, onDone = vm::leaveImportScreens)
            }
        }
        if (matched == null) {
            item {
                ImportCard("Download your Spotify data") {
                    Text(
                        "Request your data from Spotify's privacy page. Standard Account data includes playlists, saved library, recent streaming history and searches. Extended streaming history is a separate request and is required for lifetime listening-history migration.",
                        style = RevibeType.caption,
                        color = RevibeColors.TextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Instruction("1", "Open Spotify Account → Privacy and request Account data.")
                    Instruction("2", "Request Extended streaming history separately if you want lifetime history.")
                    Instruction("3", "Download the ZIP files Spotify emails you, then return here.")
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RevibeButton(
                            "Open Spotify privacy",
                            { uriHandler.openUri(SPOTIFY_PRIVACY_URL) },
                            kind = ButtonKind.Outline,
                            icon = Icons.Rounded.Language,
                        )
                        RevibeButton(
                            if (vm.spotifyImportBusy) "Reading…" else "Upload Spotify ZIP",
                            vm::chooseSpotifyArchive,
                            enabled = !vm.spotifyImportBusy,
                            icon = Icons.Rounded.CloudUpload,
                        )
                    }
                }
            }
            item {
                ImportCard("What stays local") {
                    Text(
                        "Revibe reads only music-related files in the archive. Payment details, addresses, phone numbers, IP addresses, technical logs, precise location, messages and support correspondence are ignored. The ZIP is never uploaded. Only selected music records are added locally and, when signed in, synchronized through the existing account sync.",
                        style = RevibeType.caption,
                        color = RevibeColors.TextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Supported: playlists, saved tracks, saved albums, followed artists, account streaming history, extended streaming history, search queries, taste profile and Wrapped summaries.",
                        style = RevibeType.caption,
                        color = RevibeColors.TextTertiary,
                    )
                }
            }
        } else {
            item {
                ImportCard("Preview") {
                    Text("Source archive", style = RevibeType.label, color = RevibeColors.TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Actual records found in the uploaded files. Nothing is committed until you start the import.",
                        style = RevibeType.caption,
                        color = RevibeColors.TextTertiary,
                    )
                    Spacer(Modifier.height(10.dp))
                    ImportStat("Playlists", matched.archive.playlists.size, "${matched.archive.playlists.sumOf { it.tracks.size }} ordered tracks")
                    ImportStat("Saved tracks", matched.archive.likedTracks.size, null)
                    ImportStat("Saved albums", matched.archive.savedAlbums.size, null)
                    ImportStat("Followed artists", matched.archive.followedArtists.size, null)
                    ImportStat("Listening events", matched.archive.history.size, "deduplicated across overlapping exports")
                    ImportStat("Search queries", matched.archive.searches.size, null)
                    ImportStat("Taste profile", if (matched.archive.tasteProfileSummary != null) 1 else 0, null)
                    ImportStat("Wrapped", if (matched.archive.wrappedSummary != null) 1 else 0, null)
                    ImportStat("Unmatched / needs review", matched.unmatchedCount, "never fabricated")
                    if (matched.archive.ignoredFiles.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${matched.archive.ignoredFiles.size} other files were ignored by the privacy filter.",
                            style = RevibeType.caption,
                            color = RevibeColors.TextTertiary,
                        )
                    }
                    matched.archive.readme?.let { readme ->
                        Spacer(Modifier.height(10.dp))
                        Text("Archive Read Me First", style = RevibeType.label, color = RevibeColors.TextSecondary)
                        Text(
                            readme.take(900),
                            style = RevibeType.caption,
                            color = RevibeColors.TextTertiary,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            item {
                ImportCard("Choose what to import") {
                    val selection = vm.spotifyImportSelection
                    ToggleRow("Playlists", "Recreate names, descriptions and track order.", selection.playlists) { vm.updateImportCategories(selection.copy(playlists = !selection.playlists)) }
                    ToggleRow("Liked songs", "Only Spotify saved tracks, not every playlist track.", selection.likedTracks) { vm.updateImportCategories(selection.copy(likedTracks = !selection.likedTracks)) }
                    ToggleRow("Saved albums", "Adds albums that have a confident catalog match.", selection.savedAlbums) { vm.updateImportCategories(selection.copy(savedAlbums = !selection.savedAlbums)) }
                    ToggleRow("Followed artists", "Only explicit follows in the archive.", selection.followedArtists) { vm.updateImportCategories(selection.copy(followedArtists = !selection.followedArtists)) }
                    ToggleRow("Listening history", "Timestamps, played duration, skips and completions are preserved.", selection.history) { vm.updateImportCategories(selection.copy(history = !selection.history)) }
                    ToggleRow("Search history", "Off by default; synced only when the existing privacy toggle allows it.", selection.searches) { vm.updateImportCategories(selection.copy(searches = !selection.searches)) }
                    ToggleRow("Taste profile / Wrapped", "Retained as bounded local import metadata; it never creates fake plays.", selection.tasteProfile) { vm.updateImportCategories(selection.copy(tasteProfile = !selection.tasteProfile)) }
                    Spacer(Modifier.height(8.dp))
                    ToggleRow(
                        "Include close matches",
                        "Adds ${matched.playlists.flatMap { it.tracks }.count { it.status == MatchStatus.NEEDS_REVIEW } + matched.likedTracks.count { it.status == MatchStatus.NEEDS_REVIEW } + matched.history.count { it.match.status == MatchStatus.NEEDS_REVIEW }} uncertain results. Review the report below first.",
                        vm.spotifyIncludeReview,
                        { vm.setIncludeSpotifyReview(!vm.spotifyIncludeReview) },
                    )
                }
            }
            val needsReview = matched.playlists.flatMap { it.tracks } +
                matched.likedTracks + matched.history.map { it.match }
            val unmatched = needsReview.filter { it.status == MatchStatus.UNMATCHED }.distinctBy { it.source.title + it.source.artist }
            val review = needsReview.filter { it.status == MatchStatus.NEEDS_REVIEW }.distinctBy { it.source.title + it.source.artist }
            if (unmatched.isNotEmpty() || review.isNotEmpty()) {
                item {
                    ImportCard("Match report") {
                        if (review.isNotEmpty()) {
                            Text("Needs review (${review.size})", style = RevibeType.bodyStrong, color = Color(0xFFFFC857))
                            review.take(20).forEach { item ->
                                Text(
                                    "• ${item.source.displayName}${item.track?.let { " → ${it.title}" } ?: ""}",
                                    style = RevibeType.caption,
                                    color = RevibeColors.TextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (review.size > 20) Text("and ${review.size - 20} more", style = RevibeType.caption, color = RevibeColors.TextTertiary)
                            Spacer(Modifier.height(10.dp))
                        }
                        if (unmatched.isNotEmpty()) {
                            Text("Unmatched (${unmatched.size})", style = RevibeType.bodyStrong, color = RevibeColors.Danger)
                            unmatched.take(20).forEach { item ->
                                Text(
                                    "• ${item.source.displayName}",
                                    style = RevibeType.caption,
                                    color = RevibeColors.TextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (unmatched.size > 20) Text("and ${unmatched.size - 20} more", style = RevibeType.caption, color = RevibeColors.TextTertiary)
                            Spacer(Modifier.height(6.dp))
                            Text("Unmatched source metadata is retained in this report only; no playable track is fabricated.", style = RevibeType.caption, color = RevibeColors.TextTertiary)
                        }
                    }
                }
            }
            item {
                ImportProgressLine(vm.spotifyImportProgress.stage, vm.spotifyImportProgress.current, vm.spotifyImportProgress.total)
                vm.spotifyImportError?.let { ImportError(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    RevibeButton(
                        if (vm.spotifyImportBusy) "Importing…" else "Start import",
                        vm::confirmSpotifyImport,
                        enabled = !vm.spotifyImportBusy,
                        icon = Icons.Rounded.CloudUpload,
                    )
                    RevibeButton("Choose another ZIP", vm::chooseSpotifyArchive, kind = ButtonKind.Outline, enabled = !vm.spotifyImportBusy)
                    RevibeButton("Cancel", vm::leaveImportScreens, kind = ButtonKind.Ghost)
                }
            }
        }
        item {
            vm.spotifyImportError?.takeIf { matched == null }?.let { ImportError(it) }
            if (vm.spotifyImportBusy) {
                ImportProgressLine(vm.spotifyImportProgress.stage, vm.spotifyImportProgress.current, vm.spotifyImportProgress.total)
            }
        }
    }
}

@Composable
private fun ImportHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RevibeButton("Back", onBack, kind = ButtonKind.Ghost, icon = Icons.Rounded.ArrowBack)
            Text(title, style = RevibeType.display.copy(fontSize = 32.sp), color = RevibeColors.TextPrimary)
        }
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = RevibeType.caption, color = RevibeColors.TextSecondary, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun ImportCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(RevibeColors.Surface)
            .padding(18.dp),
    ) {
        Text(title, style = RevibeType.heading.copy(fontSize = 18.sp), color = RevibeColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun Instruction(number: String, text: String) {
    Row(Modifier.padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            number,
            style = RevibeType.caption.copy(fontWeight = FontWeight.Bold),
            color = RevibeColors.Accent,
            modifier = Modifier.width(16.dp),
        )
        Text(text, style = RevibeType.caption, color = RevibeColors.TextSecondary)
    }
}

@Composable
private fun PreviewTrackRow(
    index: Int,
    title: String,
    subtitle: String,
    artwork: String?,
    status: MatchStatus,
    confidence: Float,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    HoverSurface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        onClick = { if (enabled) onClick() },
        shape = RoundedCornerShape(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("${index + 1}", style = RevibeType.caption, color = RevibeColors.TextTertiary, modifier = Modifier.width(24.dp))
            if (artwork != null) {
                Artwork(artwork, Modifier.size(42.dp), cornerRadius = 4.dp)
            } else {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(4.dp)).background(gradientFor(title)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Description, null, tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(16.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = RevibeType.bodyStrong, color = RevibeColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = RevibeType.caption, color = RevibeColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val statusColor = when (status) {
                MatchStatus.MATCHED -> RevibeColors.Accent
                MatchStatus.NEEDS_REVIEW -> Color(0xFFFFC857)
                else -> RevibeColors.TextTertiary
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(status.label, style = RevibeType.caption, color = statusColor)
                if (confidence > 0f) {
                    Text("${(confidence * 100).toInt()}%", style = RevibeType.caption.copy(fontSize = 10.sp), color = RevibeColors.TextTertiary)
                }
            }
            Chip(if (selected) "Included" else "Removed", selected, onClick = onClick, horizontalPadding = 8.dp)
        }
    }
}

@Composable
private fun ImportCompleteCard(summary: ImportSummary, onOpenPlaylist: (() -> Unit)?, onDone: () -> Unit) {
    ImportCard("Import complete") {
        ImportStat("Playlists created", summary.playlistsCreated, null)
        if (summary.playlistsSkipped > 0) ImportStat("Playlists already imported", summary.playlistsSkipped, null)
        ImportStat("Tracks added", summary.tracksAdded, null)
        ImportStat("Liked songs added", summary.likedTracksAdded, null)
        ImportStat("Albums added", summary.albumsAdded, null)
        ImportStat("Artists followed", summary.artistsAdded, null)
        ImportStat("History events added", summary.historyAdded, if (summary.historySkipped > 0) "${summary.historySkipped} already present" else null)
        ImportStat("Search queries added", summary.searchesAdded, null)
        ImportStat("Unmatched tracks", summary.unmatched.size, "kept in the report, never fabricated")
        if (summary.unmatched.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            summary.unmatched.take(20).forEach { track ->
                Text(
                    "• ${track.displayName}",
                    style = RevibeType.caption,
                    color = RevibeColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (summary.unmatched.size > 20) {
                Text("and ${summary.unmatched.size - 20} more", style = RevibeType.caption, color = RevibeColors.TextTertiary)
            }
        }
        summary.notes.forEach { note ->
            Spacer(Modifier.height(6.dp))
            Text("• $note", style = RevibeType.caption, color = RevibeColors.TextTertiary)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onOpenPlaylist != null) RevibeButton("Open playlist", onOpenPlaylist)
            RevibeButton("Done", onDone, kind = if (onOpenPlaylist == null) ButtonKind.Outline else ButtonKind.Ghost)
        }
    }
}

@Composable
private fun ImportStat(label: String, value: Int, note: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = RevibeType.body, color = RevibeColors.TextSecondary, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 80.dp)) {
            Text(value.toString(), style = RevibeType.bodyStrong, color = RevibeColors.TextPrimary)
            if (note != null) Text(note, style = RevibeType.caption.copy(fontSize = 10.sp), color = RevibeColors.TextTertiary)
        }
    }
}

@Composable
private fun ImportProgressLine(stage: String, current: Int, total: Int) {
    if (stage == "Ready" && current == 0 && total == 0) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stage, style = RevibeType.caption, color = RevibeColors.TextSecondary)
        if (total > 0) Text("$current / $total", style = RevibeType.caption, color = RevibeColors.TextTertiary)
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun ImportError(message: String) {
    Text(
        message,
        style = RevibeType.caption,
        color = RevibeColors.Danger,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(RevibeColors.Danger.copy(alpha = 0.10f)).padding(10.dp),
    )
}
