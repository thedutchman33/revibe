# Playback Engine

## Contract

```kotlin
interface PlaybackEngine {
    fun setListener(listener: Listener)
    fun load(uri: String, autoPlay: Boolean)
    fun preload(uri: String) = Unit
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
history recording and error state; an engine only decodes audio. The desktop implementation is chosen
at composition time by `DesktopPlaybackEngineFactory`.

## Desktop selection (0.3.0)

| Runtime situation | Engine |
| --- | --- |
| LibVLC found and no override | **LibVLC** via VLCJ 4.12.1 |
| No LibVLC found | **JavaFX Media** fallback |
| `REVIBE_PLAYBACK_ENGINE=vlc` | LibVLC; reports a load error if the runtime is missing |
| `REVIBE_PLAYBACK_ENGINE=javafx` | JavaFX Media |

The selected engine is printed once at startup (`[audio] Selected ...`) and shown in the Settings
platform line. JavaFX is retained as a real fallback, not dead code; the existing playback test still
instantiates it directly.

## Engine evaluation

| Candidate | Format/transport coverage | Licence | Runtime / integration | Decision |
| --- | --- | --- | --- | --- |
| **LibVLC 3.0.22 + VLCJ 4.12.1** | MP4/AAC, HLS, WebM/Opus, FLAC, Ogg/Vorbis, MP3, WAV; seeking, buffering, duration, failure events | VLCJ **GPL-3.0**; libVLC **LGPL-2.1+** | Native VLC runtime must be installed or bundled; VLCJ is a small JVM binding | **Selected** for this phase |
| GStreamer 1.x + gst1-java-core 1.4.0 | Broad codec coverage through plugins; HLS/WebM/Opus/FLAC | LGPL-3.0 bindings | GStreamer runtime + plugin set needed on every platform; pipeline/state integration is heavier | Rejected for the first POC; viable future LGPL option |
| FFmpeg via JavaCV 1.5.14 | Very broad decode, HLS/WebM/Opus/FLAC; must build an audio-output and clock layer yourself | JavaCV Apache-2.0; FFmpeg build-dependent (GPL builds common) | Large platform natives; no player abstraction, seek/buffering/position must be implemented and maintained | Rejected for this phase; useful if YouTube-specific demuxing is added later |
| JavaFX Media 23.0.2 | MP3, AAC/M4A, WAV, AIFF; progressive HTTP | GPL-2.0 + Classpath Exception | Ships with the app, no external install | **Kept as fallback** |

LibVLC was chosen because the proof of concept needed the widest real codec/HLS coverage with the
smallest integration surface. It also provides a native clock and buffering events instead of
reimplementing an audio pipeline around a decoder. The trade-off is a runtime dependency and a
larger installed footprint; the fallback keeps the app usable when VLC is absent.

### What SABR/UMP means here

SABR/UMP is a YouTube **transport protocol**, not a media container. LibVLC, GStreamer and FFmpeg do
not consume a SABR session out of the box; a dedicated transport/segment implementation is required.
Revibe therefore continues to request progressive MP4/AAC for YouTube and reports
`UNSUPPORTED_TRANSPORT` when only SABR is available. This is a technical limitation, not a source
being arbitrarily marked blocked. No DRM, paid-access or credential bypass is implemented.

## Proof-of-concept results

`shared/src/desktopTest/kotlin/com/revibe/playback/VlcPlaybackEngineTest.kt` uses real generated
media and a real local LibVLC runtime. The fixtures are 4-second tones (plus a 20-second MP3) under
`shared/src/desktopTest/resources/media`.

| Check | Result |
| --- | --- |
| MP4/AAC (`.m4a`) | Playing, duration and advancing position |
| AAC/ADTS (`.aac`) | Playing, duration and advancing position |
| MP3 | Playing, duration and advancing position |
| WAV | Playing, duration and advancing position |
| FLAC | Playing, duration and advancing position |
| Ogg/Vorbis | Playing, duration and advancing position |
| WebM/Opus | Playing, duration and advancing position |
| Local HLS playlist | Playing; duration reported |
| Seek forward to 12 s | Position reached the target |
| Seek backward to 2 s | Position left the forward target |
| Pause / resume | Position held while paused and advanced after resume |
| End of track | `finished` event fired and mapped to `onEnded` |
| Missing file | Engine error surfaced within the timeout |
| `file:` URI | Normalised to an absolute MRL and played |
| Live YouTube Music | itag 140, `audio/mp4`, duration 238,370 ms, position advanced past 1 s |

The JavaFX fallback remains verified by `YouTubePlaybackEngineTest` and `PlaybackEngineTest`.

The runtime used for the POC is downloaded into the ignored `.tooling/vlc/vlc-3.0.22` directory. It is
**not** committed. For a distributed build, either install VLC or ship the runtime with the
application and include its licence/notices.

## YouTube Music streams

```
InnerTubeX extraction (MP4/AAC first, progressive fallback)
   -> direct signed audio/mp4 URL
   -> LocalStreamProxy per-stream token (Range + headers)
   -> LibVLC active player + muted next-track preloader
