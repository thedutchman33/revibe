# Changelog

## [1.1.1] - 2026-11-28

### Fixed

- The in-app update no longer sits on `Updating 0%` forever. A download that never receives
  response headers, or stops delivering bytes mid-transfer, is now aborted (30 s response, 45 s
  stall), retried up to three times and then surfaced as an error with a Retry action, instead of
  waiting on a socket that will never answer.
- Dragging the Queue/Lyrics rail now follows the cursor: the rail is anchored to the right edge, so
  moving the handle right narrows it instead of widening it leftwards.

## [1.1.0] - 2026-11-28

### Changed

- The playlist and Spotify data pickers open the native Windows file dialog instead of Swing's
  `JFileChooser`, so importing now looks and behaves like the rest of the OS: breadcrumb path,
  search, Quick Access sidebar, filter dropdown and the usual keyboard shortcuts. Swing remains
  only as the non-Windows path and as the fallback when the native dialog cannot be shown.

## [1.0.0] - 2026-11-28

### Changed

- Released as **1.0.0**. The application, installer filename, Windows file metadata and the in-app
  About/version display all read the same `revibeVersion`; the Inno Setup script no longer carries a
  stale fallback version.

### Added

- Free-account audio quality now follows the explicit 251 → 140 → 250 → 249 → 139 ladder. Live
  checks on six real tracks all selected itag 251 (Opus, `audio/webm`, 146–170 kbps, 48 kHz stereo);
  premium-only formats such as 141 are rejected and no account entitlement is used. The resolver
  logs itag, MIME, codec, bitrate and sample rate, and the engine logs decoded rate/channels.
- Deterministic artwork coverage for MusicBrainz/Cover Art Archive using MockEngine fixtures
  (release match, recording fallback, 503 retry, 1200 px promotion, empty cover and cache). The real
  external lookup moved to the opt-in `MusicBrainzArtworkLiveTest`.
- Live quality, seek-matrix and seek-diagnostic probes for reproducible verification.
- Next-track prefetch, an expiry-aware signed-URL cache, and a persistent FFmpeg preloader. The
  queue warms the following row in the background, shares that request with a click, and swaps to a
  muted preloaded player on Next instead of rebuilding the engine.
- Push-to-main release pipeline. Each push computes the next SemVer from the latest tag and
  conventional commit subjects, runs the full suite, builds the Inno Setup EXE, verifies a clean
  install and a real upgrade from the previous GitHub release on a fresh Windows runner, then creates
  the tag and publishes the EXE, `SHA256SUMS.txt`, `update-manifest.json` and generated release notes.
- EXE auto-update support. The updater now accepts the Inno Setup `.exe`, downloads and hashes it over
  HTTPS, and hands it to a separate silent installer process that preserves the install directory and
  relaunches Revibe. MSI remains accepted for compatibility.
- Explicit `revibeAllowUnsignedUpdates` build policy. Release builds enable the current unsigned
  GitHub-release path; a configured `revibePublisher` still requires a valid Authenticode signature.
- Real EXE handoff integration test that runs the production updater PowerShell helper against the
  actual Inno installer built by CI.

### Changed

- Releases are no longer version-bumped in `gradle.properties` and no longer published from the
  manual signed-MSI workflow. The tag is the released version; CI passes it into Gradle.
- The release workflow is serialized, skips already-tagged commits, rechecks duplicate tags and
  releases before publishing, and never commits back to `main`, preventing version conflicts and
  workflow loops.

### Fixed

- Seeking no longer takes ~5.3 s or fails after five reconnects. The root cause was JavaCV's
  `setTimestamp(checkFrame = true)` silently returning when its internal grab was null, followed by
  five fresh-connection retries. The player now pulls the first frame itself, retries only earlier
  boundaries, rejects landings after the requested time and caps forward skipping at 30 s. Live
  seeks land within 14 ms in 0.6–1.7 s (one 2.9 s outlier).
