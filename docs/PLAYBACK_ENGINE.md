# Playback Engine

## Contract

```kotlin
interface PlaybackEngine {
    fun setListener(listener: Listener)
    fun load(uri: String, autoPlay: Boolean)
    fun play(); fun pause(); fun seekTo(ms: Long)
    fun setVolume(v: Float); fun stop(); fun release()

    interface Listener {
        fun onReady(durationMs: Long)
        fun onProgress(positionMs: Long)
        fun onPlayingChanged(isPlaying: Boolean)
        fun onBuffering(isBuffering: Boolean)
        fun onEnded()
        fun onError(message: String)
    }
}
```

Everything else is platform-neutral. `PlayerController` owns the queue, shuffle order, repeat mode,
history recording and error state; an engine only decodes audio.

## Desktop engine: JavaFX Media

Chosen because it is a real decoder that ships with the JVM dependency set, handles both local files
and progressive HTTPS, and adds no external runtime install.

- Formats: MP3, AAC/M4A, WAV, AIFF.
- Sources: `file:` URIs and HTTPS preview streams.
- Real duration from `totalDuration`, real position from `currentTimeProperty`.
- Volume 0..1, seek in milliseconds.
- Errors are mapped to user-readable messages.
- `Platform.setImplicitExit(false)` keeps the toolkit alive between tracks.

Verified by `PlaybackEngineTest`: it searches a real preview, loads it, and asserts the decoder
position advances (observed: 242.9 s duration, position 340 ms, `playing = true`).

## What it does not do

| Capability | Status | Reason |
| --- | --- | --- |
| FLAC / OGG / OPUS playback | Not implemented | JavaFX Media cannot decode them. The scanner still tags and indexes them. |
| Gapless playback | Not implemented | JavaFX Media has no gapless pipeline. |
| Crossfade | Not implemented | Needs two simultaneous decoders with gain curves. |
| Equalizer / normalization | Not implemented | No DSP access in JavaFX Media. |
| Audio device selection | Not implemented | Needs OS/WASAPI integration. |
| System Media Transport Controls (SMTC) | Not implemented | Needs WinRT/COM projection; media keys are implemented instead. |
| Resume after restart | Not implemented | Settings exist; position restore is future work. |

The plan for these is a native engine behind the same interface: either libVLC via VLCJ (requires the
user to have VLC, but gives FLAC/OGG/OPUS, gapless, EQ, device selection) or an FFmpeg-based decoder
(JavaCV) as Metrolist does. The engine boundary is deliberately small so this is a swap, not a
rewrite. On Windows, SMTC can be added with a JNA WinRT bridge or a small PowerShell/WinRT sidecar.

## Queue semantics

- `playQueue(tracks, startIndex, contextName)` sets the unshuffled `sourceQueue`, builds the play
  order, and loads the anchor track.
- Shuffle on: current track stays first, remaining tracks are shuffled. Shuffle off: restores the
  original order and the current index.
- Repeat OFF / ALL / ONE cycles from the player bar; ONE replays on `onEnded`.
- `next(auto)` at the end with repeat OFF stops; manual next does nothing (the button is disabled).
- `previous()` seeks to 0 when position > 3 s, otherwise moves back one track.
- `addToQueue`, `playNext`, `removeFromQueue` and `moveInQueue` maintain both the play order and the
  source order.
- Every load records listening history in SQLite.
- Missing `streamUrl` results in an explicit error, not a simulated progress bar.

## Windows integration

- **Media keys:** JNativeHook global hook maps `VK_MEDIA_PLAY_PAUSE` (0xB3), `VK_MEDIA_NEXT_TRACK`
  (0xB0) and `VK_MEDIA_PREV_TRACK` (0xB1) to the player. Works in the background.
- **Lifecycle:** engine released and hook unregistered on window close.
- **Window:** frameless Compose window with custom controls and edge-resize handles.

## Testing checklist

- [x] Real preview decodes and position advances.
- [x] Local WAV scan + playback URI creation.
- [ ] FLAC/OGG/OPUS (blocked on engine).
- [ ] Gapless/crossfade/EQ (blocked on engine).
- [ ] Long-session memory/CPU profile.
- [ ] Device switching and default-device change.