```

- InnerTubeX extraction still selects MP4/AAC for YouTube. The native engine broadens local formats
  and is ready for HLS/WebM if the resolver is extended, but YouTube HLS/WebM transport is not yet
  wired.
- The loopback proxy keeps signed URLs and headers inside the desktop process and gives each stream
  its own stable endpoint. Preparing the next row can never replace the source backing the current
  decoder.
- The controller warms the next queue row as soon as a queue is loaded (and again after queue edits
  or native refills). Resolution is deduplicated in-flight, cached until 30 seconds before the signed
  URL expiry, and shared by prefetch and a click that arrives during it.
- LibVLC keeps one active player and one muted preloader for the session. The preloader opens and
  buffers the next URI; Next swaps players and resets the preloaded position. No native player is
  recreated per track.
- A playback error re-resolves the current YouTube track once before surfacing failure. This is
  covered by `ExpiredStreamRecoveryTest`.
- Anonymous sessions only. Age-restricted content returns `AUTHENTICATION_REQUIRED`.

### Measured transition latency

Real InnerTubeX stream + LibVLC, `PlaybackLatencyProbe` (`REVIBE_LATENCY_PROBE=1`):

| Path | Before | After |
| --- | ---: | ---: |
| First Play (cold extraction) | 1,758 ms | provider-bound; ~1.6–2.9 s measured |
| Warm load of a cached URI | 98 ms | 51–97 ms |
| Next with resolved cache only | 613 ms | 1–2 ms after preload swap |

The first track still needs InnerTubeX's network extraction. Subsequent loads and Next transitions
use the same resolved URI, a warm proxy and the preloaded decoder.

## Queue semantics

- `playQueue(tracks, startIndex, contextName)` builds the play order and loads one anchor track.
- Shuffle preserves the current track first; turning it off restores `sourceQueue`.
- Repeat OFF / ALL / ONE cycles from the player bar; ONE replays on `onEnded`.
- `next(auto)` at the end with repeat OFF stops; manual next does nothing when there is no next item.
- `previous()` seeks to zero when position is over 3 s, otherwise moves back one track.
- Rapid Next/Previous clicks cancel superseded resolves; only the latest generation may call the
  engine. A click during a prefetch joins that request instead of cancelling it.
- `addToQueue`, `playNext`, `removeFromQueue` and `moveInQueue` update both orders.
- Every load records listening history in SQLite.
- A missing source URL produces an explicit error, never a simulated progress bar.

## Windows integration

- **Media keys:** JNativeHook global hook maps play/pause, next and previous.
- **Lifecycle:** engine and media-key hook are released on window close.
- **Window:** frameless Compose Desktop shell with custom controls and edge-resize handles.

## Not implemented

| Capability | Status | Reason |
| --- | --- | --- |
| SABR/UMP transport | Not implemented | Needs a dedicated YouTube transport, not a codec |
| YouTube HLS/WebM selection | Not wired | Resolver still requests progressive MP4/AAC |
| Gapless / crossfade | Not implemented | Needs two coordinated decoders |
| Equalizer / normalization | Not implemented | LibVLC supports filters, but no UI/control integration yet |
| Audio device selection | Not implemented | LibVLC exposes devices; UI and persistence are future work |
| SMTC | Not implemented | WinRT/COM projection or sidecar required; media keys work |
| Token provider | Not wired | Requires a legitimate, documented authentication flow |
| Bundled VLC installer | Not implemented | Runtime is local tooling only in this phase |

## Testing checklist

- [x] Real MP4/AAC, AAC/ADTS, MP3, WAV, FLAC, OGG and WebM/Opus play.
- [x] Local HLS playlist plays.
- [x] Seeking forward and backward.
- [x] Pause/resume holds and resumes position.
- [x] End-of-track and missing-file error paths.
- [x] Live YouTube AAC playback through the proxy.
- [x] Expired-URL re-resolve path is tested with a controlled resolver.
- [x] JavaFX fallback still compiles and plays.
- [ ] Long-session memory/CPU profile.
- [ ] Network interruption mid-stream and recovery in the UI.
- [ ] Device switching and default-device changes.
- [ ] Gapless/crossfade/EQ.
