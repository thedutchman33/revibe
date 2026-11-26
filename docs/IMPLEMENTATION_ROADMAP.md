# Implementation Roadmap

Status vocabulary: **Implemented**, **Partially implemented**, **Mocked** (none in shipped code),
**Not implemented**, **Blocked**.

## Phase 0 — Research and architecture: Implemented

- Cloned and inspected Metrolist, InnerTubeX, BitChord and VIVI Music (GPL-3.0), plus API checks.
- Produced repository research, dependency matrix, licence/compliance, architecture, source,
  playback, lyrics, metadata, UI, schema and build documents.
- Chose the catalog, audio, storage and UI strategies with explicit trade-offs.

## Phase 1 — Windows foundation: Implemented

- KMP module + Compose Desktop app, Gradle wrapper, version catalog.
- Revibe design system, frameless shell, sidebar, top bar, right panel, persistent player.
- SQLite persistence and reactive settings.
- Navigation history, loading/empty/error states.

## Phase 2 — Music integration: Implemented (single authorised source)

- iTunes Search provider behind `CatalogSource`; search, album, artist, discography, artwork.
- Unified track/album/artist models, namespaced ids, storefront selection.
- Wikipedia artist biography enrichment with attribution.
- YouTube Music / InnerTubeX: **Blocked** by provider terms. Not shipped.

## Phase 3 — Playback: Partially implemented

- Implemented: JavaFX engine, preview streaming, local MP3/M4A/AAC/WAV/AIFF, queue, shuffle, repeat,
  seek, volume, error recovery, real progress, history, media keys.
- Not implemented: FLAC/OGG/OPUS playback, gapless, crossfade, equalizer, normalization, audio device
  selection, SMTC overlay, resume position after restart.
- Next: native engine spike (VLCJ vs FFmpeg), then SMTC bridge.

## Phase 4 — Lyrics and advanced player: Partially implemented

- Implemented: LRCLIB, plain/LRC/word-sync parsing, auto-scroll, offset, caching, provider label,
  artwork palette theming.
- Not implemented: translation, romanization, lyrics editor, multi-provider priority UI, audio
  information/statistics screen, equalizer.
- Next: extract a multi-provider lyrics registry when the second reviewed provider lands.

## Phase 5 — Library: Partially implemented

- Implemented: liked songs/albums, followed artists, playlists CRUD + ordering, history, local file
  scanning/tagging/art, offline local playback.
- Not implemented: playlist folders, smart playlists, import/export, downloads of catalog tracks
  (not permitted for preview-only content), duplicate resolution UI.
- Next: playlist import/export (JSON + M3U), backup/restore.

## Phase 6 — Services: Not implemented

Spotify OAuth metadata import, Last.fm, ListenBrainz, Discord presence, SponsorBlock, Cast/DLNA/SONOS,
music recognition, Canvas artwork. Each needs its own terms review and adapter; none is faked.

## Phase 7 — Backend: Not implemented

Listen Together rooms, cloud sync, accounts, remote config. Design constraint: no service keys in the
client; a separate server owns secrets and realtime state. Reference servers were studied, not reused.

## Phase 8 — Other platforms: Not implemented

macOS, Linux, Android, iOS. The shared module and interfaces are ready; each target needs an audio
engine, store implementation and window/shell work.

## Immediate next five tasks

1. Fix the remaining UI contrast/hover details and add keyboard shortcuts.
2. Add native FLAC/OGG/OPUS playback behind `PlaybackEngine`.
3. Add disk cache for remote artwork and prefetch the next queue item's art.
4. Add playlist import/export and a first-run local-library prompt.
5. Wire SMTC on Windows (JNA WinRT projection or sidecar) and verify with the media overlay.

## Explicit non-goals for this milestone

- No YouTube/InnerTube streaming client.
- No Spotify audio streaming.
- No DRM bypass, paid-access circumvention or credential extraction.
- No simulated playback or fabricated metadata.
