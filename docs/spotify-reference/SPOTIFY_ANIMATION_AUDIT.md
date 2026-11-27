# Spotify for Windows — animation audit

## Scope and honesty note

No screen recording was made. This audit is based on before/after stills and
live use while navigating. **No durations, easing curves or frame rates were
measured**, so this document does not claim Spotify's exact motion values. What
is described as "observed" is the visible start/end state; motion detail is
marked unverified.

## 1. What was observed

| Transition | Start → end | Motion impression | Evidence |
| --- | --- | --- | --- |
| Library collapse | Expanded 454 px container → 89 px icon rail; centre expands | The containers resize horizontally and the centre content relayouts; no vertical movement of the player | `spotify_home_full.png`, `spotify_left_collapsed_full.png` |
| Queue open | Right rail shows recommendation/now-playing card → Queue tabs/list | Rail content replaces in place; width stays ~441 px | `spotify_home_full.png`, `spotify_queue_full.png` |
| Lyrics open | Centre shows previous route → full lyrics page | Centre content crossfades/replaces; right queue and bottom player stay | `spotify_lyrics_full.png` |
| Search suggestions | Search field focused → overlay suggestions panel | Panel appears under the field over the centre; results do not jump out of place | `spotify_search_suggestions_full.png` |
| Route change | Home → search results → album/playlist/artist | Centre route content changes; shell stays fixed | captured route stills |
| Row hover | Resting row → highlighted row with play glyph | Instant-looking highlight in stills; hover motion unverified | `spotify_album_full.png` |
| Card hover | Artwork card → circular play overlay | Visible end state only | `spotify_home_scrolled_full.png` |
| Play/pause | Paused glyph → playing glyph; equaliser state on row | End states observed in player and queue | `spotify_queue_full.png` |

"Motion impression" is deliberately qualitative. The captures cannot tell a
120 ms fade from a 200 ms fade.

## 2. Layout behaviour revealed by the stills

- **Width is the animated property for the library**, not opacity: content is
  reflowed into a rail, and the centre gains the released width. A pure
  `AnimatedVisibility` fade would not reproduce the layout change.
- **The player bar is outside the animated workspace.** Its top edge remains at
  y = 1023 in both expanded and collapsed captures.
- **The right rail keeps its width when content changes.** Queue and
  recommendation views are swaps inside the same 441 px container.
- **The top bar is static.** Search/home/history controls do not move when the
  library collapses.
- **Scroll position is per route/container.** The library scrolls independently
  of the centre; the player overlays neither.

## 3. Explicitly unverified

- Collapse/expand duration and easing; whether width and content animate on
  separate clocks.
- Right-rail slide/scale/fade when opened or closed (only the `x` control was
  seen; no clean closed frame was retained).
- Page-route crossfade vs. slide vs. instant replacement.
- Hover fade duration and artwork-scale behaviour.
- Skeleton/loading shimmer, image decode fade-in, lyric line scrolling and
  active-line transition timing.
- Drag-resize ghost/handle affordance; no resize interaction was observed.
- Reduced-motion behaviour.
- Queue reorder animation, toast timing, context-menu open/close animation.
- Fullscreen player transition.

## 4. Revibe motion contract (our decision)

These are Revibe product choices chosen for a calm desktop feel, not copied
Spotify values:

| Interaction | Revibe target |
| --- | --- |
| Sidebar collapse/expand | 220 ms `FastOutSlowInEasing`, animate width; content fades at the midpoint |
| Drag resize | No animation while dragging; 120 ms settle on release |
| Right panel open/close | 180 ms fade + 12 dp horizontal slide; width changes only on close/open, not on resize |
| Route change | 140 ms crossfade; keep scroll state per route |
| Hover surface | 120 ms background colour; no layout shift |
| Card play overlay | 120 ms fade/scale from 0.92 to 1.0 |
| Toast | 160 ms fade in, 2.6 s hold, 160 ms fade out |
| Lyrics active line | 180 ms colour/scale; no automatic scroll while the user is dragging |
| Reduced motion | Respect the OS setting where the desktop toolkit exposes it; fall back to instant |

Motion must never delay input or block the player. The player and the audio
engine are independent of these UI animations.
