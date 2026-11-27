# Licensing and Compliance

## Revibe's licence: GPL-3.0

Revibe now **links InnerTubeX**, which is licensed under GPL-3.0. The combined application is
therefore distributed under **GNU GPL v3.0** (see the root `LICENSE`). This is a deliberate consequence
of the integration request, not an accident.

- Revibe's own source may be used under GPL-3.0.
- Revibe's own code was MIT-licensed through checkpoint commit `b48101a`; that snapshot remains
  available under MIT. The new combined distribution is GPL-3.0.
- InnerTubeX is consumed as an unmodified JitPack dependency; no InnerTubeX source files were copied.
- QuackJS is a transitive InnerTubeX dependency, also GPL-3.0.

**Distribution obligations:** publish the complete corresponding source for the GPL-covered work,
include the full licence text and third-party notices, and do not add restrictions that forbid
redistribution or modification. If Revibe is shipped through a store, confirm that the store's terms
are compatible with GPL-3.0. Distributing only a proprietary binary without source would violate the
licence.

## Dependency licences

| Component | Licence | How it is used |
| --- | --- | --- |
| InnerTubeX v0.13.1 | GPL-3.0 | Linked library, resolved from JitPack, unmodified |
| QuackJS 0.1.4 | GPL-3.0 | Transitive hosted player-cipher backend; no JNI or browser engine |
| JavaCV 1.5.14 | Apache-2.0 (also GPL-2.0+ / GPLv2 + Classpath Exception) | FFmpeg decode wrapper; unmodified |
| JavaCPP 1.5.14 | Apache-2.0 (also GPL-2.0+ / GPLv2 + Classpath Exception) | Native-library loader; unmodified |
| FFmpeg 8.1.2 native libraries | LGPL-2.1-or-later (default Bytedeco build, not `-gpl`) | Bundled Windows x64 demux/decode/resample DLLs; dynamically loaded |
| JNA / jna-platform | Apache-2.0 / LGPL-2.1 | DPAPI and Windows AppUserModelID helpers |
| jaudiotagger 3.0.1 | LGPL-3.0 | Unmodified local-tag reader; notice + relink ability |
| JNativeHook 2.2.2 | LGPL-3.0 | Unmodified global media-key hook; notice + relink ability |
| SQLite JDBC (Xerial) | Apache-2.0 | Notice |
| Kotlin, Coroutines, Serialization, Ktor, Compose | Apache-2.0 | Notice |
| Inter typeface | SIL OFL 1.1 | Bundled UI font; full licence in `licenses/Inter-OFL.txt` |
| SLF4J Simple | MIT | Notice |

A packaged build must ship the GPL-3.0 text, the licences above and an offer of corresponding source.
JavaCV and JavaCPP are used under Apache-2.0; they do not change the overall GPL-3.0 licence, which
comes from InnerTubeX and QuackJS. The bundled FFmpeg build is LGPL-2.1-or-later, so include its
licence/notice, identify the exact Bytedeco FFmpeg version/build, and provide the corresponding
source offer. No VLC runtime is bundled or required.

## Reference projects

| Project | Licence | Use |
| --- | --- | --- |
| Metrolist | GPL-3.0 | Architecture and integration reference only; no code copied |
| InnerTubeX | GPL-3.0 | **Actual dependency** |
| QuackJS | GPL-3.0 | **Transitive dependency** |
| BitChord | GPL-3.0 | UX/architecture study only |
| VIVI Music | GPL-3.0 | Integration study only |
| metroserver / VIVI-SERVER | GPL-3.0 (as published) | Not used |
| VideoLAN VLC / libVLC | GPL-2.0+ / LGPL-2.1+ | Not used after the FFmpeg migration |

Architectural patterns and public API usage are not copyrightable expression. No Metrolist source was
copied into Revibe; the Android player implementation was studied, then the Windows path was designed
around a native engine boundary, with signed streams opened directly by FFmpeg using the
resolver's request headers.

## Spotify visual reference

Spotify's Circular typeface, icon set and logo are proprietary and are not included. The player bar
matches Spotify's public layout metrics (heights, spacing, type sizes and opacity) but ships Inter
under the SIL OFL 1.1 (`licenses/Inter-OFL.txt`) and Material Icons as open substitutes.

The installer bundles the FFmpeg/JavaCPP native jars. Verify the current InnerTubeX, JavaCV/JavaCPP
and FFmpeg licence texts and the Bytedeco FFmpeg build's corresponding-source offer before
distribution.

## Service terms

### YouTube Music / InnerTube

**Implemented.** Revibe uses InnerTubeX against YouTube Music's web (`WEB_REMIX`) and player endpoints.
This relies on non-public, unstable interfaces and is subject to YouTube's Terms of Service. The app:

- does not bypass DRM or paid access;
- does not harvest cookies, OAuth tokens or credentials from other applications;
- does not implement unauthorized access-control circumvention;
- uses anonymous sessions only;
- does not download or persist media bytes.

YouTube may change endpoints, rate-limit, region-block or require authentication at any time. Those
conditions are surfaced as explicit statuses. **Review the current YouTube Terms before distributing
or commercialising Revibe.** Revibe is not affiliated with or endorsed by YouTube or Google.

### Native playback and SABR

FFmpeg is a general media engine. It does not implement YouTube's SABR/UMP session transport, and
Revibe does not bypass DRM, paid access or authentication to manufacture one. YouTube extraction
continues to request progressive MP4/AAC and reports unsupported transport honestly. Local
FLAC/OGG/Opus support is independent of YouTube authentication.

### Token provider

InnerTubeX exposes an injectable token-provider interface for authenticated requests. Revibe does not
wire one and does not extract credentials. A future integration must use credentials the user
legitimately supplies (for example, a documented OAuth device flow) and must never read another app's
cookie jar or private keys. Without a provider, age-restricted tracks return
`AUTHENTICATION_REQUIRED`; that is the intended honest behaviour.

### LRCLIB

Open, keyless lyrics API. The provider is attributed in the UI. Lyrics remain the property of their
rights-holders; Revibe displays them for personal playback rather than redistributing a lyrics corpus.

### Wikipedia

Biographies and images are CC BY-SA 4.0. The UI shows “Wikipedia, CC BY-SA 4.0”. Downstream reuse must
preserve attribution and share-alike for the text.

### Spotify

**Not implemented.** The official Web API provides metadata and playlist access through OAuth, not
audio streaming. Spotify audio cannot be played through a third-party app without an agreement. No
Spotify binary was decompiled, disassembled, extracted or modified. The supplied `.exe` and `.apk`
were treated as visual reference only.

### JioSaavn, Musixmatch, Genius, KuGou

No integration. These services either require keys, prohibit client access, or need legal review.
They are named in the roadmap, not silently scraped.

## Privacy

- No account, no telemetry, no analytics, no crash upload.
- Search terms are sent to YouTube Music; track/artist names go to LRCLIB and Wikipedia when
  lyrics/biography are requested. This should be shown in-app before release.
- Signed stream URLs and request headers stay inside the desktop process; the UI and logs receive
  sanitized diagnostics only.
- Library data, history and playlists stay in local SQLite.
- Media keys are captured globally only while Revibe is running.
- No service-role keys or private credentials exist in the client; there is no backend yet.

## Prohibited behaviours (enforced by the design)

- No DRM or paid-access circumvention.
- No credential, cookie or key extraction from proprietary clients.
- No copying of proprietary artwork or Spotify assets.
- No fabricated metadata, lyrics or “synthetic” playback state.
- No silent claim that a stream format is playable when the engine cannot decode it.
