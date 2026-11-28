package com.revibe.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.revibe.AppGraph
import com.revibe.account.AccountState
import com.revibe.imports.ImportCategorySelection
import com.revibe.imports.ImportHistoryItem
import com.revibe.imports.ImportProgress
import com.revibe.imports.ImportSummary
import com.revibe.imports.MatchStatus
import com.revibe.imports.PlaylistFilePreview
import com.revibe.imports.SpotifyMatchedArchive
import com.revibe.account.AuthException
import com.revibe.catalog.ArtistKnowledge
import com.revibe.model.Album
import com.revibe.model.AlbumDetail
import com.revibe.model.LayoutSpec
import com.revibe.model.Artist
import com.revibe.model.ArtistDetail
import com.revibe.model.PlayedPlaylist
import com.revibe.model.PlayedTrack
import com.revibe.model.Playlist
import com.revibe.model.PlaylistTrackEntry
import com.revibe.model.ScanReport
import com.revibe.model.SearchResults
import com.revibe.model.Track
import com.revibe.model.UserActivitySnapshot
import com.revibe.image.artworkColorSourceUrl
import com.revibe.lyrics.Lyrics
import com.revibe.personalization.HomepagePlan
import com.revibe.personalization.HomepageSectionKind
import com.revibe.personalization.HomepageSectionManager
import com.revibe.personalization.UserMaturity
import com.revibe.playback.PlaybackSession
import com.revibe.ui.theme.RevibeColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
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
    data object Account : Route
    data object ImportPlaylist : Route
    data object SpotifyImport : Route
    data class AlbumRoute(val id: String) : Route
    data class ArtistRoute(val id: String) : Route
    data class PlaylistRoute(val id: String) : Route
}

enum class RightTab(val label: String) { NOW_PLAYING("Now playing"), LYRICS("Lyrics"), QUEUE("Queue") }

/** The home filter chips. Each one maps to real catalog discovery queries. */
enum class HomeCategory(val label: String) {
    ALL("All"),
    BOLLYWOOD("Bollywood"),
    GLOBAL_POP("Global Pop"),
    INDIE("Indie"),
    NINETIES("90s Songs"),
    PUNJABI("Punjabi"),
    LOFI("Lo-fi"),
}

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

