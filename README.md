# Revibe

**Your music. Your vibe.**

Revibe is an independent, Windows-first music application built with Kotlin Multiplatform and
Compose Multiplatform. YouTube Music is the catalog, playback and queue authority through
**InnerTubeX v0.13.1**, and your own files play through either the native **LibVLC** engine or the
built-in JavaFX fallback.

This is a working application, not a mockup. See [Status](#status) for verified behaviour and exact
limitations.

---

## Run it

Requirements: Windows 10/11, JDK 21+ (this machine uses JDK 23), and a network connection. **VLC is
optional but recommended**: when a VLC runtime is available, Revibe uses LibVLC for FLAC/OGG/Opus,
HLS and the full native playback path. Without VLC it falls back to JavaFX and the original formats.
Set `REVIBE_VLC_HOME` if VLC is in a non-standard location, or `REVIBE_PLAYBACK_ENGINE=javafx` to
force the fallback.

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
powershell -File tools/build-windows-installer.ps1  # standalone per-user EXE
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
| YouTube full playback | **Implemented** | Progressive MP4/AAC resolved per play, played by LibVLC or JavaFX |
| Native audio engine | **Implemented** | LibVLC 3.0.22 + vlcj 4.12.1; automatic JavaFX fallback |
| Stream compatibility layer | **Implemented** | Loopback `.m4a` proxy forwards Range + headers; signed URLs stay out of the player |
| Native Up Next / radio | **Implemented** | InnerTubeX `next()` + continuation, provider order preserved |
| Manual queue operations | **Implemented** | Play Next, Add to Queue, remove, reorder, clear; manual rows take priority |
| Local library | **Implemented** | MP3/M4A/AAC/WAV/AIFF always; FLAC/OGG/Opus and HLS with LibVLC |
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
                    LocalStreamProxy (127.0.0.1, /stream.m4a, Range + headers)
                         |
                    LibVLC (preferred) / JavaFX Media fallback

native queue     ->  next(WEB_REMIX, videoId, playlistId = "RDAMVM<videoId>")
                         |
                    playlistPanelRenderer rows + continuation
```

- Search and browse use InnerTubeX's public API; Revibe maps the raw YouTube Music JSON into its own
  `Track`/`Album`/`Artist` models. No InnerTubeX model reaches Compose.
- The extractor prefers `audio/mp4` (itag 140, AAC). It falls back to the documented progressive
  client (itag 18, muxed MP4/AAC) when needed. Formats neither engine can decode are surfaced with
  the actual MIME/codec, not dropped.
- The loopback proxy gives JavaFX a real `.m4a` endpoint, forwards `Range` requests and extraction
  headers, and keeps signed URLs out of the player.
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
  desktopMain/ LibVLC engine + JavaFX fallback, loopback proxy, SQLite, local scanner,
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

The release gate runs **181 deterministic tests, 0 failures** (44 suites), including account/sync
and update coverage. Four additional live YouTube transport checks are opt-in because GitHub's
datacenter IPs currently receive SABR-only responses; run them with
`./gradlew :shared:desktopTest -PrevibeLiveTests=true`. The release workflow upgrades the previous
Inno EXE release on a fresh Windows runner and verifies the result, data and relaunch.

The suite includes live requests and real native playback:

- `InnerTubeXIntegrationTest` — initializes and searches against YouTube Music.
- `YouTubeSearchTest` — tracks, albums and artists return real results.
- `YouTubeTrackMappingTest` — metadata, artwork, namespaced IDs, album/artist browse.
- `YouTubePlaylistTest` — live playlist search/details and a real SQLite import.
- `YouTubePlaybackResolutionTest` — resolves an AAC stream (itag 140 observed).
- `YouTubeStreamCompatibilityTest` — accepts MP4/AAC; rejects unsupported SABR/HLS/WebM requests.
- `YouTubePlaybackEngineTest` — JavaFX fallback plays the proxied stream.
- `VlcPlaybackEngineTest` — 9 real-media checks: M4A/AAC/MP3/WAV/FLAC/OGG/WebM, local HLS, seek,
  pause/resume, file URIs, end-of-track, error handling and a live YouTube stream.
- `ExpiredStreamRecoveryTest` — a signed URL is refreshed exactly once before an error is shown.
- `QueueEngineTest` — native order, continuation, manual priority, autoplay off, failures, stale
  context, auto-advance and history.
- `ArtworkResolverTest`, `DeezerArtworkProviderTest` and `ArtworkMatchingTest` — chain order, strict
  title/artist matching, high-res URL promotion, Deezer cover selection and live CAA lookup.
- `HomepagePlanTest` — maturity thresholds, no personalised sections for new users, shortcuts only
  for real playlists, relative timestamps.
- `PlaybackEngineTest` — JavaFX decodes a bundled local MP3 and advances.
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

Linking InnerTubeX and vlcj (both GPL-3.0) means the combined Revibe distribution is **GPL-3.0**.
LibVLC is LGPL-2.1+; if VLC is bundled, its notices and source-offer terms must ship too. The
original Revibe code was MIT through checkpoint commit `b48101a`; it remains available under that
licence at that commit. No source code from Metrolist, BitChord or VIVI Music was copied. The
InnerTubeX and vlcj dependencies are unmodified and consumed from JitPack / Maven Central.

YouTube Music extraction relies on InnerTube's web endpoints and is subject to YouTube's Terms of
Service; review the current terms before distributing or commercialising the app. Lyrics come from
LRCLIB. Biographies come from Wikipedia (CC BY-SA 4.0, attributed).

The player bar is modelled on Spotify's layout as a visual reference. Spotify's Circular typeface and
icon artwork are proprietary and are **not** bundled. Revibe ships Inter under the SIL Open Font
License 1.1 (`licenses/Inter-OFL.txt`) as the closest open substitute and uses Material Icons for the
controls. No Spotify branding or logo is used.

See [LICENSE](LICENSE) and [docs/LICENSE_AND_COMPLIANCE.md](docs/LICENSE_AND_COMPLIANCE.md).
