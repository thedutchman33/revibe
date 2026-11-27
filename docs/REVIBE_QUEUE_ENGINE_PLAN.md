# Revibe intelligent continuous queue — implementation plan

> **Superseded.** The queue now consumes the native YouTube Music `next()` queue and continuation.
> Current contract and tests: [QUEUE_ENGINE.md](QUEUE_ENGINE.md).

Status: **planned, not yet implemented**. This is the highest product priority
after the shell foundation. It must use real providers and real listening data;
no hardcoded song lists, fake "audio features", invented popularity numbers or
decorative queue entries.

## Goal

Pressing Play on any track produces an endless, relevant listening session:

1. the chosen context is queued;
2. real recommendations are added before the queue runs out;
3. manual queue additions always take priority over generated tracks;
4. the next track starts automatically when the current one ends;
5. duplicate and stale recommendations are suppressed;
6. autoplay can be turned off and the choice persists;
7. every generated entry explains why it is there.

The queue must remain useful when offline: the existing context queue still
plays; autoplay simply reports that it could not refill.

## Current implementation

`shared/src/commonMain/kotlin/com/revibe/playback/PlayerController.kt` owns:

- `sourceQueue` (unshuffled order);
- `PlayerState.queue: List<Track>`, `index`, `contextName`;
- `playQueue`, `playAt`, `next(auto)`, `previous`, shuffle/repeat;
- `addToQueue`, `playNext`, `removeFromQueue`, `moveInQueue`;
- `onEnded() -> next(auto = true)`;
- `loadCurrent` records play immediately via `library.recordPlay(track)`.

`shared/src/commonMain/kotlin/com/revibe/playback/PlaybackEngine.kt` defines
`PlayerState`; `hasNext` currently depends only on index/repeat.

Limitations:

- Queue is a flat `List<Track>` with no origin, entry identity or reason.
- The queue stops at its last item unless repeat is on.
- A manual "play next" can sit beside context tracks but there is no durable
  manual-vs-auto priority rule.
- `recordPlay` runs when a stream is loaded, not when a track is actually
  listened to; there is no skip/completion signal.
- There is no provider hook for "more like this".

## Data model

Add to `shared/src/commonMain/kotlin/com/revibe/playback/QueueModels.kt` (new):

```kotlin
enum class QueueOrigin { CONTEXT, MANUAL, AUTO }

data class QueueEntry(
    val entryId: String,          // stable, unique even if a track repeats
    val track: Track,
    val origin: QueueOrigin,
    val reason: String? = null,   // short, real explanation for AUTO entries
    val seedTrackId: String? = null,
)

enum class AutoplayState { OFF, IDLE, REFILLING, READY, EXHAUSTED, FAILED }
```

`PlayerState` evolves to hold `entries: List<QueueEntry>` and `index`, plus:

- `autoplayEnabled: Boolean`;
- `autoplayState: AutoplayState`;
- `autoplayMessage: String?` (real failure reason, not a fake status).

Keep a derived `queue: List<Track>` property so the first UI integration does not
have to change every screen at once. Update the 17 existing queue references
(the controller plus `Chrome.kt` and `RightPanel.kt`) to use entries where
origin matters.

SQLite settings gain `autoplay` (default **on**). No new table is required for
the live queue; optional session restore is a later item and not assumed.

## Queue invariants

1. **Manual priority.** `playNext` and `addToQueue` insert into the manual
   region before any AUTO entries. Context tracks are not "manual"; only explicit
   user actions are.
2. **Stable order.** Context entries keep the order supplied by the route. AUTO
   entries append after manual entries and never reorder existing items.
3. **Dedupe.** An AUTO candidate is rejected if its track id is already in the
   queue, or if normalized `(title, artist)` matches an existing entry or the
   last 20 played tracks. Manual adds are allowed to duplicate intentionally but
   must have distinct `entryId`.
4. **Lookahead.** Refill when the number of entries after the current index
   (manual + auto) is **<= 2** or when the current track has **20 seconds**
   remaining, whichever comes first. Refill target: 8 entries ahead.
5. **Autoplay off.** No fetch and no append. Reaching the end stops and sets
   `autoplayState = OFF`; it does not silently restart context.
6. **Repeat.** `RepeatMode.ONE` replays the same track and does not fetch.
   `RepeatMode.ALL` may wrap the existing queue; autoplay should not double the
   context indefinitely.
7. **Stale responses.** Each fetch carries a generation and seed track id. If the
   user starts another context, removes the seed, or clears the queue, discard
   the response.
8. **Failure.** A provider error leaves the current queue untouched, sets
   `FAILED` with a short diagnostic, and never blocks playback. Retry at most
   once per track/context, then wait for the next natural trigger.
9. **No fake entries.** Never insert a placeholder row, preview, unavailable
   track, or duplicate just to keep the queue populated.

## Components

