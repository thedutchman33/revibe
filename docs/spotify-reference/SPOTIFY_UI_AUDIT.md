# Spotify for Windows — UI audit

Reference: live Spotify Free desktop app, 2026-11-26. See `README.md` for the
capture environment. This document records **what was observed**, not what
Spotify's public documentation says or what older web screenshots show.

## 1. Application shell

The running app is a two-band shell:

1. **Global top bar** across the full window: history controls, Home, a centred
   search field, notification/friends/profile controls and Windows caption
   buttons.
2. **Workspace**: three side-by-side containers in a Row, with an 8 dp gutter
   between them.
   - Left: `Your Library` (expanded or collapsed icon rail).
   - Centre: the current route (Home / Search / album / artist / playlist /
     Liked Songs / lyrics).
   - Right: a contextual rail (Now playing, Queue, lyrics preview, "Based on"
     recommendation card). It can be closed with an `x`.
3. **Bottom player** is a separate persistent band below the workspace. It is
   always visible while navigating; on the lyrics route the workspace content
   changes but the player remains in place.

Observed evidence: `spotify_home_full.png`, `spotify_queue_full.png`,
`spotify_lyrics_full.png`, `spotify_left_collapsed_full.png`.

### 1.1 Top bar anatomy (left to right)

- Back and forward chevrons. Back is enabled after navigating; forward appears
  dim until there is forward history.
- A filled circular Home button. It is visually selected while Home is open.
- A wide, centred search field with:
  - a search icon on the left;
  - placeholder `What do you want to play?`;
  - a divider and a "browse/search" icon on the right.
- Notification bell, friends icon, and a circular avatar.
- Windows minimise / maximise / close caption controls. The title bar is
  integrated: there is no separate native title strip in the client area.

### 1.2 Left library

Expanded:

- Header `Your Library` with a `+ Create` pill and an expand/collapse chevron.
- Filter chips: `Playlists`, `Podcasts`, `Albums`, `Artists`.
- A library search icon and a `Recents` control with a list/grid toggle.
- Rows with 44 px-ish artwork, title, and two-line metadata
  (`Playlist · owner`, `Album · artist`, etc.).
- `Liked Songs` uses a distinct gradient tile.
- The library list scrolls independently of the centre route.

Collapsed:

- The container becomes a narrow rail (~89 physical px wide, about 71 dp).
- It shows a library icon, a `+` create button, then a vertical stack of
  artwork-only shortcuts for Liked Songs, playlists and albums.
- The centre container expands into the vacated width; the right rail keeps its
  width.

Observed: `spotify_home_full.png`, `spotify_left_collapsed_full.png`.

### 1.3 Centre route

Common elements:

- Rounded, near-black surface with a subtle border; each container is visually
  separate rather than one continuous background.
- Route content scrolls under a sticky-feeling top area. On playlist/album pages
  the header artwork and metadata are large, followed by an action row and a
  track table.
- Cards use a hover play button; rows show a play affordance on hover, a
  liked/playing indicator, and a trailing `…` menu.
- Content is clipped by the container, not by the window.

Home:

- Filter chips at the top: `All`, `Music`, `Podcasts`, `Audiobooks`.
- A compact grid of square shortcut cards with artwork, title, and a two-line
  description.
- Shelves: `Made For <user>`, `Recommended for today`, each with a `Show all`
  link and horizontally clipped cards. Cards are square with rounded corners;
  the final visible card is cut off at the container edge to signal overflow.
- Daily Mix cards use a coloured corner numeral (01–06) over artwork. This is
  Spotify's generated content, not an asset we reuse.

Search:

- Search field is focused, suggestions panel drops below it.
- Suggestions show a locate/search icon, query text, and grouped entity results
  with small artwork, type (`Song`, `Playlist`), and an add/check action.
- Results page shows a filter chip row `All / Playlists / Songs / Artists /
  Albums / Profiles / Podcasts & Shows / Genres & Moods`.
- A top result card can contain a large artwork, title, owner, `Following` or
  play button, and an overflow menu.
- Song rows use a compact two-line layout with duration/album metadata and
  hover actions.

Album / playlist / artist:

- Header has artwork on the left and a metadata block on the right.
- Playlist header: `Public Playlist`, huge title, description, owner and
  `50 songs, 5 hr 5 min` / `11 songs, 1 hr 4 min`.
- Album header: `Compilation`, title, artist, year and track count.
- Artist header: full-bleed image, artist name, `Verified by Spotify`, monthly
  listeners, play/shuffle/follow/overflow controls, then `Popular` table.
- Action row uses a large green play circle, shuffle, add/save, download and
  overflow controls.
- Table columns in the captured album: `#`, `Title`, `Plays`, duration; in a
  playlist: `#`, `Title`, `Album`, added-at/duration. The current row is
  highlighted and shows a small equaliser/play glyph.
- Playlist pages also expose mood filter chips (`Moody`, `Romantic`, `Soulful`,
  etc.) and a sort control.

Liked Songs:

- Gradient artwork tile with a white heart.
- `Playlist`, title, owner, `45 songs, 2 hr 3 min`.
- Play, shuffle, download, search-in-playlist and sort controls.
- Mood chips.
- Rows show `#`, artwork/title/artist, album, `Date added`, duration.

