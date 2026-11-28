package com.revibe.platform

import com.revibe.imports.ImportFilePicker
import com.revibe.imports.PickedTextFile
import com.revibe.imports.SpotifyArchiveEntries
import com.revibe.imports.archiveFingerprint
import kotlinx.coroutines.CancellationException
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
    override suspend fun pickPlaylistFile(): PickedTextFile? {
        val file = pickFile(
            title = "Choose the playlist file downloaded from TuneMyMusic",
            filterLabel = "Playlist files (CSV, TXT, M3U, M3U8, XSPF, XML)",
            extensions = listOf("csv", "txt", "tsv", "m3u", "m3u8", "xspf", "xml"),
        ) ?: return null
        if (file.length() > MAX_PLAYLIST_BYTES) error("Playlist files must be smaller than 10 MB.")
        return withContext(Dispatchers.IO) { PickedTextFile(file.name, decode(file.readBytes())) }
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

    override suspend fun pickSpotifyArchive(): SpotifyArchiveEntries? {
        val file = pickFile(
            title = "Choose your Spotify data ZIP",
            filterLabel = "ZIP archives",
            extensions = listOf("zip"),
        ) ?: return null
        if (!file.name.endsWith(".zip", ignoreCase = true)) error("Choose the ZIP downloaded from Spotify.")
        if (file.length() > MAX_ARCHIVE_BYTES) error("The Spotify ZIP is larger than the 200 MB import limit.")
        return withContext(Dispatchers.IO) { readSpotifyArchive(file) }
    }

    /**
     * The Windows shell dialog matches the rest of the OS; other desktops keep the
     * Swing chooser, and it is also the fallback when PowerShell is unavailable.
     */
    private suspend fun pickFile(title: String, filterLabel: String, extensions: List<String>): File? {
        if (WindowsFileDialog.isAvailable()) {
            try {
                return withContext(Dispatchers.IO) {
                    WindowsFileDialog.open(
                        title = title,
                        filterLabel = filterLabel,
                        extensions = extensions,
                        // Start where browsers put exports; the user still picks the exact file.
                        initialDirectory = downloadsDirectory().takeIf { it.isDirectory },
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Fall through to the Swing chooser rather than leaving the click with no dialog.
            }
        }
        return pickWithSwing(title, filterLabel, extensions)
    }

    private suspend fun pickWithSwing(title: String, filterLabel: String, extensions: List<String>): File? =
        withContext(Dispatchers.Swing) {
            configureLookAndFeel()
            val chooser = JFileChooser().apply {
                dialogTitle = title
                fileSelectionMode = JFileChooser.FILES_ONLY
                isMultiSelectionEnabled = false
                currentDirectory = downloadsDirectory().takeIf { it.isDirectory }
                fileFilter = FileNameExtensionFilter(filterLabel, *extensions.toTypedArray())
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
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
