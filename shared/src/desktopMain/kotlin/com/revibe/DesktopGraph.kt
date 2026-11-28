package com.revibe

import com.revibe.account.AuthApi
import com.revibe.account.AuthManager
import com.revibe.account.CloudConfig
import com.revibe.account.CloudEnvironment
import com.revibe.account.DesktopOAuthLauncher
import com.revibe.account.DpapiAuthSessionStore
import com.revibe.catalog.CatalogRepository
import com.revibe.catalog.MusicSourceManager
import com.revibe.catalog.WikipediaArtistKnowledge
import com.revibe.data.JdbcLibraryStore
import com.revibe.image.SkiaArtworkLoader
import com.revibe.imports.ImportRepository
import com.revibe.library.LibraryRepository
import com.revibe.local.DesktopLocalLibrary
import com.revibe.lyrics.LrcRedLyricsSource
import com.revibe.lyrics.LrclibLyricsSource
import com.revibe.lyrics.LyricsProviderResolver
import com.revibe.lyrics.LyricsRepository
import com.revibe.lyrics.UnisonLyricsSource
import com.revibe.playback.DesktopPlaybackEngineFactory
import com.revibe.playback.DesktopTrackStreamResolver
import com.revibe.playback.PlaybackSessionRepository
import com.revibe.playback.PlayerController
import com.revibe.platform.DesktopImportFilePicker
import com.revibe.platform.SwingFolderPicker
import com.revibe.provider.ArtworkResolver
import com.revibe.provider.DeezerArtworkProvider
import com.revibe.provider.MusicBrainzArtworkProvider
import com.revibe.recommendation.AutoplayService
import com.revibe.RevibeBuildInfo
import com.revibe.recommendation.InnerTubeNativeQueueProvider
import com.revibe.settings.SettingsRepository
import com.revibe.sync.CloudSyncApi
import com.revibe.sync.SyncEngine
import com.revibe.update.FileUpdateStateStore
import com.revibe.update.HttpUpdateDownloader
import com.revibe.update.HttpUpdateManifestClient
import com.revibe.update.ResilientHttpProxy
import com.revibe.update.Sha256FileHasher
import com.revibe.update.UpdateConfig
import com.revibe.update.UpdateService
import com.revibe.update.WindowsInstaller
import com.revibe.update.WindowsPublisherVerifier
import com.revibe.youtube.YouTubeMusicSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyConfig
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import java.net.InetSocketAddress
import java.net.ProxySelector

/**
 * Builds the desktop graph. One place wires HTTP, storage, audio and the UI
 * state holder, so adding an Android/iOS target is additive rather than a
 * rewrite of business logic.
 */
