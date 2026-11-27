# Project Status

Milestone: **1.0.0 — Release**
Date: 2026-11-28
Platform: Windows 10/11, desktop JVM

## Summary

Revibe plays real YouTube Music through InnerTubeX, now including the **native Up Next queue** with
continuation, and no longer contains the retired preview catalog. Windows playback is a single
in-process FFmpeg engine: the signed stream is opened directly with extraction headers, decoded at
its native rate/channels to PCM, and written to Java Sound. The installed Windows application was
driven through play, pause/resume, multiple seeks, next/previous, rapid switching, end-of-track and a
sustained session. The maturity-driven home with a recent-playlist grid built from real plays, right
sidebar, queue, miniplayer and keyboard shortcuts are working.
MusicBrainz/Cover Art Archive artwork fallback is live. Lyrics now resolve through LRCLIB, lrc.red
and Unison; the first two returned real English/Hindi word-synced results. Supabase accounts and
multi-device sync are implemented on the client with a versioned SQL/RLS migration; the live
Supabase project run is still pending. Last.fm, ListenBrainz, Spotify import, Canvas and recognition
remain planned.

| Area | State | Detail |
| --- | --- | --- |
| YouTube Music search/browse | **Implemented** | Songs, albums, artists, playlists via InnerTubeX v0.13.1 |
| Native queue + autoplay | **Implemented** | `next()` + continuation, provider order preserved |
| Manual queue | **Implemented** | Play Next, Add to Queue, remove, reorder, clear, autoplay toggle |
| Spotify-style home | **Implemented** | Maturity-driven sections and a 4×2 recent-playlist grid from real plays |
| Homepage maturity system | **Implemented (first pass)** | NEW/EARLY/ACTIVE/ESTABLISHED from SQLite signals |
| Right sidebar | **Implemented** | Now Playing, lyrics preview, About, credits, queue, recently played |
| Sidebar drag-and-drop / timestamps | **Implemented** | Reorder plus real "played 2h ago" labels |
| Provider enrichment | **Partially implemented** | YouTube primary; MusicBrainz/CAA + Deezer artwork; lrc.red and Unison lyrics; Last.fm/ListenBrainz need keys |
| Spotify Canvas | **Not integrated** | No official API; undocumented access refused |
| Music recognition | **Blocked** | No licensed Windows-capable provider selected |
| Supabase accounts/sync | **Implemented (client)** | GoTrue/PostgREST over Ktor, DPAPI session, RLS migration, outbox/tombstones, background pull/push; live project E2E pending |
| Account/sync UI | **Implemented** | Sign-in/create, confirmation, profile/avatar, sync status, last sync, Sync Now, offline/error and logout |
| Search history | **Implemented** | Persisted locally; cloud upload opt-in, off by default |
| Shared homepage activity | **Implemented** | Pulled history/likes/follows feed the existing maturity model |
| Automatic updates | **Implemented** | Push-to-main releases, HTTPS manifest, SHA-256, optional Authenticode pin, silent Inno EXE handoff, CI-verified upgrade from the previous release |
| Settings → About Revibe | **Implemented** | Installed version, check button, status, notes, progress, retry, skip/remind |
| Miniplayer | **Implemented** | Floating 400×336 dp Spotify-style card: drag, hover transport, seek, like, resize; main window stays open |
| Playlist detail | **Spotify-style redesign** | Tinted hero, date-added/duration table, hover actions, search and sort; no download action |
| Queue rail | **Spotify-style redesign** | Two-tab header, Now playing / Next from blocks, hover actions |
| Window controls | **Fixed** | Maximize respects the Windows work area; restore returns to previous bounds |
| Branding | **Neutral UI** | YouTube/YT Music naming removed from screens, errors and labels |
| Keyboard shortcuts | **Implemented** | Search, play/pause, next/previous, volume, like, Esc |
| Device selection, notifications, friends | **Not implemented** | Planned Windows pass |
| Local library | **Implemented** | Tags, embedded art, scan, full playback |
| Lyrics | **Implemented** | LRCLIB primary; lrc.red + Unison fallback; TTML/LRC; SQLite cache |
| SQLite library | **Implemented** | Likes, albums, artists, playlists, history, settings, search history, sync outbox, playlist recency (schema v3) |
| Native playback | **Implemented** | FFmpeg 8.1.2 + JavaCV 1.5.14 → native-rate PCM → Java Sound; direct signed URL + headers; bundled Windows x64 natives |

