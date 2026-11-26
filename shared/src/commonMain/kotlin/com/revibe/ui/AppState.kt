package com.revibe.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.revibe.AppGraph
import com.revibe.catalog.ArtistKnowledge
import com.revibe.model.Album
import com.revibe.model.AlbumDetail
import com.revibe.model.Artist
import com.revibe.model.ArtistDetail
import com.revibe.model.Playlist
import com.revibe.model.ScanReport
import com.revibe.model.SearchResults
import com.revibe.model.Track
import com.revibe.lyrics.Lyrics
import com.revibe.ui.theme.RevibeColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Route {
    data object Home : Route
    data object Search : Route
    data object Library : Route
    data object Liked : Route
    data object Local : Route
    data object Settings : Route
    data class AlbumRoute(val id: String) : Route
    data class ArtistRoute(val id: String) : Route
    data class PlaylistRoute(val id: String) : Route
}

enum class RightTab(val label: String) { NOW_PLAYING("Now playing"), LYRICS("Lyrics"), QUEUE("Queue") }

/** A single card in a shelf, independent of whether it is a track, album or artist. */
data class MediaItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val round: Boolean = false,
    val track: Track? = null,
    val album: Album? = null,
    val artist: Artist? = null,
)

data class Shelf(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val items: List<MediaItem> = emptyList(),
)

data class PlaylistDetail(val playlist: Playlist, val tracks: List<Track>)

/**
 * Compose state holder. Intentionally explicit: every screen's data, loading and
 * error state is visible here rather than hidden in dozens of tiny view models.
 */
