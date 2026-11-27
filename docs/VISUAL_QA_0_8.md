# Visual QA — 0.8.0

Date: 26 November 2026. Windows desktop JVM, 1920×1200 display at **125% scaling (120 DPI)**.
Screenshots were captured from the real running app, not a mock-up.

## Method

Visual QA now uses **isolated profiles** under `build/visual-qa/profiles/`. Each profile is its own
SQLite database, launched with `REVIBE_HOME`; the real user library is never opened or modified.
`build/visual-qa/seed_profiles.py` copies the real schema and seeds only the two synthetic profiles.
Synthetic rows are confined to build output and are never shipped.

`build/visual-qa/win.py` is the Win32/PIL harness. It focuses the real window with
`AttachThreadInput` before clicks, reports DPI, resizes by logical dp, and captures only after the
app has foreground. That fixed the earlier failure where the terminal received the miniplayer click.

| Logical window | Physical capture |
| --- | --- |
| 1120×720 dp (minimum) | 1400×900 px |
| 1440×900 dp | 1800×1125 px |
| Miniplayer 400×336 dp | 500×420 px |

### Maturity profiles

| Profile | Real SQLite signals | Expected homepage |
| --- | --- | --- |
| NEW | 0 plays, 0 likes, 0 playlists, 0 follows | “Discover music”, discovery shelves; no recent-playlist grid |
| EARLY | 8 plays, 3 artists, 2 likes, 1 playlist, 0 follows | “Welcome back”, recently played, Made For You, discovery; playlist grid only after a playlist play |
| ESTABLISHED | 150 plays, 26 artists, 30 likes, 3 playlists, 3 follows, 59-day span | personalised mixes, followed artists, all discovery shelves |

Captured evidence:

- `home-new-final.png` — empty NEW profile at 1440 dp.
- `home-early-final.png`, `home-early-scrolled-final.png` — EARLY profile.
- `home-shortcuts-final.png`, `home-established-scrolled.png`, `home-established-lower.png` —
  ESTABLISHED profile.
- `playing-established.png` — real YouTube Music playback from the isolated history.
- `final-miniplayer-desktop.png`, `final-miniplayer-card.png`, `final-miniplayer-hover.png` —
  floating 400×336 dp miniplayer bottom-right above the taskbar, resting and hover states.
- `mini-check-next2.png`, `mini-check-resized2.png` — next track and resized card.
- `playlist-final2.png`, `playlist-hover.png`, `playlist-search-typed2.png`,
  `playlist-row-menu.png`, `playlist-name-modal.png`, `playlist-download-toast.png`,
  `min-playlist-final4.png`.
- `queue-final.png`, `recently-played-final.png`.
- `maximized-new.png`, `restored-new2.png` — work-area maximize (taskbar visible) and restore.
- `perf-playlist-top.png`, `perf-scroll-mid.png`, `perf-bottom-130.png`, `perf-hover-row.png`,
  `perf-sort-title.png`, `perf-sort-artist3.png`, `perf-sort-album2.png`, `perf-sort-recent2.png` —
  130-track performance pass.
- `settings-top.png`, `settings-sources.png` — neutral catalogue labels.

## Defects found and fixed

1. **Miniplayer crashed on first frame with “ArtworkLoader was not provided”.** The compact window is
   a separate Compose composition from `RevibeApp`, so it did not inherit the artwork loader or
   theme. `Main.kt` now provides `LocalArtworkLoader`, `LocalAccent` and `RevibeTheme` around
   `MiniPlayer`. The miniplayer opens, plays, and expands without stopping audio.
2. **Miniplayer was double-scaled.** AWT window bounds are logical units on this host; multiplying
   by `graphicsConfiguration.defaultTransform` produced a 575 dp window instead of 460 dp. Sizes now
   pass dp directly, and drag deltas are converted from physical pixels to logical units. The
   miniplayer is now a separate floating 400×336 dp window (500×420 px at 125%).
3. **Playlist shortcut labels clipped.** Four columns at the 1440 dp centre left only ~80 dp for
   text. The grid now uses four columns only above 820 dp, three above 520 dp, and shortened the
   subtitle to “5 songs · Playlist”. Titles and subtitles are fully visible in
   `home-established-final.png`.
4. **Library track count went stale after removing a playlist row.** `removeFromPlaylist` now reloads
   the `playlists()` rows as well as the detail, so the left library, hero count and total duration
   agree.
5. **Playlist hero broke at the 1120 dp minimum.** The fixed 216 dp cover left too little for
   “Ordinary”, which wrapped in the middle of the word and clipped the song count. The hero now uses
   a 160 dp cover, 48 sp title and a separate count line below 620 dp.
