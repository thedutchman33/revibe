package com.revibe.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.revibe.imports.ImportBatchRecord
import com.revibe.imports.ImportHistoryItem
import com.revibe.ui.screens.ImportHistoryCardContent
import com.revibe.ui.theme.RevibeTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class AccountImportHistoryUiTest {
    @Test
    fun `history shows counts and undo requires confirmation`() = runComposeUiTest {
        var refreshed = 0
        var undone: String? = null
        val batch = ImportBatchRecord(
            importId = "batch-1",
            sourcePlatform = "spotify",
            fingerprint = "fingerprint",
            importedAt = 1_700_000_000_000,
            summary = "Spotify account archive",
            playlists = 1,
            tracks = 2,
            likes = 1,
            albums = 1,
            artists = 1,
            history = 2,
            searches = 1,
        )

        setContent {
            RevibeTheme {
                Box(Modifier.width(760.dp)) {
                    ImportHistoryCardContent(
                        items = listOf(
                            ImportHistoryItem(batch = batch, canUndo = true),
                            ImportHistoryItem(
                                batch = batch.copy(
                                    importId = "batch-2",
                                    sourcePlatform = "tune_my_music",
                                    fingerprint = "fingerprint-2",
                                    playlists = 0,
                                    tracks = 0,
                                    likes = 0,
                                    albums = 0,
                                    artists = 0,
                                    history = 0,
                                    searches = 0,
                                ),
                                canUndo = false,
                                undone = true,
                            ),
                        ),
                        loading = false,
                        busy = false,
                        onRefresh = { refreshed++ },
                        onUndo = { undone = it },
                    )
                }
            }
        }

        onNodeWithText("Import history").assertIsDisplayed()
        onNodeWithText("Spotify account archive").assertIsDisplayed()
        onNodeWithText("1 playlist · 2 tracks · 1 like · 1 artist", substring = true).assertIsDisplayed()
        onNodeWithText("1 album · 2 listening events · 1 search", substring = true).assertIsDisplayed()
        onNodeWithText("Undone").assertIsDisplayed()

        onNodeWithText("Refresh").performClick()
        assertEquals(1, refreshed)

        onNodeWithText("Undo").performClick()
        onNodeWithText("Undo import").assertIsDisplayed()
        onNodeWithText("This cannot be undone after it runs", substring = true).assertIsDisplayed()
        assertNull(undone)

        onNodeWithText("Cancel").performClick()
        onNodeWithText("Undo import").assertDoesNotExist()
        assertNull(undone)

        onNodeWithText("Undo").performClick()
        onNodeWithText("Undo import").performClick()
        assertEquals("batch-1", undone)
        onNodeWithText("Undo import").assertDoesNotExist()
    }
}
