package com.revibe.settings

import com.revibe.library.LibraryStore
import com.revibe.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(private val store: LibraryStore) {
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    suspend fun load() {
        val current = _settings.value
        _settings.value = current.copy(
            market = store.getSetting("market") ?: current.market,
            volume = store.getSetting("volume")?.toFloatOrNull() ?: current.volume,
            showRightPanel = store.getSetting("showRightPanel")?.toBooleanStrictOrNull() ?: current.showRightPanel,
            compactSidebar = store.getSetting("compactSidebar")?.toBooleanStrictOrNull() ?: current.compactSidebar,
        )
    }

    suspend fun setMarket(market: String) {
        val normalized = market.trim().uppercase().take(2)
        _settings.value = _settings.value.copy(market = normalized)
        store.setSetting("market", normalized)
    }

    suspend fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _settings.value = _settings.value.copy(volume = clamped)
        store.setSetting("volume", clamped.toString())
    }

    suspend fun setRightPanel(visible: Boolean) {
        _settings.value = _settings.value.copy(showRightPanel = visible)
        store.setSetting("showRightPanel", visible.toString())
    }

    suspend fun setCompactSidebar(compact: Boolean) {
        _settings.value = _settings.value.copy(compactSidebar = compact)
        store.setSetting("compactSidebar", compact.toString())
    }
}