- Seeking to zero now reopens and reads from the start; it can no longer fall back to 10 s when an
  already-open decoder flushes without a packet. An installed run reproduced the bad first landing
  at 20.014 s, then the retry reopened and returned 14 ms in ~0.7 s.
- Artwork tests no longer depend on a live MusicBrainz/CAA request inside the deterministic suite;
  the public service's HTTP 503 "server is busy" response was the cause of the sole test failure.
- Seek bar presses and drags seek immediately instead of competing with the tap gesture.
- Rapid Next/Previous no longer leaves superseded loads racing the selected track.
- The installer gets a real Revibe icon for the EXE, shortcuts and taskbar, and local installer
  builds default to the production update feed instead of reporting a development build.
- The Google mark and the Import from Spotify glyph render upright. The G's opening now sits above
  the crossbar with canonical arc order, and import uses a down-into-tray icon instead of an
  upload-style arrow.
- Update UI copy no longer promises Windows Installer-specific progress when an Inno EXE is used.

## [0.11.1] - 2026-11-27

### Changed

- Replaced the LibVLC/vlcj desktop engine and the JavaFX fallback with one **FFmpeg 8.1.2** engine
  through JavaCV 1.5.14. FFmpeg decodes at the stream's native sample rate/channels to signed 16-bit
  PCM, written to Java Sound `SourceDataLine`. FFmpeg/JavaCPP Windows x64 natives ship inside the
  installer; no VLC, JavaFX, GStreamer or external `ffmpeg.exe` is needed.
- YouTube streams now open directly in FFmpeg with the resolver's HTTP headers. Signed URLs stay out
  of the playback API/UI, and seeking no longer depends on a loopback range re-server.
- Each player has one decode thread, one render thread, a 15-frame PCM queue and a 150 ms line buffer;
  engine commands remain serialized so slow opens do not block Compose.
- VideoLAN, vlcj and JavaFX code, dependencies and build steps were removed; `jna-platform` remains
  only for DPAPI and the Windows AppUserModelID.

### Added

- Prepared next players decode three PCM frames while paused. Live preload-to-next audio measured
  **2 ms**, and seeking to 45 s after a swap resumed in **5.3 s** with playback continuing past 47 s.
- Exact decoder seeks: `setTimestamp` is validated by pulling the first frame, nearby byte boundaries
  are retried on fresh connections, and a short landing is decoder-skipped forward to the requested
  timestamp. Resume waits until the seek finishes.
- Deterministic FFmpeg coverage for MP4/AAC, AAC/ADTS, MP3, WAV, FLAC, Ogg/Vorbis, WebM/Opus, forward
  and backward seek, MP4 seek, pause/resume, end-of-track, near-end seek, prefetched seek, rapid
  switching, long sessions and missing files; a live YouTube Music stream test; and a live preload
  durability probe.
- Installed-Windows acceptance: play, pause/resume, seeks at ~25/50/80/90%, next/previous, five rapid
  Next clicks, end-of-track → prepared next, and 70+ seconds of sustained InnerTubeX playback.

### Fixed

- Pause no longer deadlocks resume when a render thread is parked in a stopped Java Sound line;
  PCM writes are cancellable.
- Seeking no longer restarts a track from zero; direct signed-stream transport and first-frame seek
  validation replaced the unreliable proxy range path.
- A full decode queue at EOF no longer strands the renderer at the last second; EOF waits for a slot
  before marking completion.
- Play pressed during a seek no longer starts stale PCM or races the seek clock.
- Seek bar presses/drags and taskbar identity remain fixed from the unreleased queue work.

### Known limitations

- If a specific signed stream rejects every exact/nearby seek candidate, playback can surface an
  error instead of guaranteeing an exact position; tested tracks sought within 30 ms.
- There is no user-facing audio-quality selector; all installed checks used itag 140 at 44.1 kHz
  stereo.
- `revibeSupabaseUrl`/`revibeSupabaseAnonKey` remain blank in `gradle.properties`; installed builds
  need the release secrets or environment variables for account sync.

## [0.9.0] - 2026-11-26

### Added

