package com.revibe

import com.revibe.catalog.CatalogRepository
import com.revibe.image.ArtworkLoader
import com.revibe.library.LibraryRepository
import com.revibe.local.LocalLibrary
import com.revibe.lyrics.LyricsRepository
import com.revibe.playback.PlayerController
import com.revibe.settings.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/** Platform file picker used for local library folders. */
interface FolderPicker {
    suspend fun pickFolder(): String?
}

/**
 * Explicit dependency graph. Small enough that a DI framework would add noise;
 * when Android/iOS targets land this is the one place that grows.
 */
class AppGraph(
    val catalog: CatalogRepository,
    val library: LibraryRepository,
    val player: PlayerController,
    val lyrics: LyricsRepository,
    val artwork: ArtworkLoader,
    val settings: SettingsRepository,
    val local: LocalLibrary,
    val folderPicker: FolderPicker,
    val io: CoroutineDispatcher,
    val appScope: CoroutineScope,
    val platformName: String,
    val dataDirectory: String,
    val appVersion: String,
)
