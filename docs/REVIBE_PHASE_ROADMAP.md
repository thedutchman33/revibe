# Revibe phase roadmap — desktop transformation

> **Historical.** Written before the native queue and catalog cleanup. Current status lives in
> [IMPLEMENTATION_ROADMAP.md](IMPLEMENTATION_ROADMAP.md); the retired preview catalog no longer
> exists and the queue now uses native YouTube Music rows.

Status: **planning / foundation in progress**. Nothing in phases 1–5 is complete
unless it has implementation evidence and passing checks. This roadmap replaces
optimistic checklists with staged, testable work.

## Current baseline

- Revibe 0.3.0: real LibVLC playback with JavaFX fallback, YouTube Music search/
  albums/artists, local library, LRCLIB lyrics, SQLite library/
  history/playlists/likes, persisted settings.
- Last verified suite before this phase: **26 tests, 0 failures**
  (`./gradlew :shared:desktopTest`).
- Spotify reference audit completed with limitations (session expired, some
  interactive states unverified). See `docs/spotify-reference/`.
- Shell layout foundation: **stage 1 implemented** (resizable/collapsible
  containers, persisted widths, right rail close); evidence in
  `docs/revibe-layout/`. Continuous autoplay is the next prerequisite.

## Phase 0 — Reference, shell and queue prerequisites

### 0.1 Spotify audit and plans

Deliverables:

- `docs/spotify-reference/SPOTIFY_UI_AUDIT.md`
- `SPOTIFY_INTERACTION_INVENTORY.md`
- `SPOTIFY_LAYOUT_MEASUREMENTS.md`
- `SPOTIFY_ANIMATION_AUDIT.md`
- `SPOTIFY_COMPONENT_REFERENCE.md`
- this roadmap and the three implementation plans.

Exit criteria: verified layout measurements, explicit unverified list, no Spotify
credentials/assets, screenshot index.

### 0.2 Flexible three-container shell — stage 1 implemented

Plan: `docs/REVIBE_UI_IMPLEMENTATION_PLAN.md`. Evidence:
`docs/revibe-layout/README.md`.

- persisted left/right widths;
- left collapse to a 72 dp rail;
- right rail close/reopen;
- 8 dp drag handles;
- centre minimum and responsive clamp;
- persistent player.

Exit criteria: existing routes/tests unaffected; width persistence verified;
manual drag/collapse/close checks; no audio change.

### 0.3 Intelligent continuous queue

Plan: `docs/REVIBE_QUEUE_ENGINE_PLAN.md`.

- queue entries with origin/reason;
- manual priority;
- refill before exhaustion;
- autoplay off setting;
- stale-response guards;
- real recommendation provider;
- queue sections in the UI.

Exit criteria: the queue acceptance tests pass with a fake provider; a real
manual session plays 30+ tracks without duplicates or dead ends; failure stops
cleanly.

These are prerequisites for the five phases below, not replacements for them.

## Phase 1 — Native playback polish

Goal: make local/YouTube playback feel finished on Windows without changing the
existing engine selection contract.

Scope:

1. **Gapless / preloading**
   - Pre-resolve the next queue entry only (not the whole queue) while the
     current track is near its end.
   - Queue the resolved stream in the active engine if it supports gapless;
     otherwise pre-resolve and swap quickly. Do not claim true gapless if the
     engine only crossfades or hard-swaps.
2. **Crossfade**
   - Engine capability flag; only expose the control when supported.
   - Persist 0–12 s setting; off by default.
   - Never crossfade between previews or unavailable tracks.
3. **Playback speed**
   - 0.5×–2.0×, persists; reset to 1.0× on app restart unless user opted to keep.
   - Verify pitch behavior with LibVLC; do not claim pitch correction if absent.
4. **Equalizer**
   - Only if LibVLC exposes a reliable band interface in the bundled build;
     otherwise defer and document. No decorative sliders.
5. **Normalization / ReplayGain**
   - Use real file tags where available; optional, off by default.
6. **Fullscreen / now-playing**
   - Existing `nowPlayingExpanded` state becomes a real full-window view with
     artwork, controls, lyrics and queue access; Escape returns.
7. **Lyrics**
   - Current LRCLIB synced/plain lyrics already work; add per-track offset
     persistence, better active-line contrast, and a fallback that never invents
     timings.
8. **Keyboard shortcuts**
   - Audit existing desktop handlers first.
   - Document and test Space, next/previous, seek ±5/10 s, volume, mute, queue,
     lyrics, search focus, Escape.
9. **Windows media integration**
   - Verify SMTC/media keys against the actual engine; do not fake system
     metadata.

Files likely touched:

- `shared/src/commonMain/kotlin/com/revibe/playback/PlaybackEngine.kt`
- `PlayerController.kt`
- `shared/src/desktopMain/kotlin/com/revibe/playback/` (LibVLC/JavaFX engines)
- `shared/src/desktopMain/kotlin/com/revibe/DesktopGraph.kt`
- `shared/src/commonMain/kotlin/com/revibe/ui/AppState.kt`
- `ui/components/Chrome.kt`, `RightPanel.kt`
- new `ui/screens/NowPlayingScreen.kt` if fullscreen is implemented
- `docs/PLAYBACK_ENGINE.md`, `PROJECT_STATUS.md`

Exit criteria: engine capability matrix documented; real tests for seek, pause,
resume, end-of-track, next pre-resolution, and each exposed setting; features not
supported by an engine are hidden, not simulated.

## Phase 2 — Discovery and search

Goal: a real browse/search experience on top of the existing catalog sources.