- Supabase account support: email/password sign-up, sign-in, confirmation state, password-reset
  email request, session restore, secure token refresh, profile editing and logout. GoTrue/PostgREST
  are called directly over the existing Ktor client; no service-role key or Supabase SDK is shipped.
- Windows DPAPI-encrypted `account.session` store. Tokens never appear in UI state, logs or SQLite.
- Versioned Supabase migration for `profiles`, `playlists`, `playlist_tracks`, `liked_songs`,
  `followed_artists`, `listening_history`, `search_history`, `user_preferences` and `user_devices`,
  with RLS on every table, ownership checks, updated-at triggers, new-user provisioning and the
  `sync_replace_playlist_tracks` / `sync_soft_delete` RPCs.
- SQLite schema v2: stable `history.sync_id`, persisted/de-duplicated `search_history`, outbox,
  tombstones, state and cursors. Existing rows are preserved and existing history rows get UUIDs.
- Central `SyncEngine`: first-login bootstrap, push-before-pull, incremental cursors, background
  retry with backoff, offline queue, tombstones, idempotent upserts, conflict suppression for pending
  local entities and last-writer-wins resolution.
- Account screen and profile-menu entry: sign-in/create-account forms, signed-in profile/avatar,
  sync phase, last synchronized time, pending count, device id, manual Sync Now, offline/error copy,
  search-history privacy toggle and sign out. The avatar carries a sync status dot.
- Opt-in search-history sync. Off by default; local terms remain local until enabled.
- Shared account activity: pulled listening history, likes and follows feed the existing
  `UserActivitySnapshot`/maturity homepage on every signed-in device.
- Tests: SQLite sync/ownership, mocked GoTrue/session handling, sanitized PostgREST payloads and a
  two-SQLite-library sync engine simulation covering edits, offline retry, conflicts and account
  isolation, plus a DPAPI at-rest encryption round-trip (26 new tests).
- Central `UpdateService` and Settings → About Revibe: startup/6-hour/manual checks, SemVer,
  optional/mandatory policy, release notes, download progress, skip/remind, error/retry and
  completed-update confirmation. A non-modal notice never pauses playback.
- HTTPS-only streaming MSI download with SHA-256 verification, Authenticode signature and pinned
  publisher verification, and a separate PowerShell/MSI handoff that waits for app shutdown, writes
  an install result and relaunches Revibe. No running application files are replaced.
- `revibeVersion`, `revibeUpdateManifestUrl` and `revibePublisher` are build-time configuration;
  the generated `RevibeBuildInfo` and installer package version share one source. GitHub Releases
  workflow signs, hashes, generates the manifest and publishes the MSI. No personal repository URL
  is hardcoded. MSIX/App Installer compatibility is documented as a future path, not implemented.
- Update tests cover SemVer, manifest parsing, optional/mandatory checks, skip/remind, progress,
  interruption, checksum mismatch, publisher rejection, installer failure/success recovery, data
  preservation and unpackaged-build refusal. A real signed MSI upgrade remains pending.
- Standalone per-user Windows EXE built with Inno Setup 6 from the bundled app image. It installs
  under `%LOCALAPPDATA%\Programs\Revibe`, creates Start Menu and desktop shortcuts, and ships its
  own Java runtime. `tools/build-windows-installer.ps1` reproduces the build.

### Changed

- History retention increased from 1,000 to 5,000 rows so combined account activity has enough
  signal; remote pulls apply the same cap.
- Local media, private file paths and temporary stream URLs are excluded from cloud payloads. Track
  metadata is carried in a dedicated sanitized JSON shape; playback still resolves through InnerTubeX.
- First login binds the local SQLite library to one account. A different account is signed out before
  any upload/download, preventing cross-account library leakage.
- `README`, `PROJECT_STATUS`, `DATABASE_SCHEMA` and the new `docs/ACCOUNT_SYNC.md` document setup,
  RLS, privacy and test boundaries.

### Fixed

- The packaged launcher now includes `jdk.httpserver`; without it `LocalStreamProxy` failed before
  the window appeared even though the development build worked.
