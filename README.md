# Revibe

**Your music. Your vibe.**

Revibe is an independent, Windows-first music application built with Kotlin Multiplatform and
Compose Multiplatform. It has a real catalog search, real artwork and metadata, real lyrics, real
audio playback, a persistent queue, playlists, a local music library and a Spotify-inspired desktop
interface.

This is a working application, not a mockup. It is also an early milestone: see
[Status](#status) for exactly what is implemented, partial, blocked or not started.

---

## Run it

Requirements: Windows 10/11, JDK 21+ (this machine uses JDK 23), and a network connection for
catalog search.

```bash
./gradlew :desktopApp:run
```

The Gradle wrapper downloads the toolchain used by the project. Data lives in
`%USERPROFILE%\.revibe` (override with `REVIBE_HOME`).

Build the test suite:

```bash
./gradlew :shared:desktopTest
```

Build an MSI/EXE:

```bash
./gradlew :desktopApp:packageMsi
./gradlew :desktopApp:packageExe
```

## What works

| Area | Status | Notes |
| --- | --- | --- |
| Catalog search (songs, albums, artists) | **Implemented** | iTunes Search API, real results, storefront selection |
| Album, artist and discography pages | **Implemented** | Real metadata and artwork from the catalog |
| Artist biographies | **Implemented** | Wikipedia REST API, attributed CC BY-SA 4.0 |
| Streaming playback | **Implemented** | 30-second catalog previews through JavaFX Media |
| Local library | **Implemented** | MP3/M4A/AAC/WAV/AIFF playback in full; FLAC/OGG/OPUS indexed but not yet playable |
| Artwork + palette theming | **Implemented** | Skia decoding, memory cache, dominant-colour accent |
| Synced lyrics | **Implemented** | LRCLIB, LRC parsing, word-level highlight when available, offset control |
| Queue, shuffle, repeat, seek, volume | **Implemented** | Single queue controller with real engine events |
| Liked songs/albums, followed artists | **Implemented** | SQLite, persisted across restarts |
| Playlists (create/rename/delete/add/remove) | **Implemented** | SQLite with ordering |
| Listening history / recently played | **Implemented** | Last 1,000 plays retained |
| Windows media keys | **Implemented** | Play/pause, next, previous via global hook |
| Windows media overlay (SMTC) | **Partial** | Design and dependency choice documented, not yet implemented |
| YouTube Music / InnerTube | **Blocked** | Not shipped: streaming YouTube's catalog from a third-party client is outside its terms |
| Spotify metadata import | **Not implemented** | Official OAuth import planned; Spotify's API does not grant audio streaming |
| Last.fm / ListenBrainz / Discord | **Not implemented** | Optional integrations, on the roadmap |
| Equalizer / crossfade / gapless | **Not implemented** | Needs a native audio engine |
| Listen Together / casting / recognition | **Not implemented** | Separate backend and SDK work |

## Design

The interface is an original design. It uses the interaction model of a modern music player
(sidebar library, shelf-based home, contextual right panel, persistent player) without copying
Spotify assets, code or branding.

- Near-black layered surfaces with a lime signal colour.
- Artwork-driven accent gradients.
- Custom seek bars, chips, cards and window chrome.
- No Material 3 defaults used as the visual language.

## Architecture

```
shared/       domain, catalog, lyrics, playback controller, persistence contract, Compose UI
desktopApp/   desktop entry point, window chrome, platform wiring
```

The business logic is platform-neutral. Desktop implementations live in
`shared/src/desktopMain` behind interfaces so Android/iOS targets can be added without rewriting the
UI or repositories. See [docs/REVIBE_ARCHITECTURE.md](docs/REVIBE_ARCHITECTURE.md).

## Legal

Revibe is not affiliated with Spotify, Apple, YouTube or any other service. No proprietary Spotify
assets, binaries or source code are used. Reference projects studied during design (Metrolist,
BitChord, VIVI Music, InnerTubeX) are GPL-3.0; no source code from them is included in this build.

Catalog metadata and previews come from Apple's public iTunes Search API under its terms. Lyrics come
from LRCLIB. Biographies come from Wikipedia under CC BY-SA 4.0 and are attributed in the interface.
No DRM or access control is bypassed.

Revibe's own source is released under the MIT License. See [LICENSE](LICENSE) and
[docs/LICENSE_AND_COMPLIANCE.md](docs/LICENSE_AND_COMPLIANCE.md).