6. **Window minimum and compact sizes were physical pixels.** They now follow dp and the real
   compositor scale; the main minimum is 1120×720 dp, the miniplayer 400×336 dp.
7. **Maximize covered the Windows taskbar.** An undecorated `WindowPlacement.Maximized` window spans
   the full screen. Maximize now sets the AWT `maximumWindowBounds` work area (1920×1140 at
   1920×1200), so the taskbar stays visible, and restore returns to the saved floating bounds.
8. **Miniplayer opened top-left.** A post-show `setLocation` raced the platform placement. The
   window position is now state-driven, opening at the bottom-right of the work area.
9. **Download and device actions were dead controls.** The playlist download and the
   connect-to-a-device footer button were removed instead of showing unavailable toasts.

## Playlist screen

Built against the supplied Spotify reference, with Revibe’s own data and branding.

- Full-bleed hero tinted from the first track’s real dominant colour, 216 dp cover (160 dp
  compact), 72 sp display title (30–48 sp compact, scaled by title length), owner row and
  “N songs, X hr Y min”.
- Action bar: 56 dp accent play, shuffle, add songs, overflow (edit/delete), search-in-playlist
  and a working sort menu. Download is not shown.
- The right utility cluster follows the supplied Spotify strip: outline mic, queue, volume,
  miniplayer and fullscreen icons; the headphone/device action is omitted.
- Add and Name & details pills. The details modal edits name and description.
- 56 dp table rows with 40 dp artwork, title/artist stack, album, **real `playlist_tracks.added_at`
  date**, duration, and a 16 dp clock header. Album and date columns drop at narrow widths.
- Hover row animates its background, swaps the index for a play glyph, and reveals save/more
  actions. The current row uses the accent title and an equalizer glyph.
- Column layout and type sizes match the reference: 16 sp title, 14 sp artist/album/date/duration,
  12 sp header, Inter (SIL OFL) in place of proprietary Circular.

## Right rail and player bar

- Queue follows the reference: 16 sp Queue / Recently played tabs with an animated accent underline,
  a close button, “Now playing”, “Next from: <context>”, 40 dp artwork and 56 dp rows.
- Autoplay, save queue as playlist and clear queue moved into the “Now playing” overflow menu, so
  the panel keeps the reference’s two-tab header.
- Queue rows no longer show a duration; save/remove actions appear on hover, and dragging a row
  still reorders it without a permanent drag handle.
- Player-bar icon glyphs increased from 16 dp to 18 dp, muted icons brightened from `#B3B3B3` to
  `#D6D6D6`, and the volume slider widened to 100 dp.

## 130-track performance pass

`build/visual-qa/seed_perf.py` created an isolated profile with one 130-row playlist
(“Performance 130”), unique track IDs and rotated real covers.

- Scrolled continuously from the hero to row 130 and back with wheel input; no blank rows, stuck
  scroll, duplicate-key crash or log exception.
- Title, Artist, Album, Recently added and Custom order all reordered the full list correctly;
  Recently added was newest-first and Custom order restored provider order.
- Row hover revealed the play glyph, like and overflow actions at both the top and bottom of the
  list.
- Screenshot-sampled input-to-first-change and settle times (includes roughly 30–50 ms capture
  overhead): scroll first change ≈91 ms, settle ≈447 ms; hover first change ≈113 ms, settle
  ≈261 ms. These are interactive samples, not an FPS/frame-time profile.
- Not exercised: 1,000+ row playlists, simultaneous artwork loading, keyboard traversal and
  drag-reorder under load.

## Observed but not fixed

- **Resolved after this audit:** YouTube video tracks expose 16:9 thumbnails (`hq720.jpg`) instead
  of square album art. The right-panel hero now follows the loaded image's aspect ratio and uses
  `ContentScale.Fit`, so the side text is no longer cut. Square crops remain intentional in cards.
- Horizontal home shelves still clip the next card at the container edge without a fade or scroll
  arrow. The carousel scrolls, but the affordance is not obvious.
- **Resolved after this audit:** the Now Playing hero no longer uses a fixed 300 dp square band; it
  sizes from the loaded artwork ratio, including at the 282 dp minimum panel width.
- The seeded QA playlist uses synthetic album ids, so clicking an album from those rows opens the
  honest “Album unavailable” state. Real YouTube Music album ids resolve normally.

## Not inspected

- Spotify’s Circular font and icon font are proprietary; Revibe ships Inter (SIL OFL) and Material
  Rounded glyphs. Sizes, weights, spacing and opacity are matched, but glyph shapes are not identical.
- Playlists above 130 rows, native drag-and-drop between playlists, and full
  keyboard/accessibility traversal were not exhaustively exercised.
- Search pagination/suggestions were outside this pass.
