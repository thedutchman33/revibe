package com.revibe

import com.revibe.catalog.CatalogRepository
import com.revibe.catalog.ITunesCatalogSource
import com.revibe.catalog.WikipediaArtistKnowledge
import com.revibe.data.JdbcLibraryStore
import com.revibe.image.SkiaArtworkLoader
import com.revibe.library.LibraryRepository
import com.revibe.local.DesktopLocalLibrary
import com.revibe.lyrics.LyricsRepository
import com.revibe.playback.JavaFxPlaybackEngine
import com.revibe.playback.PlayerController
import com.revibe.platform.SwingFolderPicker
import com.revibe.settings.SettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File

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
        install(ContentNegotiation) {
            val format = Json {
                ignoreUnknownKeys = true
                isLenient = true
                explicitNulls = false
                coerceInputValues = true
            }
            // Apple's search API serves JSON with a text/javascript content type.
            json(format)
            json(format, contentType = ContentType.Text.JavaScript)
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

    val store = JdbcLibraryStore(File(homeDir, "revibe.db"), io)
    val settings = SettingsRepository(store)
    runBlocking { runCatching { settings.load() } }

    val library = LibraryRepository(store)
    val engine = JavaFxPlaybackEngine()
    val player = PlayerController(
        engine = engine,
        library = library,
        scope = appScope,
        initialVolume = settings.settings.value.volume,
    )

    val catalog = CatalogRepository(
        source = ITunesCatalogSource(http),
        knowledge = WikipediaArtistKnowledge(http),
    )
    val lyrics = LyricsRepository(http, store)
    val artwork = SkiaArtworkLoader(http, artCache)
    val local = DesktopLocalLibrary(store, io, artCache)

    val platformName = buildString {
        append(System.getProperty("os.name"))
        append(" · JVM ")
        append(System.getProperty("java.version"))
    }

    return AppGraph(
        catalog = catalog,
        library = library,
        player = player,
        lyrics = lyrics,
        artwork = artwork,
        settings = settings,
        local = local,
        folderPicker = SwingFolderPicker(),
        io = io,
        appScope = appScope,
        platformName = platformName,
        dataDirectory = homeDir.absolutePath,
        appVersion = "0.1.0",
    )
}
