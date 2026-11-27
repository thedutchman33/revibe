# Revibe

**Your music. Your vibe.**

Revibe is an independent, Windows-first music application built with Kotlin Multiplatform and
Compose Multiplatform. YouTube Music is the catalog, playback and queue authority through
**InnerTubeX v0.13.1**, and your own files play through the bundled **FFmpeg** engine with native
Windows audio output.

This is a working application, not a mockup. See [Status](#status) for verified behaviour and exact
limitations.

---

## Run it

Requirements: Windows 10/11, JDK 21+ (this machine uses JDK 23), and a network connection. Playback
uses **FFmpeg 8.1.2 through JavaCV 1.5.14** and Java Sound; the Windows x64 native libraries ship in
the app. No VLC, external `ffmpeg.exe`, GStreamer or JavaFX install is needed. JavaCPP extracts the
natives to `%USERPROFILE%\.javacpp\cache` on first use, so the first launch is slightly slower.

```bash
./gradlew :desktopApp:run
```

### Windows installer

Download the latest per-user installer from
[GitHub Releases](https://github.com/thedutchman33/revibe/releases/latest). It bundles its own Java
runtime, installs under `%LOCALAPPDATA%\Programs\Revibe`, and creates Start Menu and desktop
shortcuts. Java, a JDK and Gradle are not required to run it.

To build the installer locally (requires Inno Setup 6):

```powershell
powershell -ExecutionPolicy Bypass -File tools/build-windows-installer.ps1
# desktopApp/build/installer/Revibe-<version>-Setup.exe
```

Data lives in `%USERPROFILE%\.revibe` (override with `REVIBE_HOME`). Set `REVIBE_CONTACT_EMAIL`
to include the distributor contact address in the lrc.red request identity; lyrics credit and links
are shown regardless.

### Optional cloud account

Revibe works fully signed out. To enable accounts and multi-device sync, apply the Supabase migration
and launch with the project's **publishable/anon** key:

```bash
REVIBE_SUPABASE_URL="https://<project-ref>.supabase.co" \
REVIBE_SUPABASE_ANON_KEY="<publishable-or-anon-key>" \
./gradlew :desktopApp:run
```

Open the profile menu → **Account & sync**. The SQL, RLS policies and RPCs are in
[`supabase/migrations/`](supabase/migrations/); setup, privacy and conflict semantics are in
[docs/ACCOUNT_SYNC.md](docs/ACCOUNT_SYNC.md). Service-role keys are rejected. Cloud sync has been
tested against mocked PostgREST/GoTrue and two SQLite libraries, **not** against a live Supabase
project.

```bash
./gradlew :shared:desktopTest     # full suite, including live YouTube Music tests
powershell -File tools/build-windows-installer.ps1  # standalone per-user EXE, production update feed
powershell -File tools/build-windows-installer.ps1 -DevelopmentBuild  # local-only, no update checks
```

Every push to `main` is released automatically: the workflow runs the tests, builds
`Revibe-<version>-Setup.exe`, verifies a clean install and a real upgrade from the previous release
on a fresh Windows runner, then creates the tag and GitHub Release. The version is computed from the
latest tag and conventional commit subjects; no version bump is committed back. See
[docs/AUTOMATIC_UPDATES.md](docs/AUTOMATIC_UPDATES.md) and
[docs/BUILD_AND_RELEASE.md](docs/BUILD_AND_RELEASE.md).

Installed builds check `releases/latest/download/update-manifest.json`, download the EXE over HTTPS,
verify its SHA-256 (and Authenticode when a publisher is pinned), then hand off to the silent Inno
installer and relaunch. Accounts, playlists, settings and local data are preserved. Release builds
currently opt in to the unsigned SHA-256-only path because no code-signing certificate is configured;
`v0.9.0` must be replaced by the first EXE release manually because its updater only accepted MSI.

## What works

| Area | Status | Notes |
| --- | --- | --- |
| YouTube Music search | **Implemented** | Real songs, albums and artists through InnerTubeX v0.13.1 |
| YouTube album / artist pages | **Implemented** | Artwork, metadata, track lists, discography, top songs |
| YouTube playlist search / details | **Implemented** | Real playlist browse and track lists |
| Playlist import | **Implemented** | Copies a YouTube playlist into a local SQLite-backed Revibe playlist |
| YouTube full playback | **Implemented** | Progressive MP4/AAC with next-track prefetch and a pre-decoded PCM lead-in |
| Native audio engine | **Implemented** | FFmpeg 8.1.2 via JavaCV 1.5.14; Java Sound output; bundled Windows x64 natives |
| Stream compatibility layer | **Implemented** | Direct signed URL opened by FFmpeg with resolver headers; signed URLs stay out of the UI |
| Native Up Next / radio | **Implemented** | InnerTubeX `next()` + continuation, provider order preserved |
| Manual queue operations | **Implemented** | Play Next, Add to Queue, remove, reorder, clear; manual rows take priority |
| Local library | **Implemented** | MP3/M4A/AAC/WAV/AIFF/FLAC/OGG/Opus through FFmpeg |
| Synced lyrics | **Implemented** | LRCLIB; line and word synchronisation |
| Flexible three-container shell | **Implemented** | Drag-resizable library/right rail, collapse, close/reopen, persisted widths |
| Dynamic home | **Implemented (first pass)** | Maturity from real activity; recent-playlist grid from real plays; provider-seeded personal mixes |
| Artwork enrichment | **Implemented (artwork)** | YouTube Music primary; MusicBrainz/Cover Art Archive and Deezer fallback when the catalog has no cover |
| Miniplayer | **Implemented** | Compact always-on-top window; same playback session; expand without interruption |
| Automatic updates | **Implemented** | Automatic GitHub Releases, HTTPS manifest, SHA-256, optional Authenticode pin, silent Inno EXE handoff, verified real upgrade |
| Keyboard shortcuts | **Implemented** | Ctrl+F, Ctrl+Space, Ctrl+←/→, Ctrl+↑/↓, Ctrl+L, Esc |
| Right contextual sidebar | **Implemented** | Now Playing, lyrics preview, About, credits, queue, recently played |
| Liked, saved albums, followed artists, playlists, history | **Implemented** | SQLite, persisted |
| Artwork + palette theming | **Implemented** | High-resolution selection, Skia memory + disk cache, dominant-colour accent |
| Media keys | **Implemented** | Global play/pause, next, previous |
| Dynamic new/established-user homepage | **Implemented (first pass)** | Real activity thresholds, dynamic sections and real shortcuts |
| Provider enrichment (Last.fm, ListenBrainz, Spotify) | **Not implemented** | MusicBrainz/CAA artwork is live; the rest remain planned |
| Spotify Canvas | **Not integrated** | No official API; undocumented access refused |
| Music recognition | **Blocked on Windows** | ShazamKit is Apple-only; no licensed provider chosen |
| Supabase accounts / sync | **Implemented (client)** | GoTrue/PostgREST client, DPAPI session, SQLite outbox, RLS migration, account UI; live-project E2E pending |
| Device selection, notifications, friends | **Not implemented** | Device button reports the limitation honestly |
| SABR / UMP playback | **Not implemented** | Needs a dedicated transport |
| Gapless, crossfade, EQ, SMTC | **Not implemented** | Needs a player-specific DSP pipeline / WinRT bridge |

There is no Apple catalog, preview, search or artwork integration in Revibe. Only the YouTube Music
catalog and the documented artwork providers remain.

## How the YouTube path works

```
search / browse  ->  InnerTubeX (WEB_REMIX, anonymous session)
                         |
playback         ->  InnerTubeExtractor (MP4/AAC first; progressive probe fallback)
                         |
                    direct signed audio/mp4 URL + resolver headers
                         |
                    FFmpeg decoder -> native-rate 16-bit PCM -> Java Sound

native queue     ->  next(WEB_REMIX, videoId, playlistId = "RDAMVM<videoId>")
                         |
                    playlistPanelRenderer rows + continuation
```

- Search and browse use InnerTubeX's public API; Revibe maps the raw YouTube Music JSON into its own
  `Track`/`Album`/`Artist` models. No InnerTubeX model reaches Compose.
- Free-account audio quality follows the required ladder: **251** (Opus 160k) → **140** (AAC 128k)
  → **250** (Opus 70k) → **249** (Opus 50k) → **139** (AAC 48k). Revibe asks InnerTubeX for the
  highest available otherwise-compatible bitrate and accepts only these itags, so a Premium-only
  format such as 141 is never used. The documented progressive client (itag 18, muxed MP4/AAC) is
  the last fallback. The selected itag, MIME, codec, bitrate and sample rate are logged, and the
  engine logs the decoded rate and channel count.
- FFmpeg opens the signed stream directly and receives the extraction headers through its `headers`
  option. Signed URLs remain out of the playback API and UI. Direct transport is also required for
  reliable seeking: an equivalent loopback re-server failed FFmpeg's seek at some byte boundaries.
- The next queue row is resolved in the background and cached until shortly before URL expiry. The
  FFmpeg preloader decodes three PCM frames; Next adopts that player and writes the lead-in
  immediately instead of repeating extraction and native initialization. Clicking during a prefetch
  shares the same in-flight request, and superseded Next/Previous loads are cancelled.
- Decoded PCM keeps the source's native sample rate and channels; it is not forced through a 48 kHz
  stereo resampler.
- Stream URLs are cached briefly and re-resolved once if playback fails, because signed URLs expire.
- The queue is YouTube Music's: rows are appended in provider order, then the continuation page,
  with no local scoring or artist substitution.
- Anonymous sessions are used. Age-restricted or sign-in-only tracks return
  `AUTHENTICATION_REQUIRED`; no cookies or credentials are harvested.
- IDs are namespaced (`ytm:track:…`, `ytm:album:…`, `ytm:artist:…`, `local:…`).

## Status vocabulary

- **Implemented** — exercised by a test or a live UI run.
- **Partially implemented** — some paths work; the missing paths are named.
- **Not implemented** — absent, not mocked.
- **Blocked** — depends on an external service, licence or platform capability.

## Architecture

```
shared/
  commonMain/  domain, catalog sources, YouTube adapter, playback controller,
               native queue, lyrics, library contracts, Compose UI
  desktopMain/ FFmpeg engine + Java Sound sink, signed-stream headers, SQLite, local scanner,
               platform wiring
desktopApp/    entry point, frameless window, media keys
```

`MusicSourceManager` currently wraps the single YouTube Music catalog but stays multi-source so a
local or licensed source can be added without touching the UI. `CatalogRepository` routes album and
artist ids to their owning source. `PlayerController` asks `TrackStreamResolver` for a URI only when
a track is loaded, so a 50-track queue does not resolve 50 streams up front.

See [docs/REVIBE_ARCHITECTURE.md](docs/REVIBE_ARCHITECTURE.md),
[docs/MUSIC_SOURCE_ARCHITECTURE.md](docs/MUSIC_SOURCE_ARCHITECTURE.md) and
[docs/QUEUE_ENGINE.md](docs/QUEUE_ENGINE.md). The research index is
[docs/README.md](docs/README.md).

## Verified

The current deterministic run covers **197 tests with no failures**, including account/sync, update
and artwork fixtures. MusicBrainz/Cover Art Archive live behavior is a separate opt-in integration
test (`REVIBE_ARTWORK_LIVE=1`); the deterministic artwork test uses MockEngine fixtures and never
depends on the public service. Live YouTube transport checks are opt-in because GitHub's datacenter
IPs can receive SABR-only responses; run them with
`./gradlew :shared:desktopTest -PrevibeLiveTests=true`. The release workflow upgrades the previous
Inno EXE release on a fresh Windows runner and verifies the result, data and relaunch.

An installed Windows test build with a unique AppId was driven through real InnerTubeX playback:
pause/resume, multiple forward and backward seeks, next/previous, rapid switching, restart-to-zero,
artwork and lyrics loading, and sustained playback all passed through the Compose UI. The selected
streams were itag 251 Opus at 48 kHz stereo.

The opt-in live checks include real network requests and native playback:

- `InnerTubeXIntegrationTest` — initializes and searches against YouTube Music.
- `YouTubeSearchTest` — tracks, albums and artists return real results.
- `YouTubeTrackMappingTest` — metadata, artwork, namespaced IDs, album/artist browse.
- `YouTubePlaylistTest` — live playlist search/details and a real SQLite import.
- `YouTubePlaybackResolutionTest` — resolves a free audio stream and asserts it is in the
  251/140/250/249/139 ladder (itag 251 Opus observed on six live tracks).
- `YouTubeStreamCompatibilityTest` — accepts the five free audio itags, rejects premium-only 141,
  mismatched containers/codecs, SABR and HLS; progressive MP4/AAC remains a fallback.
- `FfmpegPlaybackEngineTest` — 13 deterministic checks: M4A/AAC, AAC/ADTS, MP3, WAV, FLAC,
  OGG/WebM/Opus, forward/backward seek (including MP4), restart-to-zero, pause/resume, prefetched
  seek, rapid switching, long session, end-of-track, near-end completion and missing file, plus a
  live YouTube Music stream.
- `PreloadDurabilityProbe` — opt-in live check that an idle preload still starts in ~24 ms and that
  a 45 s seek after the swap lands in ~1.1 s without restarting the track.
- `FreeAudioQualityProbe` — opt-in live report of itag, codec, bitrate, advertised/decoded sample
  rate and channels for six real tracks.
- `LiveSeekMatrixProbe` — opt-in live timing of 10/45/90/25/150/0 s seeks and pause/resume.
- `ExpiredStreamRecoveryTest` — a signed URL is refreshed exactly once before an error is shown.
- `QueueEngineTest` — native order, continuation, manual priority, autoplay off, failures, stale
  context, auto-advance and history.
- `ArtworkResolverTest`, `DeezerArtworkProviderTest` and `ArtworkMatchingTest` — chain order, strict
  title/artist matching, high-res URL promotion, Deezer cover selection, MusicBrainz release and
  recording lookups, 503 retry and cache behavior using deterministic MockEngine fixtures.
- `MusicBrainzArtworkLiveTest` — opt-in (`REVIBE_ARTWORK_LIVE=1`) real MusicBrainz/CAA lookup, kept
  out of the deterministic suite because the public service returns HTTP 503 when busy.
- `HomepagePlanTest` — maturity thresholds, no personalised sections for new users, shortcuts only
  for real playlists, relative timestamps.
- `YouTubeLyricsTest` — LRCLIB returned synced lines for the mapped track.
- `LiveLyricsProviderTest` — Unison returned 16 word-synced English lines; lrc.red returned 44
  word-synced Hindi lines for *Kesariya*.
- `TtmlParserTest` — TTML lines, word timings, spacing, entities and malformed input.
- `LyricsProviderResolverTest` — provider priority, fallback, error containment and wrong-song guard.
- `LayoutSettingsTest` — panel widths clamp and survive a SQLite reload.
- `JdbcSyncStoreTest` — schema v2, persisted/de-duplicated search history, outbox/tombstones,
  first-account claiming and a guard against a second account, local-file filtering.
- `DpapiAuthSessionStoreTest` — the session file is DPAPI-encrypted, contains no plaintext tokens and
  round-trips through save/load/clear.
- `AuthApiTest` — GoTrue password/refresh/confirmation/error paths, session restore and service-role
  key rejection.
- `CloudSyncApiTest` — sanitized track payloads, search upserts, soft deletions, 401/offline handling.
- `SyncEngineTwoDeviceTest` — two independent SQLite libraries, combined activity, edits, offline
  retry, last-writer-wins conflicts and cross-account isolation against an in-memory PostgREST stub.

The visual QA pass is documented in [docs/VISUAL_QA_0_8.md](docs/VISUAL_QA_0_8.md). Captures are in
`build/visual-qa/` and are not committed.

The native queue was also checked live: `NATIVE_QUEUE count=40 continuation=true`, and the
continuation page returned 40 further unique tracks.

## Legal and licensing

Revibe is **not affiliated with Spotify, Apple, YouTube or any other service**. It does not bypass
DRM or paid access, harvest cookies, or extract credentials from other applications. Spotify Canvas
is not integrated because it has no official API; Revibe refuses undocumented access paths.

Linking InnerTubeX and QuackJS (both GPL-3.0) means the combined Revibe distribution is **GPL-3.0**.
JavaCV and JavaCPP are Apache-2.0 (also offered under GPL-2.0 terms); the bundled FFmpeg 8.1.2
natives are the default Bytedeco build and are **LGPL-2.1-or-later**. Ship the FFmpeg licence/notice
and corresponding-source offer with packaged builds. The original Revibe code was MIT through
checkpoint commit `b48101a`; it remains available under that licence at that commit. No source code
from Metrolist, BitChord or VIVI Music was copied. InnerTubeX, JavaCV, JavaCPP and FFmpeg are
unmodified dependencies consumed from JitPack / Maven Central.

YouTube Music extraction relies on InnerTube's web endpoints and is subject to YouTube's Terms of
Service; review the current terms before distributing or commercialising the app. Lyrics come from
LRCLIB. Biographies come from Wikipedia (CC BY-SA 4.0, attributed).

The player bar is modelled on Spotify's layout as a visual reference. Spotify's Circular typeface and
icon artwork are proprietary and are **not** bundled. Revibe ships Inter under the SIL Open Font
License 1.1 (`licenses/Inter-OFL.txt`) as the closest open substitute and uses Material Icons for the
controls. No Spotify branding or logo is used.

See [LICENSE](LICENSE) and [docs/LICENSE_AND_COMPLIANCE.md](docs/LICENSE_AND_COMPLIANCE.md).
