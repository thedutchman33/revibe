# Dependency Matrix

## Build toolchain

| Component | Version | Notes |
| --- | --- | --- |
| Gradle | 8.14 (wrapper) | Java 23-capable |
| JDK | 23 runtime, target 21 | JDK 21+ supported |
| Kotlin | 2.4.21 | KMP, serialization and Compose compiler plugins |
| Compose Multiplatform | 1.12.2 | Desktop UI |
| Coroutines | 1.11.0 | Async, StateFlow, Swing dispatcher |
| Ktor | 3.6.0 | InnerTubeX compatibility + app HTTP |
| Kotlinx Serialization | 1.12.0 | JSON |
| Android SDK | Present, unused | No `androidTarget()` in this milestone |

## Runtime dependencies

| Library | Version | Licence | Purpose |
| --- | --- | --- | --- |
| **InnerTubeX** (`com.github.MetrolistGroup.innertubex:innertubex`) | **v0.13.1** | **GPL-3.0** | YouTube Music search, browse, extraction |
| **QuackJS** (`com.github.MetrolistGroup.quackjs:quackjs`) | **0.1.4** (transitive) | **GPL-3.0** | Player cipher backend used by InnerTubeX |
| **JavaCV** (`org.bytedeco:javacv`) | **1.5.14** | **Apache-2.0** (also GPL-2.0+ / GPLv2 + Classpath Exception) | FFmpeg frame grabber/decoder wrapper |
| **JavaCPP** (`org.bytedeco:javacpp`) | **1.5.14** | **Apache-2.0** (also GPL-2.0+ / GPLv2 + Classpath Exception) | JNI/native-library loader |
| **FFmpeg native libraries** (`org.bytedeco:ffmpeg:8.1.2-1.5.14:windows-x86_64`) | **8.1.2** | **LGPL-2.1-or-later** (default Bytedeco build; not the `-gpl` artifact) | Demux, decode, resample; bundled in the app |
| JNA / jna-platform | 5.x | Apache-2.0 / LGPL-2.1 | DPAPI and Windows AppUserModelID helpers |
| Compose Desktop (`compose.desktop.currentOs`) | 1.12.2 | Apache-2.0 | Window, Skia rendering |
| Compose Material 3 | 1.12.2 | Apache-2.0 | Primitives only; visuals are custom |
| Material Icons Extended | 1.12.2 | Apache-2.0 | Generic icon glyphs |
| Kotlinx Coroutines | 1.11.0 | Apache-2.0 | Async/Flow |
| Kotlinx Serialization | 1.12.0 | Apache-2.0 | JSON DTOs |
| Ktor Client (CIO, content negotiation, JSON) | 3.6.0 | Apache-2.0 | HTTP |
| SQLite JDBC (Xerial) | 3.49.1.0 | Apache-2.0 | Local SQLite storage |
| jaudiotagger | 3.0.1 | LGPL-3.0 | Local tags + embedded artwork |
| JNativeHook | 2.2.2 | LGPL-3.0 | Global media keys |
| SLF4J Simple | 2.0.17 | MIT | Logging backend |
| JUnit / kotlin-test | via Kotlin 2.4.21 | EPL/Apache | Tests |

JitPack is configured only for the InnerTubeX and QuackJS groups:

```kotlin
maven("https://jitpack.io") {
    content {
        includeGroup("com.github.MetrolistGroup.innertubex")
        includeGroup("com.github.MetrolistGroup.quackjs")
    }
}
```

## External services

| Service | Endpoint | Auth | Used for | Status |
| --- | --- | --- | --- | --- |
| YouTube Music (InnerTube) | InnerTubeX, `WEB_REMIX` + player | Anonymous session | Search, albums, artists, full AAC streaming, native queue | Active, unstable/ToS-bound |
| Wikipedia REST | `en.wikipedia.org/api/rest_v1/page/summary` | None | Artist biography + image | Active, attributed |
| LRCLIB | `lrclib.net/api/get`, `/api/search` | None | Plain and synced lyrics | Active |
| MusicBrainz | `musicbrainz.org/ws/2` | None (UA required) | Release matching for artwork | Active; 1 request/second |
| Cover Art Archive | `coverartarchive.org` | None | Artwork fallback by MBID | Active |
| Deezer API | `api.deezer.com` | None | 1000 px album artwork fallback | Active |
| Spotify Web API | `api.spotify.com` | OAuth | Playlist/metadata import | Not implemented; no audio streaming grant |
| Spotify Canvas | internal endpoints only | — | Visual loops | Not integrated; no official API |
| Last.fm | `ws.audioscrobbler.com/2.0` | API key | Tags, similar artists, scrobbles | Planned |
| ListenBrainz | `api.listenbrainz.org/1` | Public reads; user token to submit | History, optional scrobbling | Planned; 1 request/second |
| OpenSubsonic | user server | User credentials | Optional self-hosted library | Not implemented |
| Music recognition | ShazamKit / ACRCloud / AudD | Platform SDK / key | Identify audio | Blocked on Windows without a licensed provider |
| Discord | IPC | Client ID | Rich presence | Not implemented |
| Cast / DLNA / SONOS | protocols | — | Device playback | Not implemented |

## Licence consequences

- **InnerTubeX and QuackJS are GPL-3.0.** Because they are linked into Revibe, the combined app is
  distributed under GPL-3.0. The root licence is now GPL-3.0; corresponding source and notices must
  accompany binaries. Revibe's pre-integration code remains MIT at commit `b48101a`.
- **JavaCV and JavaCPP are Apache-2.0** (with GPL alternatives offered by Bytedeco). They are
  compatible with the GPL-3.0 combined work.
- **FFmpeg is LGPL-2.1-or-later** in the default Bytedeco build used here (the dependency is
  `ffmpeg`, not `ffmpeg-gpl`). Dynamically linked native libraries are extracted from the jar at
  runtime. Packaged builds must include the FFmpeg licence/notice and an offer for the corresponding
  source of the exact build.
- jaudiotagger and JNativeHook are LGPL-3.0. They are unmodified libraries; ship notices and allow
  replacement; no static relinking.
- Test media under `shared/src/desktopTest/resources/media` was generated locally with FFmpeg from a
  sine tone; it is not third-party music.
- Metrolist, BitChord and VIVI Music are reference material only. No code was copied from them.

The installer bundles the FFmpeg/JavaCPP Windows x64 native jars, so no separate VLC runtime is
installed. Verify the current InnerTubeX, JavaCV/JavaCPP and FFmpeg licence texts and the exact
Bytedeco FFmpeg build's source offer before distributing.