- The right-panel artwork follows the loaded image's aspect ratio instead of cropping 16:9 stills.

### Known limitations

- Google sign-in was completed on the live Supabase project during installer testing. The SQL
  migration, email delivery, RLS enforcement and a real two-device sync were still not exercised
  end to end.
- Whole-playlist last-writer-wins for simultaneous offline playlist edits; no per-track merge.
- Password reset is completed on the Supabase-hosted page; Revibe only requests the email.
- Polling, not Realtime. Profile edits are not queued offline.
- No signed MSI was available to exercise the real Windows Installer handoff, relaunch or update
  while playing. The standalone EXE was installed, launched without Java and uninstalled; the MSI
  upgrade path is still not claimed as verified.

## [0.8.0] - 2026-11-26

### Added

- Maturity-driven homepage: NEW/EARLY/ACTIVE/ESTABLISHED computed from real SQLite plays,
  completions, skips, distinct artists, likes, follows, playlists and local imports. New users get
  Getting Started and discovery; established users get made-for-you and artist mixes derived from
  actual top artists.
- Real playlist shortcut grid from the same `LibraryRepository` rows as Your Library (up to 8, no
  padding, no invented playlists).
- Recently played timestamps in the sidebar, with a testable relative-time formatter.
- Provider seam (`ArtworkProvider`, `MusicMetadataProvider`, `LyricsProvider`, `RecognitionProvider`,
  `ListeningHistoryProvider`, `ExternalAccountProvider`, `VisualBackgroundProvider`) and a live
  MusicBrainz + Cover Art Archive artwork fallback, used only when the catalog has no cover.
- Spotify-style floating miniplayer: a separate 400×336 dp always-on-top card with a six-dot drag
  handle, artwork hover transport (volume, play/pause, next, repeat), seek bar, title/artist,
  like/close actions and a resize grip. The main window stays open; playback is the same session.
- Keyboard shortcuts: Ctrl+F search, Ctrl+Space play/pause, Ctrl+Left/Right previous/next,
  Ctrl+Up/Down volume, Ctrl+L like, Esc leaves fullscreen.
- Central `LyricsProviderResolver`: LRCLIB primary, lrc.red and Unison fallbacks, TTML word/line
  parser, wrong-song guards, provider-error containment, and source-aware SQLite lyrics cache.
- lrc.red (keyless, ISRC + TTML) and Unison (exact video id) integrations with visible credit and
  links. Live tests returned 16 word-synced English lines and 44 word-synced Hindi lines.
- Spotify-style playlist detail: tinted full-bleed hero, 216 dp cover, large display title,
  play/shuffle/add/search/sort actions, Add and Name & details pills, and 56 dp rows with artwork,
  title/artist, album, real date-added and duration columns plus hover actions.
- Isolated NEW/EARLY/ESTABLISHED visual-QA profiles and a reliable Win32 focus/capture harness;
  evidence in `docs/VISUAL_QA_0_8.md` and `build/visual-qa/`.

### Changed

- The home no longer relabels arbitrary discovery shelves as "Made for you" or "Your top mixes".
  Personal shelves require real history and are titled from the actual artist.
- `LibraryStore` gained `userActivity()` and `recentHistory()` backed by SQL aggregates.
- The right rail now stays reachable at the 1120dp minimum by shrinking the library first; window and
  miniplayer minimums are DPI-aware; the idle play disc is white; queue labels no longer repeat
  "Next up"; player controls sit next to the track text.
- Queue panel matches the Spotify reference with two underlined tabs, a Now playing block, a
  "Next from: context" block, 40 dp artwork and hover-only actions. Autoplay/save/clear moved into
  the queue overflow menu.
- Player-bar glyphs are 18 dp with brighter `#D6D6D6` resting tint; the volume slider is 100 dp.
- Miniplayer is composed with the artwork loader/theme, floats bottom-right above the taskbar and
  never interrupts playback. Playlist shortcut columns no longer clip labels at 1440 dp.
- Maximize now uses the Windows work area instead of an undecorated `Maximized` placement, so the
  taskbar stays visible; clicking restore returns to the previous floating bounds.
