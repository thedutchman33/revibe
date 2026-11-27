# Windows UI Design System

## Intent

Revibe uses the interaction model people already know from desktop music players — library on the
left, browsing in the centre, context on the right, playback always at the bottom — with an original
visual identity. Spotify's desktop app was used as an interaction reference from screenshots and the
supplied screen recording. No Spotify assets, fonts, icons, code or branding are used.

Brand: **REVIBE — Your music. Your vibe.** Lime signal colour on near-black, heavy display type,
quiet metadata, artwork as the only loud colour.

## Colour tokens

| Token | Value | Use |
| --- | --- | --- |
| `Base` | `#08090A` | Window and content background |
| `Sidebar` | `#0B0D0E` | Library rail |
| `Surface` | `#111314` | Cards, settings sections, right panel |
| `SurfaceRaised` | `#181B1C` | Menus, toasts, inputs |
| `SurfaceHover` | `#212526` | Row and control hover |
| `Outline` | `#242829` | Hairlines |
| `TextPrimary` | `#F3F5EF` | Titles, active labels |
| `TextSecondary` | `#9AA39B` | Artist, metadata |
| `TextTertiary` | `#69726B` | Captions, disabled |
| `Accent` | `#C8F45E` | Play, active states, progress |
| `AccentHover` | `#D7FF79` | Accent hover |
| `OnAccent` | `#101408` | Text/icons on accent |
| `Danger` | `#FF7A72` | Destructive actions, errors |

Artwork palettes feed `LocalAccent` with a luminance floor. Backgrounds use alpha 0.08–0.12 so the
interface never turns into a colour wash.

## Type scale

System sans (Segoe UI on Windows), tight tracking on display sizes.

| Role | Size / weight | Example |
| --- | --- | --- |
| Hero | 52 sp / Black, −1.8 tracking | Artist name, page heroes |
| Display | 30 sp / ExtraBold | Section page titles |
| Heading | 21 sp / Bold | Shelf titles, section cards |
| Title | 15 sp / SemiBold | Card titles |
| Body | 13.5 sp / Regular | Body copy |
| Body strong | 13.5 sp / SemiBold | Track title, labels |
| Label | 12.5 sp / Medium | Chips, buttons |
| Caption | 11.5 sp / Medium | Artist lines, metadata |
| Micro | 10 sp / Bold, 1.2 tracking, uppercase | `ALBUM`, `PLAYLIST`, status |

## Layout

- Window: 1440×900 default, minimum 1120×720, frameless with custom controls and edge resize.
- Top bar: 64 px — history arrows, home pill, centred search (max 520 px), settings, profile,
  window controls.
- Three containers with 8 dp drag gutters. Side panels are resizable and persisted.
- Sidebar: 280 dp default, 220–420 dp range, 72 dp compact rail. Logo, Home, Search, library header,
  filter chips, Liked Songs, playlists, saved albums, followed artists, local files, Settings.
- Content: scrollable, centre minimum 480 dp; 24 dp left gutter, 16 dp right gutter; clipped to a
  rounded surface.
- Right panel: 340 dp default, 280–420 dp range, closable and reopened from Queue/Lyrics; tabs Now
  playing / Lyrics / Queue.
- Player bar: 94 dp, always visible outside the resized workspace.
- Persisted layout: `leftPanelWidth`, `rightPanelWidth`, `compactSidebar`, `showRightPanel`.
  Contract and clamps live in `model/LayoutSpec.kt`; evidence in `docs/revibe-layout/`.

Shelves are horizontal rows of 168 px artwork cards. Track tables use an index column, title/artist,
album, duration, like and overflow. Rows are 56 px high with 6 px radius hover.

## Components

- **Seek bar:** custom Canvas; 4 px track, 6 px while dragging, white thumb on hover, accent fill.
  Material Slider was rejected because its internals fight this geometry.
- **Buttons:** accent pill, outline pill, ghost; 20 px horizontal padding, 10 px vertical.
- **Chips:** pill, `SurfaceRaised`; selected is white with dark text.
- **Cards:** artwork scales to 1.025 on hover; accent play button fades in bottom-right.
- **Hover rows:** no ripple; background `SurfaceHover` plus pointer cursor.
- **Modals:** dimmed backdrop, 420 px panel, custom text field.
- **Toasts:** bottom centre above the player, 2.6 s.
- **Empty states:** title, explanation, optional action; never a dead blank screen.
- **Skeletons:** gradient blocks matching the final layout.

## Motion

- Accent colour animates over 700 ms when artwork changes.
- Screen changes cross-fade.
- Hover scale and colour transitions are short (120–180 ms) and never block input.
- No decorative animation competes with artwork.

## Window behaviour

- Frameless (`undecorated = true`) for a Spotify-like edge-to-edge surface.
- Drag the top bar to move; custom minimise/maximise/close.
- Eight edge handles resize the window with correct cursors; minimum size enforced.
- Native Windows snapping is not available in a frameless window in this milestone.

## Accessibility and keyboard

Current gaps, to address before a public release:

- Focus rings are not drawn consistently; keyboard traversal exists through Compose but is not
  styled.
- Icon buttons have content descriptions, but no global shortcut map (Space/arrows) yet.
- Contrast of `TextTertiary` on `Base` is intentionally quiet; it should be raised for body text.
- Text scaling above 125% has not been tested.
- Reduced-motion preference is not respected.

## Comparison with the reference

Kept: three-zone layout, persistent player, shelf browsing, right-hand context panel, filter chips,
lyrics view, hover-revealed play affordances, artwork-driven tinting.

Changed deliberately: Revibe's own logo, lime palette, typography, card geometry, queue and settings
design, window chrome, and all copy. The result should feel familiar in use and unmistakably not
Spotify in appearance.
