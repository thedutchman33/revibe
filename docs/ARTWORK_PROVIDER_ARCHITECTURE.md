# Artwork Provider Architecture

Status: **Implemented** — YouTube Music is the primary artwork source, with MusicBrainz +
Cover Art Archive and Deezer as keyless fallbacks, high-resolution selection, disk caching and
artwork-derived accent theming.

## Goal

Show the correct cover for a track, album or playlist with a deterministic fallback chain, without
ever pairing a track with another release's artwork. Artwork is best-effort: a provider failure must
never delay playback or navigation.

## Implemented source matrix

| Source | Category | Status | Notes |
| --- | --- | --- | --- |
| YouTube Music / InnerTubeX | Track, album, playlist artwork | **Active (primary)** | Header, browse and search thumbnails; promoted to the largest rendition available |
| Local embedded tags | Local file artwork | **Active** | jaudiotagger extracts to `~/.revibe/cache/art/embedded-*.jpg` |
| MusicBrainz + Cover Art Archive | Album artwork fallback | **Active** | Release match first, then recording match; front/approved CAA image at up to 1200 px |
| Deezer | Album artwork fallback | **Active** | Keyless public search; `cover_xl` at 1000 px; strict title + artist matching |
| Spotify Canvas | Animated artwork | **Unsupported** | No public Canvas API; reverse-engineered Pathfinder/spclient access is refused |
| Unison / community Canvas | Animated artwork | **Unsupported** | Public manifests exist, but Revibe has no desktop video-canvas surface and licences are unverified |
| Apple Music | High-quality / animated artwork | **Unsupported** | Requires a private AMP token or scraping the web player; no keyless public API |
| Tidal | Animated artwork | **Unsupported** | Needs OAuth client credentials; no developer app is configured |
| Gradient placeholder | No artwork | **Active** | Palette-derived tile with a music glyph; never a random image |

## Resolution chain

```
track
 ├─ YouTube Music artwork (if present)          → primary, never replaced
 ├─ MusicBrainz release search (album + artist)
 │    └─ Cover Art Archive front image (up to 1200 px)
 ├─ MusicBrainz recording search (title + artist)
 │    └─ first release for the matched recording
 ├─ Deezer track search (strict title + artist)
 │    └─ cover_xl (1000 px)
 └─ gradient placeholder
```

`ArtworkResolver` only consults fallbacks when the catalogue supplied no artwork. It passes the
track's real artist, album, title and duration to each provider, returns the first valid candidate,
and treats timeouts, HTTP errors, parse failures and mismatches as a miss. A missing cover is better
than a wrong cover.

## Matching

`ArtworkMatching.kt` folds case, combining marks and punctuation, and removes release/video
qualifiers such as `(From "Film")`, `[Official Video]` and `| Lyrical`. Titles must match exactly
after folding; containment is deliberately not accepted because it would admit remixes and
different editions. Artist credits are split on commas, ampersands, `feat.`, `ft.`, `featuring`,
`with` and `×`; every requested artist must appear in the returned credit.

The album is used as a ranking signal for Deezer and as the release key for MusicBrainz. It is not a
hard requirement for Deezer because playlist playback may only know the playlist name. MusicBrainz
falls back to a recording lookup when a release match fails.

## High-resolution selection

`ArtworkUrl.kt` centralises URL normalisation:

- YouTube video renditions (`hqdefault`, `sddefault`, `mqdefault`, `hq720`) are promoted to
  `maxresdefault`; if that rendition is absent, the loader retries `hqdefault` and then `mqdefault`.
- Google/YouTube Music artwork (`=w544-h544-l90-rj`) is promoted to `=w1200-h1200`.
- Palette sampling uses the bar-free `mqdefault` or a 256 px Google rendition, never a large image.

## Caching and loading

`SkiaArtworkLoader` now has two cache layers:

1. **Memory:** 90 decoded entries keyed by URL and requested size, with in-flight de-duplication.
2. **Disk:** content-addressed bytes under `~/.revibe/cache/art/remote/`, bounded to 320 files /
   192 MB. Writes are atomic; a corrupt cached image is evicted and re-fetched.

`Artwork` cross-fades decoded images, so a cover swap does not flash. Embedded local artwork still
uses the `file:` path and is never pruned by the remote cache.

## Dynamic player theme

The existing `LocalAccent` theme is artwork-driven. Palette extraction samples an 18×18 grid,
scores saturation and brightness, rejects near-black/near-transparent samples, brightens
the winner to a UI-safe luminance and derives a darker secondary tone. The accent animates over
700 ms when the track changes. Palette reads use the small, bar-free colour source so a video
thumbnail's letterboxing cannot skew the theme. Greyscale artwork falls back to the brand accent.

## Tests

- `ArtworkMatchingTest`: qualifier folding, strict title matching, artist splitting, YouTube and
  Google URL promotion.
- `DeezerArtworkProviderTest`: correct 1000 px cover, wrong-artist rejection, no request for unknown
  metadata.
- `ArtworkResolverTest`: catalogue artwork wins without provider calls, fallback order stops at the
  first cover, unknown metadata is rejected, live MusicBrainz/CAA lookup and cache hit.

## Live verification

- YouTube Music catalog artwork and high-resolution promotion: verified in the running app.
- MusicBrainz + Cover Art Archive: the live test returned a real 1200 px cover and a cached second
  lookup; one 503 was observed and is retried once after the rate-limit interval.
- Deezer: the anonymous search endpoint returned `{"data":[],"total":87}` from this network, so a
  live cover could not be verified here. The provider is unit-tested and fails closed to the next
  fallback or the gradient placeholder; it does not invent a cover.

## Unsupported sources

Spotify Canvas, Apple Music motion artwork, Tidal video covers and community Canvas indexes are not
wired into the app. They either require undocumented/credentialed access or a desktop video surface
that Revibe does not have. No UI claims these sources are available. See
[SPOTIFY_CANVAS_INTEGRATION.md](SPOTIFY_CANVAS_INTEGRATION.md).
