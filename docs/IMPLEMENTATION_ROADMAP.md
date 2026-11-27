# Implementation Roadmap

Milestone: **0.7.0 — native queue, catalog cleanup, provider research**.
Canonical research report: [RESEARCH_REPORT_0_7.md](RESEARCH_REPORT_0_7.md).

Status vocabulary: **Implemented**, **Partially implemented**, **Not implemented**, **Blocked**.

## Stage 1 — Research and architecture: Implemented

- InnerTubeX v0.13.1 inspected; `next()` and continuation verified with live rows.
- Metrolist, BitChord and VIVI queue approaches studied; no code copied (GPL-3.0 respected).
- MusicBrainz, Cover Art Archive, Last.fm, ListenBrainz, Spotify, Canvas and recognition access
  verified or explicitly rejected. See [PROVIDER_INTEGRATION_MATRIX.md](PROVIDER_INTEGRATION_MATRIX.md).
- Native queue divergence explained in [QUEUE_ENGINE.md](QUEUE_ENGINE.md).
- Homepage maturity and section rules designed in [DYNAMIC_HOMEPAGE.md](DYNAMIC_HOMEPAGE.md).

## Stage 2 — Native queue: Implemented

- `next(WEB_REMIX, videoId, playlistId = "RDAMVM<videoId>")`, continuation tokens, provider order.
- Manual Play Next/Add to Queue priority; autoplay off; failure and stale-context guards.
- 40-row live queue and 40-row continuation verified.
- Tests: native order, continuation, manual priority, autoplay off, failure, stale context,
  auto-advance, history.

## Stage 4 — Provider architecture: Partially implemented

Done:

1. Provider interfaces defined (`ArtworkProvider`, `MusicMetadataProvider`, `LyricsProvider`,
   `RecognitionProvider`, `ListeningHistoryProvider`, `ExternalAccountProvider`,
   `VisualBackgroundProvider`).
2. MusicBrainz + Cover Art Archive artwork adapter: exact album/artist matching, 1 req/s gate,
   User-Agent, in-memory cache, 8 s timeout, live test, no playback impact.
3. Artwork resolver prefers catalog artwork and is wired into Now Playing, footer and miniplayer.
4. Central lyrics resolver: LRCLIB primary, lrc.red and Unison fallbacks, TTML word/line parsing,
   title/artist/version/duration guards, SQLite cache, source credit links. Live English and Hindi
   tests pass ([LYRICS_PROVIDER_ARCHITECTURE.md](LYRICS_PROVIDER_ARCHITECTURE.md)).

Remaining:

5. SQLite artwork cache and release-group matching.
6. Last.fm is blocked on an app key (and Pro from 1 Dec 2026); ListenBrainz needs an opt-in user token.
7. Spotify OAuth metadata import only; never playback or credentials.
8. Apple Music/Tidal artwork need private/credentialed APIs; not added.
9. Canvas remains unimplemented and documented ([SPOTIFY_CANVAS_INTEGRATION.md](SPOTIFY_CANVAS_INTEGRATION.md)).
10. Recognition remains blocked until a licensed Windows-capable provider and consent flow exist.

## Stage 5 — Dynamic homepage: Implemented (first pass)

Implemented: `UserActivitySnapshot` + SQL aggregates, `UserMaturityService`,
`HomepageSectionManager`, NEW/EARLY/ACTIVE/ESTABLISHED plans, Getting Started, real playlist
shortcuts, recently played, provider-seeded Made For You/top artist mixes, followed artists,
discovery, timestamps, category empty states.

Not implemented: returning-user boost, genre-derived mixes, per-section cache and the full
loading/error matrix for every provider shelf.

## Stage 6 — Right sidebar: Partially implemented

Implemented: Now Playing header/hero/like/lyrics preview/About/credits, queue and recently played
tabs, native Up Next rows, manual queue controls, drag-and-drop reordering, autoplay toggle,
close/reopen, persisted width, relative play timestamps, provider artwork.

The right rail now stays reachable at the 1120dp minimum by shrinking the library first.

Not implemented: full queue-row context menu, artwork prefetching, per-row "played at" actions.

## Stage 7 — Windows completion: Partially implemented

Implemented: fullscreen player, media keys, resizable/collapsible shell, minimum-width layout,
DPI-aware window/miniplayer sizing, real always-on-top miniplayer, keyboard shortcuts, honest
device limitation.

Visual QA covered 1120/1200/1440dp at 125% DPI ([VISUAL_QA_0_8.md](VISUAL_QA_0_8.md)). Not implemented:
device selection, notifications, friends activity, search pagination and persisted suggestions,
accessibility pass, full DPI matrix, long-session soak.

## Stage 8 — Accounts and cloud: Not implemented (blocked on a project)

Supabase auth/sync requires a real Supabase project URL, anon key and redirect configuration to
implement and verify. None is present in this repository, so no untested auth code was added.
The design constraint is fixed: SQLite stays authoritative offline, sync is additive and
conflict-aware, and no service-role key may ship in the client.

## Stage 9 — Final QA: Ongoing

- `./gradlew :shared:desktopTest` (**181 tests, 0 failures, 46 suites**), live queue/artwork/lyrics diagnostics.
- Visual QA at 1120/1200/1440dp on 125% DPI, with remaining states documented.
- Still required: clean-machine MSI/EXE install, full DPI matrix, network interruption and
  long-session tests, maturity-state and miniplayer visual checks, accessibility.

## Explicit non-goals

- No DRM bypass, paid-access circumvention, cookie/token scraping or credential extraction.
- No Spotify/Apple/Tidal audio playback.
- No undocumented Spotify Canvas access.
- No fabricated metadata, history, rankings or recommendations.
