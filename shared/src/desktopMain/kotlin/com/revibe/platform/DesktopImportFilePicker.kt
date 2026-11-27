package com.revibe.platform

import com.revibe.imports.ImportFilePicker
import com.revibe.imports.PickedTextFile
import com.revibe.imports.SpotifyArchiveEntries
import com.revibe.imports.archiveFingerprint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipFile
import javax.swing.JFileChooser
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter

class DesktopImportFilePicker : ImportFilePicker {
    override suspend fun pickPlaylistFile(): PickedTextFile? = withContext(Dispatchers.Swing) {
        configureLookAndFeel()
        val chooser = JFileChooser().apply {
            dialogTitle = "Choose the playlist file downloaded from TuneMyMusic"
            fileSelectionMode = JFileChooser.FILES_ONLY
            isMultiSelectionEnabled = false
            // Start where browsers put exports; the user still picks the exact file.
            currentDirectory = downloadsDirectory().takeIf { it.isDirectory }
            fileFilter = FileNameExtensionFilter(
                "Playlist files (CSV, TXT, M3U, M3U8, XSPF, XML)",
                "csv", "txt", "tsv", "m3u", "m3u8", "xspf", "xml",
            )
        }
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return@withContext null
        val file = chooser.selectedFile ?: return@withContext null
        if (file.length() > MAX_PLAYLIST_BYTES) error("Playlist files must be smaller than 10 MB.")
        PickedTextFile(file.name, decode(file.readBytes()))
    }

    override suspend fun openDownloadsFolder(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val directory = downloadsDirectory()
            if (!directory.isDirectory) return@runCatching false
            if (!Desktop.isDesktopSupported()) return@runCatching false
            val desktop = Desktop.getDesktop()
            if (!desktop.isSupported(Desktop.Action.OPEN)) return@runCatching false
            desktop.open(directory)
            true
        }.getOrDefault(false)
    }

    private fun downloadsDirectory(): File =
        File(System.getProperty("user.home").orEmpty(), "Downloads")

    override suspend fun pickSpotifyArchive(): SpotifyArchiveEntries? = withContext(Dispatchers.IO) {
        val file = withContext(Dispatchers.Swing) {
            configureLookAndFeel()
            val chooser = JFileChooser().apply {
                dialogTitle = "Choose your Spotify data ZIP"
                fileSelectionMode = JFileChooser.FILES_ONLY
                fileFilter = FileNameExtensionFilter("ZIP archives", "zip")
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
        } ?: return@withContext null
        if (!file.name.endsWith(".zip", ignoreCase = true)) error("Choose the ZIP downloaded from Spotify.")
        if (file.length() > MAX_ARCHIVE_BYTES) error("The Spotify ZIP is larger than the 200 MB import limit.")
        readSpotifyArchive(file)
    }

    internal fun readSpotifyArchive(file: File): SpotifyArchiveEntries {
        val files = mutableListOf<PickedTextFile>()
        var total = 0L
        ZipFile(file).use { zip ->
            val entries = zip.entries()
            var count = 0
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (++count > MAX_ENTRIES) error("The archive contains too many files (limit $MAX_ENTRIES).")
                if (entry.isDirectory) continue
                val name = entry.name.replace('\\', '/')
                rejectUnsafeEntry(name)
                if (!isRelevant(name)) continue
                if (entry.size > MAX_ENTRY_BYTES) error("${entry.name} exceeds the 64 MB file limit.")
                val bytes = zip.getInputStream(entry).use { readLimited(it, MAX_ENTRY_BYTES) }
                total += bytes.size
                if (total > MAX_TOTAL_BYTES) error("The archive expands beyond the 128 MB processing limit.")
                files += PickedTextFile(name, decode(bytes))
            }
        }
        if (files.isEmpty()) error("No supported Spotify music files were found in this ZIP.")
        return SpotifyArchiveEntries(files, archiveFingerprint(files))
    }

    private fun rejectUnsafeEntry(name: String) {
        val parts = name.split('/')
        require(!name.startsWith("/") && !Regex("^[A-Za-z]:").containsMatchIn(name) && ".." !in parts) {
            "The ZIP contains an unsafe path and was rejected."
        }
    }

    private fun isRelevant(name: String): Boolean {
        val lower = name.lowercase()
        val base = lower.substringAfterLast('/')
        return (base.endsWith(".json") && listOf(
            "playlist", "yourlibrary", "liked", "follow", "streaminghistory",
            "streaming_history", "searchqueries", "search_queries", "taste", "wrapped",
        ).any(base::contains)) ||
            ((base.endsWith(".md") || base.endsWith(".txt")) && base.contains("read"))
    }

    private fun readLimited(input: InputStream, limit: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) error("A file inside the ZIP exceeded the 64 MB limit.")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun decode(bytes: ByteArray): String {
        if (startsWith(bytes, 0xEF, 0xBB, 0xBF)) {
            return bytes.copyOfRange(3, bytes.size).toString(Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16BE)
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            bytes.toString(Charset.forName("windows-1252"))
        }
    }

    private fun startsWith(bytes: ByteArray, vararg prefix: Int): Boolean =
        bytes.size >= prefix.size && prefix.indices.all { bytes[it].toInt() and 0xFF == prefix[it] }

    private fun configureLookAndFeel() {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    }

    private companion object {
        const val MAX_PLAYLIST_BYTES = 10L * 1024 * 1024
        const val MAX_ARCHIVE_BYTES = 200L * 1024 * 1024
        const val MAX_ENTRY_BYTES = 64L * 1024 * 1024
        const val MAX_TOTAL_BYTES = 128L * 1024 * 1024
        const val MAX_ENTRIES = 5_000
    }
}
