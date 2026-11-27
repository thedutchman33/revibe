# Queue Engine

Status: **Implemented** — native YouTube Music queue, continuation and manual priority.
See [RESEARCH_REPORT_0_7.md](RESEARCH_REPORT_0_7.md) for the investigation history.

## Contract

1. Provider rows are appended in the exact order YouTube Music returns them.
2. Revibe never scores, filters by artist, shuffles or deduplicates provider rows on its own.
3. Manual actions (Play Next, Add to Queue, Remove, Reorder, Clear) operate around provider rows
   and must never rewrite the native context or continuation token.
4. Autoplay off stops refilling after the current queue; it does not mutate existing rows.
5. A stale or failed native response never clears the queue or breaks manual playback.

## Request path

```
PlayerController
  └─ AutoplayService.next(seed, continuation, limit)   // 12 s timeout
       └─ InnerTubeNativeQueueProvider
            ├─ seed is YouTube Music  → use it
            └─ seed is local/archived → map the same title+artist to YouTube Music
                 └─ YouTubeMusicSource.nativeQueue(seed, continuation, limit)
                      ├─ first page: next(WEB_REMIX, videoId, playlistId="RDAMVM<videoId>")
                      └─ next pages: next(WEB_REMIX, continuation=token)
```

Parsing reads:

```
musicQueueRenderer.content.playlistPanelRenderer.contents[].playlistPanelVideoRenderer
```

and extracts video id, title, artist, duration, artwork and the continuation token.

## State

`PlayerController` tracks:

- `nativeSeed` — the track that owns the current provider context;
- `nativeContinuation` — the next-page token (null at the end);
- `autoplayState` — IDLE, LOADING, READY, EXHAUSTED, FAILED;
- a generation counter that discards responses from a superseded context.

A new seed resets the continuation and generation. A continuation page is appended only if it still
belongs to the current generation.

`QueuePolicy` holds the pure rules (insertion, manual-priority placement, duplicate handling,
refill trigger). `QueueModels` holds origins (`PROVIDER`, `MANUAL`, `LIBRARY`) and autoplay states.

## Invariants

| Invariant | Test |
| --- | --- |
| Provider order preserved | `QueueEngineTest.nativeQueueOrderIsPreservedAndContinuationAppends` |
| Continuation appends, never replaces | same test |
| Play Next sits before provider rows | manual-priority test |
| Add to Queue appends after provider rows | manual-priority test |
| Autoplay off does not refill | `autoplay off keeps the queue finite` |
| Provider failure keeps manual playback working | `nativeQueueFailureKeepsManualPlaybackWorking` |
| Stale context responses are ignored | stale-context test |
| Auto-advance consumes native rows | auto-advance test |
| History records provider tracks | history test |

## Why it can still differ from YouTube Music

- Revibe uses an anonymous InnerTube session; YouTube Music personalises radio with account history.
- The seed may already be present in the returned page; Revibe shows it as returned.
- Some rows can be unavailable in the user's region and fail at resolve time. The queue row still
  exists because the provider returned it.
- Album and playlist playback use their own contexts; they are deliberately not converted into
  radio. Autoplay only applies when the provider supplies a continuation.

## Manual operations

| Action | Effect on provider context |
| --- | --- |
| Play Next | Inserts immediately after the current row; context untouched |
| Add to Queue | Appends after existing rows; context untouched |
| Remove | Removes that row only; continuation stays valid |
| Reorder | Changes local order only; provider order is not claimed afterwards |
| Clear | Empties local queue and drops the continuation; next play starts a new context |
| Autoplay off | Keeps rows, stops continuation requests |
| Save queue | Writes the current order into a normal SQLite playlist |

## Diagnostics

`REVIBE_LIVE=1 ./gradlew :shared:desktopTest --tests '*YouTubeRelatedLiveTest*'` performs a real
`next()` call and prints `NATIVE_QUEUE count=… continuation=…` plus the first rows. Last verified:
40 rows with continuation, and a continuation page of 40 further unique tracks.

No signed URL, cookie or token is printed.
