# Revibe 0.7 Technical Research Report

Research date: 26 November 2026
Scope: native YouTube Music queue, dynamic homepage maturity, right sidebar, supporting providers.
Status vocabulary is the same as the rest of the docs: **Implemented**, **Partially implemented**,
**Not implemented**, **Blocked**.

---

## 1. Executive summary

1. **The queue is native.** Revibe asks InnerTubeX `next(WEB_REMIX, videoId, playlistId =
   "RDAMVM<videoId>")` and consumes the returned `musicQueueRenderer → playlistPanelRenderer`
   rows in the order YouTube Music returns them, then follows the provider's continuation token.
   There is no local ranking, scoring or artist-discography substitution. This was implemented and
   verified before this report; see [QUEUE_ENGINE.md](QUEUE_ENGINE.md).
2. **The earlier mismatch is explained.** The previous queue was built from artist top-songs and
   related shelves, not `next()`. That is why "Taare" produced Parwaaz / Deewar / Kora Panna /
   Likhta Hoon — those are catalogue rows for the same artist, not YouTube Music's radio queue.
   The current path returns the mixed provider queue (for example Aarzu, Paro, Udi Udi, Alfaaz,
   O Piya in the live check).
3. **Provider enrichment is partly implemented.** LRCLIB and Wikipedia remain live, and a
   MusicBrainz + Cover Art Archive artwork fallback is now wired into Now Playing with a live test.
   The remaining candidate services were verified or explicitly rejected; see
   [PROVIDER_INTEGRATION_MATRIX.md](PROVIDER_INTEGRATION_MATRIX.md).
5. **Homepage maturity is now implemented as a first pass.** It classifies users from real
   SQLite activity, shows Getting Started for new users, and builds real playlist shortcuts and
   provider-seeded mixes for active users. See [DYNAMIC_HOMEPAGE.md](DYNAMIC_HOMEPAGE.md).

---

## 2. Queue investigation

### 2.1 InnerTubeX surface

| Capability | Availability in v0.13.1 | Revibe use |
| --- | --- | --- |
| `search` / `browse` (WEB_REMIX) | Yes | Catalog search, albums, artists, playlists |
| `next(videoId, playlistId)` | Yes | Native Up Next / radio |
| `next(continuation)` | Yes | Queue continuation |
| Stream extraction | Yes | Playback resolution |
| `getQueue()` | Yes | Confirms the active provider queue |

Verified JSON path:

```
contents.singleColumnMusicWatchNextResultsRenderer
  .tabbedRenderer.watchNextTabbedResultsRenderer
  .tabs[0].tabRenderer.content.musicQueueRenderer
  .content.playlistPanelRenderer.contents[].playlistPanelVideoRenderer
```

The provider returns up to 40 rows per page plus a continuation token. Revibe maps title, artist,
duration, artwork and video id; it never reorders.

### 2.2 Why the old queue looked like a discography

The old implementation requested an artist's top songs and related rows and assembled a local list.
It did not maintain a YouTube Music playback context, so YouTube Music could not return radio
recommendations for the seed. The similarity to a discography was therefore expected, not a bug in
InnerTubeX.

### 2.3 Reference projects

| Project | Queue approach | Consequence for Revibe |
| --- | --- | --- |
| Metrolist | Uses InnerTubeX queue/continuation; manages its own local queue around the provider context | Confirms Option A: use the provider queue, not a custom recommender |
| BitChord | Multiple source plugins; queue is source-provided with local manual operations | Same pattern: provider order + manual priority |
| VIVI Music | InnerTube plus other sources; provider queue, local extras | Same pattern |
| LastWave / Kreate / Utaloom | Not available as verified queue implementations for this stack | No adaptation attempted |

No reference source code was copied. Licences (GPL-3.0) were already respected through Revibe's own
GPL-3.0 position.

### 2.4 Remaining honest differences

- YouTube Music's signed-in client can personalise radio from account history. Revibe uses an
  anonymous InnerTube session, so the same seed can yield a related but not identical queue.
- The first page may include the seed; Revibe displays exactly what the provider returns.
- Age-restricted or sign-in-only tracks can fail to resolve; the provider queue can still contain
  them.
- Radio, album playback and playlist playback are different contexts. Revibe sends a radio context
  (`RDAMVM…`) for autoplay seeds and does not pretend an album is a radio.