| File | Responsibility |
| --- | --- |
| `playback/QueueModels.kt` (new) | `QueueEntry`, `QueueOrigin`, `AutoplayState`, pure queue helpers |
| `playback/QueuePolicy.kt` (new) | pure functions: insert manual, append auto, dedupe, refill threshold, pick next |
| `playback/PlayerController.kt` | own queue entries; trigger refill; consume results; expose autoplay commands |
| `playback/AutoplayService.kt` (new) | coroutine fetch, generation guard, cache, retry policy |
| `recommendation/RecommendationEngine.kt` (new) | candidate collection + scoring; see separate plan |
| `recommendation/RecommendationProvider.kt` (new) | provider interface used by tests |
| `youtube/YouTubeMusicSource.kt` | add real related/watch-next candidate method if the InnerTubeX build supports it |
| `data/JdbcLibraryStore.kt` | history migration: completion/skip signals and aggregates |
| `library/LibraryStore.kt`, `LibraryRepository.kt` | expose `recordPlayback`, `recordSkip`, `listeningProfile` |
| `AppGraph.kt`, `desktopMain/DesktopGraph.kt` | construct catalog before autoplay service; inject provider |
| `ui/AppState.kt` | autoplay toggle, queue state exposure, manual add actions |
| `ui/components/RightPanel.kt` | Queue tabs show Now playing, Next up, manual/auto sections |
| `ui/components/Chrome.kt` | player autoplay/queue affordance if added later |

### Wiring order

`DesktopGraph` currently creates `player` before `catalog`. The autoplay service
needs the catalog. Reorder construction:

```
store -> library -> youtube -> catalog -> recommendationEngine
      -> autoplayService -> player(engine, library, scope, resolver, autoplayService)
```

`PlayerController` must accept `AutoplayService? = null` so existing tests and
the JavaFX fallback compile unchanged. No network call is made when it is null.

## Refill flow

```
onProgress / onEnded / manual queue change
        |
        v
QueuePolicy.needsRefill(entries, index, autoplayEnabled)
        | no -> return
        v
AutoplayService.request(seedTrack, context, excludeIds, generation)
        | suspend, max 8s, max 3 provider calls
        v
RecommendationEngine.candidates(...) -> score -> dedupe
        |
        v
if generation still current:
   append AUTO entries after manual region
   autoplayState = READY
else discard
```

`onEnded()` must not wait for the network before advancing. If entries already
exist, advance immediately. If the queue is empty at the end, set
`autoplayState = REFILLING`, keep the player idle, and start the next entry only
when a successful result is appended. A failed result stops cleanly.

## UI behavior

- Queue panel sections:
  - **Now playing** (current entry, no index number);
  - **Next up** — manual entries first, marked "Added by you";
  - **Autoplay** — generated entries with a short reason (`More from Arijit
    Singh`, `From this album`, `Because you liked ...`).
- Manual entries show a drag handle and remove action; AUTO entries can be
  removed but are not user-reorderable until a later polish pass.
- An autoplay switch lives in Settings and, if space allows, in the Queue
  header. Its state persists through `SettingsRepository`.
- `autoplayState` shows "Finding more music…" only while a real fetch is active;
  failure shows a retry action, not an endless spinner.
- No "Recently played" tab until history is real and tested; the existing
  history table can back it later.

## Persistence and history signals

Add to the `history` table (migration-safe `ALTER TABLE` with defaults):

- `played_ms INTEGER NOT NULL DEFAULT 0`
- `completed INTEGER NOT NULL DEFAULT 0`
- `skipped INTEGER NOT NULL DEFAULT 0`

Record behavior:

- Do **not** record on `loadCurrent`. Record a play at `max(30s, 50% of
  duration)` or when the track completes.
- A skip is recorded only when the user advances after < 30s or < 20% of the
  track; record `skipped = 1`.
- Aggregate per track/artist: play count, completion rate, skip rate, last
  played. These are real observations, not fake audio features.
- Keep existing `recentlyPlayed` queries working during migration.

## Tests (fake engine + fake provider, no network)

1. Play a 3-track context: no refill until threshold; then exactly 8 unique AUTO
   entries appended.
2. Manual `playNext` during a refill appears before AUTO entries and survives a
   stale response.
3. Duplicate id and normalized title/artist are rejected.
4. Autoplay off: no fetch, playback stops at the end, state `OFF`.
5. Provider failure: queue unchanged, `FAILED`, current track still plays and
   manual next still works.
6. Stale generation: start context B while A's refill is in flight; A's result
   is discarded.
7. Repeat ONE/ALL interactions do not duplicate the queue or fetch incorrectly.
8. Remove current/next entry adjusts index and autoplay threshold correctly.
9. History: no play recorded on load; play recorded at threshold; skip recorded
   under threshold; completion recorded once.
10. Existing 26 tests remain green, especially `YouTubeQueueTest` and
    `ExpiredStreamRecoveryTest`.

Use a deterministic fake provider with a fixed candidate list and delays to test
generation cancellation. Do not assert network behavior in unit tests; add a
manual diagnostic for the real YouTube endpoint.

## Rollout

1. Data model + policy + pure tests (no UI change).
2. Controller refactor behind `autoplayService = null`; verify all existing
   tests.
3. Inject the real recommendation engine in desktop graph; log candidate counts
   and provenance at debug level (no track titles in release logs if avoidable).
4. Queue UI sections and autoplay toggle.
5. Manual soak test: play 30+ tracks from different seeds, skip, add next,
   remove, toggle autoplay, restart app; record actual behavior in
   `docs/PROJECT_STATUS.md`.

## Non-goals

- Spotify API, Spotify login, Spotify queue import.
- Fake "song radio" that is only a static search.
- Audio feature vectors or invented tempo/energy/danceability values.
- Cloud sync of the queue (phase 4).