fun createDesktopGraph(): AppGraph {
    val homeDir = File(
        System.getenv("REVIBE_HOME")?.takeIf { it.isNotBlank() }
            ?: (System.getProperty("user.home") + File.separator + ".revibe"),
    ).apply { mkdirs() }
    val artCache = File(homeDir, "cache/art").apply { mkdirs() }

    val io = Dispatchers.IO
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val http = HttpClient(CIO) {
        expectSuccess = false
        followRedirects = true
        install(ContentNegotiation) {
            val format = Json {
                ignoreUnknownKeys = true
                isLenient = true
                explicitNulls = false
                coerceInputValues = true
            }
            json(format)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 25_000
            connectTimeoutMillis = 12_000
            socketTimeoutMillis = 25_000
        }
        defaultRequest {
            header("User-Agent", "Revibe/0.1 (Windows desktop; https://github.com/revibe)")
            header("Accept-Language", "en")
        }
    }

    // Update traffic uses a loopback proxy that tries every resolved CDN address.
    // One blackholed GitHub edge otherwise hangs Java's first-address connect and
    // the app reports "Could not reach the update server." or sits on 0%.
    // If the loopback listener cannot start, updates fall back to a direct
    // connection rather than blocking application startup.
    val updateProxy = runCatching { ResilientHttpProxy() }.getOrNull()
    val updateProxySelector = updateProxy?.let {
        ProxySelector.of(InetSocketAddress("127.0.0.1", it.port))
    }
    val updateHttp = HttpClient(CIO) {
        expectSuccess = false
        followRedirects = true
        install(HttpTimeout) {
            requestTimeoutMillis = 25_000
            connectTimeoutMillis = 12_000
            socketTimeoutMillis = 25_000
        }
        defaultRequest {
            header("User-Agent", "Revibe/0.1 (Windows desktop; https://github.com/revibe)")
            header("Accept-Language", "en")
        }
        if (updateProxy != null) {
            engine {
                proxy = ProxyConfig(java.net.Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", updateProxy.port))
            }
        }
    }

    val store = JdbcLibraryStore(File(homeDir, "revibe.db"), io)
    val settings = SettingsRepository(store)
    runBlocking { runCatching { settings.load() } }

    // InnerTubeX manages its own YouTube headers and visitor data, so it gets a
    // separate client from the lyrics/biography traffic.
    val youtubeHttp = HttpClient(CIO) {
        expectSuccess = false
        followRedirects = true
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 45_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 45_000
        }
    }

    val library = LibraryRepository(store)
    val playbackSession = PlaybackSessionRepository(store)
    val youtube = YouTubeMusicSource(youtubeHttp)
    val streamResolver = DesktopTrackStreamResolver(youtube)
    val catalog = CatalogRepository(
        sources = MusicSourceManager(listOf(youtube)),
        knowledge = WikipediaArtistKnowledge(http),
    )
    val imports = ImportRepository(catalog = catalog, library = library, store = store)
    val autoplay = AutoplayService(
        provider = InnerTubeNativeQueueProvider(
            youtube = youtube,
            catalog = catalog,
            market = { settings.settings.value.market },
        ),
    )
    val selectedEngine = DesktopPlaybackEngineFactory.create()
    val player = PlayerController(
        engine = selectedEngine.engine,
        library = library,
        scope = appScope,
        initialVolume = settings.settings.value.volume,
        resolver = streamResolver,
        autoplay = autoplay,
        initialAutoplayEnabled = settings.settings.value.autoplayEnabled,
    )
    val contactEmail = System.getenv("REVIBE_CONTACT_EMAIL")?.takeIf { it.isNotBlank() }
    val lyrics = LyricsRepository(
        http,
        store,
        LyricsProviderResolver(
            LrclibLyricsSource(http),
            LrcRedLyricsSource(http, contactEmail),
            UnisonLyricsSource(http),
        ),
    )
    val artwork = SkiaArtworkLoader(http, artCache)
    // Keyless fallback chain: MusicBrainz/CAA first, then Deezer. YouTube
    // Music's own artwork still takes priority in ArtworkResolver.
    val artworkResolver = ArtworkResolver(
        listOf(
            MusicBrainzArtworkProvider(http),
            DeezerArtworkProvider(http),
        ),
    )
    val local = DesktopLocalLibrary(store, io, artCache)

    val platformName = buildString {
        append(System.getProperty("os.name"))
        append(" · JVM ")
        append(System.getProperty("java.version"))
        append(" · Audio ")
        append(selectedEngine.name)
    }

    // Environment wins for development; a packaged installer carries the
    // publishable project URL/key baked in by the release build.
    val cloudConfig = CloudConfig(
        url = System.getenv(CloudEnvironment.URL_KEY)?.takeIf { it.isNotBlank() }
            ?: RevibeBuildInfo.supabaseUrl,
        anonKey = System.getenv(CloudEnvironment.ANON_KEY)?.takeIf { it.isNotBlank() }
            ?: RevibeBuildInfo.supabaseAnonKey,
    )
    val auth = AuthManager(
        config = cloudConfig,
        api = AuthApi(http, cloudConfig),
        sessionStore = DpapiAuthSessionStore(File(homeDir, "account.session")),
        oauth = DesktopOAuthLauncher(cloudConfig),
    )
    val deviceName = System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() }?.let { "$it · Windows" } ?: "Windows desktop"
    val sync = SyncEngine(
        api = CloudSyncApi(http, cloudConfig),
        auth = auth,
        store = store,
        settings = settings,
        deviceName = deviceName,
        platform = platformName,
        scope = appScope,
    )

    val updatesDirectory = File(homeDir, "updates").apply { mkdirs() }
    val updateManifestUrl = System.getenv("REVIBE_UPDATE_MANIFEST_URL")
        ?.takeIf { it.isNotBlank() }
        ?: RevibeBuildInfo.updateManifestUrl
    val updatePublisher = System.getenv("REVIBE_UPDATE_PUBLISHER")
        ?.takeIf { it.isNotBlank() }
        ?: RevibeBuildInfo.publisher
    val allowUnsignedUpdates = System.getenv("REVIBE_ALLOW_UNSIGNED_UPDATES")
        ?.toBooleanStrictOrNull()
        ?: RevibeBuildInfo.allowUnsignedUpdates
    val updates = UpdateService(
        config = UpdateConfig(
            currentVersion = RevibeBuildInfo.version,
            manifestUrl = updateManifestUrl,
            publisher = updatePublisher,
            updatesDirectory = updatesDirectory.absolutePath,
            allowUnsignedInstaller = allowUnsignedUpdates,
        ),
        manifestClient = HttpUpdateManifestClient(updateHttp),
        downloader = HttpUpdateDownloader(proxy = updateProxySelector),
        hasher = Sha256FileHasher(),
        publisherVerifier = WindowsPublisherVerifier(),
        installer = WindowsInstaller(updatesDirectory),
        stateStore = FileUpdateStateStore(updatesDirectory),
        scope = appScope,
    )

    return AppGraph(
        catalog = catalog,
        library = library,
        player = player,
        playbackSession = playbackSession,
        youtube = youtube,
        streamResolver = streamResolver,
        lyrics = lyrics,
        artwork = artwork,
        artworkResolver = artworkResolver,
        settings = settings,
        auth = auth,
        sync = sync,
        updates = updates,
        local = local,
        folderPicker = SwingFolderPicker(),
        importFilePicker = DesktopImportFilePicker(),
        imports = imports,
        io = io,
        appScope = appScope,
        platformName = platformName,
        dataDirectory = homeDir.absolutePath,
        appVersion = RevibeBuildInfo.version,
    )
}
