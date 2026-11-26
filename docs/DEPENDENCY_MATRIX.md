# Dependency Matrix

## Build toolchain

| Component | Version | Notes |
| --- | --- | --- |
| Gradle | 8.14 (wrapper) | Java 23-capable |
| JDK | 23 (runtime), target 21 | Installed on this machine |
| Kotlin | 2.1.21 | KMP + serialization + Compose compiler plugin |
| Compose Multiplatform | 1.8.2 | Desktop UI |
| Android SDK | Present, unused in milestone 1 | Needed only when `androidTarget()` is added |

## Runtime dependencies

| Library | Version | License | Purpose |
| --- | --- | --- | --- |
| Compose Desktop (`compose.desktop.currentOs`) | 1.8.2 | Apache-2.0 | Window, Skia rendering |
| Compose Material 3 | 1.8.2 | Apache-2.0 | Primitives only (MaterialTheme + Text/Icon); visuals are custom |
| Material Icons Extended | 1.8.2 | Apache-2.0 | Generic icon glyphs |
| Kotlinx Coroutines | 1.10.2 | Apache-2.0 | Async, StateFlow, Swing dispatcher |
| Kotlinx Serialization | 1.8.1 | Apache-2.0 | JSON DTOs |
| Ktor Client (CIO, content negotiation, JSON) | 3.1.3 | Apache-2.0 | HTTP |
| SQLite JDBC (Xerial) | 3.49.1.0 | Apache-2.0 | Local SQLite storage |
| jaudiotagger | 3.0.1 | LGPL-3.0 | Local tags + embedded artwork |
| JavaFX Media / Graphics / Base | 23.0.2 (win) | GPL-2.0 + Classpath Exception | Audio decoding/playback |
| JNativeHook | 2.2.2 | LGPL-3.0 | Global media keys |
| SLF4J Simple | 2.0.17 | MIT | Logging backend for libraries |
| JUnit / kotlin-test | via Kotlin 2.1.21 | EPL/Apache | Tests |

No Spotify SDK, no InnerTubeX, no NewPipeExtractor, no FFmpeg/JavaCV in this build.

## External services

| Service | Endpoint | Auth | Used for | Status |
| --- | --- | --- | --- | --- |
| iTunes Search API | `itunes.apple.com/search`, `/lookup` | None | Search, albums, artists, artwork, 30s previews | Active |
| Wikipedia REST | `en.wikipedia.org/api/rest_v1/page/summary` | None | Artist biography + image | Active, attributed |
| LRCLIB | `lrclib.net/api/get`, `/api/search` | None | Plain and synced lyrics | Active |
| MusicBrainz | `musicbrainz.org/ws/2` | None (UA required) | Enrichment | Not integrated; returned 503 during research |
| Cover Art Archive | `coverartarchive.org` | None | Artwork fallback | Not integrated |
| Deezer API | `api.deezer.com` | None | Alternative catalog | Not integrated; returned empty data from this region |
| YouTube InnerTube | internal | Scraped web key | Full catalog streaming | **Blocked: terms of service** |
| Spotify Web API | `api.spotify.com` | OAuth | Playlist/metadata import | Not implemented; no audio streaming grant |
| Last.fm / ListenBrainz | respective | API key / token | Scrobbling | Not implemented |
| Discord | IPC | Client ID | Rich presence | Not implemented |
| ShazamKit / recognition | SDK | Platform | Music recognition | Not implemented |
| Cast / DLNA / SONOS | protocols | — | Device playback | Not implemented |

## Licence consequences

- JavaFX is GPL-2.0 **with the Classpath Exception**, so linking it into Revibe does not force Revibe
  to be GPL. The exception is the reason this engine is acceptable for a non-GPL app.
- jaudiotagger and JNativeHook are LGPL-3.0. They are used as unmodified libraries; ship their
  notices and allow replacement. No static relinking.
- Metrolist, BitChord, VIVI Music and InnerTubeX are GPL-3.0. They are reference material only; no
  code is copied into Revibe. Copying would require Revibe to be distributed under GPL-3.0.

Verify current licence texts before distributing a packaged build.
