package com.revibe

import com.revibe.data.JdbcLibraryStore
import com.revibe.model.LayoutSpec
import com.revibe.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class LayoutSettingsTest {

    @Test
    fun `layout widths clamp to supported ranges`() {
        assertEquals(LayoutSpec.LEFT_MIN, LayoutSpec.clampLeft(10f))
        assertEquals(LayoutSpec.LEFT_MAX, LayoutSpec.clampLeft(999f))
        assertEquals(300f, LayoutSpec.clampLeft(300f))

        assertEquals(LayoutSpec.RIGHT_MIN, LayoutSpec.clampRight(100f))
        assertEquals(LayoutSpec.RIGHT_MAX, LayoutSpec.clampRight(999f))
        assertEquals(360f, LayoutSpec.clampRight(360f))
    }

    @Test
    fun `panel widths persist through the settings store and reload clamped`() = runBlocking {
        val root = Files.createTempDirectory("revibe-layout-test").toFile()
        val store = JdbcLibraryStore(java.io.File(root, "layout.db"), Dispatchers.IO)
        val settings = SettingsRepository(store)
        settings.load()

        settings.setPanelWidths(left = 310f, right = 500f)
        assertEquals(310f, settings.settings.value.leftPanelWidth)
        assertEquals(LayoutSpec.RIGHT_MAX, settings.settings.value.rightPanelWidth)

        val reloaded = SettingsRepository(store)
        reloaded.load()
        assertEquals(310f, reloaded.settings.value.leftPanelWidth)
        assertEquals(LayoutSpec.RIGHT_MAX, reloaded.settings.value.rightPanelWidth)
    }
}
