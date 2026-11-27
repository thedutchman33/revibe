# Spotify for Windows — interaction inventory

Status legend:

- **Observed** — seen in a live capture or exercised live.
- **Static** — visible in a capture but the outcome was not exercised.
- **Unverified** — not observed; must not be described as supported.
- **Revibe decision** — how Revibe will handle the gap (not a Spotify claim).

Reference images are in `screenshots/`.

## 1. Global chrome

| Control | Where | Observed behaviour | Status | Revibe decision |
| --- | --- | --- | --- | --- |
| Back | Top left | Returns to previous route; dims when unavailable | Static | Keep existing Back; preserve forward stack |
| Forward | Top left | Re-enters a route after Back | Static | Keep existing Forward |
| Home | Top centre-left | Opens Home; filled/selected state | Observed | Keep |
| Search field | Top centre | Focus opens suggestions; Enter searches; results replace centre | Observed | Keep, extend with debounced suggestions |
| Browse icon in field | Search field right | Not exercised | Unverified | Keep as a shortcut to Search/Genres once available |
| Notification bell | Top right | Not exercised | Unverified | Out of scope for this phase |
| Friends | Top right | Not exercised | Unverified | Out of scope |
| Avatar | Top right | Opens account menu | Observed | Revibe profile menu with Settings, About, data folder, sign-out if cloud |
| Minimise / maximise / close | Top right | Native window controls | Observed (visible) | Keep existing window controls |
| Drag caption area | Top bar | Moves window (inferred from integrated caption) | Unverified | Keep existing window drag behaviour |

## 2. Left library

| Control | Observed behaviour | Status |
| --- | --- | --- |
| `Create` pill | Opens playlist creation (inferred from Spotify convention) | Static |
| Expand/collapse chevron | Collapses to icon rail; centre expands | Observed |
| `Playlists` chip | Filters library to playlists | Static |
| `Podcasts` chip | Filters library to podcasts | Static |
| `Albums` chip | Filters library to albums | Static |
| `Artists` chip | Filters library to artists | Static |
| Library search icon | Not exercised | Unverified |
| `Recents` control | Not exercised | Unverified |
| List/grid toggle | Not exercised | Unverified |
| Liked Songs row | Opens Liked Songs page | Observed |
| Playlist row | Opens playlist page | Observed |
| Album/artist row | Opens detail page (inferred) | Static |
| Row hover | Row lightens; no overflow menu visible in stills | Static |
| Rail artwork shortcuts | Navigate to saved items | Static; outcome unverified |

Revibe already has library filters, create, liked and local rows. Add a resize
handle and keep the rail artwork visible in compact mode.

## 3. Home and discovery

| Control | Observed behaviour | Status |
| --- | --- | --- |
| Filter chips | Switch Home content type | Static |
| Shortcut card | Opens the selected collection/album/playlist | Static |
| Card hover play | Circular play appears over artwork | Static |
| Shelf card | Opens detail route | Static |
| `Show all` | Expands shelf (inferred) | Unverified |
| Horizontal card clipping | Indicates more content off-screen | Observed (visual only) |

Revibe should keep real shelves from the catalog and add card hover play only
where the destination is resolved.

## 4. Search

| Control | Observed behaviour | Status |
| --- | --- | --- |
| Type >= 2 chars | Suggestions panel drops down | Observed |
| Suggestion query row | Replaces query/search (inferred) | Static |
| Suggestion entity row | Opens entity / adds to library via trailing control | Static; outcome unverified |
| `Enter` | Runs search | Observed |
| Search results chips | Filter result groups | Static |
| Result row | Opens song/album/artist | Static |
| Trailing `+` / check | Add/remove from library | Static; outcome unverified |
| `Following` | Toggles artist follow | Static |
| Top result play | Plays playlist/album | Static |

Revibe's search is already real (YouTube Music). The interaction gap is
suggestions and the add/check affordance; those are planned, not copied.

## 5. Album / playlist / artist pages

