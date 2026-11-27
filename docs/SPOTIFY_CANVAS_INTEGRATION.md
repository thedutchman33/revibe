# Canvas and Animated Artwork

Status: **Not integrated — no legitimate public access path and no desktop video surface.**
Fallback: **high-resolution album artwork** (active) and **artwork-derived accent theming** (active).

## What was checked

| Source | Finding | Decision |
| --- | --- | --- |
| Spotify Canvas | Not part of the Spotify Web API. Open-source clients reach it through undocumented Pathfinder/`spclient` endpoints with web-player or user-session tokens. | Refused: impersonation and token harvesting are outside Revibe's acceptable-use policy. |
| Apple Music motion artwork | `editorialVideo` is exposed on Apple's private `amp-api` after scraping a web-player bearer token. Revibe also excludes Apple preview endpoints entirely. | Refused: private/undocumented access. |
| Tidal video covers | `videoCover` is available only with a Tidal developer token/client credentials. | Unsupported: no credentials or approved developer app. |
| Community Canvas manifest | BitChord and Vivi Music use a public `canvas.json` index with song/artist/album keys. | Unsupported: no renderer in Revibe desktop and asset licences are unverified. |
| Unison | Unison is integrated for lyrics; it is not a Canvas provider. | Not applicable. |

## Why Revibe does not render Canvas

The Now Playing surface is a Compose desktop application. Playing a looping MP4/HLS clip inside the
artwork slot would require a new video surface and decoder path, lifecycle management for hidden
windows, and a cache for clips. The existing FFmpeg playback engine is an audio engine and is not
mounted into Compose. Adding this would change the player surface, which is outside the current
request to preserve the existing UI and playback behaviour.

No Canvas button, badge or setting is shown. A still cover is always present underneath, so nothing
in the player is blocked by the absence of motion artwork.

## Artwork-based alternative (implemented)

- Large artwork in Now Playing and fullscreen.
- High-resolution static covers from YouTube Music, MusicBrainz/Cover Art Archive and Deezer.
- Artwork palette extraction feeds `LocalAccent`; the accent animates over 700 ms on track change.
- Artwork failure falls back to a gradient tile; playback never waits for artwork.

## Future requirements

Any future Canvas implementation must have:

1. A documented, licensed API or explicit written permission.
2. A per-track opt-in setting.
3. Bounded clip caching, off-UI decode and pausing when the window is hidden.
4. A graceful still-image fallback and immediate asset release on track change.

`VisualBackgroundProvider` remains the reserved seam for that future work; no implementation is
registered.
