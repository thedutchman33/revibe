package com.revibe

import com.revibe.data.JdbcLibraryStore
import com.revibe.local.DesktopLocalLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalLibraryTest {

    @Test
    fun `scan indexes a real wav file and skips unchanged files`() = runBlocking {
        val root = Files.createTempDirectory("revibe-local-test").toFile()
        val musicDir = File(root, "music").apply { mkdirs() }
        writeSilentWav(File(musicDir, "Test Song.wav"))

        val store = JdbcLibraryStore(File(root, "library.db"), Dispatchers.IO)
        store.addScanFolder(musicDir.absolutePath)
        val library = DesktopLocalLibrary(store, Dispatchers.IO, File(root, "art"))

        val first = library.scan()
        println("[local] first scan added=${first.added} updated=${first.updated} errors=${first.errors}")
        assertEquals(1, first.added)
        assertEquals(0, first.errors.size)

        val tracks = library.tracks()
        assertEquals(1, tracks.size)
        val track = tracks.first()
        assertEquals("Test Song", track.title)
        assertTrue(track.streamUrl?.startsWith("file:") == true)
        assertTrue(track.durationMs > 0)

        val second = library.scan()
        assertEquals(0, second.added)
        assertEquals(1, second.skipped)

        File(musicDir, "Test Song.wav").delete()
        val third = library.scan()
        assertEquals(1, third.removed)
        assertTrue(library.tracks().isEmpty())
    }

    private fun writeSilentWav(file: File) {
        val format = AudioFormat(44_100f, 16, 1, true, false)
        val samples = ByteArray(44_100) // half a second of silence, 16-bit mono
        AudioInputStream(ByteArrayInputStream(samples), format, (samples.size / 2).toLong()).use { stream ->
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, file)
        }
    }
}
