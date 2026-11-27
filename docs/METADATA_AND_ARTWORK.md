# Metadata and Artwork

## Sources

| Field | Source | Notes |
| --- | --- | --- |
| Title, artist, album | YouTube Music / local tags | Real values only |
| Duration | YouTube Music / audio header | Used for display and lyrics matching |
| Track/disc number, year, genre | YouTube Music / ID3/Vorbis tags | Optional, never invented |
| Artwork | YouTube Music artwork / embedded tag art | Primary; MusicBrainz+CAA then Deezer fallback when missing |
| Artist image | Wikipedia thumbnail, else provider artwork | Attributed when from Wikipedia |
| Biography | Wikipedia REST summary | CC BY-SA 4.0, label shown in UI |
| Lyrics | LRCLIB | Separate provider, see lyrics doc |

YouTube Music is the primary artwork source. MusicBrainz + Cover Art Archive and Deezer are
integrated for **artwork fallback only** (see
[ARTWORK_PROVIDER_ARCHITECTURE.md](ARTWORK_PROVIDER_ARCHITECTURE.md)). Metadata enrichment beyond
artwork and the Last.fm/ListenBrainz plan remain in [MUSIC_METADATA_ARCHITECTURE.md](MUSIC_METADATA_ARCHITECTURE.md).

## Artwork handling

`SkiaArtworkLoader`:

1. Memory cache keyed by URL and requested size (90 entries).
2. Disk cache under `~/.revibe/cache/art/remote/`, bounded to 320 files / 192 MB.
3. In-flight de-duplication so the same image is downloaded once.
4. Decode with Skia (already present through Compose), no additional image library.
5. Palette extraction by sampling an 18×18 grid:
   - skip transparent pixels,
   - score by saturation and brightness,
   - fall back to the brand accent for greyscale artwork,
   - brighten the selected colour to a UI-safe luminance,
   - derive a darker secondary colour.
6. Return the bitmap and both colours in one object.

Cards use square artwork crops. The right-panel hero instead follows the loaded image's aspect ratio
with `ContentScale.Fit`, so a 16:9 video still is shown complete and square album art stays square.
A missing image shows a layered gradient tile
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

- Catalog artwork: memory + content-addressed disk cache with atomic writes and bounded eviction.
- Lyrics: SQLite, permanent per track id.
- Track metadata: persisted whenever a track is liked, queued, played or added to a playlist; catalog
  detail responses are additionally memoised per session.
- Local files: `local_files(path, mtime)`; unchanged files are skipped, changed files re-read, missing
  files removed.
- Archived (retired-catalog) rows keep the artwork URL they were saved with, but no live provider is
  queried for them.

## Attribution requirements

- Wikipedia text: "Wikipedia, CC BY-SA 4.0" shown in the artist panel.
- LRCLIB: provider name shown in the lyrics tab.
- YouTube Music: named as the catalog source in Settings, the about dialog and playback copy.
- Revibe does not claim ownership of catalog metadata or artwork.
