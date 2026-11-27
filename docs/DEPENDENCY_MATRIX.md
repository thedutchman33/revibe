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
| **vlcj** (`uk.co.caprica:vlcj`) | **4.12.1** | **GPL-3.0** | Java binding for the native LibVLC engine |
| JNA (transitive via vlcj) | 5.x | Apache-2.0 / LGPL-2.1 | Native library binding used by vlcj |
| **LibVLC runtime** (VLC 3.0.22 during verification) | 3.0.22 | **LGPL-2.1+** (VLC application/plugins GPL-2.0+) | Native decoder/player; not bundled in the repository |
| Compose Desktop (`compose.desktop.currentOs`) | 1.12.2 | Apache-2.0 | Window, Skia rendering |
| Compose Material 3 | 1.12.2 | Apache-2.0 | Primitives only; visuals are custom |
| Material Icons Extended | 1.12.2 | Apache-2.0 | Generic icon glyphs |
| Kotlinx Coroutines | 1.11.0 | Apache-2.0 | Async/Flow |
| Kotlinx Serialization | 1.12.0 | Apache-2.0 | JSON DTOs |
| Ktor Client (CIO, content negotiation, JSON) | 3.6.0 | Apache-2.0 | HTTP |
| SQLite JDBC (Xerial) | 3.49.1.0 | Apache-2.0 | Local SQLite storage |
| jaudiotagger | 3.0.1 | LGPL-3.0 | Local tags + embedded artwork |
| JavaFX Media / Graphics / Base | 23.0.2 (win) | GPL-2.0 + Classpath Exception | Fallback audio decoding/playback |
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
- **vlcj is GPL-3.0**, compatible with the combined work. **libVLC is LGPL-2.1+**; the VLC
  application and plugin distribution is GPL-2.0+. If VLC is bundled, include its `COPYING`,
  `AUTHORS` and source/offer terms for the exact build.
- JavaFX is GPL-2.0 **with the Classpath Exception**, which permits linking; the overall app is GPL
  anyway because of InnerTubeX and vlcj.
- jaudiotagger and JNativeHook are LGPL-3.0. They are unmodified libraries; ship notices and allow
  replacement; no static relinking.
- Test media under `shared/src/desktopTest/resources/media` was generated locally with FFmpeg from a
  sine tone; it is not third-party music.
- Metrolist, BitChord and VIVI Music are reference material only. No code was copied from them.

VLC is not bundled by this repository. A packaged build must either require the user to install VLC
or ship a compatible runtime, and must include the corresponding notices. Verify current licence
texts, the InnerTubeX/vlcj releases and the VLC build's source offer before distributing.
