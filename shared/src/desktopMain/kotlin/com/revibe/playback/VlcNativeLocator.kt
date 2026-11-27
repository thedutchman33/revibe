package com.revibe.playback

import java.io.File

/**
 * Finds a LibVLC runtime. Revibe does not silently download or install native
 * libraries at runtime; the locator only uses an explicit VLC home, the
 * documented OS locations, or the project-local development runtime.
 */
object VlcNativeLocator {
    fun locate(): File? {
        val candidates = buildList {
            listOf("REVIBE_VLC_HOME", "VLC_HOME").forEach { name ->
                System.getenv(name)?.takeIf { it.isNotBlank() }?.let { add(File(it)) }
            }
            val userDir = File(System.getProperty("user.dir", "."))
            add(File(userDir, ".tooling/vlc/vlc-3.0.22"))
            add(File(userDir.parentFile ?: userDir, ".tooling/vlc/vlc-3.0.22"))
            add(File("C:/Program Files/VideoLAN/VLC"))
            add(File("C:/Program Files (x86)/VideoLAN/VLC"))
            add(File("/Applications/VLC.app/Contents/MacOS/lib"))
            add(File("/usr/lib/x86_64-linux-gnu"))
            add(File("/usr/lib"))
        }
        return candidates.firstOrNull { isRuntime(it) }
    }

    fun isRuntime(dir: File): Boolean {
        if (!dir.isDirectory) return false
        return File(dir, "libvlc.dll").isFile ||
            File(dir, "libvlc.dylib").isFile ||
            File(dir, "libvlc.so").isFile
    }

    fun pluginDirectory(dir: File): File = File(dir, "plugins")
}
