# Spotify desktop reference capture

This folder records a **behavioural and layout reference** captured from the real
Spotify for Windows application. It is not a source of Spotify code, assets,
logos or trademarks, and nothing here should be copied into Revibe artwork.

## Capture environment

| Item | Value |
| --- | --- |
| Date | 2026-11-26 |
| App | Spotify for Windows (Store package `SpotifyAB.SpotifyMusic_zpdnekdrzrea0!SpotifyMusic`) |
| Account state at capture | Signed in as **Spotify Free** (no credentials accessed or stored) |
| Display | 1920 x 1200 physical, Windows scaling **125%** (logical 1536 x 960) |
| Spotify window | 1920 x 1125 physical, positioned at y = 6, maximised |
| Capture method | `ffmpeg -f gdigrab` stills after foreground activation with AWT Robot input |
| Playback during capture | `Alfaaz` — Hamza Malik, Zain Zohaib (3:04) |

All pixel numbers in these documents are **physical screenshot pixels**. To get
Compose `dp` at 125% scaling divide by **1.25**. The app window is therefore
1536 x 900 logical.

## Evidence index

Full-resolution PNGs live in `screenshots/`; 1280-wide review JPEGs live in
`screenshots/previews/`.

| Preview | Verified state |
| --- | --- |
| `spotify_home_full.png` | Home, expanded library, right "Now playing" rail |
| `spotify_home_scrolled_full.png` | Home scrolled to "Made For" / "Recommended for today" |
| `spotify_search_results_full.png` | Search results for `arijit singh` |
| `spotify_search_suggestions_full.png` | Search suggestions for `taare` |
| `spotify_liked_songs_full.png` | Liked Songs playlist page with mood filter chips |
| `spotify_artist_full.png` | Artist page (Arijit Singh), Popular tracks |
| `spotify_album_full.png` | Album page, track list, hovered row |
| `spotify_playlist_full.png` | Daily Mix 1 playlist page |
| `spotify_queue_full.png` | Queue tab, Now playing / Next up |
| `spotify_lyrics_full.png` | Full lyrics view with queue still visible |
| `spotify_left_collapsed_full.png` | Left library collapsed to an icon rail |
| `spotify_context_menu_full.png` | Track right-click context menu |
| `spotify_profile_menu_full.png` | Account avatar menu |

## What is deliberately not here

- No Spotify login QR code, cookie, token, credential or account data. A capture
  made while the session had expired contained a login QR code; it was deleted
  immediately and never used.
- No Spotify fonts, icons, images or audio. The screenshots are reference
  evidence only.
- No claim that a state was observed when it was not. See the "unverified" list
  in `SPOTIFY_UI_AUDIT.md`.

## Related Revibe documents

- `../revibe-layout/README.md` — captures of the implemented Revibe shell (kept
  separate from this Spotify evidence)
- `../REVIBE_UI_IMPLEMENTATION_PLAN.md`
- `../REVIBE_QUEUE_ENGINE_PLAN.md`
- `../REVIBE_RECOMMENDATION_ENGINE_PLAN.md`
- `../REVIBE_PHASE_ROADMAP.md`
