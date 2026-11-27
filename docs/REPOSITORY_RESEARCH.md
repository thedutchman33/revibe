# Repository Research

Research date: 26 November 2026. Shallow clones under `reference/` (git-ignored). Findings below come
from build files and source trees, not only READMEs.

## Metrolist — `MetrolistGroup/Metrolist`

- Licence: GPL-3.0.
- Modules: `shared`, `listenTogetherProto`, `opusDecoder`, `androidApp`, `desktopApp`, `iosApp`.
- Build: Gradle 9.8.2, JDK 25 toolchain, Compose Multiplatform, Room KMP (KSP), Koin, Ktor, Coil 3,
  DataStore, kotlinx-datetime, Multiplatform Settings.
- Desktop audio: `org.bytedeco:ffmpeg` (JavaCV) with a custom playback pipeline, plus an Opus decoder
  module and an FFmpeg-transcoded audio cache. This is how it gets FLAC/Opus/gapless behaviour.
- Catalog: `com.github.MetrolistGroup.innertubex:innertubex` from JitPack; a local `includeBuild` mode
  exists. It also bundles a `metro-wv2-helper` and `bgutil-ytdlp-pot-provider-rs` sidecar for
  YouTube's WebView/PoT challenges.
- Packaging: Compose `nativeDistributions` with MSI/EXE on Windows, DMG, DEB/RPM/AppImage; heavy
  work on CDS archives, ProGuard, platform native trimming.
- Notable docs: `docs/playback.md`, `audio-processing.md`, `desktop.md`, `listen-together.md`,
  `media-menus.md`, `device-connect.md`, `innertube.md`.
- Reusable ideas: expect/actual playback, Room schema + migrations, media menu integration, a
  `listenTogetherProto` module, sidecar handling for DRM-free content.
- Not reused: any source. GPL-3.0 would relicense Revibe, and its catalog path depends on extracting
  YouTube client keys and defeating bot checks.

## InnerTubeX — `MetrolistGroup/innertubex`

- Licence: GPL-3.0.
- KMP library published via JitPack, with an `api` source area and a `harness` module.
- Gradle 9.8.2; exposes YouTube Music search/browse/stream resolution and models.
- It requires web-client API keys and visitor data; these are not public, stable or licensed for
  third-party clients.
- Decision: **integrated as the primary catalog** (v0.13.1) in later milestones. It now handles
  search, browse, playback resolution and the native `next()` queue. The caveats above still apply:
  endpoints can change, and access is subject to YouTube's terms.

## BitChord — `kushagrasinghx/BitChord`

- Licence: GPL-3.0.
- Modules: `shared`, `sharedUi`, `native`, `app`, `tv`, `wearos`, `backend`, `web`.
- Gradle 8.11.1; JitPack for NewPipeExtractor; Android module included only when an SDK exists
  (`local.properties`/`ANDROID_HOME`), a pattern worth copying for desktop-only machines.
- Focus: native audio engine, local library, source plugins, word-synchronised lyrics, multiple
  lyrics providers, dynamic theming, frosted glass, equalizer, gapless, crossfade, sleep timer,
  listening statistics, Listen Together.
- Reusable ideas: source plugin boundaries, capability flags, lyrics provider fallback ordering,
  audio-info/statistics screens.
- Not reused: source code (GPL-3.0) or native engine binaries.

## VIVI Music — `vivizzz007/vivi-music`

- Licence: GPL-3.0.
- Modules: `app`, `innertube`, `kizzy`, `lastfm`, `shazamkit`, `artistvideo`, `Motionartwork`,
  `jiosaavn`, `spotify`, `lyricsProvider`.
- Gradle 9.3.1; Android-first, NewPipeExtractor option, ShazamKit, Last.fm, Spotify metadata,
  JioSaavn module, multiple lyrics providers (AM-Lyrics, Better Lyrics, YouLyPlus, Unison).
- Reusable ideas: integration isolation per service, lyrics provider registry, motion artwork.
- Not reused: source code, and no unauthorised JioSaavn/Spotify streaming path.

## External API checks performed

| Check | Result |
| --- | --- |
| `api.deezer.com/search?q=...` | `{"data":[],"total":288}` — empty payload from this network/region; not depended on |
| `musicbrainz.org/ws/2/recording` | 503 "server busy" at research time |
| `en.wikipedia.org/api/rest_v1/page/summary/Arijit_Singh` | Real extract + image |
| `lrclib.net/api/get` + `/api/search` | Real plain and synced lyrics; `hasWordSync` field present |
| Spotify public API without token | 401 as expected; OAuth required |

## Licensing conclusion

All four primary references are GPL-3.0. Revibe is an independent implementation: no files, classes,
assets or algorithms copied. Only architectural patterns and public API knowledge were used. If any
GPL code is ever brought in, Revibe must ship under GPL-3.0 with complete corresponding source.
