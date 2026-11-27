# Spotify for Windows — component reference

This is a component inventory for behavioural parity. It describes anatomy,
states and relative sizing. It is **not** a pixel-exact design spec and it is
not a licence to copy Spotify assets, artwork, icons or code.

## 1. Shell components

### 1.1 App top bar

```
[ < > ]   (Home)      [ search ................ browse ]      (bell) (friends) (avatar)   [ - ][ □ ][ x ]
```

- Height: ~79 physical px (~63 dp).
- Background: black.
- Left controls are 32 px targets with 22 px icons.
- Home is a 40 px filled circle; selected state is lighter.
- Search is a 592 x 48 px pill (~474 x 38 dp), centred on the window.
- Right-side controls are 32 px; caption buttons use the OS convention.
- No bottom border is visible against the black top band.

### 1.2 Workspace container

- Fill: `#121212` with a subtle border/soft edge.
- Radius: ~8 physical px outer.
- Inset 10 px from neighbouring containers.
- Scrolls internally; clips content and card overflow.
- The header area of routes can use artwork-derived colour as a large wash.

### 1.3 Left library

Expanded:

- Width 454 physical (363 dp).
- Header row: title, `+ Create` pill (height ~32 px), collapse chevron.
- Filter chips: 32 px high, pill radius, muted fill, selected is lighter.
- Search/recents row: 16 px icons with 14 px labels.
- Library rows: 44 px artwork, 12 px gap to text, 8 px vertical padding.
- Section subtitles are 13–14 px, secondary grey, one line.
- Selected row: raised fill with ~6 dp radius.

Collapsed:

- Width 89 physical (71 dp).
- Stack: library glyph, `+` square, artwork squares at 44 px, 8 px gaps.
- Labels are hidden, but icons/artwork retain accessible names.

### 1.4 Centre card/shelf

- Section heading: ~24 px semibold; `Show all` right-aligned, 12 px grey.
- Grid gap: 16–20 px.
- Square artwork card: 4–8 dp radius, hover play circle at bottom-right of
  artwork, 2-line title, 2-line subtitle.
- Shortcut card: artwork 56 px, title 14 px, description 12 px, row height
  ~80 px, darker raised fill.
- Shelf overflow: last card clipped at container edge; no visible scrollbar in
  the capture.

### 1.5 Right rail

- Width 441 physical (353 dp).
- Header: two tabs with selected underline (18 px wide, 2 px high), close `x`
  at far right.
- "Now playing" label: 12 px secondary; artwork 40 px; title accent-tinted.
- Queue rows: 40 px art, 10 px gap, title 14 px, artist 12 px, 10 px vertical
  rhythm. No index numbers observed.
- Promo/recommendation card: large square/portrait art, label, CTA button;
  visually distinct from library cards.
- Lyrics preview: dark red panel, 20 px lyric lines, clipped to 2–3 lines.

### 1.6 Player bar

- Height 109 physical (87 dp).
- Left: 54 px art, 12 px gap, title/artist; like icon.
- Centre: 5 controls with 18–20 px gaps; play circle 44 px.
- Seek row: thin 3–4 px track, 44 px time labels, thumb on hover.
- Right: 4 icons, 96 px volume track, 30 px targets.
- Text: title 14 px medium/semibold; artist 12–13 px secondary; times 11–12 px
  tertiary.

## 2. Route headers

### 2.1 Album header

```
[ artwork 232 ]   Compilation
                  Album title (display, 48–72 px)
                  artist · year · 11 songs, 1 hr 4 min
[play] [shuffle] [add] [download] [ ... ]              List / sort
```

- Artwork size in the captured window: ~232 physical px (~186 dp).
- Header background: artwork-derived flat/washed colour, fading into the
  container.
- Title is the largest text on the page; wraps at container width.
- Metadata is one line at 13–14 px.

### 2.2 Playlist header

- Artwork ~232 px; `Public Playlist` eyebrow; huge title; description block;
  owner line; count/duration line.
- Same action row as album; playlist also gets search-in-playlist and sort.
- Mood chips follow the action row in Liked Songs and Daily Mix.

### 2.3 Artist header

