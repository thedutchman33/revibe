# Revibe recommendation engine — implementation plan

> **Superseded.** InnerTubeX exposes the native YouTube Music queue and continuation, so the custom
> `RecommendationEngine`/`CatalogRecommendationProvider` and its tests were deleted. See
> [QUEUE_ENGINE.md](QUEUE_ENGINE.md). This document is retained only as design history.

Status: **planned, not yet implemented**. This engine supplies candidates to the
continuous queue. It must be explainable, local-first and based on real
catalog/listening data.

## Principles

1. **Real inputs only.** Track metadata, provider relationships, library items,
   followed artists and actual play/skip/completion history.
2. **No hardcoded recommendation lists.** Seed shelves in `AppState` are browse
   queries, not recommendations; they must not be reused as fake personalized
   results.
3. **No invented audio features.** Revibe has no tempo/energy/danceability
   analysis and must not display any.
4. **No fake statistics.** Do not show listener counts, play counts or
   "match %" unless the provider supplied them.
5. **Explain every candidate.** A generated queue entry carries a reason such as
   "More from Arijit Singh" or "From the album Alfaaz".
6. **Diversity matters.** Do not return eight tracks by the same artist in a row.
7. **Offline-degradable.** If a provider fails, fall back to the existing
   context; never insert a placeholder.

## Inputs and signals

| Signal | Source today | Use |
| --- | --- | --- |
| Seed track (id, artist, album, genre, duration) | `Track` | Primary seed |
| Remaining context tracks | current queue / album / playlist | Immediate queue, not "recommendation" |
| Artist identity | `artistId`, `artist` | Same-artist candidates |
| Album identity | `albumId`, `album` | Album-mate candidates |
| Genre | `Track.genre` where non-null | Soft similarity |
| Liked tracks | `LibraryStore.likedTracks()` | Affinity, exclusions for repeats |
| Liked albums | `likedAlbums()` | Artist/album affinity |
| Followed artists | `followedArtists()` | Artist affinity |
| Play history | `history` table | Recency/frequency, skip penalty |
| Completion/skip | new history columns (queue plan) | Quality signal |
| Local library | `localTracks` | Same artist/genre from owned files |
| YouTube Music related | provider (see below) | Strongest candidate source if supported |

There is no Spotify data, no audio analysis and no external "taste profile".
Cloud taste sync belongs to phase 4 and must be opt-in.

## Candidate sources (in priority order)

### 1. Provider relationship data — real, if available

- **YouTube watch-next / radio**: verify the InnerTubeX version in this project
  exposes a `next`/watch-next call or a generated radio playlist id
  (`RDAMVM<videoId>`). If verified, use its related tracks as candidates.
  If it does not, do not emulate it with a static list; skip to source 2.
- **Artist top tracks**: `CatalogSource.tracksForArtist(artistId, artistName,
  market, limit)`; already implemented for YouTube Music.
- **Artist albums**: `albumsForArtist` then load at most two albums; expensive,
  so cache and cap calls.
- **Album mates**: `CatalogSource.album(albumId, market).tracks` for the seed's
  album; use tracks after the seed, excluding the seed.

### 2. Library affinity — real

- Top artists from liked tracks, play counts and completed plays.
- Followed artists with recent activity.
- Liked albums whose artist/genre overlaps the seed.
- Never recommend a track the user has skipped repeatedly in the last 14 days
  unless it is an explicit manual choice.

### 3. Metadata search — real but weaker

Only when sources 1–2 do not produce enough candidates:

- query `"<artist> <genre>"` and `"<artist> similar"`? The second phrase claims
  similarity the catalog does not guarantee. Prefer `"<artist>"` and
  `"<genre>"`; label the provenance as `Search: <query>`, not "similar artist".
- Search results are candidates, not facts about similarity. Score them lower.

### 4. Cold start

If there is no history:

- seed album remainder;
- seed artist top tracks;
- same-genre provider search;
- if none, queue only the real context and set autoplay `EXHAUSTED`.
- Do not show "Because you listened to…" when no such history exists.

## Scoring model

Scores are integers/floats we can explain and test. No ML model in the first
iteration.