| Control | Observed behaviour | Status |
| --- | --- | --- |
| Large green play | Starts context from the first track | Static |
| Shuffle | Starts shuffled | Static |
| Save/add circle | Adds to library | Static |
| Download | Not exercised | Unverified |
| Overflow `…` | Opens page menu | Static |
| Track row click | Plays the track | Static (current row shows playing state) |
| Track hover | Reveals play and `…`; row highlight | Observed |
| Track `…` | Opens context menu | Observed |
| Column sort | Not exercised | Unverified |
| Playlist mood chips | Filter playlist tracks | Static |
| Search in playlist | Not exercised | Unverified |
| Artist follow | Toggles following | Static |
| Artist `Popular` rows | Play track / open menu | Static |
| `See more` | Expands popular list (inferred) | Unverified |

Revibe already has album/artist/playlist screens and real play actions. The
queue work will make the green play action populate a context plus autoplay.

## 6. Queue and right rail

| Control | Observed behaviour | Status |
| --- | --- | --- |
| Queue tab | Shows Now playing + Next up | Observed |
| Recently played tab | Not captured | Unverified |
| Close `x` | Closes right rail | Static (control visible; clean closed frame not kept) |
| Queue row click | Plays that row (inferred) | Static |
| Queue `…` | Row actions (inferred) | Unverified |
| Drag reorder | Not exercised | Unverified |
| Remove from queue | Not exercised | Unverified |
| Add to queue | Menu action exists | Static |
| Now playing card | Shows current track; like check | Observed |
| Lyrics preview | Shows current lyric snippet | Observed |

For Revibe, queue must be an ordered model with **manual priority**: user-added
tracks sit ahead of autoplay suggestions. Drag/drop and remove should be real,
not decorative.

## 7. Player bar

| Control | Observed behaviour | Status |
| --- | --- | --- |
| Artwork | Navigates to now-playing/expanded view (inferred) | Unverified |
| Title/artist | Artist name opens artist (inferred) | Static |
| Like/check | Toggles saved state | Static |
| Shuffle | Toggles shuffle | Static |
| Previous | Restarts current after 3 s, else previous | Static |
| Play/pause | Toggles playback | Observed (state changes in captures) |
| Next | Advances | Static |
| Repeat | Cycles off/all/one | Static |
| Seek bar | Seeks | Static |
| Time labels | Display position/duration | Observed |
| Lyrics/mic | Opens lyrics | Observed |
| Queue | Opens Queue tab | Observed |
| Volume icon | Mutes/unmutes | Static |
| Volume slider | Changes volume | Static |
| Now playing/fullscreen | Expands player (inferred) | Unverified |

Revibe must keep the player persistent while resizing/collapsing containers.

## 8. Context menu

| Menu item | Shortcut shown | Observed outcome |
| --- | --- | --- |
| Add to playlist | — | Unverified |
| Add to Liked Songs | `L` | Unverified |
| Add to queue | `Q` | Unverified |
| Hide in this album | — | Unverified |
| Exclude from your taste profile | — | Unverified |
| Sleep timer | — | Unverified |
| Go to song radio | `R` | Unverified |
| Go to artist | `Alt A` | Unverified |
| View credits | — | Unverified |
| Share | — | Unverified |

Only the menu itself is observed. Revibe should implement only actions it can
back with real data: add to playlist, like, add to queue, go to artist, view
credits (local metadata) and share via clipboard. No fake "radio" unless the
recommendation engine can actually build one.

## 9. Keyboard and accessibility

- Shortcut hints visible in the context menu: `L`, `Q`, `R`, `Alt A`.
- No global shortcut was exercised. `Ctrl+K`, `Space`, `Ctrl+Left/Right` and
  media keys are **unverified**.
- Focus order, tab trapping in menus, Escape-to-close and screen-reader labels
  were **not** tested.

Revibe already has window-level shortcuts in desktop code; extend them only
after checking the existing handlers, and document the final set.

## 10. Interaction rules Revibe will enforce

These are product decisions, not claims about Spotify:

1. A click on a side-panel edge starts a drag; double-click resets the width.
2. Layout widths and collapsed states persist in SQLite settings.
3. The centre never drops below 480 dp; side panels clamp before content.
4. Closing the right rail is explicit and reversible; selecting Queue/Lyrics
   reopens it.
5. Play on a track creates a queue with **manual** and **auto** sections.
6. Manual additions always outrank autoplay suggestions.
7. Autoplay can be disabled persistently.
8. Every queue recommendation is traceable to a seed or a real listening signal.
