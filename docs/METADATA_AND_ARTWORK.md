# Metadata and Artwork

## Sources

| Field | Source | Notes |
| --- | --- | --- |
| Title, artist, album | iTunes Search / local tags | Real values only |
| Duration | `trackTimeMillis` / audio header | Used for display and lyrics matching |
| Track/disc number, year, genre | iTunes / ID3/Vorbis tags | Optional, never invented |
| Artwork | iTunes `artworkUrl100` at 600×600 / embedded tag art | No random images |
| Artist image | Wikipedia thumbnail, else first album cover | Attributed when from Wikipedia |
| Biography | Wikipedia REST summary | CC BY-SA 4.0, label shown in UI |
| Lyrics | LRCLIB | Separate provider, see lyrics doc |

MusicBrainz and Cover Art Archive are not integrated. MusicBrainz returned 503 during research, and
adding a second metadata authority without a concrete gap would add rate-limit and matching
complexity. The repository has a clean place to add enrichment later.

## Artwork handling

`SkiaArtworkLoader`:

1. Memory cache keyed by URL and requested size (90 entries).
2. In-flight de-duplication so the same image is downloaded once.
3. Decode with Skia (already present through Compose), no additional image library.
4. Palette extraction by sampling an 18×18 grid:
   - skip transparent pixels,
   - score by saturation and brightness,
   - fall back to the brand accent for greyscale artwork,
   - brighten the selected colour to a UI-safe luminance,
   - derive a darker secondary colour.
5. Return the bitmap and both colours in one object.

Artwork is cropped square in cards and the right panel. A missing image shows a layered gradient tile
with a music glyph, never an unrelated picture. Local files use embedded artwork extracted by
jaudiotagger into `~/.revibe/cache/art/embedded-*.jpg`, served through the same loader via `file:`.

The dominant colour feeds `LocalAccent`, which animates the hero gradients and panel accents. It is
never used as text colour; contrast stays controlled by the design tokens.

## Local tags

jaudiotagger reads MP3, FLAC, M4A, OGG, WAV and friends. The scanner stores:

- title (falls back to filename),
- artist / album (clear fallbacks "Unknown artist"/"Unknown album"),
- duration from the audio header,
- year, track number, genre,
- whether the file is playable by the current engine (implied by format).

Embedded artwork extraction is content-addressed by file path hash, so re-scans do not duplicate files.
The DB stores the artwork URL, not image bytes.

## Caching and invalidation

- Catalog artwork: memory only in this milestone; disk cache directory exists and is wired for local
  embedded art. Adding HTTP disk caching is a small follow-up.
- Lyrics: SQLite, permanent per track id.
- Track metadata: persisted whenever a track is liked, queued, played or added to a playlist; catalog
  detail responses are additionally memoised per session.
- Local files: `local_files(path, mtime)`; unchanged files are skipped, changed files re-read, missing
  files removed.

## Attribution requirements

- Wikipedia text: "Wikipedia, CC BY-SA 4.0" shown in the artist panel.
- LRCLIB: provider name shown in the lyrics tab.
- iTunes: catalog source named in Settings and the album preview notice.
- Revibe does not claim ownership of catalog metadata or artwork.