class AppViewModel(private val graph: AppGraph) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var route by mutableStateOf<Route>(Route.Home)
        private set
    var backStack by mutableStateOf<List<Route>>(emptyList())
        private set
    var forwardStack by mutableStateOf<List<Route>>(emptyList())
        private set

    var homeLoading by mutableStateOf(true)
        private set
    var homeError by mutableStateOf<String?>(null)
        private set
    var shelves by mutableStateOf<List<Shelf>>(emptyList())
        private set
    var recentTracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var followedArtistCards by mutableStateOf<List<Artist>>(emptyList())
        private set

    var searchQuery by mutableStateOf("")
        private set
    var searchResults by mutableStateOf<SearchResults?>(null)
        private set
    var searchLoading by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set
    var searchTab by mutableStateOf(0)
        private set
    var recentSearches by mutableStateOf<List<String>>(emptyList())
        private set

    var libraryTab by mutableStateOf(0)
        private set
    var playlists by mutableStateOf<List<Playlist>>(emptyList())
        private set
    var likedTracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var likedAlbums by mutableStateOf<List<Album>>(emptyList())
        private set
    var followedArtists by mutableStateOf<List<Artist>>(emptyList())
        private set
    var likedIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var albumDetail by mutableStateOf<AlbumDetail?>(null)
        private set
    var artistDetail by mutableStateOf<ArtistDetail?>(null)
        private set
    var playlistDetail by mutableStateOf<PlaylistDetail?>(null)
        private set
    var detailLoading by mutableStateOf(false)
        private set
    var detailError by mutableStateOf<String?>(null)
        private set

    var localTracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var scanFolders by mutableStateOf<List<String>>(emptyList())
        private set
    var scanning by mutableStateOf(false)
        private set
    var scanProgress by mutableStateOf(0 to 0)
        private set
    var scanReport by mutableStateOf<ScanReport?>(null)
        private set

    var rightTab by mutableStateOf(RightTab.NOW_PLAYING)
    var rightPanelVisible by mutableStateOf(true)
        private set
    var compactSidebar by mutableStateOf(false)
        private set
    var market by mutableStateOf("IN")
        private set
    var accent by mutableStateOf(RevibeColors.Accent)
        private set
    var lyrics by mutableStateOf<Lyrics?>(null)
        private set
    var lyricsLoading by mutableStateOf(false)
        private set
    var lyricsOffsetMs by mutableStateOf(0L)
        private set
    var artistKnowledge by mutableStateOf<ArtistKnowledge?>(null)
        private set
    var toast by mutableStateOf<String?>(null)
        private set
    var showCreatePlaylist by mutableStateOf(false)
    var addToPlaylistTrack by mutableStateOf<Track?>(null)
    var nowPlayingExpanded by mutableStateOf(false)
    var showAboutDialog by mutableStateOf(false)

    val playerState = graph.player.state
    val settings = graph.settings.settings
    val artworkLoader = graph.artwork
    val platformName = graph.platformName
    val dataDirectory = graph.dataDirectory
    val appVersion = graph.appVersion

    private var searchJob: Job? = null
    private var volumePersistJob: Job? = null
    private var toastJob: Job? = null

    init {
        scope.launch {
            graph.settings.load()
            val loaded = graph.settings.settings.value
            market = loaded.market
            compactSidebar = loaded.compactSidebar
            rightPanelVisible = loaded.showRightPanel
            graph.player.setVolume(loaded.volume)
            refreshLibrary()
            loadHome()
        }
        scope.launch {
            graph.player.state
                .map { it.current }
                .distinctUntilChanged()
                .collectLatest { track ->
                    accent = RevibeColors.Accent
                    nowPlayingExpanded = false
                    lyrics = null
                    artistKnowledge = null
                    if (track == null) return@collectLatest
                    launch { loadAccent(track) }
                    launch { loadLyrics(track) }
                    launch { loadKnowledge(track) }
                }
        }
    }

    // ---------------------------------------------------------------- navigation

    fun navigate(next: Route) {
        if (next == route) return
        backStack = backStack + route
        forwardStack = emptyList()
        route = next
        onRouteEntered(next)
    }

    fun goBack() {
        val previous = backStack.lastOrNull() ?: return
        forwardStack = forwardStack + route
        backStack = backStack.dropLast(1)
        route = previous
        onRouteEntered(previous)
    }

    fun goForward() {
        val next = forwardStack.lastOrNull() ?: return
        backStack = backStack + route
        forwardStack = forwardStack.dropLast(1)
        route = next
        onRouteEntered(next)
    }

    private fun onRouteEntered(route: Route) {
        when (route) {
            Route.Home -> loadHome()
            Route.Library -> scope.launch { refreshLibrary() }
            Route.Liked -> scope.launch { refreshLibrary() }
            Route.Local -> scope.launch { refreshLocal() }
            Route.Settings -> Unit
            Route.Search -> Unit
            is Route.AlbumRoute -> loadAlbum(route.id)
            is Route.ArtistRoute -> loadArtist(route.id)
            is Route.PlaylistRoute -> loadPlaylist(route.id)
        }
    }

    fun openAlbum(album: Album) {
        navigate(Route.AlbumRoute(album.id))
    }

    fun openArtist(artistId: String?) {
        artistId ?: return
        navigate(Route.ArtistRoute(artistId))
    }

    fun openPlaylist(playlistId: String) {
        navigate(Route.PlaylistRoute(playlistId))
    }

    fun navigateToAlbumId(albumId: String?) {
        if (albumId != null) navigate(Route.AlbumRoute(albumId))
    }

    fun openLiked() = navigate(Route.Liked)
    fun openLocal() = navigate(Route.Local)
    fun openSettings() = navigate(Route.Settings)
    fun openHome() = navigate(Route.Home)
    fun openSearch() = navigate(Route.Search)

    // --------------------------------------------------------------------- home

    private fun loadHome() {
        homeLoading = true
        homeError = null
        val currentMarket = graph.settings.settings.value.market
        scope.launch {
            runCatching {
                coroutineScope {
                    val seedDefs = seedShelves(currentMarket)
                    val shelfTasks = seedDefs.map { (query, title) ->
                        async {
                            val albums = graph.catalog.shelfAlbums(query, currentMarket, 14)
                            if (albums.isEmpty()) null else Shelf(
                                id = query,
                                title = title,
                                subtitle = "Fresh from the ${marketLabel(currentMarket)} storefront",
                                items = albums.map { it.toMediaItem() },
                            )
                        }
                    }
                    val recentTask = async { graph.library.recentlyPlayed(12) }
                    val followedTask = async { graph.library.followedArtists() }
                    val recent = recentTask.await()
                    val followed = followedTask.await()
                    val loadedShelves = shelfTasks.awaitAll().filterNotNull()
                    Triple(recent, followed, loadedShelves)
                }
            }.onSuccess { (recent, followed, loaded) ->
                recentTracks = recent
                followedArtistCards = followed
                shelves = loaded
                homeLoading = false
                if (loaded.isEmpty()) homeError = "The catalog did not return anything. Check your connection and try again."
            }.onFailure { error ->
                homeError = error.message ?: "Could not reach the music catalog."
                homeLoading = false
            }
        }
    }

    fun refreshHome() = loadHome()

    private fun seedShelves(market: String): List<Pair<String, String>> = when (market.uppercase()) {
        "IN" -> listOf(
            "bollywood hits" to "Bollywood, right now",
            "punjabi pop" to "Punjabi heat",
            "hindi indie" to "Hindi indie",
            "lofi chill" to "Lo-fi focus",
            "arijit singh" to "Arijit Singh essentials",
        )
        "US" -> listOf(
            "pop hits" to "Pop, right now",
            "hip hop" to "Hip-hop heat",
            "indie" to "Indie discoveries",
            "lofi chill" to "Lo-fi focus",
            "country" to "Country picks",
        )
        "GB" -> listOf(
            "uk pop" to "UK pop, right now",
            "british indie" to "British indie",
            "hip hop" to "Hip-hop heat",
            "lofi chill" to "Lo-fi focus",
            "electronic" to "Electronic picks",
        )
        else -> listOf(
            "pop hits" to "Pop, right now",
            "hip hop" to "Hip-hop heat",
            "indie" to "Indie discoveries",
            "lofi chill" to "Lo-fi focus",
            "rock" to "Rock essentials",
        )
    }

    private suspend fun loadAccent(track: Track) {
        withContext(graph.io) {
            val palette = runCatching { graph.artwork.load(track.artworkUrl ?: track.localPath, 96) }.getOrNull()
            if (palette != null) {
                accent = palette.dominantColor
            }
        }
    }

    private suspend fun loadLyrics(track: Track) {
        lyricsLoading = true
        lyrics = withContext(graph.io) {
            runCatching { graph.lyrics.lyricsFor(track) }.getOrNull()
        }
        lyricsLoading = false
    }

    private suspend fun loadKnowledge(track: Track) {
        val knowledge = withContext(graph.io) {
            runCatching { graph.catalog.artistKnowledge(track.artist) }.getOrNull()
        }
        artistKnowledge = knowledge
    }

    fun retryLyrics() {
        val track = playerState.value.current ?: return
        lyrics = null
        scope.launch { loadLyrics(track) }
    }

    fun shiftLyricsOffset(deltaMs: Long) {
        lyricsOffsetMs = (lyricsOffsetMs + deltaMs).coerceIn(-10_000L, 10_000L)
    }

    // -------------------------------------------------------------------- search

    fun onSearchQueryChange(query: String) {
        searchQuery = query
        searchJob?.cancel()
        searchError = null
        if (query.trim().length < 2) {
            searchResults = null
            searchLoading = false
            return
        }
        searchJob = scope.launch {
            delay(380)
            performSearch(query)
        }
    }

    fun submitSearch(query: String = searchQuery) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        searchQuery = trimmed
        searchJob?.cancel()
        searchJob = scope.launch { performSearch(trimmed) }
    }

    fun searchGenre(term: String) {
        searchQuery = term
        navigate(Route.Search)
        submitSearch(term)
    }

    private suspend fun performSearch(query: String) {
        navigate(Route.Search)
        searchLoading = true
        searchError = null
        val currentMarket = graph.settings.settings.value.market
        runCatching { withContext(graph.io) { graph.catalog.search(query, currentMarket) } }
            .onSuccess { results ->
                searchResults = results
                searchLoading = false
                if (!results.isEmpty) {
                    recentSearches = (listOf(query) + recentSearches).distinct().take(8)
                }
            }
            .onFailure { error ->
                searchError = error.message ?: "Search failed."
                searchLoading = false
            }
    }

    fun selectSearchTab(index: Int) { searchTab = index }
    fun selectLibraryTab(index: Int) { libraryTab = index }

    // ------------------------------------------------------------------ library

    fun refreshLibrary() {
        scope.launch {
            withContext(graph.io) {
                runCatching {
                    val likes = graph.library.likedTracks()
                    val ids = graph.library.likedIds()
                    val lists = graph.library.playlists()
                    val albums = graph.library.likedAlbums()
                    val artists = graph.library.followedArtists()
                    Quad(likes, ids, lists, albums to artists)
                }
            }.onSuccess { (likes, ids, lists, pair) ->
                likedTracks = likes
                likedIds = ids
                playlists = lists
                likedAlbums = pair.first
                followedArtists = pair.second
            }
        }
    }

    fun isLiked(trackId: String): Boolean = trackId in likedIds

    fun toggleLike(track: Track) {
        scope.launch {
            val nowLiked = withContext(graph.io) { runCatching { graph.library.toggleLike(track) }.getOrDefault(false) }
            likedIds = if (nowLiked) likedIds + track.id else likedIds - track.id
            likedTracks = withContext(graph.io) { runCatching { graph.library.likedTracks() }.getOrDefault(likedTracks) }
            showToast(if (nowLiked) "Added to Liked Songs" else "Removed from Liked Songs")
        }
    }

    fun toggleLikedAlbum(album: Album) {
        scope.launch {
            val nowLiked = withContext(graph.io) { runCatching { graph.library.toggleLikedAlbum(album) }.getOrDefault(false) }
            likedAlbums = if (nowLiked) listOf(album) + likedAlbums else likedAlbums.filterNot { it.id == album.id }
            showToast(if (nowLiked) "Saved to your library" else "Removed from your library")
        }
    }

    fun toggleFollowArtist(artist: Artist) {
        scope.launch {
            val nowFollowing = withContext(graph.io) { runCatching { graph.library.toggleFollowArtist(artist) }.getOrDefault(false) }
            followedArtists = if (nowFollowing) listOf(artist) + followedArtists else followedArtists.filterNot { it.id == artist.id }
            artistDetail = artistDetail?.let {
                if (it.artist.id == artist.id) it.copy(artist = it.artist) else it
            }
            showToast(if (nowFollowing) "Following ${artist.name}" else "Unfollowed ${artist.name}")
        }
    }

    fun isArtistFollowed(artistId: String): Boolean = followedArtists.any { it.id == artistId }
    fun isAlbumLiked(albumId: String): Boolean = likedAlbums.any { it.id == albumId }

    fun createPlaylist(name: String) {
        scope.launch {
            val playlist = withContext(graph.io) { runCatching { graph.library.createPlaylist(name) }.getOrNull() }
            if (playlist != null) {
                playlists = playlists + playlist
                showCreatePlaylist = false
                showToast("Created \"${playlist.name}\"")
                navigate(Route.PlaylistRoute(playlist.id))
            } else {
                showToast("Could not create the playlist")
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        scope.launch {
            withContext(graph.io) { runCatching { graph.library.deletePlaylist(playlistId) } }
            playlists = playlists.filterNot { it.id == playlistId }
            if (route == Route.PlaylistRoute(playlistId)) goBack()
            showToast("Playlist deleted")
        }
    }

    fun renamePlaylist(playlistId: String, name: String) {
        scope.launch {
            withContext(graph.io) { runCatching { graph.library.renamePlaylist(playlistId, name) } }
            playlists = playlists.map { if (it.id == playlistId) it.copy(name = name) else it }
            playlistDetail = playlistDetail?.let {
                if (it.playlist.id == playlistId) it.copy(playlist = it.playlist.copy(name = name)) else it
            }
            showToast("Playlist renamed")
        }
    }

    fun addToPlaylist(playlistId: String, track: Track) {
        scope.launch {
            val added = withContext(graph.io) { runCatching { graph.library.addToPlaylist(playlistId, track) }.getOrDefault(false) }
            addToPlaylistTrack = null
            refreshLibrary()
            showToast(if (added) "Added to playlist" else "Already in that playlist")
        }
    }

    fun removeFromPlaylist(playlistId: String, track: Track) {
        scope.launch {
            withContext(graph.io) { runCatching { graph.library.removeFromPlaylist(playlistId, track.id) } }
            loadPlaylist(playlistId)
            showToast("Removed from playlist")
        }
    }

    // ------------------------------------------------------------------- detail

    private fun loadAlbum(albumId: String) {
        albumDetail = null
        detailError = null
        detailLoading = true
        val currentMarket = graph.settings.settings.value.market
        scope.launch {
            runCatching { withContext(graph.io) { graph.catalog.album(albumId, currentMarket) } }
                .onSuccess { albumDetail = it; detailLoading = false }
                .onFailure {
                    detailError = it.message ?: "Could not load this album."
                    detailLoading = false
                }
        }
    }

    private fun loadArtist(artistId: String) {
        artistDetail = null
        detailError = null
        detailLoading = true
        val currentMarket = graph.settings.settings.value.market
        scope.launch {
            runCatching { withContext(graph.io) { graph.catalog.artist(artistId, currentMarket) } }
                .onSuccess { artistDetail = it; detailLoading = false }
                .onFailure {
                    detailError = it.message ?: "Could not load this artist."
                    detailLoading = false
                }
        }
    }

    private fun loadPlaylist(playlistId: String) {
        detailLoading = true
        detailError = null
        scope.launch {
            runCatching {
                withContext(graph.io) {
                    val playlist = graph.library.playlists().firstOrNull { it.id == playlistId }
                    val tracks = graph.library.playlistTracks(playlistId)
                    if (playlist == null) null else PlaylistDetail(playlist, tracks)
                }
            }.onSuccess {
                playlistDetail = it
                detailLoading = false
                if (it == null) detailError = "That playlist no longer exists."
            }.onFailure {
                detailError = it.message ?: "Could not load this playlist."
                detailLoading = false
            }
        }
    }

    fun reloadPlaylist() {
        val id = (route as? Route.PlaylistRoute)?.id ?: return
        loadPlaylist(id)
    }

    // --------------------------------------------------------------------- play

    fun play(track: Track, context: List<Track> = listOf(track), contextName: String = "Revibe") {
        val list = context.ifEmpty { listOf(track) }
        val index = list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        graph.player.playQueue(list, index, contextName)
    }

    fun playAlbum(detail: AlbumDetail, shuffled: Boolean = false) {
        if (detail.tracks.isEmpty()) return
        if (shuffled && !playerState.value.shuffle) graph.player.toggleShuffle()
        if (!shuffled && playerState.value.shuffle) graph.player.toggleShuffle()
        graph.player.playQueue(detail.tracks, 0, detail.album.title)
    }

    /** Resolves an album before playing so card play buttons start audio, not a page open. */
    fun playAlbumById(albumId: String) {
        scope.launch {
            val currentMarket = graph.settings.settings.value.market
            val detail = withContext(graph.io) {
                runCatching { graph.catalog.album(albumId, currentMarket) }.getOrNull()
            } ?: return@launch
            if (detail.tracks.isEmpty()) return@launch
            if (playerState.value.shuffle) graph.player.toggleShuffle()
            graph.player.playQueue(detail.tracks, 0, detail.album.title)
        }
    }

    fun playLiked(shuffled: Boolean = false) {
        if (likedTracks.isEmpty()) return
        if (shuffled && !playerState.value.shuffle) graph.player.toggleShuffle()
        if (!shuffled && playerState.value.shuffle) graph.player.toggleShuffle()
        graph.player.playQueue(likedTracks, 0, "Liked Songs")
    }

    fun playPlaylist(detail: PlaylistDetail, shuffled: Boolean = false) {
        if (detail.tracks.isEmpty()) return
        if (shuffled && !playerState.value.shuffle) graph.player.toggleShuffle()
        if (!shuffled && playerState.value.shuffle) graph.player.toggleShuffle()
        graph.player.playQueue(detail.tracks, 0, detail.playlist.name)
    }

    fun playArtistTop(tracks: List<Track>, artistName: String) {
        if (tracks.isEmpty()) return
        graph.player.playQueue(tracks, 0, artistName)
    }

    fun addToQueue(track: Track) {
        graph.player.addToQueue(track)
        showToast("Added to queue")
    }

    fun playNext(track: Track) {
        graph.player.playNext(track)
        showToast("Playing next")
    }

    fun toggleShuffle() = graph.player.toggleShuffle()
    fun cycleRepeat() = graph.player.cycleRepeat()
    fun togglePlayPause() = graph.player.togglePlayPause()
    fun nextTrack() = graph.player.next()
    fun previousTrack() = graph.player.previous()
    fun cycleRepeatMode() = graph.player.cycleRepeat()
    fun playQueueAt(index: Int) = graph.player.playAt(index)
    fun removeQueueAt(index: Int) = graph.player.removeFromQueue(index)
    fun seekToFraction(fraction: Float) {
        val duration = playerState.value.durationMs
        if (duration > 0) graph.player.seekTo((duration * fraction).toLong())
    }

    fun setVolume(volume: Float) {
        graph.player.setVolume(volume)
        volumePersistJob?.cancel()
        volumePersistJob = scope.launch {
            delay(500)
            graph.settings.setVolume(volume)
        }
    }

    // ---------------------------------------------------------------------- local

    fun refreshLocal() {
        scope.launch {
            withContext(graph.io) {
                runCatching {
                    graph.local.tracks() to graph.local.folders()
                }
            }.onSuccess { (tracks, folders) ->
                localTracks = tracks
                scanFolders = folders
            }
        }
    }

    fun addLocalFolder() {
        scope.launch {
            val folder = withContext(Dispatchers.Main) { runCatching { graph.folderPicker.pickFolder() }.getOrNull() }
                ?: return@launch
            withContext(graph.io) { runCatching { graph.local.addFolder(folder) } }
            refreshLocal()
            showToast("Folder added")
        }
    }

    fun removeLocalFolder(folder: String) {
        scope.launch {
            withContext(graph.io) { runCatching { graph.local.removeFolder(folder) } }
            refreshLocal()
        }
    }

    fun scanLocal() {
        if (scanning) return
        scanning = true
        scanReport = null
        scope.launch {
            val report = withContext(graph.io) {
                runCatching {
                    graph.local.scan { scanned, total -> scanProgress = scanned to total }
                }.getOrElse { ScanReport(errors = listOf(it.message ?: "Scan failed")) }
            }
            scanReport = report
            scanning = false
            refreshLocal()
            showToast(
                when {
                    report.errors.isNotEmpty() && report.added == 0 -> "Scan finished with errors"
                    else -> "Library updated: ${report.added} added, ${report.updated} refreshed"
                },
            )
        }
    }

    fun removeLocalTrack(track: Track) {
        showToast("Delete files from disk is not enabled in this build")
    }

    // ------------------------------------------------------------------- settings

    fun changeMarket(newMarket: String) {
        scope.launch {
            graph.settings.setMarket(newMarket)
            market = graph.settings.settings.value.market
            loadHome()
            if (searchQuery.length >= 2) performSearch(searchQuery)
        }
    }

    fun toggleRightPanel() {
        rightPanelVisible = !rightPanelVisible
        scope.launch { graph.settings.setRightPanel(rightPanelVisible) }
    }

    fun toggleCompactSidebar() {
        compactSidebar = !compactSidebar
        scope.launch { graph.settings.setCompactSidebar(compactSidebar) }
    }

    fun selectRightTab(tab: RightTab) {
        rightTab = tab
        if (!rightPanelVisible) rightPanelVisible = true
    }

    fun showToast(message: String) {
        toast = message
        toastJob?.cancel()
        toastJob = scope.launch {
            delay(2600)
            if (toast == message) toast = null
        }
    }

    fun dismissToast() {
        toast = null
    }

    fun clearPlayerError() = graph.player.clearError()

    fun shutdown() {
        runCatching { graph.player.release() }
        scope.cancel()
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}

fun Album.toMediaItem() = MediaItem(
    id = id,
    title = title,
    subtitle = artist,
    imageUrl = artworkUrl,
    album = this,
)

fun Track.toMediaItem() = MediaItem(
    id = id,
    title = title,
    subtitle = artist,
    imageUrl = artworkUrl,
    track = this,
)

private fun marketLabel(market: String): String = when (market.uppercase()) {
    "IN" -> "Indian"
    "US" -> "US"
    "GB" -> "UK"
    "DE" -> "German"
    "AU" -> "Australian"
    "JP" -> "Japanese"
    "BR" -> "Brazilian"
    "FR" -> "French"
    else -> market.uppercase()
}

fun Artist.toMediaItem() = MediaItem(
    id = id,
    title = name,
    subtitle = genre ?: "Artist",
    imageUrl = imageUrl,
    round = true,
    artist = this,
)
