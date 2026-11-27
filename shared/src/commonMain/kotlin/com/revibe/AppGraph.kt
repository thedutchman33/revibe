package com.revibe

import com.revibe.account.AuthManager
import com.revibe.catalog.CatalogRepository
import com.revibe.image.ArtworkLoader
import com.revibe.imports.ImportFilePicker
import com.revibe.imports.ImportRepository
import com.revibe.library.LibraryRepository
import com.revibe.local.LocalLibrary
import com.revibe.lyrics.LyricsRepository
import com.revibe.playback.PlayerController
import com.revibe.playback.TrackStreamResolver
import com.revibe.provider.ArtworkResolver
import com.revibe.settings.SettingsRepository
import com.revibe.sync.SyncEngine
import com.revibe.update.UpdateService
import com.revibe.youtube.YouTubeMusicSource
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
    val youtube: YouTubeMusicSource,
    val streamResolver: TrackStreamResolver,
    val lyrics: LyricsRepository,
    val artwork: ArtworkLoader,
    val artworkResolver: ArtworkResolver,
    val settings: SettingsRepository,
    val auth: AuthManager,
    val sync: SyncEngine,
    val updates: UpdateService,
    val local: LocalLibrary,
    val folderPicker: FolderPicker,
    val importFilePicker: ImportFilePicker,
    val imports: ImportRepository,
    val io: CoroutineDispatcher,
    val appScope: CoroutineScope,
    val platformName: String,
    val dataDirectory: String,
    val appVersion: String,
)
