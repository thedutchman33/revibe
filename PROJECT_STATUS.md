# Project Status

Milestone: **0.9.0 — cloud accounts, multi-device sync and automatic updates (client)**
Date: 2026-11-27
Platform: Windows 10/11, desktop JVM

## Summary

Revibe plays real YouTube Music through InnerTubeX, now including the **native Up Next queue** with
continuation, and no longer contains the retired preview catalog. The maturity-driven home with a
recent-playlist grid built from real plays, right sidebar, queue, miniplayer and keyboard shortcuts
are working.
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
| Automatic updates | **Implemented (unverified installer)** | HTTPS manifest, SemVer policy, SHA-256, Authenticode pin, separate MSI handoff, release workflow; real signed upgrade pending |
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
| Native playback | **Implemented** | LibVLC + JavaFX fallback, loopback proxy |

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
- `./gradlew :shared:desktopTest` — **181 tests, 0 failures** (46 suites), including update and
  artwork-provider coverage. Live Cover Art Archive and YouTube checks passed.
- Standalone per-user EXE: built with Inno Setup 6, installed without admin, launched with PATH
  reduced to Windows system directories and no Java variables, then uninstalled cleanly. Playback,
  Google sign-in, settings and a synced playlist were exercised in the installed copy.
- New account/sync coverage: `JdbcSyncStoreTest` (8), `AuthApiTest` (6), `CloudSyncApiTest` (5),
  `SyncEngineTwoDeviceTest` (6), `DpapiAuthSessionStoreTest` (1) — **26 passing**.
- Automatic-update coverage: SemVer, manifest parsing, optional/mandatory policy, skip/remind,
  progress, interrupted download, SHA-256 mismatch, Authenticode rejection, installer failure and
  success recovery, database preservation and unpackaged-build refusal. A real signed MSI upgrade
  and relaunch remain pending.
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
  `docs/VISUAL_QA_0_8.md`. JavaFX local-media decode test passes.

## Known limitations

- Anonymous InnerTube session, so radio personalisation is not identical to a signed-in YouTube
  Music account.
- Some provider rows may be region-restricted or need authentication and fail at resolve time.
- SABR/UMP is not playable; Revibe requests MP4/AAC.
- WebM/Opus/HLS YouTube variants are not selected.
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
- Long-session and full accessibility testing were not run. Installer coverage is local plus the
  clean Windows runner workflow; a physical second machine has not been used.
- Automatic update installation has not been exercised with a real signed MSI, GitHub release,
  Windows Installer elevation or packaged relaunch. Unit tests use a fake downloader/installer and do
  not claim the real handoff works. MSIX/App Installer is documented only as a future path.

## Next

1. ListenBrainz opt-in submission (user token) and Last.fm once an API key is available.
2. Search pagination, suggestions, history and accessibility pass.
3. Apply the Supabase migration and run the real two-device acceptance flow once project URL, anon
   key and redirects are supplied.
