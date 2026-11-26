# Licensing and Compliance

## Revibe's own licence

Revibe's source is MIT-licensed (see `LICENSE`). The code was written for this project. No source
files, classes, assets or branding were copied from the reference applications.

## Reference projects

| Project | Licence | Use |
| --- | --- | --- |
| Metrolist | GPL-3.0 | Architecture study only |
| InnerTubeX | GPL-3.0 | API study only; **not a dependency** |
| BitChord | GPL-3.0 | Architecture/UX study only |
| VIVI Music | GPL-3.0 | Integration study only |
| metroserver / quackjs / VIVI-SERVER | GPL-3.0 (as published) | Not used |

**Consequence:** if any GPL-3.0 code were copied into Revibe, Revibe would have to be distributed
under GPL-3.0 with complete corresponding source and notices. To keep Revibe independently licensable,
the implementation was written from public API documentation and observation only. Architectural
patterns (interfaces, module boundaries, provider fallbacks) are not copyrightable expression, but
close paraphrasing of code would be; none was done.

If a future decision brings in GPL code, the project must either relicense to GPL-3.0 or replace that
component. This must be decided before distribution, not after.

## Runtime dependencies

| Dependency | Licence | Obligation |
| --- | --- | --- |
| JavaFX (Media/Graphics/Base) | GPL-2.0 **with Classpath Exception** | Linking does not relicense Revibe; keep notices; the exception must be preserved |
| jaudiotagger | LGPL-3.0 | Unmodified library; provide notice and ability to replace |
| JNativeHook | LGPL-3.0 | Same |
| SQLite JDBC (Xerial) | Apache-2.0 | Notice |
| Ktor, Kotlin, Coroutines, Serialization, Compose | Apache-2.0 | Notice |
| SLF4J Simple | MIT | Notice |

A packaged release should include a third-party notices file with the full texts. The Classpath
Exception is essential: it is what makes the JavaFX engine compatible with a non-GPL application.

## Service terms

### iTunes Search API
Used for search, metadata, artwork and 30-second previews. No authentication, no bulk downloading, no
circumvention. Apple's terms restrict commercial use and require that content not be presented as
Apple-endorsed. A commercial distribution must review the current terms and may need an affiliate or
licensing arrangement. Previews are streamed, not stored as a library.

### LRCLIB
Open, keyless lyrics API. Provider is attributed in the UI. Lyrics remain the property of their
rights-holders; Revibe displays them for personal playback rather than redistributing a lyrics corpus.

### Wikipedia
Biographies and images are CC BY-SA 4.0. The UI shows "Wikipedia, CC BY-SA 4.0". Downstream reuse must
preserve attribution and share-alike for the text.

### YouTube / InnerTube
**Not implemented.** Extracting web-client keys and streaming YouTube's catalog from a third-party
client is outside YouTube's Terms of Service and depends on non-public, unstable endpoints. The
`MusicSourceId` entry exists as `BLOCKED` so the UI and roadmap are honest about the decision.

### Spotify
**Not implemented.** The official Web API provides metadata and playlist access through OAuth, not
audio streaming. Spotify audio cannot be played through a third-party app without an agreement.
No Spotify binary was decompiled, disassembled, extracted or modified. The supplied `.exe` and `.apk`
were treated as visual reference only.

### JioSaavn, Musixmatch, Genius, KuGou
No integration. These services either require keys, prohibit client access, or need legal review.
They are named in the roadmap, not silently scraped.

## Privacy

- No account, no telemetry, no analytics, no crash upload.
- Search terms are sent to Apple; track/artist names to LRCLIB and Wikipedia when lyrics/biography are
  requested. This is disclosed here and should be shown in-app before release.
- Library data, history and playlists stay in local SQLite.
- Media keys are captured globally only while Revibe is running.
- No service-role keys or private credentials exist in the client; there is no backend yet.

## Prohibited behaviours (enforced by the design)

- No DRM or paid-access circumvention.
- No credential or key extraction from proprietary clients.
- No copying of proprietary artwork or Spotify assets.
- No fabricated metadata, lyrics or "synthetic" playback state.