- Full-bleed artist image with a gradient overlay.
- Name at display size, verification line, monthly listeners.
- Actions: play, shuffle, follow pill, overflow.
- `Popular` section with a numbered table; track rows show artwork, title,
  artist credits, play count and duration.

### 2.4 Track table

Columns observed:

- `#` (index or playing glyph)
- Title block (44 px art + title + artist credits)
- Album (playlist) or Plays (album)
- Date added (Liked Songs)
- Duration
- trailing `…` on hover/selected rows

- Header row is sticky-ish and low contrast.
- Row height ~56 px; hover raises the row and exposes play/overflow.
- Current row shows a small green equaliser/play state.
- Liked state is a green check or heart, not a separate column.

## 3. Search components

- Search pill: 48 px height, pill radius, icon left, divider/browse icon right.
- Suggestions overlay: ~592 px wide, starts under the field, dark elevated
  surface, radius ~8 px, shadow, 12 px internal padding.
- Suggestion query rows: 40 px high, search/locate icon, 14 px text.
- Suggestion entity rows: 40 px art, title 14 px, subtitle 12 px, trailing
  add/check icon; grouped with a small heading (`Songs`, etc.).
- Results filter chips: 32 px pills, horizontally scrollable; last chip clipped.
- Top result card: large art, title, type/owner, follow/play and overflow.

## 4. Menus and overlays

### Context menu

- Dark surface (`#282828`-ish), radius 4–6 px, shadow, 8 px vertical padding.
- Item height ~36 px, 12 px horizontal padding, 14 px text.
- Shortcut hint right-aligned in tertiary grey.
- Submenu chevron on the right.
- Destructive items, if any, are not in the captured menu.
- Appears at pointer, overlays both centre and right rail.

### Profile menu

- Width ~280 px.
- Rows 32–36 px high.
- Divider before `Your Updates`.
- External-link glyph on `Account`, `Support`.
- Bottom promo blocks with 64 px art and two lines of text.

## 5. Tokens inferred from the captures

These are approximate visual tokens; the exact CSS is private and was not
inspected.

| Token | Observation |
| --- | --- |
| Base | pure black `#000000` |
| Container | `#121212` |
| Raised surface | `#1F1F1F` / `#242424` |
| Menu surface | `#282828` |
| Primary text | near-white `#FFFFFF` |
| Secondary text | `#B3B3B3` |
| Tertiary text | `#7A7A7A` |
| Accent | Spotify green (brand colour; **do not reuse in Revibe**) |
| Selected tab underline | 18 x 2 px, accent |
| Container radius | ~8 px physical |
| Card radius | 4–8 px |
| Gutter | 10 px physical (8 dp) |
| Row hover | one step lighter than container, no border |

Revibe will keep its own mint accent and logo. Colour roles, spacing rhythm and
hierarchy can be adapted; the actual green and proprietary marks are not part of
the design system we ship.

## 6. Component mapping to Revibe (current → target)

| Spotify component | Revibe component/file | Action |
| --- | --- | --- |
| Top bar | `ui/components/Chrome.kt` `TopBar` | Keep; make search shell responsive |
| Library | `Chrome.kt` `Sidebar` | Add width parameter, resize handle, collapse animation |
| Workspace container | `ui/RevibeApp.kt` Row | Replace fixed Row with resizable shell |
| Right rail | `ui/components/RightPanel.kt` | Accept width + close callback; add Queue sections |
| Player | `Chrome.kt` `PlayerBar` | Keep persistent; later add autoplay indicator |
| Route header | `ui/screens/DetailScreens.kt` | Already present; align actions and spacing |
| Track row | `DetailScreens.kt`, `RightPanel.kt` | Add hover play/menu and manual/auto queue origin |
| Context menu | not yet a shared component | Add `TrackContextMenu` only for real actions |
| Queue row | `RightPanel.kt` `QueueRow` | Extend with origin, drag handle, remove |
| Suggestion overlay | `ui/screens/SearchScreen.kt` (no overlay) | Phase 2; use real search source |

## 7. Non-goals

- Do not copy the Spotify logo, wordmark, icon set, green accent, fonts,
  artwork, generated Daily Mix artwork or any proprietary illustration.
- Do not reproduce the exact promotional copy or "Verified by Spotify".
- Do not build a settings screen that only looks like Spotify's; Revibe settings
  must control real Revibe behaviour.
