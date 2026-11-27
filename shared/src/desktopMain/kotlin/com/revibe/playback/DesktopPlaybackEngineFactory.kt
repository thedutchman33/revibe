package com.revibe.playback

/**
 * Desktop playback is FFmpeg decoding into Java Sound, following Metrolist's
 * Windows desktop implementation. There is no LibVLC/VLC runtime dependency and
 * no external ffmpeg.exe process; the native libraries ship with the installer.
 */
object DesktopPlaybackEngineFactory {
    fun create(): SelectedPlaybackEngine =
        SelectedPlaybackEngine(FfmpegPlaybackEngine(), "FFmpeg + Java Sound")
}

data class SelectedPlaybackEngine(
    val engine: PlaybackEngine,
    val name: String,
)
