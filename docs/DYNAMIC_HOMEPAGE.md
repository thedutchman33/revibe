# Dynamic Homepage

Status: **Implemented (first pass).** `UserMaturityService` and `HomepageSectionManager` classify
users from real SQLite activity and order sections accordingly. Personal shelves are live YouTube
Music results for artists that actually appear in history. The top playlist grid contains only
playlists with a real persisted play, up to eight, newest first; it is absent until the first
playlist is played.

Still missing: returning-user boost, genre-derived mixes, per-section caching and the full
loading/empty/error matrix below.

## Non-negotiables

- No hardcoded songs, artists, playlists, mixes, rankings or listening history.
- Recent playlists come from `playlist_plays`, recorded only when a playlist is actually played.
- If the user played two playlists, show two. If they never played one, show no grid.
- A section is omitted when its data requirements are not met.
- A new account with an imported library is not treated as empty.

## Signals (all local, already persisted or derivable)

| Signal | Source |
| --- | --- |
| Plays, completions, skips, played ms | `history` |
| Distinct artists / albums / tracks | join `history` → `tracks` |
| Likes | `liked_tracks`, `liked_albums` |
| Follows | `followed_artists` |
| Playlists and their tracks | `playlists`, `playlist_tracks` |
| Playlist recency | `playlist_plays` (`played_at`, newest first) |
| Local library size | `tracks` where source = LOCAL |
| Recent searches | session/`settings` |
| Recency | `MAX(history.played_at)` |
| Top genres | `tracks.genre` weighted by plays |

Implemented metrics (`UserActivitySnapshot`): playCount, completionCount, skipCount,
distinctArtists, distinctGenres, liked tracks/albums, followed artists, playlists, local tracks,
historySpanDays, daysSinceLastPlay, top artists and top genres. All come from SQL aggregates over
`history`, `tracks`, `liked_tracks`, `liked_albums`, `followed_artists` and `playlists`.

Derived metrics such as completionRate/skipRate remain available from those counts; imported music
is represented by real local-track and playlist counts rather than an invented number.

Account age is never used.

## Maturity states

Thresholds are initial values, tuned with real data, not magic:

| State | Rule (all must hold) |
| --- | --- |
| NEW | plays < 5 **and** likedCount < 3 **and** playlistCount == 0 **and** followedCount == 0 **and** importedCount < 25 |
| EARLY | plays < 30 **or** distinctArtists < 8; not NEW |
| ACTIVE | plays ≥ 30, distinctArtists ≥ 8, and (likes ≥ 5 or playlists ≥ 2) |
| ESTABLISHED | plays ≥ 120, distinctArtists ≥ 20, ≥ 30 days of history, and (playlists ≥ 3 or likes ≥ 25) |

A returning user (daysSinceLastPlay > 30) keeps their state but gets a "Welcome back" ordering
boost; they are not demoted.

`UserMaturityService` is a pure function over a `UserActivitySnapshot`, so it is unit-testable
without the database or network.

## Section catalogue

| Section | Eligibility | Data source | Notes |
| --- | --- | --- | --- |
| Recent playlists | ≥1 playlist play | `playlist_plays` joined to `playlists` | 4×2 grid, up to 8, newest first, first available cover; no empty state |
| Recently played | any real history | `history` | Real rows only |
| Made for you | EARLY+ and top artists exist | Official YouTube Music home mixes + album search for top 3 artists | Only official RD… mixes matched to history artists; no user playlists; either type is omitted when empty |
| Your top mixes | ACTIVE+ | one provider shelf per top artist | Titled "Because you listen to…" |
| Artists you follow | non-NEW and followed > 0 | `followed_artists` | Omit when empty |
| Discovery | every state | YouTube Music search shelves | Real results; always keeps discovery present |
| Trending / Top remixes / Global hits / India top hits | catalog returns real rows | InnerTubeX search | Empty results are omitted, never padded |

No "Based on your listening" or "Recommended for today" label is shown without a matching data
path; those remain future sections rather than decorative headings.

Home chips are `All`, `Bollywood`, `Global Pop`, `Indie`, `90s Songs`, `Punjabi` and `Lo-fi`. Each
chip runs real catalog discovery queries; Global Pop resolves best English/Western songs. There are
no podcast or audiobook chips because those formats are not supported.

## Ordering

Priority score per section:

```
base(state) + recencyBoost + affinityBoost - repetitionPenalty
```

- `base`: NEW favours discovery; ESTABLISHED favours library/personal sections.
- `recencyBoost`: recently played/made-for-you rise after listening.
- `affinityBoost`: sections matching the user's top genre/artist rise.
- `repetitionPenalty`: a section shown prominently for several sessions drops.
- Discovery always keeps at least one eligible slot so the page does not become an echo chamber.

Ordering is computed in `HomepageSectionManager`, not in composables.

## Loading and failure

- Each section loads independently; one provider failure never blanks the page.
- Skeletons for loading, inline retry for errors, explicit empty states.
- Cached last-good payload per section so the home renders instantly, then refreshes.
- Offline: local sections (shortcuts, recent, likes) still render; provider sections show a
  non-blocking offline note.

## Test matrix

| Scenario | Expected |
| --- | --- |
| Fresh install | Discovery only; no recent-playlist grid |
| New, 2 playlists but none played | No recent-playlist grid |
| Played playlists | Up to 8 cards, newest play first, grid disappears after the last is deleted |
| New, 500 imported tracks | Not NEW; local-library-aware sections appear |
| 5 plays | EARLY; no "Made for you" until threshold |
| Active user | Personalised sections; discovery still present |
| Established user | Mixes, recent tracks, recommendations; playlist grid in play-recency order |
| 30-day absence | Same state, "Welcome back" ordering |
| Provider offline | Local sections render; no crash |

Unit tests target `UserMaturityService` and `HomepageSectionManager` with synthetic snapshots.
Integration tests use the real SQLite store. No test asserts a fabricated song or playlist.
