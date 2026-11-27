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

`PlayerController` owns the queue, shuffle order, repeat mode, history recording and error state; the
engine only decodes and renders audio. Windows selects `FfmpegPlaybackEngine` through
`DesktopPlaybackEngineFactory`.

## Windows engine: FFmpeg + Java Sound

There is no LibVLC, vlcj, JavaFX, external `ffmpeg.exe` or user-installed media runtime.

| Layer | Implementation |
| --- | --- |
| Demux / decode | FFmpeg **8.1.2** through JavaCV **1.5.14** (`FFmpegFrameGrabber`) |
| Native bridge | JavaCPP **1.5.14**, Windows x64 natives bundled as jars |
| Decoded format | Signed 16-bit little-endian PCM at the stream's native sample rate and channel count |
| Output | Java Sound `SourceDataLine`, the JVM's native Windows audio path |
| Buffer | Approximately 150 ms line buffer; 15 decoded frames queued; 3-frame start prebuffer |
| Volume | `MASTER_GAIN` when the Windows line exposes it, otherwise software gain on the PCM buffer |
| Threads | One decode thread and one render thread per player; commands serialized on one engine worker |
| UI boundary | `load`/`play`/`pause`/`seek`/`stop` enqueue commands; Compose never blocks on native I/O |

FFmpeg is not forced to 48 kHz stereo. The selected free stream is decoded at its native format:
itag 251 (Opus) is 48 kHz stereo, itag 140 (AAC) is 44.1 kHz stereo, and local files keep their own
rate/channel count. No track is transcoded or resampled to a higher rate. Java Sound opens the
default line first, then available mixers, with four bounded retries.

## Free-account audio selection

`YouTubeMusicSource` requests `AudioQuality.HIGH` from InnerTubeX and accepts only these audio-only
itags, in this order:

| Priority | itag | Codec | Typical bitrate | Sample rate |
| --- | --- | --- | --- | --- |
| 1 | 251 | Opus (`audio/webm`) | 130–170 kbps | 48 kHz |
| 2 | 140 | AAC (`audio/mp4`) | 128 kbps | 44.1 kHz |
| 3 | 250 | Opus (`audio/webm`) | 70 kbps | 48 kHz |
| 4 | 249 | Opus (`audio/webm`) | 50 kbps | 48 kHz |
| 5 | 139 | AAC (`audio/mp4`) | 48 kbps | 44.1 kHz |

InnerTubeX's high-quality selector sorts by bitrate, which produces exactly that order for free
formats. Revibe validates the result instead of trusting the label: premium-only formats (notably
141) and any itag outside the ladder are rejected, then AUTO/MP4/LOW requests and finally the
documented progressive client are tried. Premium capabilities, cookies and account entitlements are
never enabled. The selected itag, MIME, codec, bitrate and sample rate are logged once per new stream;
the engine logs the decoded sample rate and channels at load. A stream stays cached for the track
until shortly before its signed URL expires, so quality does not change mid-track.

Live check of six real tracks: all selected itag 251, `audio/webm` / `opus`, 146–170 kbps, decoded
48 kHz stereo.

JavaCPP extracts the native libraries to `%USERPROFILE%\.javacpp\cache` on first use.
`FFmpegFrameGrabber.tryLoad()` runs on a preloader thread during engine construction so the first
Play click does not pay that one-time cost.

## Stream transport

Resolved YouTube Music streams are opened **directly** by FFmpeg. `DesktopTrackStreamResolver`
registers the signed URL's extraction headers in `DesktopStreamHeaders`; `openGrabber` passes them
through FFmpeg's `headers` option. Signed URLs remain out of the playback API and UI. This direct
transport is important for seeking: a loopback re-server produced byte-identical range bodies but
FFmpeg's HTTP demuxer failed to seek at some boundaries through it.

The resolver caches a signed URL until shortly before expiry, so queue prefetch and the next click
use the same URL. Stream URLs are re-resolved once if playback reports an error, because signed URLs
expire.

## Playback lifecycle

1. `load(uri, autoPlay)` is queued with a monotonically increasing token. A newer load invalidates an
   older one before it can start.
2. The worker takes a matching prepared player, or opens a new one. Opening reads duration/format,
   opens the line, pauses it and decodes three PCM frames.
3. The outgoing line is paused before the new player starts. The old player closes on a separate
   thread so a slow network read cannot delay the transition.
4. `play()` starts the line. The queued lead-in is written immediately.
5. Decode and render threads share the 15-frame queue. Position comes from
   `SourceDataLine.longFramePosition` converted to media time against a base set by seek.
6. Pause stops the line and freezes the position. The render loop's PCM write is cancellable, so a
   pause cannot leave a thread parked inside a stopped line and block resume.
7. Seek flushes the line, seeks the decoder, validates the first decoded frame, resets the line clock
   and resumes only after the seek completes.
8. At EOF the decoder waits for a queue slot before enqueuing a versioned EOF marker, so a full queue
   at the end cannot strand the renderer. The renderer drains the line, emits `onEnded`, and the
   controller advances to the prepared next player.

`onProgress` is emitted at most every 100 ms. The line clock freezes while paused and is rebased after
seek. A separate decoded-sample counter is not used for UI progress.

## Seeking

The original 5.3 s delay had three causes, all confirmed with live FFmpeg debug logs:

1. `setTimestamp(ms, checkFrame = true)` pulls the first frame inside JavaCV. When that internal grab
   is null, JavaCV returns **without an exception**, so Revibe saw "no frame" and retried every
   candidate. Each retry reopened the signed URL and could drain megabytes of the open response.
