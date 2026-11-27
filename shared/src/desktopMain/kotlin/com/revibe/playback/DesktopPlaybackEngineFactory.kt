package com.revibe.playback

/**
 * Desktop engine policy:
 *  - `REVIBE_PLAYBACK_ENGINE=vlc` requires LibVLC.
 *  - `REVIBE_PLAYBACK_ENGINE=javafx` forces the original engine.
 *  - otherwise LibVLC is used when a runtime is found, JavaFX otherwise.
 *
 * The fallback keeps the application usable on machines that do not have VLC
 * installed, while native formats (FLAC/OGG/Opus/HLS) require the native runtime.
 */
object DesktopPlaybackEngineFactory {
    fun create(): SelectedPlaybackEngine {
        val requested = System.getenv("REVIBE_PLAYBACK_ENGINE")?.trim()?.lowercase()
        val vlcHome = VlcNativeLocator.locate()
        val selected = when {
            requested == "javafx" -> SelectedPlaybackEngine(JavaFxPlaybackEngine(), "JavaFX Media")
            requested == "vlc" -> SelectedPlaybackEngine(
                VlcPlaybackEngine(vlcHome),
                if (vlcHome != null) "LibVLC ${vlcHome.name}" else "LibVLC (runtime missing)",
            )
            vlcHome != null -> SelectedPlaybackEngine(VlcPlaybackEngine(vlcHome), "LibVLC ${vlcHome.name}")
            else -> SelectedPlaybackEngine(JavaFxPlaybackEngine(), "JavaFX Media")
        }
        println("[audio] Selected ${selected.name} (requested=${requested ?: "auto"})")
        return selected
    }
}

data class SelectedPlaybackEngine(
    val engine: PlaybackEngine,
    val name: String,
)