- Debounced suggestions with real source results (not hardcoded).
- Recent searches persisted locally.
- Result filters that map to actual query capabilities.
- Genre/mood browse from real catalog queries.
- Related artist/album surfaces only where provider data exists.
- Search result actions: play, queue, like, add to playlist.
- Empty/error/offline states with retry.
- No fake "top result" when no result exists.

Files: `ui/screens/SearchScreen.kt`, `ui/screens/HomeScreen.kt`,
`ui/AppState.kt`, `catalog/CatalogRepository.kt`,
`youtube/YouTubeMusicSource.kt`, `ui/components/Chrome.kt`.

Exit criteria: search works for YouTube Music; suggestions are
cancellable; no stale result overwrites a newer query; tests for query mapping
and empty states.

## Phase 3 — Personalization

Goal: make Home and autoplay reflect actual listening.

- History aggregation (play count, completion, skip, recency).
- "Recently played" rail backed by real history.
- Liked/followed affinity shelves generated from real library data.
- Autoplay recommendations improve from the same signals.
- Explanation labels for generated shelves.
- Opt-out/reset listening history in Settings.
- No fake "Daily Mix 1–6" numbering unless generated from a documented local
  algorithm; label as Revibe Mixes.

Files: `library/LibraryRepository.kt`, `data/JdbcLibraryStore.kt`,
`recommendation/RecommendationEngine.kt`, `ui/AppState.kt`,
`ui/screens/HomeScreen.kt`, `ui/components/RightPanel.kt`.

Exit criteria: every personalized shelf is traceable to actual data; clearing
history changes results; cold start remains useful; deterministic tests.

## Phase 4 — Cloud sync architecture (Supabase)

Goal: optional, encrypted-at-transport sync of library/settings/playlists — not
raw audio and not Spotify data.

Proposed architecture:

- Supabase Postgres with Row Level Security; one user row per auth user.
- Tables: `profiles`, `playlists`, `playlist_tracks`, `liked_tracks`,
  `liked_albums`, `followed_artists`, `settings`, `devices`, `sync_state`.
- Auth: Supabase Auth (email OTP/magic link) on desktop via browser or device
  code; never store passwords in the app.
- Local-first: SQLite remains the source of truth; sync writes are queued with
  `updated_at`, `device_id`, and tombstones for deletes.
- Conflict policy: per-entity last-write-wins with device id + revision; playlist
  track order uses explicit position and a server-side revision.
- History/taste data is **opt-in**, aggregated and minimized; no raw listening
  logs by default.
- Secrets: anon key + user JWT only; no service-role key on the client.
- Offline: all features work without sign-in; sync pauses and retries.

Do not implement until phases 1–3 are stable and a threat model is written. A
Supabase project should be provisioned only with explicit user approval.

Files (future): `sync/SyncClient.kt`, `sync/SyncModels.kt`,
`sync/SupabaseSyncStore.kt`, `data/JdbcLibraryStore.kt` migrations, Settings UI,
`docs/CLOUD_SYNC.md`.

Exit criteria: RLS tests, conflict tests, offline/retry tests, sign-out wipes
local tokens, no secret leakage in logs.

## Phase 5 — Advanced features and Windows installer

Potential advanced features, in priority order (only after core polish):

1. **Queue reorder and save-as-playlist** with real persistence.
2. **Sleep timer** (real timer that pauses playback).
3. **Song radio / endless station** built on the real recommendation engine.
4. **Local file metadata editing / ReplayGain scan** if a safe library exists.
5. **Multi-device sync status** from phase 4.
6. **Theme/accent options** that preserve contrast and accessibility.

Windows packaging:

- MSI or EXE installer with per-user install, Start Menu shortcut, uninstaller.
- Bundle a verified JRE and the compatible VLC runtime only if licensing allows;
  otherwise document the prerequisite and offer a guided setup.
- Code signing (certificate decision required); SmartScreen reputation plan.
- Auto-update only with a signed manifest; no silent arbitrary downloads.
- Crash/log location under `%LOCALAPPDATA%\Revibe`, opt-in diagnostics.
- First-run check for audio engine, data folder, and catalog connectivity.

Exit criteria: clean install on a fresh Windows VM, launch, play a local file and
a YouTube Music track, uninstall leaves user data or asks; installer documented.

## Cross-phase quality gates

Every phase must pass:

1. `./gradlew :shared:desktopTest` (or the current full suite) with zero
   failures.
2. No regression in existing playback/catalog/library behavior.
3. No new hardcoded data presented as personalization.
4. No Spotify credentials, tokens, cookies, assets or trademarks.
5. Docs updated with what is implemented vs. what is planned.
6. Manual desktop verification of any pointer/keyboard interaction that unit
   tests cannot cover; report it as manual, not automated.

## Sequencing rationale

- The shell and queue are the highest-impact changes and touch many screens;
  doing them before DSP avoids repeated layout/UI churn.
- Playback polish is engine-specific and benefits from a stable queue API.
- Discovery and personalization depend on real history signals.
- Cloud sync multiplies data-model mistakes; it waits until local models settle.
- Installer work waits until bundled runtime/licensing decisions are clear.

## Explicitly not claimed

- Pixel-perfect Spotify parity.
- Spotify login, playback or library import.
- Gapless/crossfade/EQ/normalization unless the chosen engine supports them and
  tests prove the behavior.
- A recommendation engine trained on Spotify data or using fake audio features.
- Cloud sync before a security review and user opt-in.
- Installer readiness before signing/runtime licensing is resolved.