Observed: `spotify_home_scrolled_full.png`, `spotify_search_results_full.png`,
`spotify_search_suggestions_full.png`, `spotify_liked_songs_full.png`,
`spotify_playlist_full.png`, `spotify_album_full.png`,
`spotify_artist_full.png`.

### 1.4 Right rail

The right rail is contextual, not a permanent queue panel.

Now playing / recommendation:

- Header area shows a promotional card ("Advertisement" label), `Digital King`
  callout, `Listen now` button, and a 3-dot overflow.
- `Alfaaz` row with artwork, artist, and a green check/liked state.
- `Lyrics preview` card with the current lyric in a dark red panel.
- In earlier Home captures the rail is titled `Based on Taare` and contains a
  recommendation card.

Queue:

- Two tabs: `Queue` and `Recently played`. The selected tab has a short
  underline.
- The queue tab has an `x` close control at the far right.
- Header note: `About recommendations and the impact of promotion`.
- `Now playing` section: artwork, green title, artist.
- `Next up`: rows with artwork, title, artist; 7 rows visible in the captured
  state. Rows do not show index numbers in the capture.
- The queue and the player's current track stay in sync (`Alfaaz` in both).

Lyrics view:

- Full lyrics are shown in the main workspace, with a blurred/coloured hero
  background derived from artwork.
- `Musixmatch` attribution appears at the top.
- The right queue remains open, and the player remains at the bottom.
- Timed lines are large; the current line is the brightest. No karaoke
  word-highlighting was verified.

Observed: `spotify_queue_full.png`, `spotify_lyrics_full.png`,
`spotify_home_full.png`.

### 1.5 Player bar

- Left cluster: 54 px artwork, title, artist, like/check, and (on hover) a
  compact "add to liked" control.
- Centre cluster: shuffle, previous, large pause/play circle, next, repeat.
  Below it: elapsed time, a thin seek bar, and total duration.
- Right cluster: lyrics/mic, queue, volume icon + horizontal volume slider, and
  a now-playing/fullscreen control.
- The background is a slightly lighter near-black than the workspace, separated
  by a hairline divider.
- The seek bar is a thin track with a thumb that becomes visible on hover; the
  played portion is light, the unplayed portion is grey.

Observed: `spotify_home_full.png`, `spotify_queue_full.png`.

## 2. Menu and overlay states

### Track context menu

Right-clicking a track row opens a dark floating menu with, in order:

- `Add to playlist` (submenu arrow)
- `Add to Liked Songs` (keyboard hint `L`)
- `Add to queue` (hint `Q`)
- `Hide in this album`
- `Exclude from your taste profile`
- `Sleep timer` (submenu arrow)
- `Go to song radio` (hint `R`)
- `Go to artist` (hint `Alt A`)
- `View credits`
- `Share` (submenu arrow)

The menu overlays the centre and right containers without resizing them. The
row behind it remains highlighted.

Observed: `spotify_context_menu_full.png`.

### Profile menu

Account avatar opens a floating menu:

`Account`, `Profile`, `Recents`, `Upgrade to Premium`, `Support`, `Private
session`, `Settings`, `Log out`, a divider, and an `Your Updates` section with
promotion cards.

Observed: `spotify_profile_menu_full.png`.

## 3. State changes observed

- **Sidebar collapse:** left container becomes the icon rail and the centre
  expands; the bottom player and right rail do not move vertically.
- **Queue open:** right rail changes from a recommendation/now-playing card to
  the Queue panel with two tabs and a close `x`.
- **Lyrics:** centre becomes a full lyrics page while the queue stays accessible.
- **Navigation:** search results, album, artist and playlist routes all keep the
  same shell; only the centre content changes.
- **Hover:** album row hover reveals a play glyph and highlights the row;
  artwork cards reveal a circular play button in the capture. This was seen in
  stills, not recorded frame-by-frame.
- **Search suggestions:** the suggestions panel overlays the centre and extends
  over the results; it is not part of route content.

## 4. Explicitly unverified

These were **not** reliably observed, so Revibe must not claim to have matched
them:

- Exact hover/pressed/focus durations and easing curves; only static hover
  states and the before/after layout were captured.
- Drag-resizing of the left or right container. No visible resize handle was
  found on the container edges in the captured states.
- Window resizing below the maximised width, narrow-window reflow, minimise and
  fullscreen player.
- Keyboard shortcuts beyond the context-menu hints shown (`L`, `Q`, `R`,
  `Alt A`). Global shortcuts were not tested.
- Tooltips, focus rings, screen-reader labels and keyboard-only navigation.
- Right-rail "Recently played" tab content.
- Settings page and account flows. The signed-in session expired during the
  audit; the resulting login/QR screen was not retained or used.
- Download, share, sleep timer, song radio and "View credits" outcomes.
- Queue drag-reorder, removal and "add to queue" confirmation behaviour.
- Lyrics timing/karaoke behaviour and the source of the Musixmatch data.
- Any network/API behaviour or recommendation algorithm. This audit covers UI
  only.

These gaps are acceptable for the Revibe adaptation: they are interaction
details we can define ourselves, not visual facts needed for the layout.