---

## 3. Homepage research and plan

Spotify's new-account home is discovery-first (popular artists, charts, popular albums/radio,
onboarding), while an established account is library-first (shortcuts, made-for-you mixes, recent
listening, recommendations). Reproducing that requires maturity classification from real signals.

Proposed signals (all already persisted or derivable from SQLite): plays, completions, skips,
likes, saved albums, followed artists, playlists, playlist tracks, distinct artists/genres,
local imports, recent searches, days since last play.

States: NEW (no meaningful activity), EARLY (a few plays/likes), ACTIVE (repeated artists/genres,
several playlists), ESTABLISHED (rich history and repeated patterns). Section eligibility is a pure
function of these counts; sections are omitted when their thresholds are not met. Full thresholds,
ordering rules and the section contract are in [DYNAMIC_HOMEPAGE.md](DYNAMIC_HOMEPAGE.md).

Current implementation reality: quick picks already use the same `LibraryRepository` playlists as
the library, so the "no duplicate playlist dataset" rule holds. Maturity-based selection is the
next stage.

---

## 4. Right sidebar research

Already implemented: Now Playing header with close, hero artwork, title/artist, like, lyrics
preview, About artist, credits, queue and recently played tabs, manual queue operations, autoplay
toggle and native-order Up Next.

Still open: drag-and-drop reordering, relative play timestamps in recently played, and
context-menu parity. These are additive and do not change queue ordering.

---

## 5. Supporting providers — what was actually verified

| Provider | Real API? | Access | Limits | Verdict |
| --- | --- | --- | --- | --- |
| LRCLIB | Yes | Keyless | Reasonable use | **Active** (existing) |
| Wikipedia REST | Yes | Keyless | Reasonable use | **Active** (existing) |
| MusicBrainz | Yes | Keyless, meaningful User-Agent required | 1 request/second | **Next** for metadata/artwork matching |
| Cover Art Archive | Yes | Keyless, MBID-based | Shared with MB | **Next**, only after a confident MBID match |
| Last.fm | Yes | API key (free non-commercial) | No hard number; no bursts | Planned, user-supplied key |
| ListenBrainz | Yes | Read public; submit needs user token | 1 request/second | Planned, opt-in only |
| Spotify Web API | Yes | OAuth, Premium required for Web API per current docs | Rate-limited | Planned metadata import only; no playback |
| Spotify Canvas | **No official API** | Undocumented internal endpoints only | — | **Not integrated**; ToS risk |
| Shazam recognition | Official ShazamKit is Apple-only | — | — | **Blocked on Windows** without a licensed provider |
| Lyprio | Not verified | No public API/licence found | — | Not integrated |
| AM-Lyrics / Better Lyrics | Extension-level integrations | No embeddable public API | — | UX reference only |
| Mimikyu lyrics DB | Not verified as public | — | — | Not integrated; no private data access |
| OpenSubsonic | Yes (protocol) | User server + credentials | Server-defined | Optional, later |
| SponsorBlock | Yes (public API) | Keyless | Community limits | Not applicable until Revibe uses YouTube player segments |

Licence and terms links, matching rules and fallback order are in
[PROVIDER_INTEGRATION_MATRIX.md](PROVIDER_INTEGRATION_MATRIX.md).

---

## 6. Decisions

1. Keep InnerTubeX as the only playback/catalog authority.
2. Keep native queue order; manual Play Next/Add to Queue stays in front of provider rows.
3. Add MusicBrainz + Cover Art Archive next because they are keyless, verifiable and improve
   artwork matching without touching playback. Add Last.fm/ListenBrainz behind user-supplied
   credentials afterwards.
5. Do not add Spotify Canvas through undocumented endpoints. Ship artwork-based visuals; document
   the limitation.
6. Do not add a recognition SDK until a licensed Windows-capable provider is chosen and user
   consent/capture behaviour is specified.
7. Build homepage maturity and provider resolvers as pure services, not inside composables.

---

## 7. Next stages

1. Provider interfaces + MusicBrainz/CAA resolver with caching, timeouts and test fixtures.
2. Homepage activity repository, maturity service and section manager; then new-user and
   established-user layouts.
3. Sidebar drag-and-drop, timestamps, context menus.
4. Windows pass: device selection, miniplayer, notifications, shortcuts, DPI.
5. Supabase auth and sync, local-first.
