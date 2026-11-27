package com.revibe.image

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.revibe.image.artworkLoadCandidates
import com.revibe.ui.theme.RevibeColors
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Artwork loader with memory + disk cache and palette extraction, all done off
 * the UI thread. Uses Skia (already present via Compose) so no extra image
 * library is required on desktop.
 */
class SkiaArtworkLoader(
    private val client: HttpClient,
    private val cacheDir: File,
) : ArtworkLoader {

    private data class CacheEntry(val artwork: LoadedArtwork)

    private val memory = ConcurrentHashMap<String, CacheEntry>()
    private val inFlight = ConcurrentHashMap<String, Any>()

    /** Network bytes are kept apart from embedded local artwork in the same cache root. */
    private val diskDir = File(cacheDir, "remote").apply { mkdirs() }

    init {
        cacheDir.mkdirs()
    }

    override suspend fun load(url: String?, sizePx: Int): LoadedArtwork? {
        if (url.isNullOrBlank()) return null
        val key = "$sizePx|$url"
        memory[key]?.let { return it.artwork }

        val gate = inFlight.putIfAbsent(key, Any())
        if (gate != null) {
            // Another caller is fetching this image; wait briefly instead of duplicating the request.
            repeat(40) {
                kotlinx.coroutines.delay(50)
                memory[key]?.let { return it.artwork }
            }
            return null
        }

        return try {
            val bytes = withContext(Dispatchers.IO) { readBytes(url) } ?: return null
            var artwork = withContext(Dispatchers.Default) { decode(bytes) }
            if (artwork == null && !url.startsWith("file:")) {
                // A truncated or stale cache entry must not permanently break
                // the cover; drop it and let the next load fetch it again.
                withContext(Dispatchers.IO) { cacheFile(url).delete() }
            }
            artwork ?: return null
            memory[key] = CacheEntry(artwork)
            if (memory.size > 90) {
                memory.keys.take(memory.size - 90).forEach(memory::remove)
            }
            artwork
        } finally {
            inFlight.remove(key)
        }
    }

    private fun readBytes(url: String): ByteArray? {
        if (url.startsWith("file:")) {
            return runCatching { File(URI(url)).readBytes() }.getOrNull()
        }
        cacheFile(url).takeIf { it.isFile }?.let { cached ->
            runCatching { cached.readBytes() }.getOrNull()?.let { return it }
        }
        for (candidate in artworkLoadCandidates(url)) {
            val bytes = runCatching {
                val response: HttpResponse = kotlinx.coroutines.runBlocking { client.get(candidate) }
                if (!response.status.isSuccess()) return@runCatching null
                kotlinx.coroutines.runBlocking { response.body<ByteArray>() }
            }.getOrNull() ?: continue
            if (bytes.isEmpty()) continue
            writeCache(url, bytes)
            return bytes
        }
        return null
    }

    private fun cacheFile(url: String): File = File(diskDir, cacheFileName(url))

    private fun writeCache(url: String, bytes: ByteArray) {
        runCatching {
            val target = cacheFile(url)
            val temp = File(diskDir, target.name + ".tmp")
            temp.writeBytes(bytes)
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
            pruneDiskCache()
        }
    }

    /** Keeps the artwork cache bounded without touching embedded local art. */
    private fun pruneDiskCache() {
        val files = diskDir.listFiles { file -> file.isFile && file.name.endsWith(".img") } ?: return
        if (files.size <= MAX_DISK_FILES) return
        var remaining = files.size
        var bytes = files.sumOf { it.length() }
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (remaining <= MAX_DISK_FILES && bytes <= MAX_DISK_BYTES) return@forEach
            bytes -= file.length()
            remaining--
            file.delete()
        }
    }

    private fun decode(bytes: ByteArray): LoadedArtwork? {
        return runCatching {
            // Decode and immediately downscale via Skia's codec where possible.
            val image = Image.makeFromEncoded(bytes)
            val bitmap: ImageBitmap = image.toComposeImageBitmap()
            val (dominant, secondary) = palette(bitmap)
            LoadedArtwork(bitmap, dominant, secondary)
        }.getOrNull()
    }

    private fun palette(bitmap: ImageBitmap): Pair<Color, Color> {
        val pixels = runCatching { bitmap.toPixelMap() }.getOrNull()
            ?: return RevibeColors.Accent to RevibeColors.Surface
        val steps = 18
        var bestScore = -1f
        var best = RevibeColors.Accent
        for (y in 0 until steps) {
            for (x in 0 until steps) {
                val px = (x * (pixels.width - 1) / (steps - 1)).coerceAtLeast(0)
                val py = (y * (pixels.height - 1) / (steps - 1)).coerceAtLeast(0)
                val color = pixels[px, py]
                if (color.alpha < 0.5f) continue
                val max = maxOf(color.red, color.green, color.blue)
                val min = minOf(color.red, color.green, color.blue)
                val saturation = if (max <= 0f) 0f else (max - min) / max
                val score = saturation * 1.5f + max * 0.25f
                if (score > bestScore && max > 0.10f) {
                    bestScore = score
                    best = color
                }
            }
        }
        if (bestScore < 0.08f) {
            return RevibeColors.Accent to RevibeColors.Surface
        }
        val accent = brightenForUi(best)
        val secondary = lerp(accent, Color(0xFF0A0B0C), 0.78f)
        return accent to secondary
    }

    private fun brightenForUi(color: Color): Color {
        val luminance = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
        val target = 0.58f
        val factor = if (luminance < target && luminance > 0.02f) (target / luminance).coerceAtMost(2.4f) else 1f
        return Color(
            red = (color.red * factor).coerceIn(0f, 1f),
            green = (color.green * factor).coerceIn(0f, 1f),
            blue = (color.blue * factor).coerceIn(0f, 1f),
        )
    }

    private fun lerp(from: Color, to: Color, fraction: Float): Color = Color(
        red = from.red + (to.red - from.red) * fraction,
        green = from.green + (to.green - from.green) * fraction,
        blue = from.blue + (to.blue - from.blue) * fraction,
    )

    private fun cacheFileName(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        return digest.joinToString("") { "%02x".format(it) } + ".img"
    }

    private companion object {
        const val MAX_DISK_FILES = 320
        const val MAX_DISK_BYTES = 192L * 1024 * 1024
    }
}
