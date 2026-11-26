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

The UI renders plain text, line-synced LRC, or word-synced lyrics from the same model. Word tags
produce `LyricWord` entries; line-only files produce one line per timestamp.

## Active provider: LRCLIB

`https://lrclib.net` is a free, open, keyless lyrics database.

Resolution order:

1. Exact `/api/get` with artist, title, album and duration.
2. `/api/search?q=artist title`, filtering out instrumental entries, choosing the closest duration.
3. No match → explicit "No lyrics found" state with retry.

Responses are cached in SQLite (`lyrics_cache`) and in memory for the session. The UI always shows the
provider name. The interface does not fabricate or re-attribute lyrics.

## Parser

`LrcParser` handles:

- `[mm:ss.xx]` timestamps with 1–3 fractional digits.
- Multiple timestamps on one line (repeated chorus lines).
- Empty timestamped lines as instrumental markers (`♪`).
- Enhanced word tags: `<mm:ss.xx>word`, producing per-word timings.
- Unsorted input (sorted by time), duplicate timestamps removed.

Unit-tested in `CatalogSmokeTest.lrc parser handles line and word sync`.

## UI behaviour

- The right panel's Lyrics tab shows source, sync type and current offset.
- The active line is bold and full-opacity; passed lines dim; upcoming lines dimmer.
- Word-synced lines highlight words as playback passes each timestamp in the accent colour.
- Auto-scroll follows the active line while playing; manual scrolling is possible.
- Offset can be shifted in ±500 ms steps, displayed in seconds.
- If the provider returns plain text only, it is shown as a readable block without fake syncing.

## Adding another provider

1. Implement a `LyricsSource`-style provider (extract the fetch step from `LyricsRepository` when a
   second provider lands) returning the shared `Lyrics` model.
2. Define priority and fallback: word-synced > line-synced > plain; then by provider preference.
3. Cache per track id, including which provider won.
4. Verify the provider's licence and terms. Do not scrape providers that prohibit client access or
   reproduce copyrighted lyrics without permission.
5. Add parsing fixtures for that provider's quirks.

Providers named in the brief (Musixmatch, Genius, KuGou, SimpMusic, BetterLyrics, AM-Lyrics,
YouLyPlus, Unison) were evaluated by reading the reference apps' integration points. None is shipped
in this milestone because the ones with usable APIs require keys or have terms that need review; the
provider seam is ready for those that pass review.