## Native queue acceptance

| Criterion | State | Evidence |
| --- | --- | --- |
| InnerTubeX `next()` used | **Yes** | `YouTubeMusicSource.nativeQueue` |
| Continuation used | **Yes** | `queueContinuation()` + `nativeContinuation` state |
| Provider order preserved | **Yes** | `QueueEngineTest` native-order test |
| No custom ranking | **Yes** | `RecommendationEngine`/`CatalogRecommendationProvider` deleted |
| Manual Play Next priority | **Yes** | Manual-priority test |
| Autoplay off stops refill | **Yes** | Autoplay-off test |
| Failure keeps manual playback | **Yes** | Failure test |
| Stale context ignored | **Yes** | Stale-context test |
| Live 40-row page + continuation | **Yes** | `REVIBE_LIVE=1 … YouTubeRelatedLiveTest` |

## Checks run

- `./gradlew :desktopApp:compileKotlinDesktop` — successful.
- `./gradlew :shared:desktopTest -PrevibeLiveTests=false` — **197 tests, 0 failures**. The
  MusicBrainz/CAA path is covered by deterministic MockEngine fixtures; the real external check is
  a separate opt-in test. All playback, queue, account, artwork and update tests passed.
- `FfmpegPlaybackEngineTest` — 13 deterministic checks, including real MP4/AAC, AAC/ADTS, MP3,
  WAV, FLAC, Ogg/Vorbis, WebM/Opus, forward/backward and MP4 seek, restart-to-zero, pause/resume,
  end-of-track, near-end seek, prefetched seek, rapid switching, long session, missing file and a
  live YouTube Music stream.
- `FreeAudioQualityProbe` (live) — six real tracks all selected **itag 251**, `audio/webm`, `opus`,
  146–170 kbps, 48 kHz stereo after decode.
- `PreloadDurabilityProbe` (live) — preload-to-next audio **24 ms**; seek to 45 s after the swap
  landed at **45.014 s in ~1.1 s**. Cold control also landed within 14 ms.
- `LiveSeekMatrixProbe` (live) — 10 s/45 s/90 s/25 s/150 s/0 s seeks landed within 14 ms in
  **0.6–1.7 s** (one first-seek outlier 2.9 s); pause froze and resume advanced.
- Installed Windows verification: a unique-AppId per-user installer was installed into a clean
  directory and launched with a fresh `REVIBE_HOME`. Real InnerTubeX playback passed pause/resume,
  seeks at 25/46/76/86/95% plus a backward seek (decoder positions within 14 ms, 0.02–2.9 s), next,
  previous, five rapid Next clicks, artwork/lyrics loading and sustained playback. Streams were
  **itag 251 Opus, 48 kHz stereo** (installed log: 158,237 bps, 48,000 Hz, 2 channels). The
  restart-to-zero test first landed at 20.014 s, then the reopen retry landed at 14 ms in ~0.7 s,
  reproducing and fixing the exact installed failure.
- Standalone per-user EXE `desktopApp/build/installer/Revibe-1.0.0-Setup.exe`: **148,282,573 bytes**,
  SHA-256 `52f49a1779a2d91a7fecd935936e5ffe311fd4f97554d032dc7c8b05c02e44bf`.
- Release 1.0.0 installed cleanly into an isolated per-user directory (unique AppId, leaving the
  existing 0.11.1 install untouched). The installed `Revibe.exe`, registry entry and About screen
  all report **1.0.0**. The Google mark and Spotify import glyph were verified upright in normal
  and hover states at 1440×900 logical size; hover changes only the button background.
- New account/sync coverage: `JdbcSyncStoreTest` (8), `AuthApiTest` (6), `CloudSyncApiTest` (5),
  `SyncEngineTwoDeviceTest` (6), `DpapiAuthSessionStoreTest` (1) — **26 passing**.
- Automatic-update coverage: SemVer, manifest parsing, optional/mandatory policy, skip/remind,
  progress, interrupted download, SHA-256 mismatch, Authenticode rejection, unsigned-mode opt-in,
  EXE URL handling, installer failure and success recovery, database preservation and unpackaged-build
  refusal. The release workflow additionally upgrades the previous GitHub release with the actual Inno
  EXE through the production handoff helper and verifies the registry version, install directory, data
  and relaunch.
- Visual QA in the live app: account-not-configured card, sign-in form, create-account form and the
  profile-menu Account & sync entry at 1440 dp. Captures in `build/visual-qa/account-*.png`.
