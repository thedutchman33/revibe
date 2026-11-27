# Lyrics Provider Architecture

## Model

```kotlin
data class LyricWord(val timeMs: Long, val text: String)
data class LyricLine(val timeMs: Long, val text: String, val words: List<LyricWord>)
data class Lyrics(
    val lines: List<LyricLine>,
    val plain: String?,
    val hasTimestamps: Boolean,
    val wordSynced: Boolean,
    val source: String,
    val syncedText: String?,
)
```

The UI renders plain text, line-synced LRC/TTML, or word-synced lyrics from the same model. Word tags
produce `LyricWord` entries; line-only files produce one line per timestamp.

## Central resolver

`LyricsProviderResolver` owns priority and failure handling:

1. **LRCLIB** remains the primary provider. Its exact `/api/get` request is tried first, then
   `/api/search` with duration-ranked candidates.
2. If LRCLIB has timed lyrics, they are used immediately.
3. If LRCLIB is missing or plain-only, **lrc.red** is asked for a verified ISRC match and TTML.
4. If lrc.red has no match, **Unison** is queried by the exact YouTube video ID.
5. Provider errors, HTTP failures and mismatches return `null`; nothing is thrown into playback.

Every source returns the shared `Lyrics` domain model. The winning source is cached in SQLite
(`lyrics_cache`) by track id, including its raw TTML/LRC and source name. Cached payloads are sniffed
for TTML (`<tt`) before parsing, so cache validity is not tied to a provider name.

## Active providers

| Provider | Access | Coverage verified | Status |
| --- | --- | --- | --- |
| LRCLIB | Keyless HTTP | English/Hindi line and word sync | **Active, primary** |
| lrc.red | Keyless HTTP; descriptive User-Agent and credit required | *Kesariya*: 44 lines, 209 word timings, Hindi | **Active, fallback** |
| Unison | Keyless reads; 600/min per IP; credit required | *Explorer*: 16 lines, word sync, submitter `boidu` | **Active, last fallback** |

### lrc.red

- Search: `GET /search.json?q={title artist}`; select by normalized title, artist-token overlap,
  exact-version guard (reject remix/teaser/lofi/slowed/cover/etc. unless the playing title says so)
  and a ±12 s duration window. Unknown durations never beat known ones.
- Lyrics: `GET /s/{ISRC}.ttml`, then `/s/{ISRC}.lrc` as a fallback. TTML is parsed by `TtmlParser`.
- Attribution: the lyrics panel shows **“Lyrics from lrc.red”** and links to `https://lrc.red`.
- Identity: `Revibe/0.8.0 (https://github.com/revibe[; mailto:<contact>])`. Distributors can set
  `REVIBE_CONTACT_EMAIL`; the lrc.red terms ask for a contact address, which this repository does not
  invent. Credit is shown either way.

### Unison

- Reads `GET /lyrics?v={videoId}`; only YouTube Music tracks with an `ytm:track:*` id are eligible.
- A response is accepted only when the title and an artist token match the playing track. A video-id
  hit alone is not treated as proof, which prevents wrong-song lyrics.
- Source credit includes the submitter, e.g. `Unison · boidu`, satisfying the attribution term.
- No writes, no account, no key.

### TTML parser

`TtmlParser` supports `<p begin>` lines, `<span begin>` words, `hh:mm:ss(.ms)`, `mm:ss(.ms)`,
seconds with a trailing `s`, XML entities, hours, and line-only paragraphs. Spacing between spans is
preserved, so adjacent words render as `Nothing`, not `No thing`.

## Evaluated, not integrated

| Provider | Finding |
| --- | --- |
| Better Lyrics API | Real public API with TTML and GPL-3.0 code, but a 401 for a live Get Lucky request: cache misses require an API key and keys are not being issued. Not a reliable fallback. |
| Lyprio | No public API or embeddable server; only an app-specific source inside Metrolist. Not integrated. |
| AM-Lyrics | A Web Component/front-end around lrc.red and Apple mirrors, not a hosted lyrics API. Integrating lrc.red directly is the supported path. |
| YouTube subtitles | Caption tracks are ASR/spoken text for videos, not a verified lyric source; no legitimate timed-caption path was added. |
| Musixmatch/Genius/KuGou | Keys or licences required; no unlicensed scraping. |

## UI behaviour

- The right panel's Lyrics tab shows source, sync type and current offset.
- The source line is a link for lrc.red and Unison.
- The active line is bold and full-opacity; passed lines dim; upcoming lines dimmer.
- Word-synced lines highlight words as playback passes each timestamp in the accent colour.
- Auto-scroll follows the active line while playing; manual scrolling is possible.
- Offset can be shifted in ±500 ms steps, displayed in seconds.
- If the provider returns plain text only, it is shown as a readable block without fake syncing.
- No match shows an explicit “No lyrics found” state; for *Roshni Hi Roshni Hai* by Soumya M, no
  provider had a verified match.

## Verification

- `TtmlParserTest`: word/line timing, spacing, entities, hours, malformed input.
- `LyricsProviderResolverTest`: primary wins, plain primary falls through, provider errors do not
  throw, wrong title/artist rejected.
- `LiveLyricsProviderTest`: Unison English word sync (**16 lines**) and lrc.red Hindi word sync
  (**44 lines**, first line “Mujhko itna bataye koyi”).
- Full suite: **181 tests, 0 failures** (46 suites) on 27 November 2026.