- Removed the download action from playlist detail and the non-functional connect-to-a-device
  action. The player bar uses Spotify's outlined utility set (lyrics, queue, volume, miniplayer,
  fullscreen) at the reference order and weight.
- Removed YouTube/YT Music naming from the interface: source labels read "Online", catalog copy,
  search, settings, errors and track genres are neutral, and the "YT Music" row badge is gone.
- 130-track playlist QA: full scroll to row 130, all five sort modes and row hover verified; see
  `docs/VISUAL_QA_0_8.md`.
- Full desktop suite: **59 tests** (24 suites); one live-network artwork test failed once and passed
  on focused retry.

## [0.7.0] - 2026-11-26

### Added

- Native YouTube Music Up Next through InnerTubeX `next(WEB_REMIX, videoId, playlistId =
  "RDAMVM<videoId>")`, including continuation pages, provider-order preservation and automatic
  advancement into provider rows.
- Manual queue priority that survives native appends: Play Next, Add to Queue, remove, reorder,
  clear, autoplay on/off, save queue as playlist.
- Queue tests for native order, continuation, manual priority, autoplay off, provider failure,
  stale context, auto-advance and history.

- Research and architecture docs: provider matrix, queue engine, dynamic homepage, artwork/metadata
  providers, Canvas limitation and the 0.7 research report.

### Changed

- YouTube Music is the single playback catalog. The retired preview catalog was removed completely
  (source, HTTP calls, models, preview playback state, UI copy, tests and docs).
- JavaFX engine coverage now uses the bundled local MP3 fixture instead of a network preview.
- Full desktop suite: **38 tests, 0 failures**.

### Removed

- The retired preview catalog source, its source enum value, preview state, fallback/merge logic,
  labels, settings rows and licensing copy.

## [Unreleased]

### Added

- Artwork resolution chain: YouTube Music catalog art first, then MusicBrainz release/recording
  matching with 1200 px Cover Art Archive images, then a keyless Deezer `cover_xl` (1000 px)
  fallback. Title and artist must match before a provider cover is accepted.
- High-resolution artwork selection for YouTube (`maxresdefault`, `w1200-h1200`) while palette
  sampling uses a small, bar-free rendition.
- Remote artwork disk cache with a memory tier, rendition fallbacks, corrupt-entry eviction and a
  320-file / 192 MB ceiling; the player theme recolours when fallback artwork replaces a thumbnail.
- Tests for the resolver order, strict matching, high-resolution URLs, Deezer selection and the live
  MusicBrainz/Cover Art Archive path.

### Changed

- Removed the last legacy catalog migration guard and stale documentation references. The only
  playback catalog is YouTube Music; no Apple catalog, preview, search or artwork integration remains.
- Artwork and provider docs now list active sources separately from Canvas, Apple Music motion
  artwork, Tidal and community Canvas, which remain unsupported.
- Full desktop suite: **184 tests, 0 failures (46 suites)** on 27 November 2026.

### Historical (0.4 shell work)

### Added

- Spotify-for-Windows behavioural reference audit with 13 live captures and five documents under
  `docs/spotify-reference/`; no Spotify credentials, tokens or assets are stored.
- Implementation plans for the flexible UI, continuous queue, recommendation engine and phases 1–5.
- Flexible three-container shell: drag-resizable library and right rail, 72 dp collapsed library,
  right-rail close/reopen from the player, animated collapse and a fixed bottom player.
- Persisted `leftPanelWidth` and `rightPanelWidth` SQLite settings with clamping.
- `LayoutSettingsTest` for clamp ranges and settings persistence (28 tests total).

### Changed

- Selecting Queue or Lyrics now persists the reopened right rail instead of only changing memory.
- The centre route is clipped into its own rounded surface; gutters expose an east-west resize cursor.

### Fixed

- Layout widths cannot make the centre collapse below its 480 dp minimum; the right rail yields first.

### Known limitations

