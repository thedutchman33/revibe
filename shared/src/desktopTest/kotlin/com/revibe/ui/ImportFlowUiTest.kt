package com.revibe.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.revibe.imports.ImportProgress
import com.revibe.imports.ImportSummary
import com.revibe.imports.SourceTrack
import com.revibe.ui.screens.PlaylistImportGuide
import com.revibe.ui.screens.PlaylistImportSummaryCard
import com.revibe.ui.theme.RevibeTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ImportFlowUiTest {

    @Test
    fun `guide shows every export step and wires the three actions`() = runComposeUiTest {
        var tuneMyMusic = 0
        var downloads = 0
        var picked = 0
        setContent {
            RevibeTheme {
                Column(Modifier.width(760.dp).verticalScroll(rememberScrollState())) {
                    PlaylistImportGuide(
                        busy = false,
                        selectedFileName = null,
                        progress = ImportProgress("Ready"),
                        error = null,
                        onOpenTuneMyMusic = { tuneMyMusic++ },
                        onOpenDownloads = { downloads++ },
                        onImportFile = { picked++ },
                    )
                }
            }
        }

        onNodeWithText("Open TuneMyMusic").assertExists()
        onNodeWithText("Copy and paste the Spotify playlist URL").assertExists()
        onNodeWithText("Load playlist").assertExists()
        onNodeWithText("Choose Destination").assertExists()
        onNodeWithText("Export to file").assertExists()
        onNodeWithText("Return to Revibe").assertExists()
        onNodeWithText("Import the downloaded CSV").assertExists()

        onNodeWithText("Import downloaded playlist").performScrollTo().performClick()
        onNodeWithText("Open Downloads folder").performScrollTo().performClick()
        onNodeWithText("Open TuneMyMusic in browser").performScrollTo().performClick()

        assertEquals(1, picked)
        assertEquals(1, downloads)
        assertEquals(1, tuneMyMusic)
    }

    @Test
    fun `a selected file is named while matching runs`() = runComposeUiTest {
        setContent {
            RevibeTheme {
                Column(Modifier.width(760.dp).verticalScroll(rememberScrollState())) {
                    PlaylistImportGuide(
                        busy = true,
                        selectedFileName = "Taare Radio.csv",
                        progress = ImportProgress("Matching tracks", 4, 10),
                        error = null,
                        onOpenTuneMyMusic = {},
                        onOpenDownloads = {},
                        onImportFile = {},
                    )
                }
            }
        }

        onNodeWithText("Taare Radio.csv").assertExists()
        onNodeWithText("Reading…").assertExists()
        onAllNodesWithText("Matching tracks")[0].assertExists()
    }

    @Test
    fun `success card reports counts and opens the playlist`() = runComposeUiTest {
        var opened = 0
        var importedAnother = 0
        var done = 0
        val summary = ImportSummary(
            primaryPlaylistId = "playlist-1",
            playlistName = "Taare Radio",
            playlistsCreated = 1,
            tracksAdded = 42,
            needsReview = 3,
            unmatched = listOf(SourceTrack(1, "Missing Song", "Nobody")),
        )

        setContent {
            RevibeTheme {
                Column(Modifier.width(760.dp).verticalScroll(rememberScrollState())) {
                    PlaylistImportSummaryCard(
                        summary = summary,
                        onOpenPlaylist = { opened++ },
                        onImportAnother = { importedAnother++ },
                        onDone = { done++ },
                    )
                }
            }
        }

        onNodeWithText("Taare Radio").assertIsDisplayed()
        onNodeWithText("Tracks added").assertIsDisplayed()
        onNodeWithText("42").assertIsDisplayed()
        onNodeWithText("Tracks requiring review").assertIsDisplayed()
        onNodeWithText("3").assertIsDisplayed()
        onNodeWithText("Unmatched tracks").assertIsDisplayed()
        onNodeWithText("Missing Song", substring = true).assertIsDisplayed()

        onNodeWithText("Open playlist").performScrollTo().performClick()
        onNodeWithText("Import another playlist").performScrollTo().performClick()
        onNodeWithText("Done").performScrollTo().performClick()

        assertEquals(1, opened)
        assertEquals(1, importedAnother)
        assertEquals(1, done)
    }
}