2. Five candidates could each pay a fresh connection and seek, turning one failed seek into ~20 s.
3. A landed position was accepted even if it was **after** the requested time, and a forward-skip
   budget of 45 s could decode tens of seconds just to reach the target.

The fixed path:

- `setTimestamp(ms, checkFrame = false)` only arms FFmpeg's seek; Revibe pulls the first PCM frame
  itself, so the landing is measured and a null frame becomes a real retry instead of a silent
  success.
- The exact target is tried first. If it fails, the same target is retried once on a fresh
  connection, then only **earlier** 5/10/20 s boundaries are tried. A later candidate can no longer
  move playback past the requested position.
- A landing at or just before the target is accepted; a short landing is decoded forward by at most
  30 s, and the forward-skipped PCM is discarded rather than queued.
- A landing after the target is rejected and another candidate is tried.
- A seek to **0 ms** is handled separately: an already-open decoder can flush with no packet, so the
  decoder is reopened and read from the beginning. A restart never falls back to 10 s.
- The UI shows the target immediately for responsiveness, then the actual `onSeeked` position. The
  line clock is reset to the landing, and resume waits until the seek finishes.

Measured live (itag 251, real Windows Java Sound): 10 s seek **1.60 s**, 45 s **1.27 s**, 90 s
**1.45 s**, backward to 25 s **1.20 s**, 150 s **1.36 s**; all landings were **14 ms** from the
requested position. Installed UI seeks landed within 14 ms in 0.02–2.9 s, including a backward jump.
After a prepared-player swap, 45 s landed at 45.014 s in ~1.1 s.

## Prefetch and prepared player

`preload(uri)` opens the stream and decodes the first three PCM frames. The player is paused and has its
own line reserved. Next promotes that player, writes the lead-in, and closes the outgoing player
asynchronously. There is no muted second engine and no hidden playback.

Live probe after a preload held for 15 s: next load-to-audio **24 ms**, and a 45 s seek landed at
45.014 s in ~1.1 s with playback continuing. A cold control also landed within 14 ms.

## Verified matrix

`FfmpegPlaybackEngineTest` uses real generated files with a paced in-memory sink; its live YouTube
test uses the real Windows Java Sound line. `PreloadDurabilityProbe` uses real signed streams.

| Check | Result |
| --- | --- |
| MP4/AAC, AAC/ADTS, MP3, WAV, FLAC, Ogg/Vorbis, WebM/Opus | Passing |
| Forward/backward seek and MP4 seek | Passing |
| Restart-to-zero seek | Passing (deterministic and live) |
| Pause/resume position | Passing |
| End-of-track event | Passing |
| Near-end seek still completes | Passing |
| Prefetched next row and seek on a prefetched row | Passing |
| Rapid switching only starts the final row | Passing |
| Long load/seek session | Passing |
| Missing file reports an error | Passing |
| Live YouTube Music stream with Java Sound | Passing |
| 45 s seek after a preloaded swap | Passing (~1.1 s, 14 ms accuracy) |
| Six-track free-format report | Passing (all itag 251, 48 kHz stereo) |

Current full deterministic result: **197 tests, 0 failures**. The former MusicBrainz/CAA failure is
now a deterministic MockEngine test; the real public-service check is `MusicBrainzArtworkLiveTest`,
opt-in with `REVIBE_ARTWORK_LIVE=1`.

## Installed Windows verification

An equivalent per-user installer with a unique AppId was installed to a clean directory, launched
with a fresh `REVIBE_HOME`, and driven through the real Compose window while playing InnerTubeX
streams:

- cold search and first play;
- pause, position hold, resume;
- seeks across 25%, 46%, 76%, 86% and 95%, plus a backward jump (actual decoder positions within
  14 ms, 0.02–2.9 s), without restarting the track;
- next from a prepared player, previous, and five rapid Next clicks, with only the final row audible;
- artwork and lyrics loaded from the real provider;
- sustained playback with advancing position and no runtime errors;
- all checked streams were itag 251 Opus, 48 kHz stereo, with no format/quality change between tracks.

The installed run also exposed the restart-to-zero fallback: seeking to 0 after a fresh load landed
at 10 s. That path is fixed (reopen and read the start, never substitute a later position) and is
covered by `FfmpegPlaybackEngineTest` and the live seek matrix.

## Not implemented

| Capability | Status | Reason |
| --- | --- | --- |
| SABR/UMP transport | Not implemented | Needs a dedicated YouTube transport |
| YouTube HLS transport | Not wired | Resolver requests direct audio-only streams |
| Premium/141 audio | Deliberately excluded | Free-account ladder only; no entitlement bypass |
| HLS local matrix | Not covered | FFmpeg can demux HLS, but no current test |
| Gapless / crossfade | Not implemented | Needs coordinated decoders and a shared clock |
| Equalizer / normalization | Not implemented | No filter graph/UI |
| User-selectable audio quality | Not implemented | Free-account quality is selected automatically |
| Audio device selection | Not implemented | Java Sound uses the default Windows device |
| SMTC | Not implemented | WinRT/COM projection or sidecar; media keys work |
| Network interruption recovery | Partial | FFmpeg reconnect options and one URL re-resolve; no UI retry test |

## Testing

```bash
./gradlew --no-daemon :shared:desktopTest -PrevibeLiveTests=false
REVIBE_LATENCY_PROBE=1 ./gradlew --no-daemon :shared:desktopTest \
  --tests 'com.revibe.playback.FfmpegPlaybackEngineTest' \
  --tests 'com.revibe.playback.PreloadDurabilityProbe' \
  --tests 'com.revibe.playback.FreeAudioQualityProbe' \
  --tests 'com.revibe.playback.LiveSeekMatrixProbe' -PrevibeLiveTests=true
```