- Continuous autoplay, queue refill, recommendation reasons and playback polish are not implemented;
  they are planned in `docs/REVIBE_QUEUE_ENGINE_PLAN.md` and the phase roadmap.
- The 1120 dp minimum and non-100% DPI were not reliably exercised; forced Win32 resize was inconclusive.

## [0.3.0] - 2026-11-26

### Added

- Native desktop playback through **LibVLC 3.0.22 + vlcj 4.12.1**, selected automatically when a
  VLC runtime is found; JavaFX Media remains the fallback.
- Format coverage verified with real fixtures: MP4/AAC, AAC/ADTS, MP3, WAV, FLAC, Ogg/Vorbis,
  WebM/Opus and local HLS, including forward/back seek, pause/resume and end-of-track events.
- `REVIBE_PLAYBACK_ENGINE=vlc|javafx` override and engine name in the Settings platform line.
- YouTube Music **playlist search and details**, mapped to the existing album detail screen.
- **Import to library** for YouTube playlists; tracks are copied into Revibe's SQLite playlist store.
- Real-media LibVLC tests and a live YouTube playback test.
- `ExpiredStreamRecoveryTest` covering the one-time signed-URL refresh after a playback error.

### Changed

- Local FLAC/OGG/Opus/HLS playback is now available when LibVLC is present; the JavaFX fallback
  keeps the previous limited format set.
- Search results now carry a separate playlist collection, so albums and playlists are not conflated.
- Version is 0.3.0.
- Documentation now includes the LibVLC/GStreamer/FFmpeg evaluation, runtime requirements and
  packaging obligations.

### Fixed

- `file:` URIs are normalised to absolute MRLs before being handed to LibVLC.
- VLC plugin discovery uses `VLC_PLUGIN_PATH`, because VLC 3 removed the old `--plugin-path` option.

### Known limitations

- SABR/UMP still requires a dedicated transport implementation; the native engine does not make it
  playable. YouTube extraction continues to request progressive MP4/AAC.
- VLC is not bundled in this repository; without an installed/bundled runtime the app falls back to
  JavaFX. Search pagination, related tracks and a token provider remain future work.

## [0.2.0] - 2026-11-26

### Added

- InnerTubeX v0.13.1 (`com.github.MetrolistGroup.innertubex:innertubex:v0.13.1`, JitPack) as a
  first-class catalog and streaming source.
- `YouTubeMusicSource`: live song/album/artist search, album and artist browse, artwork, track
  mapping, pagination hook, sanitized diagnostics and explicit availability states.
- Real YouTube Music playback: stream extraction, MP4/AAC compatibility filter, signed-URL cache,
  one automatic re-resolve on playback failure.
- `LocalStreamProxy`: loopback range-forwarding `.m4a` endpoint so JavaFX can decode audio-only MP4
  and never sees a signed URL.
- `MusicSourceManager` with YouTube Music priority; `CatalogRepository` routes
  namespaced ids to the owning source.
- On-demand `TrackStreamResolver` in `PlayerController`; queues resolve only the current track.
- Source badges in track rows, source labels on album/artist pages, and live YouTube status in
  Settings.
- Live integration tests for search, mapping, resolution, transport compatibility, queue, LRCLIB
  matching and real JavaFX playback.

### Changed

- Toolchain upgraded to Kotlin 2.4.21, Compose Multiplatform 1.12.2, Ktor 3.6.0, coroutines 1.11.0,
  serialization 1.12.0.
- `MusicSourceId.YOUTUBE_MUSIC` is now `ACTIVE`.
- Project licence is now GPL-3.0 because Revibe links the GPL-3.0 InnerTubeX library. Prior commits
  remain MIT.

### Fixed

- Search cancellation no longer appears as a user-facing “Search failed” error.
- YouTube album header artwork and artist discographies map correctly.
- Namespaced YouTube album/artist ids resolve to the right browse page.

## [0.1.0] - 2026-11-26

- Initial Windows desktop app: preview-catalog search, LRCLIB lyrics, local library, SQLite
  library/playlists/history, JavaFX playback, Compose UI, media keys.
