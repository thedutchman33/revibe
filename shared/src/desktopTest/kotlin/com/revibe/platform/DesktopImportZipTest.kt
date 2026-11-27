package com.revibe.platform

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopImportZipTest {

    private fun zip(entries: Map<String, String>): File {
        val file = Files.createTempFile("spotify-import-", ".zip").toFile()
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }

    @Test
    fun `only relevant music files are read from a spotify zip`() {
        val file = zip(
            mapOf(
                "Spotify Account Data/Playlist1.json" to
                    """{"playlists":[{"name":"Mix","items":[{"track":{"trackName":"Kesariya"}}]}]}""",
                "Spotify Account Data/Payment.json" to """{"card":"ignored"}""",
                "Spotify Account Data/ReadMeFirst.md" to "Read Me First: Spotify account data",
            ),
        )

        val archive = DesktopImportFilePicker().readSpotifyArchive(file)

        assertEquals(2, archive.files.size)
        assertTrue(archive.files.any { it.name.endsWith("Playlist1.json") })
        assertTrue(archive.files.any { it.name.endsWith("ReadMeFirst.md") })
        assertTrue(archive.files.none { it.name.endsWith("Payment.json") })
    }

    @Test
    fun `zip path traversal is rejected before reading`() {
        val file = zip(mapOf("../evil.json" to "{}"))

        assertFailsWith<IllegalArgumentException> {
            DesktopImportFilePicker().readSpotifyArchive(file)
        }
    }

    @Test
    fun `zip with no supported spotify music files is rejected`() {
        val file = zip(mapOf("account.json" to "{}"))
        assertFailsWith<IllegalStateException> {
            DesktopImportFilePicker().readSpotifyArchive(file)
        }
    }
}
