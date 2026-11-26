package com.revibe.local

import com.revibe.library.LibraryStore
import com.revibe.model.MusicSourceId
import com.revibe.model.ScanReport
import com.revibe.model.Track
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.security.MessageDigest

/**
 * Scans folders, reads tags and embedded artwork with jaudiotagger, and stores
 * the result in the library. Files remain the source of truth: a scan refreshes
 * changed files and drops entries whose files disappeared.
 */
class DesktopLocalLibrary(
    private val store: LibraryStore,
    private val io: CoroutineDispatcher,
    private val cacheDir: File,
) : LocalLibrary {

    private val extensions = setOf("mp3", "flac", "m4a", "aac", "ogg", "oga", "opus", "wav", "aiff", "aif", "wma")

    override suspend fun folders(): List<String> = store.scanFolders()

    override suspend fun addFolder(path: String) = store.addScanFolder(path)

    override suspend fun removeFolder(path: String) = store.removeScanFolder(path)

    override suspend fun tracks(): List<Track> = store.localTracks()

    override suspend fun scan(onProgress: (scanned: Int, total: Int) -> Unit): ScanReport = withContext(io) {
        cacheDir.mkdirs()
        val folders = store.scanFolders()
        if (folders.isEmpty()) {
            return@withContext ScanReport()
        }

        val files = folders
            .mapNotNull { runCatching { File(it) }.getOrNull() }
            .filter { it.isDirectory }
            .flatMap { folder ->
                folder.walkTopDown()
                    .onEnter { !it.name.startsWith(".") }
                    .filter { it.isFile && it.extension.lowercase() in extensions }
                    .toList()
            }
            .distinctBy { it.absolutePath }

        val existing = store.localFileState()
        val toUpsert = mutableListOf<Track>()
        val mtimes = mutableMapOf<String, Long>()
        val errors = mutableListOf<String>()
        var added = 0
        var updated = 0
        var skipped = 0

        onProgress(0, files.size)
        files.forEachIndexed { index, file ->
            val path = file.absolutePath
            val mtime = file.lastModified()
            val previous = existing[path]
            if (previous != null && previous == mtime) {
                skipped++
            } else {
                val track = runCatching { readTrack(file) }.getOrElse { error ->
                    if (errors.size < 8) errors += "$path: ${error.message ?: "unreadable"}"
                    null
                }
                if (track != null) {
                    toUpsert += track
                    mtimes[path] = mtime
                    if (previous == null) added++ else updated++
                }
            }
            if (index % 12 == 0 || index == files.lastIndex) {
                onProgress(index + 1, files.size)
            }
        }

        if (toUpsert.isNotEmpty() || mtimes.isNotEmpty()) {
            store.upsertLocalTracks(toUpsert, mtimes)
        }

        val seen = files.map { it.absolutePath }.toSet()
        val removed = existing.keys.filterNot { it in seen }
        if (removed.isNotEmpty()) {
            store.removeLocalTracks(removed)
        }

        ScanReport(
            added = added,
            updated = updated,
            removed = removed.size,
            skipped = skipped,
            errors = errors,
        )
    }

    private fun readTrack(file: File): Track {
        val audio = AudioFileIO.read(file)
        val tag = audio.tag
        val header = audio.audioHeader

        val title = tag?.getFirst(FieldKey.TITLE)?.trim()?.takeIf { it.isNotEmpty() }
            ?: file.nameWithoutExtension
        val artist = tag?.getFirst(FieldKey.ARTIST)?.trim()?.takeIf { it.isNotEmpty() } ?: "Unknown artist"
        val album = tag?.getFirst(FieldKey.ALBUM)?.trim()?.takeIf { it.isNotEmpty() } ?: "Unknown album"
        val genre = tag?.getFirst(FieldKey.GENRE)?.trim()?.takeIf { it.isNotEmpty() }
        val year = tag?.getFirst(FieldKey.YEAR)?.take(4)?.toIntOrNull()
        val trackNumber = tag?.getFirst(FieldKey.TRACK)?.takeWhile { it.isDigit() }?.toIntOrNull()
        val durationMs = header.trackLength.coerceAtLeast(0).toLong() * 1000L

        val artworkUrl = runCatching {
            val artwork = tag?.firstArtwork ?: return@runCatching null
            val bytes = artwork.binaryData ?: return@runCatching null
            val digest = MessageDigest.getInstance("SHA-256").digest(file.absolutePath.toByteArray())
            val name = digest.joinToString("") { "%02x".format(it) } + ".jpg"
            val target = File(cacheDir, "embedded-$name")
            if (!target.exists()) target.writeBytes(bytes)
            target.toURI().toString()
        }.getOrNull()

        return Track(
            id = "local:$title:${file.absolutePath}",
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            artworkUrl = artworkUrl,
            streamUrl = file.toURI().toString(),
            source = MusicSourceId.LOCAL,
            year = year,
            trackNumber = trackNumber,
            genre = genre,
            localPath = file.absolutePath,
        )
    }
}