- Update visual QA at 1440 dp: disabled/configured About section and unreachable-server error with
  retry in `build/visual-qa/update-settings-about.png` and `update-final-error.png`. Available-update
  banner and signed-installer progress were not visually exercised.
- The SQL migration has **not** been executed against Postgres/Supabase, so RLS, triggers and RPCs
  remain unverified in a real database.
- Live native queue: **40 rows + continuation**, continuation page 40 unique tracks (recorded in
  `docs/QUEUE_ENGINE.md`).
- Live artwork lookup returned a real Cover Art Archive URL for Daft Punk — Random Access Memories.
- Live lyrics: Unison returned 16 word-synced English lines; lrc.red returned 44 word-synced Hindi
  lines for *Kesariya*.
- Visual QA at 1120/1200/1440 dp on 125% DPI with isolated NEW/EARLY/ESTABLISHED profiles:
  maturity homepages, 460×158 dp miniplayer, Spotify-style playlist, queue/recently-played rail,
  player bar, maximize/restore and the 1120 dp compact layout captured; see
  `docs/VISUAL_QA_0_8.md`. The FFmpeg local-media decode test passes.

## Known limitations

- Anonymous InnerTube session, so radio personalisation is not identical to a signed-in YouTube
  Music account.
- Direct signed-stream seeking is accurate on tested tracks and timestamps (observed within 14 ms).
  If every exact/nearby range candidate is rejected by a stream, the engine can still surface an
  error rather than an exact position; there is no guarantee for arbitrary googlevideo offsets.
- Audio quality is selected automatically for free accounts; there is no user-facing quality
  selector. The top free stream (usually itag 251 Opus) stays fixed for the track until its signed
  URL expires.
- The public MusicBrainz service returns HTTP 503 when busy or rate limited, so its live test is
  opt-in. Deterministic fixtures cover the provider and resolver; artwork resolution is unchanged.
- Some provider rows may be region-restricted or need authentication and fail at resolve time.
- SABR/UMP and HLS are not playable; Revibe requests direct audio-only streams.
- Archived rows remain visible until the user removes them; they are clearly labelled.
- Homepage ordering is a first pass: no returning-user boost and no genre-derived mixes yet.
- Static artwork uses YouTube Music → MusicBrainz/Cover Art Archive → Deezer. The Deezer anonymous search returned no items from this network, so it is unit-tested but not live-verified. Canvas, Apple Music motion artwork, Tidal and community Canvas remain unsupported.
- lrc.red requires a distributor contact address for its User-Agent; set `REVIBE_CONTACT_EMAIL` before shipping.
- Cloud sync has not been exercised against a real Supabase project: GoTrue email delivery,
  real RLS enforcement, migration application and two physical/real-device runs remain pending.
- Simultaneous offline edits to the **same playlist** resolve at whole-playlist last-writer-wins
  granularity; track additions are not line-merged.
- Password reset sends the Supabase recovery email; the new password is completed on the hosted
  Supabase page, not inside Revibe.
- Profile edits are immediate requests rather than queued offline changes.
- No Realtime subscription; incremental sync polls in the background every 60 seconds.
- No Canvas, recognition, device selection or notifications.
- Spotify Circular and its icon font are proprietary; Inter (SIL OFL) and Material Rounded are
  used with matched sizes, weights, spacing and opacity.
- Long playlist scrolling, cross-playlist drag-and-drop and full accessibility traversal were not
  exhaustively tested.
- Long-session and full accessibility testing were not run. The deterministic long-session FFmpeg
  test covers repeated loads/seeks, not multi-hour memory/CPU behaviour. Installer coverage is local
  plus GitHub Actions Windows runners; a physical second machine has not been used.
- Supabase URL and publishable key are still blank in `gradle.properties`, so the packaged 1.0.0 EXE
  will show the not-configured state unless the release secrets or environment variables are set.
- The EXE is not Authenticode signed. Release builds explicitly opt in to SHA-256-only verification;
  a code-signing certificate and publisher pin remain pending. `v0.9.0` must be replaced by the first
  EXE release manually because its updater only accepted MSI. MSIX/App Installer is a future path.

## Next

1. ListenBrainz opt-in submission (user token) and Last.fm once an API key is available.
2. Search pagination, suggestions, history and accessibility pass.
3. Apply the Supabase migration and run the real two-device acceptance flow once project URL, anon
   key and redirects are supplied.