data class PlaylistDetail(
    val playlist: Playlist,
    val tracks: List<Track>,
    val entries: List<PlaylistTrackEntry> = emptyList(),
)

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

    /** Real, local-only activity summary used by the homepage maturity system. */
    var activity by mutableStateOf(UserActivitySnapshot())
        private set
    var maturity by mutableStateOf(UserMaturity.NEW)
        private set
    var homePlan by mutableStateOf(HomepageSectionManager.plan(UserActivitySnapshot(), false))
        private set
    var personalShelves by mutableStateOf<List<Shelf>>(emptyList())
        private set
    var recentHistory by mutableStateOf<List<PlayedTrack>>(emptyList())
        private set
    var homeCategory by mutableStateOf(HomeCategory.ALL)
        private set
    var categoryShelves by mutableStateOf<List<Shelf>>(emptyList())
        private set
    var categoryLoading by mutableStateOf(false)
        private set
    var categoryError by mutableStateOf<String?>(null)
        private set

    /** Playlists with a real persisted play, newest first. Drives the home grid. */
    var recentlyPlayedPlaylists by mutableStateOf<List<PlayedPlaylist>>(emptyList())

    /** Library collection that started the current queue, used to highlight it in Your Library. */
    var playingPlaylistId by mutableStateOf<String?>(null)
        private set
    var playingAlbumId by mutableStateOf<String?>(null)
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
    var importingPlaylist by mutableStateOf(false)
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

    /** Non-null while the right rail is showing the playlist song picker. */
    var addToPlaylistPlaylistId by mutableStateOf<String?>(null)
        private set
    var addToPlaylistQuery by mutableStateOf("")
        private set
    var addToPlaylistResults by mutableStateOf<List<Track>>(emptyList())
        private set
    var addToPlaylistSuggestions by mutableStateOf<List<Track>>(emptyList())
        private set
    var addToPlaylistAddedIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var addToPlaylistLoading by mutableStateOf(false)
        private set
    var addToPlaylistError by mutableStateOf<String?>(null)
        private set
    var compactSidebar by mutableStateOf(false)
        private set
    var leftPanelWidth by mutableStateOf(LayoutSpec.LEFT_DEFAULT)
        private set
    var rightPanelWidth by mutableStateOf(LayoutSpec.RIGHT_DEFAULT)
        private set
    var market by mutableStateOf("IN")
        private set
    var accent by mutableStateOf(RevibeColors.Accent)
        private set

    /** Catalog artwork first, then a matched enrichment provider. */
    var currentArtworkUrl by mutableStateOf<String?>(null)
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
    var showAuthModal by mutableStateOf(false)
    var addToPlaylistTrack by mutableStateOf<Track?>(null)
    var nowPlayingExpanded by mutableStateOf(false)
    var miniplayer by mutableStateOf(false)
        private set
    var showAboutDialog by mutableStateOf(false)

    var playlistImportPreview by mutableStateOf<PlaylistFilePreview?>(null)
        private set
    var playlistImportFileName by mutableStateOf<String?>(null)
        private set
    var playlistImportName by mutableStateOf("")
    var playlistImportDestination by mutableStateOf<String?>(null)
    var playlistImportBusy by mutableStateOf(false)
        private set
    var playlistImportProgress by mutableStateOf(ImportProgress("Ready"))
        private set
    var playlistImportError by mutableStateOf<String?>(null)
        private set
    var playlistImportSummary by mutableStateOf<ImportSummary?>(null)
        private set

    var spotifyArchiveMatches by mutableStateOf<SpotifyMatchedArchive?>(null)
        private set
    var spotifyImportSelection by mutableStateOf(ImportCategorySelection())
    var spotifyImportBusy by mutableStateOf(false)
        private set
    var spotifyImportProgress by mutableStateOf(ImportProgress("Ready"))
        private set
    var spotifyImportError by mutableStateOf<String?>(null)
        private set
    var spotifyImportSummary by mutableStateOf<ImportSummary?>(null)
        private set
    var spotifyIncludeReview by mutableStateOf(false)
        private set

    var importHistory by mutableStateOf<List<ImportHistoryItem>>(emptyList())
        private set
    var importHistoryLoading by mutableStateOf(false)
        private set
    var undoImportBusy by mutableStateOf(false)
        private set

    val accountState = graph.auth.state
    val syncStatus = graph.sync.status
    val updateState = graph.updates.state
    var authBusy by mutableStateOf(false)
        private set
    var authError by mutableStateOf<String?>(null)
        private set
    var accountNotice by mutableStateOf<String?>(null)
        private set

    val playerState = graph.player.state
    val youtubeStatus = graph.youtube.status
    val youtubeDiagnostic = graph.youtube.diagnostic
    val settings = graph.settings.settings
    val artworkLoader = graph.artwork
    val platformName = graph.platformName
    val dataDirectory = graph.dataDirectory
    val appVersion = graph.appVersion

    private var searchJob: Job? = null
    private var addToPlaylistJob: Job? = null
    private var volumePersistJob: Job? = null
    private var layoutPersistJob: Job? = null
    private var toastJob: Job? = null
    private var lastSessionSignature: String? = null
    private var lastSessionBucket: Long = -1L

    init {
        graph.updates.start()
        scope.launch {
            graph.settings.load()
            val loaded = graph.settings.settings.value
            market = loaded.market
            compactSidebar = loaded.compactSidebar
            leftPanelWidth = loaded.leftPanelWidth
            rightPanelWidth = loaded.rightPanelWidth
            rightPanelVisible = loaded.showRightPanel
            graph.player.setVolume(loaded.volume)
            graph.player.setAutoplayEnabled(loaded.autoplayEnabled)
            val restoredSession = withContext(graph.io) {
                runCatching { graph.playbackSession.load() }.getOrNull()
            }
            if (restoredSession != null) graph.player.restoreSession(restoredSession)
            startPlaybackSessionPersistence()
            refreshLibrary()
            loadHome()
            recentSearches = runCatching {
                withContext(graph.io) { graph.library.searchHistory(8).map { it.query } }
            }.getOrDefault(emptyList())

            launch {
                graph.settings.settings
                    .map { Triple(it.market, it.autoplayEnabled, it.syncSearchHistory) }
                    .distinctUntilChanged()
                    .collect { (remoteMarket, remoteAutoplay, _) ->
                        val marketChanged = market != remoteMarket
                        market = remoteMarket
                        if (graph.player.state.value.autoplayEnabled != remoteAutoplay) {
                            graph.player.setAutoplayEnabled(remoteAutoplay)
                        }
                        if (marketChanged) {
                            loadHome()
                            if (homeCategory != HomeCategory.ALL) loadCategory(homeCategory)
                            if (route == Route.Search && searchQuery.length >= 2) performSearch(searchQuery)
                        }
                    }
            }
            launch {
                graph.sync.events.collect {
                    refreshLibrary()
                    when (val current = route) {
                        Route.Home -> loadHome()
                        is Route.PlaylistRoute -> loadPlaylist(current.id)
                        else -> Unit
                    }
                    recentSearches = runCatching {
                        withContext(graph.io) { graph.library.searchHistory(8).map { it.query } }
                    }.getOrDefault(recentSearches)
                }
            }

            graph.auth.restore()
            if (graph.auth.state.value is AccountState.SignedIn && !graph.sync.claimLocalLibrary()) {
                graph.auth.signOut()
                authError = "This device's local library is linked to a different account. Sign in with that account to keep it."
            }
            graph.sync.start()
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
                    currentArtworkUrl = track?.artworkUrl
                    if (track == null) return@collectLatest
                    launch { loadAccent(track) }
                    launch { loadLyrics(track) }
                    launch { loadKnowledge(track) }
                    launch { loadProviderArtwork(track) }
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
        // The picker belongs to one playlist and must not follow the user elsewhere.
        if (route !is Route.PlaylistRoute || route.id != addToPlaylistPlaylistId) {
            clearAddToPlaylist()
        }
        when (route) {
            Route.Home -> loadHome()
            Route.Library -> scope.launch { refreshLibrary() }
            Route.Liked -> scope.launch { refreshLibrary() }
            Route.Local -> scope.launch { refreshLocal() }
            Route.Settings -> Unit
            Route.Account -> scope.launch {
                graph.sync.refreshStatus()
                refreshImportHistory()
            }
            Route.ImportPlaylist, Route.SpotifyImport -> Unit
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

    /**
     * Opens the playlist, album or library view the current track was started from.
     * Falls back to the track's own album so the player title also works for
     * search results and home shelves, which do not carry a playlist id.
     */
    fun openPlayingContext() {
        val state = playerState.value
        val track = state.current ?: return
        val playlistId = playingPlaylistId
        val albumId = playingAlbumId
        when {
            playlistId != null -> navigate(Route.PlaylistRoute(playlistId))
            albumId != null -> navigate(Route.AlbumRoute(albumId))
            state.contextName == "Liked Songs" -> openLiked()
            state.contextName == "Local files" -> openLocal()
            track.albumId != null -> navigate(Route.AlbumRoute(track.albumId))
            track.artistId != null -> openArtist(track.artistId)
            else -> showToast("This song isn't part of an album or playlist")
        }
    }

    fun openLiked() = navigate(Route.Liked)
    fun openAuthModal() {
        showAuthModal = true
    }

    fun closeAuthModal() {
        if (!authBusy) showAuthModal = false
    }

    fun openLocal() = navigate(Route.Local)
    fun openSettings() = navigate(Route.Settings)
    fun openAccount() = navigate(Route.Account)
    fun openHome() = navigate(Route.Home)
    fun openSearch() = navigate(Route.Search)
    fun openImportPlaylist() = navigate(Route.ImportPlaylist)
    fun openSpotifyImport() = navigate(Route.SpotifyImport)

    // --------------------------------------------------------------------- home

    private fun loadHome() {
        homeLoading = true
        homeError = null
        val currentMarket = graph.settings.settings.value.market
        scope.launch {
            runCatching {
                coroutineScope {
                    val activityTask = async { graph.library.activity() }
                    val recentTask = async { graph.library.recentlyPlayed(12) }
                    val historyTask = async { graph.library.recentHistory(24) }
                    val followedTask = async { graph.library.followedArtists() }
                    val playlistsTask = async { graph.library.playlists() }
                    val recentPlaylistsTask = async { graph.library.recentlyPlayedPlaylists(8) }

                    val activity = activityTask.await()
                    val recent = recentTask.await()
                    val history = historyTask.await()
                    val followed = followedTask.await()
                    val playlists = playlistsTask.await()
                    val recentPlaylists = recentPlaylistsTask.await()
                    // A played playlist is real recent history even before the first track row lands.
                    val plan = HomepageSectionManager.plan(activity, recent.isNotEmpty() || recentPlaylists.isNotEmpty())
                    val discovery = loadDiscoveryShelves(currentMarket)
                    val historyArtists = (history.map { it.track.artist } + recent.map { it.artist })
                        .distinctBy { it.trim().lowercase() }
                    val personal = loadPersonalShelves(activity, plan, currentMarket, historyArtists)
                    HomeData(activity, plan, recent, history, followed, playlists, recentPlaylists, discovery, personal)
                }
            }.onSuccess { data ->
                activity = data.activity
                maturity = data.plan.maturity
                homePlan = data.plan
                recentTracks = data.recent
                recentHistory = data.history
                followedArtistCards = data.followed
                playlists = data.playlists
                recentlyPlayedPlaylists = data.recentPlaylists
                shelves = data.discovery
                personalShelves = data.personal
                homeLoading = false
                if (data.discovery.isEmpty() && data.personal.isEmpty()) {
                    homeError = "The catalog did not return anything. Check your connection and try again."
                }
            }.onFailure { error ->
                homeError = error.message ?: "Could not reach the music catalog."
                homeLoading = false
            }
        }
    }

    private suspend fun loadDiscoveryShelves(market: String, category: HomeCategory = HomeCategory.ALL): List<Shelf> = coroutineScope {
        seedShelves(market, category).map { (query, title) ->
            async {
                val albums = runCatching { graph.catalog.shelfAlbums(query, market, 14) }
                    .getOrDefault(emptyList())
                if (albums.isEmpty()) {
                    null
                } else {
                    Shelf(
                        id = query,
                        title = title,
                        subtitle = "From the catalog",
                        items = albums.map { it.toMediaItem() },
                    )
                }
            }
        }.awaitAll().filterNotNull()
    }

    /**
     * Personal shelves are provider results for artists that actually appear in
     * the user's history. Made for you contains official YouTube Music home-feed
     * mixes plus real albums; top mixes keep their real provider tracks. No item
     * is invented, and an empty provider response omits the shelf or type.
     */
    private suspend fun loadPersonalShelves(
        activity: UserActivitySnapshot,
        plan: HomepagePlan,
        market: String,
        historyArtists: List<String>,
    ): List<Shelf> = coroutineScope {
        val wantsMadeForYou = HomepageSectionKind.MADE_FOR_YOU in plan.sections
        val wantsTopMixes = HomepageSectionKind.TOP_MIXES in plan.sections
        if (!wantsMadeForYou && !wantsTopMixes) return@coroutineScope emptyList()

        val artists = activity.topArtists.take(3).map { it.artist }
        if (artists.isEmpty()) return@coroutineScope emptyList()

        data class ArtistPicks(
            val artist: String,
            val albums: List<Album>,
            val tracks: List<Track>,
        )

        // Fetch the provider's curated home feed once, then keep only official
        // mixes whose artist lineup contains someone from the local history.
        val officialMixesTask = if (wantsMadeForYou) {
            async {
                runCatching { graph.catalog.officialMixes(market) }.getOrDefault(emptyList())
            }
        } else {
            null
        }

        val perArtist = artists.map { artist ->
            async {
                val albums = if (wantsMadeForYou) {
                    runCatching { graph.catalog.shelfAlbums(artist, market, 10) }
                        .getOrDefault(emptyList())
                        .filter { album ->
                            !album.isPlaylist && album.id.isNotBlank() &&
                                album.artist.contains(artist, ignoreCase = true)
                        }
                        .distinctBy { it.id }
                } else {
                    emptyList()
                }
                val tracks = if (wantsTopMixes) {
                    runCatching { graph.catalog.shelfTracks(artist, market, 12) }
                        .getOrDefault(emptyList())
                        .filter { it.artist.contains(artist, ignoreCase = true) }
                        .distinctBy { it.id }
                } else {
                    emptyList()
                }
                ArtistPicks(artist, albums, tracks)
            }
        }.awaitAll()

        val officialMixes = officialMixesTask?.await().orEmpty()

        buildList {
            if (wantsMadeForYou) {
                val lowerArtists = (artists + historyArtists)
                    .flatMap { name -> name.split(ARTIST_NAME_SEPARATORS) }
                    .map { it.trim().lowercase() }
                    .filter { it.length >= 3 }
                    .distinct()
                val mixes = officialMixes
                    .mapNotNull { mix ->
                        val haystack = "${mix.title} ${mix.artist}".lowercase()
                        val relevance = lowerArtists.count { haystack.contains(it) }
                        if (relevance == 0) null else mix.copy(artist = "YouTube Music") to relevance
                    }
                    .sortedByDescending { it.second }
                    .map { it.first }
                    .distinctBy { it.id }
                    .take(3)
                val albums = perArtist.flatMap { it.albums }.distinctBy { it.id }.take(5)
                val picks = buildList {
                    repeat(maxOf(mixes.size, albums.size)) { index ->
                        mixes.getOrNull(index)?.let { add(it) }
                        albums.getOrNull(index)?.let { add(it) }
                    }
                }.distinctBy { it.id }.take(8)
                if (picks.isNotEmpty()) {
                    add(
                        Shelf(
                            id = "made-for-you",
                            title = "Made for you",
                            subtitle = when {
                                mixes.isNotEmpty() && albums.isNotEmpty() -> "Official mixes and albums from artists you play"
                                mixes.isNotEmpty() -> "Official mixes from artists you play"
                                else -> "Albums from artists you play"
                            },
                            items = picks.map { it.toRecommendationItem() },
                        ),
                    )
                }
            }
            if (wantsTopMixes) {
                perArtist.forEach { picks ->
                    if (picks.tracks.isNotEmpty()) {
                        add(
                            Shelf(
                                id = "artist:${picks.artist}",
                                title = "Because you listen to ${picks.artist}",
                                subtitle = "More from an artist in your history",
                                items = picks.tracks.take(14).map { it.toMediaItem() },
                            ),
                        )
                    }
                }
            }
        }
    }

    /** Real first-play action: a discovery album, then real history, then a prompt. */
    fun playFirstDiscovery() {
        val album = shelves.firstOrNull()?.items?.firstOrNull()?.album
        if (album != null) {
            playAlbumById(album.id)
            return
        }
        val track = recentTracks.firstOrNull()
        if (track != null) {
            play(track, recentTracks, "Recently played")
            return
        }
        showToast("Search for a song to start")
    }

    fun refreshHome() {
        if (homeCategory == HomeCategory.ALL) {
            loadHome()
        } else {
            loadCategory(homeCategory)
        }
    }

    fun selectHomeCategory(category: HomeCategory) {
        if (category == homeCategory) return
        homeCategory = category
        if (category == HomeCategory.ALL) {
            categoryShelves = emptyList()
            categoryLoading = false
            categoryError = null
        } else {
            loadCategory(category)
        }
    }

    private fun loadCategory(category: HomeCategory) {
        categoryLoading = true
        categoryError = null
        val market = graph.settings.settings.value.market
        scope.launch {
            runCatching { loadDiscoveryShelves(market, category) }
                .onSuccess { shelves ->
                    if (homeCategory != category) return@onSuccess
                    categoryShelves = shelves
                    categoryLoading = false
                    if (shelves.isEmpty()) {
                        categoryError = "No ${category.label} shelves came back from the catalog."
                    }
                }
                .onFailure { error ->
                    if (homeCategory != category) return@onFailure
                    categoryShelves = emptyList()
                    categoryLoading = false
                    categoryError = error.message ?: "Could not reach the music catalog."
                }
        }
    }

    private fun seedShelves(market: String, category: HomeCategory = HomeCategory.ALL): List<Pair<String, String>> = when (category) {
        HomeCategory.BOLLYWOOD -> listOf(
            "bollywood hits" to "Bollywood, right now",
            "best of arijit singh" to "Arijit Singh essentials",
        )
        HomeCategory.GLOBAL_POP -> listOf(
            "best english songs" to "Best English songs",
            "english pop hits" to "Western pop hits",
        )
        HomeCategory.INDIE -> listOf(
            if (market.uppercase() == "IN") "hindi indie" to "Hindi indie" else "indie hits" to "Indie discoveries",
            "indie" to "Indie essentials",
        )
        HomeCategory.NINETIES -> listOf(
            if (market.uppercase() == "IN") "90s bollywood songs" to "90s Bollywood" else "90s hits" to "90s throwbacks",
            "90s music hits" to "Songs from the 90s",
        )
        HomeCategory.PUNJABI -> listOf(
            "punjabi hits" to "Punjabi, right now",
            "latest punjabi songs" to "New Punjabi",
        )
        HomeCategory.LOFI -> listOf(
            "lofi chill" to "Lo-fi focus",
            "lofi sleep" to "Lo-fi to unwind",
        )
        HomeCategory.ALL -> seedAllShelves(market)
    }

    private fun seedAllShelves(market: String): List<Pair<String, String>> {
        val marketShelves = when (market.uppercase()) {
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
        // InnerTubeX search powers these shelves; they are omitted automatically when a query
        // returns no real albums.
        return marketShelves + listOf(
            "trending songs" to "Trending songs",
            "top remixes" to "Top remixes",
            "global hits" to "Global hits",
            "india top hits" to "India top hits",
        )
    }

    private suspend fun loadAccent(track: Track) {
        loadAccentForUrl(track.artworkUrl ?: track.localPath)
    }

    private suspend fun loadAccentForUrl(url: String?) {
        withContext(graph.io) {
            val palette = runCatching { graph.artwork.load(url?.let(::artworkColorSourceUrl), 96) }.getOrNull()
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

    /** Only fills a missing cover; catalog artwork is never replaced. */
    private suspend fun loadProviderArtwork(track: Track) {
        if (!track.artworkUrl.isNullOrBlank() || track.isArchived) return
        val resolved = withContext(graph.io) {
            runCatching { graph.artworkResolver.resolve(track) }.getOrNull()
        }
        if (!resolved.isNullOrBlank() && playerState.value.current?.id == track.id) {
            currentArtworkUrl = resolved
            // Theme now follows the provider cover, not the old placeholder.
            loadAccentForUrl(resolved)
        }
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
        try {
            val results = withContext(graph.io) { graph.catalog.search(query, currentMarket) }
            searchResults = results
            searchLoading = false
            if (!results.isEmpty) {
                recentSearches = (listOf(query) + recentSearches).distinct().take(8)
                runCatching {
                    withContext(graph.io) { graph.library.recordSearch(query) }
                    recentSearches = graph.library.searchHistory(8).map { it.query }
                }
            }
        } catch (error: CancellationException) {
            // A newer query superseded this one; never surface this as a search error.
            throw error
        } catch (error: Exception) {
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

    /** Copies a YouTube Music playlist into Revibe's own SQLite-backed playlist store. */
    fun importYouTubePlaylist(detail: AlbumDetail) {
        if (importingPlaylist) return
        if (detail.tracks.isEmpty()) {
            showToast("This playlist has no tracks to import")
            return
        }
        importingPlaylist = true
        scope.launch {
            try {
                val playlist = withContext(graph.io) {
                    graph.library.createPlaylist(detail.album.title)
                }
                var added = 0
                detail.tracks.forEach { track ->
                    val ok = withContext(graph.io) { runCatching { graph.library.addToPlaylist(playlist.id, track) }.getOrDefault(false) }
                    if (ok) added++
                }
                playlists = withContext(graph.io) { runCatching { graph.library.playlists() }.getOrDefault(playlists) }
                showToast("Imported $added tracks into \"${playlist.name}\"")
                navigate(Route.PlaylistRoute(playlist.id))
            } catch (error: Exception) {
                showToast("Could not import the playlist: ${error.message ?: "unknown error"}")
            } finally {
                importingPlaylist = false
            }
        }
    }

    // ------------------------------------------------------------------ imports

    fun beginPlaylistImport() {
        showCreatePlaylist = false
        playlistImportPreview = null
        playlistImportFileName = null
        playlistImportName = ""
        playlistImportDestination = null
        playlistImportError = null
        playlistImportSummary = null
        playlistImportBusy = false
        playlistImportProgress = ImportProgress("Choose a file")
        navigate(Route.ImportPlaylist)
    }

    fun choosePlaylistImportFile() {
        if (playlistImportBusy) return
        scope.launch {
            playlistImportBusy = true
            playlistImportError = null
            playlistImportSummary = null
            try {
                val file = graph.importFilePicker.pickPlaylistFile() ?: return@launch
                playlistImportFileName = file.name
                playlistImportProgress = ImportProgress("Reading ${file.name}")
                val preview = graph.imports.matchPlaylistFile(file, market) { playlistImportProgress = it }
                playlistImportPreview = preview
                playlistImportName = preview.playlistName
                playlistImportProgress = ImportProgress("Ready to review")
            } catch (error: Exception) {
                playlistImportError = error.message ?: "Could not read that playlist file."
            } finally {
                playlistImportBusy = false
            }
        }
    }

    /** Opens the OS download folder; the user still chooses the file in Revibe. */
    fun openDownloadsFolder() {
        scope.launch {
            val opened = runCatching { graph.importFilePicker.openDownloadsFolder() }.getOrDefault(false)
            if (!opened) showToast("Could not open the Downloads folder. Use the file picker instead.")
        }
    }

    fun togglePlaylistImportTrack(index: Int) {
        val preview = playlistImportPreview ?: return
        if (index !in preview.tracks.indices || preview.tracks[index].track == null) return
        playlistImportPreview = preview.copy(
            tracks = preview.tracks.mapIndexed { i, item ->
                if (i == index) item.copy(selected = !item.selected) else item
            },
        )
    }

    fun confirmPlaylistImport() {
        val preview = playlistImportPreview ?: return
        if (playlistImportBusy || preview.selectedCount == 0) return
        scope.launch {
            playlistImportBusy = true
            playlistImportError = null
            try {
                val summary = graph.imports.commitPlaylistFile(
                    preview = preview,
                    destinationPlaylistId = playlistImportDestination,
                    newPlaylistName = playlistImportName,
                    market = market,
                ) { playlistImportProgress = it }
                playlistImportSummary = summary
                playlists = withContext(graph.io) { runCatching { graph.library.playlists() }.getOrDefault(playlists) }
                refreshImportHistory()
                playlistImportProgress = ImportProgress("Import complete")
                showToast("Imported ${summary.tracksAdded} tracks")
            } catch (error: Exception) {
                playlistImportError = error.message ?: "The playlist import failed."
            } finally {
                playlistImportBusy = false
            }
        }
    }

    fun openImportedPlaylist() {
        val id = playlistImportSummary?.primaryPlaylistId ?: return
        openPlaylist(id)
    }

    fun beginSpotifyImport() {
        spotifyArchiveMatches = null
        spotifyImportSelection = ImportCategorySelection()
        spotifyImportError = null
        spotifyImportSummary = null
        spotifyIncludeReview = false
        spotifyImportBusy = false
        spotifyImportProgress = ImportProgress("Choose your Spotify ZIP")
        navigate(Route.SpotifyImport)
    }

    fun chooseSpotifyArchive() {
        if (spotifyImportBusy) return
        scope.launch {
            spotifyImportBusy = true
            spotifyImportError = null
            spotifyImportSummary = null
            try {
                val archive = graph.importFilePicker.pickSpotifyArchive() ?: return@launch
                spotifyImportProgress = ImportProgress("Reading archive")
                val matched = graph.imports.matchArchive(archive, market) { spotifyImportProgress = it }
                spotifyArchiveMatches = matched
                spotifyImportProgress = ImportProgress("Ready to review")
            } catch (error: Exception) {
                spotifyImportError = error.message ?: "Could not read the Spotify archive."
            } finally {
                spotifyImportBusy = false
            }
        }
    }

    fun updateImportCategories(selection: ImportCategorySelection) {
        spotifyImportSelection = selection
    }

    fun setIncludeSpotifyReview(include: Boolean) {
        spotifyIncludeReview = include
        spotifyArchiveMatches = spotifyArchiveMatches?.withReviewSelection(include)
    }

    fun confirmSpotifyImport() {
        val matched = spotifyArchiveMatches ?: return
        if (spotifyImportBusy) return
        scope.launch {
            spotifyImportBusy = true
            spotifyImportError = null
            try {
                val summary = graph.imports.commitArchive(matched, spotifyImportSelection) {
                    spotifyImportProgress = it
                }
                spotifyImportSummary = summary
                refreshLibrary()
                loadHome()
                refreshImportHistory()
                spotifyImportProgress = ImportProgress("Import complete")
                showToast("Spotify import complete")
            } catch (error: Exception) {
                spotifyImportError = error.message ?: "The Spotify import failed."
            } finally {
                spotifyImportBusy = false
            }
        }
    }

    fun leaveImportScreens() {
        playlistImportSummary = null
        spotifyImportSummary = null
        if (route == Route.ImportPlaylist || route == Route.SpotifyImport) goBack()
    }

    private fun SpotifyMatchedArchive.withReviewSelection(include: Boolean): SpotifyMatchedArchive = copy(
        playlists = playlists.map { playlist ->
            playlist.copy(
                tracks = playlist.tracks.map { item ->
                    item.copy(
                        selected = when (item.status) {
                            MatchStatus.MATCHED -> true
                            MatchStatus.NEEDS_REVIEW -> include
                            else -> false
                        },
                    )
                },
            )
        },
        likedTracks = likedTracks.map { item ->
            item.copy(
                selected = when (item.status) {
                    MatchStatus.MATCHED -> true
                    MatchStatus.NEEDS_REVIEW -> include
                    else -> false
                },
            )
        },
        history = history.map { item ->
            item.copy(
                match = item.match.copy(
                    selected = when (item.match.status) {
                        MatchStatus.MATCHED -> true
                        MatchStatus.NEEDS_REVIEW -> include
                        else -> false
                    },
                ),
            )
        },
    )

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

    fun setPlaylistDescription(playlistId: String, description: String) {
        scope.launch {
            withContext(graph.io) { runCatching { graph.library.setPlaylistDescription(playlistId, description) } }
            playlists = playlists.map { if (it.id == playlistId) it.copy(description = description) else it }
            playlistDetail = playlistDetail?.let {
                if (it.playlist.id == playlistId) it.copy(playlist = it.playlist.copy(description = description)) else it
            }
            showToast("Playlist details saved")
        }
    }

    fun addToPlaylist(playlistId: String, track: Track) {
        scope.launch {
            val added = withContext(graph.io) { runCatching { graph.library.addToPlaylist(playlistId, track) }.getOrDefault(false) }
            addToPlaylistTrack = null
            refreshLibrary()
            showToast(if (added) "Added to playlist" else "Could not add to that playlist")
        }
    }

    fun removeFromPlaylist(playlistId: String, entryId: String) {
        scope.launch {
            withContext(graph.io) { runCatching { graph.library.removePlaylistEntry(playlistId, entryId) } }
            // The library row carries trackCount; keep it in step with the detail header.
            playlists = withContext(graph.io) {
                runCatching { graph.library.playlists() }.getOrDefault(playlists)
            }
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

    private fun loadPlaylist(playlistId: String, showLoading: Boolean = true) {
        if (showLoading) detailLoading = true
        detailError = null
        scope.launch {
            runCatching {
                withContext(graph.io) {
                    val playlist = graph.library.playlists().firstOrNull { it.id == playlistId }
                    val entries = graph.library.playlistTrackEntries(playlistId)
                    if (playlist == null) null else PlaylistDetail(playlist, entries.map { it.track }, entries)
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

    fun play(
        track: Track,
        context: List<Track> = listOf(track),
        contextName: String = "Revibe",
        playlistId: String? = null,
        albumId: String? = null,
    ) {
        val list = context.ifEmpty { listOf(track) }
        val index = list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        if (playlistId != null) recordPlaylistPlay(playlistId)
        playingPlaylistId = playlistId
        playingAlbumId = albumId
        graph.player.playQueue(list, index, contextName)
    }

    fun playAlbum(detail: AlbumDetail, shuffled: Boolean = false) {
        if (detail.tracks.isEmpty()) return
        if (shuffled && !playerState.value.shuffle) graph.player.toggleShuffle()
        if (!shuffled && playerState.value.shuffle) graph.player.toggleShuffle()
        playingPlaylistId = null
        playingAlbumId = detail.album.id
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
            playingPlaylistId = null
            playingAlbumId = detail.album.id
            graph.player.playQueue(detail.tracks, 0, detail.album.title)
        }
    }

    fun playLiked(shuffled: Boolean = false) {
        if (likedTracks.isEmpty()) return
        if (shuffled && !playerState.value.shuffle) graph.player.toggleShuffle()
        if (!shuffled && playerState.value.shuffle) graph.player.toggleShuffle()
        playingPlaylistId = null
        playingAlbumId = null
        graph.player.playQueue(likedTracks, 0, "Liked Songs")
    }

    /** Starts a recent playlist from a home-card play button without leaving home. */
    fun playPlaylistById(playlistId: String) {
        scope.launch {
            val detail = withContext(graph.io) {
                runCatching {
                    val playlist = graph.library.playlists().firstOrNull { it.id == playlistId }
                    val entries = graph.library.playlistTrackEntries(playlistId)
                    if (playlist == null) null else PlaylistDetail(playlist, entries.map { it.track }, entries)
                }.getOrNull()
            }
            if (detail == null || detail.tracks.isEmpty()) {
                showToast("This playlist has no songs to play")
                return@launch
            }
            playPlaylist(detail)
        }
    }

    fun playPlaylist(detail: PlaylistDetail, shuffled: Boolean = false) {
        if (detail.tracks.isEmpty()) return
        if (shuffled && !playerState.value.shuffle) graph.player.toggleShuffle()
        if (!shuffled && playerState.value.shuffle) graph.player.toggleShuffle()
        recordPlaylistPlay(detail.playlist.id)
        playingPlaylistId = detail.playlist.id
        playingAlbumId = null
        graph.player.playQueue(detail.tracks, 0, detail.playlist.name)
    }

    private fun recordPlaylistPlay(playlistId: String) {
        scope.launch {
            withContext(graph.io) {
                runCatching { graph.library.recordPlaylistPlay(playlistId) }
            }
            val latest = withContext(graph.io) {
                runCatching { graph.library.recentlyPlayedPlaylists(8) }.getOrNull()
            }
            if (latest != null) recentlyPlayedPlaylists = latest
        }
    }

    fun playArtistTop(tracks: List<Track>, artistName: String) {
        if (tracks.isEmpty()) return
        playingPlaylistId = null
        playingAlbumId = null
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

    fun testYouTube() {
        scope.launch {
            val reachable = withContext(graph.io) {
                runCatching { graph.youtube.searchTracks("arijit singh", market, 1).isNotEmpty() }.getOrDefault(false)
            }
            showToast(
                if (reachable) "The catalog is reachable"
                else "Catalog: ${graph.youtube.diagnostic.value}",
            )
        }
    }

    /** Mirrors the controller's persisted autoplay choice for the queue panel. */
    val autoplayEnabled: Boolean
        get() = playerState.value.autoplayEnabled

    fun toggleAutoplay() {
        val enabled = !graph.player.state.value.autoplayEnabled
        graph.player.setAutoplayEnabled(enabled)
        scope.launch { graph.settings.setAutoplayEnabled(enabled) }
        showToast(if (enabled) "Autoplay on" else "Autoplay off")
    }

    fun clearQueue() {
        graph.player.clearQueue()
        showToast("Queue cleared")
    }

    fun moveQueueItem(from: Int, to: Int) = graph.player.moveInQueue(from, to)

    fun refreshHistory() {
        scope.launch {
            val history = withContext(graph.io) {
                runCatching { graph.library.recentHistory(40) }.getOrDefault(emptyList())
            }
            recentHistory = history
            recentTracks = history.map { it.track }
        }
    }

    /** Saves the real live queue, preserving order, as a normal SQLite playlist. */
    fun saveQueueAsPlaylist() {
        val tracks = playerState.value.queue.distinctBy { it.id }
        if (tracks.isEmpty()) {
            showToast("The queue is empty")
            return
        }
        scope.launch {
            val playlist = withContext(graph.io) {
                runCatching { graph.library.createPlaylist("Saved queue") }.getOrNull()
            }
            if (playlist == null) {
                showToast("Could not save the queue")
                return@launch
            }
            tracks.forEach { track ->
                withContext(graph.io) { runCatching { graph.library.addToPlaylist(playlist.id, track) } }
            }
            refreshLibrary()
            showToast("Saved ${tracks.size} tracks to \"${playlist.name}\"")
        }
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

    // ------------------------------------------------------------ session restore

    /**
     * Saves what is loaded about every ten seconds of playback and immediately on
     * track, pause, seek-bucket or queue-mode changes. The desktop close path calls
     * [persistPlaybackSession] for the exact final position.
     */
    private fun startPlaybackSessionPersistence() {
        scope.launch {
            graph.player.state.collect { state ->
                val track = state.current
                if (track == null) {
                    if (lastSessionSignature != null) {
                        lastSessionSignature = null
                        lastSessionBucket = -1L
                        withContext(graph.io) { runCatching { graph.playbackSession.clear() } }
                    }
                    return@collect
                }
                val signature = buildString {
                    append(track.id).append('|')
                    append(state.index).append('|')
                    append(state.entries.map { it.track.id }.hashCode()).append('|')
                    append(state.shuffle).append('|')
                    append(state.repeat).append('|')
                    append(state.contextName).append('|')
                    append(state.isPlaying)
                }
                val bucket = state.positionMs / SESSION_SAVE_BUCKET_MS
                // A load in flight reports a transient 0 before the restore seek lands;
                // keep the already-saved position for that row until it is ready.
                if (state.isBuffering && signature == lastSessionSignature) return@collect
                if (signature == lastSessionSignature && bucket == lastSessionBucket) return@collect
                lastSessionSignature = signature
                lastSessionBucket = bucket
                val snapshot = state.toPlaybackSession()
                withContext(graph.io) { runCatching { graph.playbackSession.save(snapshot) } }
            }
        }
    }

    /** Blocking-safe snapshot write used right before the window closes. */
    suspend fun persistPlaybackSession() {
        val state = graph.player.state.value
        withContext(graph.io) {
            runCatching {
                if (state.current == null) {
                    graph.playbackSession.clear()
                } else {
                    graph.playbackSession.save(state.toPlaybackSession())
                }
            }
        }
    }

    private fun com.revibe.playback.PlayerState.toPlaybackSession() = PlaybackSession(
        queue = queue,
        index = index.coerceAtLeast(0),
        positionMs = positionMs.coerceAtLeast(0),
        shuffle = shuffle,
        repeat = repeat,
        contextName = contextName,
    )

    private companion object {
        const val SESSION_SAVE_BUCKET_MS = 10_000L
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
        if (!rightPanelVisible) clearAddToPlaylist()
        scope.launch { graph.settings.setRightPanel(rightPanelVisible) }
    }

    fun toggleMiniplayer() {
        miniplayer = !miniplayer
    }

    fun exitMiniplayer() {
        miniplayer = false
    }

    fun closeRightPanel() {
        clearAddToPlaylist()
        if (!rightPanelVisible) return
        rightPanelVisible = false
        scope.launch { graph.settings.setRightPanel(false) }
    }

    /** Opens Spotify-style song search in the right rail for this playlist. */
    fun openAddToPlaylist(playlistId: String) {
        addToPlaylistPlaylistId = playlistId
        addToPlaylistQuery = ""
        addToPlaylistResults = emptyList()
        addToPlaylistAddedIds = emptySet()
        addToPlaylistError = null
        addToPlaylistLoading = true
        rightPanelVisible = true
        scope.launch { graph.settings.setRightPanel(true) }
        loadAddToPlaylistSuggestions(playlistId)
    }

    private fun loadAddToPlaylistSuggestions(playlistId: String) {
        addToPlaylistJob?.cancel()
        addToPlaylistLoading = true
        addToPlaylistJob = scope.launch {
            val existing = withContext(graph.io) {
                runCatching { graph.library.playlistTrackEntries(playlistId).map { it.track.id }.toSet() }
                    .getOrDefault(emptySet())
            }
            if (addToPlaylistPlaylistId != playlistId || addToPlaylistQuery.isNotEmpty()) return@launch
            addToPlaylistAddedIds = existing
            val discovered = (shelves + personalShelves).flatMap { shelf ->
                shelf.items.mapNotNull { it.track }
            }
            addToPlaylistSuggestions = (likedTracks + recentTracks + discovered)
                .filter { it.id !in existing }
                .distinctBy { it.id }
                .take(24)
            addToPlaylistLoading = false
        }
    }

    fun onAddToPlaylistQueryChange(query: String) {
        addToPlaylistQuery = query
        addToPlaylistJob?.cancel()
        addToPlaylistError = null
        if (query.trim().length < 2) {
            addToPlaylistResults = emptyList()
            addToPlaylistLoading = false
            val playlistId = addToPlaylistPlaylistId
            if (query.isBlank() && playlistId != null && addToPlaylistSuggestions.isEmpty()) {
                loadAddToPlaylistSuggestions(playlistId)
            }
            return
        }
        addToPlaylistLoading = true
        addToPlaylistJob = scope.launch {
            delay(280)
            try {
                val results = withContext(graph.io) {
                    graph.catalog.search(query.trim(), market)
                }
                if (addToPlaylistQuery == query && addToPlaylistPlaylistId != null) {
                    addToPlaylistResults = results.tracks
                    addToPlaylistLoading = false
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (addToPlaylistQuery == query) {
                    addToPlaylistError = error.message ?: "Search failed."
                    addToPlaylistLoading = false
                }
            }
        }
    }

    fun clearAddToPlaylistQuery() {
        if (addToPlaylistQuery.isNotEmpty()) onAddToPlaylistQueryChange("")
    }

    fun addTrackToOpenPlaylist(track: Track) {
        val playlistId = addToPlaylistPlaylistId ?: return
        if (track.id in addToPlaylistAddedIds) return
        addToPlaylistAddedIds = addToPlaylistAddedIds + track.id
        scope.launch {
            val added = withContext(graph.io) {
                runCatching { graph.library.addToPlaylist(playlistId, track) }.getOrDefault(false)
            }
            if (added) {
                refreshLibrary()
                loadPlaylist(playlistId, showLoading = false)
                val name = playlists.firstOrNull { it.id == playlistId }?.name
                showToast(name?.let { "Added to $it" } ?: "Added to playlist")
            } else {
                addToPlaylistAddedIds = addToPlaylistAddedIds - track.id
                showToast("Could not add to that playlist")
            }
        }
    }

    private fun clearAddToPlaylist() {
        addToPlaylistJob?.cancel()
        addToPlaylistPlaylistId = null
        addToPlaylistQuery = ""
        addToPlaylistResults = emptyList()
        addToPlaylistSuggestions = emptyList()
        addToPlaylistAddedIds = emptySet()
        addToPlaylistLoading = false
        addToPlaylistError = null
    }

    fun toggleCompactSidebar() {
        compactSidebar = !compactSidebar
        scope.launch { graph.settings.setCompactSidebar(compactSidebar) }
    }

    fun resizeLeftPanel(width: Float) {
        leftPanelWidth = LayoutSpec.clampLeft(width)
        scheduleLayoutPersist()
    }

    fun resizeRightPanel(width: Float) {
        rightPanelWidth = LayoutSpec.clampRight(width)
        scheduleLayoutPersist()
    }

    fun resetLeftPanelWidth() = resizeLeftPanel(LayoutSpec.LEFT_DEFAULT)

    fun resetRightPanelWidth() = resizeRightPanel(LayoutSpec.RIGHT_DEFAULT)

    private fun scheduleLayoutPersist() {
        layoutPersistJob?.cancel()
        layoutPersistJob = scope.launch {
            delay(240)
            graph.settings.setPanelWidths(leftPanelWidth, rightPanelWidth)
        }
    }

    fun selectRightTab(tab: RightTab) {
        clearAddToPlaylist()
        rightTab = tab
        if (!rightPanelVisible) {
            rightPanelVisible = true
            scope.launch { graph.settings.setRightPanel(true) }
        }
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

    // ------------------------------------------------------------------- account

    fun clearAccountMessages() {
        authError = null
        accountNotice = null
    }

    fun signIn(email: String, password: String) {
        if (authBusy) return
        scope.launch {
            authBusy = true
            authError = null
            accountNotice = null
            try {
                graph.auth.signIn(email, password)
                linkLocalLibrary()
                accountNotice = "Signed in. Your account library will sync in the background."
                refreshLibrary()
                loadHome()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                authError = accountError(error)
            } finally {
                authBusy = false
            }
        }
    }

    fun signInWithGoogle() {
        if (authBusy) return
        scope.launch {
            authBusy = true
            authError = null
            accountNotice = null
            try {
                graph.auth.signInWithGoogle()
                linkLocalLibrary()
                accountNotice = "Signed in with Google. Your account library will sync in the background."
                refreshLibrary()
                loadHome()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                authError = accountError(error)
            } finally {
                authBusy = false
            }
        }
    }

    fun signUp(email: String, password: String) {
        if (authBusy) return
        scope.launch {
            authBusy = true
            authError = null
            accountNotice = null
            try {
                graph.auth.signUp(email, password)
                if (graph.auth.state.value is AccountState.SignedIn) linkLocalLibrary()
                if (graph.auth.state.value is AccountState.AwaitingConfirmation) {
                    accountNotice = "Check your email to confirm the account, then sign in."
                } else {
                    accountNotice = "Account created. Your local library is being linked to it."
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                authError = accountError(error)
            } finally {
                authBusy = false
            }
        }
    }

    /** Refuses to merge this device's library into a different account. */
    private suspend fun linkLocalLibrary() {
        if (!graph.sync.claimLocalLibrary()) {
            graph.auth.signOut()
            throw IllegalStateException(
                "This device's local library is linked to a different account. Sign in with that account to keep it, or use a separate device.",
            )
        }
    }

    fun signOut() {
        if (authBusy) return
        scope.launch {
            authBusy = true
            authError = null
            accountNotice = null
            runCatching { graph.auth.signOut() }
                .onFailure { authError = accountError(it) }
                .onSuccess { accountNotice = "Signed out. Your local library remains on this device." }
            authBusy = false
        }
    }

    fun requestPasswordReset(email: String) {
        if (authBusy) return
        scope.launch {
            authBusy = true
            authError = null
            accountNotice = null
            runCatching { graph.auth.requestPasswordReset(email) }
                .onSuccess { accountNotice = "Password reset email sent. Follow the link in your inbox." }
                .onFailure { authError = accountError(it) }
            authBusy = false
        }
    }

    fun updateProfile(displayName: String, avatarUrl: String) {
        if (authBusy) return
        scope.launch {
            authBusy = true
            authError = null
            accountNotice = null
            runCatching { graph.auth.updateProfile(displayName, avatarUrl) }
                .onSuccess { accountNotice = "Profile updated." }
                .onFailure { authError = accountError(it) }
            authBusy = false
        }
    }

    fun syncNow() {
        graph.sync.requestSync()
        showToast("Checking the cloud for changes…")
    }

    fun refreshImportHistory() {
        scope.launch {
            importHistoryLoading = true
            importHistory = withContext(graph.io) {
                runCatching { graph.imports.importHistory() }.getOrDefault(emptyList())
            }
            importHistoryLoading = false
        }
    }

    fun undoImport(importId: String) {
        if (undoImportBusy) return
        scope.launch {
            undoImportBusy = true
            val result = withContext(graph.io) {
                runCatching { graph.imports.undoImport(importId) }
            }
            undoImportBusy = false
            result.onSuccess { removed ->
                if (!removed.any) {
                    showToast("Nothing from that import remains to undo")
                } else {
                    val total = removed.playlists + removed.tracks + removed.likes + removed.albums +
                        removed.artists + removed.history + removed.searches
                    showToast("Removed $total imported records")
                }
                refreshImportHistory()
                refreshLibrary()
                if (route == Route.Home) loadHome()
                graph.sync.requestSync()
            }.onFailure { error ->
                showToast("Could not undo that import: ${error.message ?: "unknown error"}")
            }
        }
    }

    fun setSearchHistorySync(enabled: Boolean) {
        scope.launch {
            graph.settings.setSyncSearchHistory(enabled)
            graph.sync.refreshStatus()
            if (enabled) graph.sync.requestSync()
            showToast(if (enabled) "Search history sync on" else "Search history stays on this device")
        }
    }

    private fun accountError(error: Throwable): String = when (error) {
        is AuthException -> error.message ?: "Account request failed."
        is IllegalArgumentException -> error.message ?: "Check the form and try again."
        else -> error.message ?: "Account request failed."
    }

    fun checkForUpdates() = graph.updates.checkNow()

    fun installUpdate() = graph.updates.installNow()

    fun remindUpdateLater() = graph.updates.remindLater()

    fun skipUpdateVersion() = graph.updates.skipVersion()

    fun dismissUpdateNotice() = graph.updates.dismissNotice()

    fun retryUpdate() {
        if (graph.updates.state.value.available != null) installUpdate() else checkForUpdates()
    }

    fun shutdown() {
        graph.updates.shutdownLoop()
        runCatching { graph.player.release() }
        runCatching { graph.youtube.close() }
        (graph.streamResolver as? AutoCloseable)?.let { runCatching { it.close() } }
        scope.cancel()
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    private data class HomeData(
        val activity: UserActivitySnapshot,
        val plan: HomepagePlan,
        val recent: List<Track>,
        val history: List<PlayedTrack>,
        val followed: List<Artist>,
        val playlists: List<Playlist>,
        val recentPlaylists: List<PlayedPlaylist>,
        val discovery: List<Shelf>,
        val personal: List<Shelf>,
    )
}

fun Album.toMediaItem() = MediaItem(
    id = id,
    title = title,
    subtitle = artist,
    imageUrl = artworkUrl,
    album = this,
)

private val ARTIST_NAME_SEPARATORS = Regex(
    """\s*(?:,|&|\bfeat\.?\b|\bft\.?\b|\s+-\s+)\s*""",
    RegexOption.IGNORE_CASE,
)

/** Makes provider mixes visually distinct from albums on the personalised shelf. */
private fun Album.toRecommendationItem() = MediaItem(
    id = id,
    title = title,
    subtitle = if (isPlaylist) "Official mix · $artist" else "Album · $artist",
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