| Feature | Weight |
| --- | ---: |
| Same artist as seed | +4.0 |
| Same album as seed | +3.0 |
| Same genre as seed (non-null) | +1.5 |
| From watch-next/radio candidate | +3.0 |
| Liked track or liked album artist | +2.0 |
| Followed artist | +2.0 |
| Completed-play affinity with artist (0–1) | +2.0 × rate |
| Skip penalty for artist (0–1) | −2.0 × rate |
| Recently played within last 20 tracks | −3.0 |
| Played more than 3 times in 7 days | −1.0 |
| Candidate order from provider (position 0..n) | +1.0 × (1 − pos/n) |
| Same artist already twice in the next 8 entries | −2.5 |
| Same album already twice | −2.0 |
| Local file available | +0.5 (playability/reliability) |
| Unknown/unavailable/preview-only | reject, do not score |

Normalize per candidate group only for ordering; never display a "match score"
to the user. The UI shows the **reason**, not the number.

### Reason strings

- Same artist: `More from <artist>`
- Same album: `From <album>`
- Watch-next: `Related on YouTube Music`
- Library affinity: `Because you liked <artist/track>`
- Genre search: `More <genre>`
- Local: `From your local library`

Only use a reason whose underlying signal actually exists.

## Pipeline

```
Seed + context + exclusions
        |
        v
Collect candidates from enabled sources (max 3 calls / 8 s)
        |
        v
Normalize identity: id, lowercase(title), lowercase(artist)
        |
        v
Hard filters: unavailable, duplicate, explicit exclusions, current queue
        |
        v
Score + diversity rerank
        |
        v
Return top N with reason + provenance
```

- **Budget:** at most 3 network calls per refill; 8 s total timeout; cancel on
  new seed. Cache results per `(seedId, market)` for 5 minutes.
- **Diversity rerank:** greedily pick the highest score, then penalize same
  artist/album for the next picks; stop at 8 entries.
- **Stable output:** sort ties by provider order, then title, for testability.
- **No repeats:** exclude IDs already in queue and the last 20 played unique
  tracks. A track may return in a later session.

## Files to add/change

| File | Change |
| --- | --- |
| `recommendation/RecommendationModels.kt` (new) | `Candidate`, `CandidateSource`, `RecommendationReason`, `ListeningProfile` |
| `recommendation/RecommendationEngine.kt` (new) | collection, filters, scoring, diversity |
| `recommendation/RecommendationProvider.kt` (new) | interface for catalog/history; fakeable |
| `recommendation/CatalogRecommendationProvider.kt` (new) | wraps `CatalogRepository`, `LibraryRepository`, local tracks |
| `youtube/YouTubeMusicSource.kt` | optional real related/watch-next method, only after endpoint verification |
| `catalog/CatalogRepository.kt` | expose helper for album mates/top tracks if not already covered |
| `library/LibraryRepository.kt` | listening profile queries: counts/completion/skips |
| `data/JdbcLibraryStore.kt` | history columns + aggregate queries |
| `playback/AutoplayService.kt` | calls the engine, budget/cache/cancellation |
| `desktopTest/...RecommendationEngineTest.kt` (new) | pure scoring/filter tests |

## Testing strategy

Pure unit tests with a fake provider and fake profile:

1. Same artist/album candidates rank above generic search candidates.
2. Liked/followed artist gets a real boost; skipped artist is demoted.
3. Candidate already in queue or last 20 played is excluded.
4. Two same-artist tracks in a row are penalized; result set has at least two
   artists when candidates exist.
5. Provider failure returns empty, not a fabricated list.
6. Cold start with no history uses only real provider/context candidates.
7. Reason string matches the actual source (never "Because you liked" without a
   like).
8. Timeout/cancellation does not mutate current queue.
9. Deterministic output for fixed inputs.
10. A diagnostic/manual test against the real YouTube Music related path (if
    implemented) records candidate counts, not user data.

## Acceptance criteria

- Starting any real track with autoplay on yields at least 4 relevant, unique
  entries, or an honest `EXHAUSTED` state if no candidate exists.
- Every AUTO entry has a verifiable reason.
- No hardcoded track IDs, fake popularity values, or fake audio features.
- A seed by an artist with only one available track does not loop that track.
- Turning autoplay off stops refills immediately.
- All existing tests remain green; new engine tests are deterministic and do not
  use the network.

## Privacy

- Everything is computed locally from the user's library/history.
- No listening profile leaves the device in this phase.
- Phase 4 cloud sync must be explicit opt-in and must not upload raw history by
  default.
- Logs must not include credentials, stream URLs or full history dumps.
