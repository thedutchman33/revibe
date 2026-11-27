# Provider Integration Matrix

Research date: 26 November 2026. Statuses describe this repository, not the vendor's marketing.
Visual QA evidence for this milestone is in [VISUAL_QA_0_8.md](VISUAL_QA_0_8.md).

**InnerTubeX / YouTube Music is the primary catalog, playback and queue authority.** Enrichment
providers may never substitute it, block playback, or change queue order.

## 1. Capability matrix

| Provider | Category | What it actually provides | Auth | Rate limit | Licence / terms | Status |
| --- | --- | --- | --- | --- | --- | --- |
| InnerTubeX / YouTube Music v0.13.1 | Catalog, playback, queue | Search, albums, artists, playlists, progressive MP4/AAC stream, `next()` queue + continuation | Anonymous InnerTube session | Provider-controlled | GPL-3.0 (library); YouTube ToS applies to extraction | **Active** |
| Local files | Playback | Full playback, tags, embedded art | None | — | User's own files | **Active** |
| SQLite library | User data | Likes, albums, artists, playlists, history, settings, lyrics cache | None | — | Local | **Active** |
| LRCLIB | Lyrics | Plain and line/word-synced LRC, keyless `/api/get` + `/api/search` | None | Reasonable use | Community database; verify per-track rights before redistribution | **Active (primary)** |
| lrc.red | Lyrics | Word/line-synced TTML by ISRC, keyless search + `/s/{isrc}.ttml` | None; descriptive User-Agent and credit required | Rate-limited; identify the app | Free API; credit + link required; contact address requested | **Active (fallback)** |
| Unison | Lyrics | Video-id-keyed community TTML, word/syllable/line sync | None for reads | Read: 600/min per IP | Open-source/non-commercial licence; credit service + submitter | **Active (fallback)** |
| Better Lyrics API | Lyrics | Cache-first TTML; public OpenAPI | Read cached hits without a key; cache misses need `X-API-Key` | Per-IP two-tier limiter | GPL-3.0 API code; upstream rights | **Evaluated, not integrated** |
| Wikipedia REST | Biography | Artist summary + thumbnail | None | Reasonable use | CC BY-SA 4.0, attributed in UI | **Active** |
| MusicBrainz | Metadata | Release and recording search used for artwork matching | None; meaningful `User-Agent` required | **1 request/second**, enforced by a serialised gate | Non-commercial free; commercial plans exist | **Active (artwork matching)** |
| Cover Art Archive | Artwork | Release front cover by MBID at up to 1200 px | None | Shared MB etiquette | Internet Archive / MetaBrainz | **Active (artwork fallback)** |
| Deezer | Artwork | Keyless track search and 1000 px album cover | None | Reasonable use | Public API; album art remains label-owned | **Active (artwork fallback; live search empty from this network)** |
| Last.fm | Metadata | Artist info, tags, similar artists, scrobble | API key + shared secret for desktop auth | No bursts; excessive use may be suspended | API key requires Last.fm Pro from 1 Dec 2026 | **Blocked on an app key** |
| ListenBrainz | History | Submit listens, public stats/recommendations | User token to submit; reads public | **1 request/second** | Open data, user-owned | **Planned (opt-in)** |
| Spotify Web API | Account metadata | Playlists, saved tracks, artist/album metadata | OAuth (PKCE); current docs state Premium required for Web API | Documented rate limits | Developer Terms; **no audio streaming** | **Planned (optional)** |
| Spotify Canvas | Visuals | 3–8 s vertical loops on Spotify mobile | No public API | — | Undocumented endpoints; ToS risk | **Not integrated** |
| Apple Music artwork | Artwork | Optional high-quality still or `editorialVideo` motion artwork | Private `amp-api` bearer scraped from the web player | Undocumented | Private API; no keyless public path | **Not integrated** |
| Tidal artwork | Artwork | Catalog artwork / `videoCover` via Tidal API | OAuth client credentials | Documented quotas | Developer terms; no keyless public artwork endpoint | **Not integrated (no credentials)** |
| Community Canvas | Visuals | Community `canvas.json` song/artist → looping MP4 index | None for reads | Community hosted | Asset licences unverified | **Not integrated (no desktop video surface)** |
| ShazamKit | Recognition | Audio fingerprint matching | Apple platforms/SDK | — | Apple terms; Apple-only | **Blocked on Windows** |
| ACRCloud / AudD | Recognition | Fingerprint matching, commercial API | Key/subscription | Plan-based | Commercial | **Not selected** |
| Lyprio | Lyrics | No public API/server; app-specific provider only | — | — | Unknown | **Not integrated** |
| AM-Lyrics | Lyrics | Web Component/front-end around lrc.red + Apple mirrors | — | — | Open-source front-end; no hosted API | **Not integrated; lrc.red used directly** |
| Better Lyrics extension | Lyrics | Extension that sources community lyrics | — | — | GPL-3.0 front-end | **Not used; its API evaluated** |
| Mimikyu lyrics DB | Lyrics | Claims a large synced-lyrics database | — | — | No verified public API/licence | **Not integrated** |
| Kiss/Genius/Musixmatch | Lyrics | Licensed APIs | Key/licence | Plan-based | Commercial; lyrics rights | **Not selected** |
| OpenSubsonic | Local server | Browse/stream a user's self-hosted library | User server credentials | Server-defined | Open protocol; server licences vary | **Optional, later** |
| SponsorBlock | Playback segments | Crowd-sourced segment skips | None | Community limits | CC BY-NC-SA 4.0 data; YouTube-client specific | **Not applicable yet** |

## 2. Verified facts that shaped decisions

- **MusicBrainz** requires a meaningful User-Agent and at most one call per second; exceeding it can
  get the IP blocked. Any resolver must serialise calls or cache aggressively.
- **Cover Art Archive** is keyed by MusicBrainz release/release-group MBIDs; cover matching must be
  identifier-based, not title-based.
- **ListenBrainz** requires `Authorization: Token <token>` for `POST /1/submit-listens`, and listens
  should only be submitted after half the track or 4 minutes, whichever is lower.
- **Spotify Canvas** is not exposed by the Spotify Web API. Public libraries reach it through
  reverse-engineered internal endpoints (Pathfinder / `spclient`), which is outside Revibe's
  acceptable-use policy. Revibe will not scrape it.
- **ShazamKit** is an Apple-platform SDK. Revibe targets Windows, so recognition needs either a
  licensed fingerprint API and explicit capture consent, or it stays unimplemented.
- **LRCLIB** already supplies plain, line-synced and word-synced lyrics and remains the primary
  provider.
- **lrc.red** is keyless and returned 44 line/209 word-synced entries for *Kesariya* in a live test.
  It requires a descriptive User-Agent, credit/link and a contact address; `REVIBE_CONTACT_EMAIL`
  supplies the latter without inventing one.
- **Unison** reads are keyless at 600/min per IP and are keyed by YouTube video id. A live English
  lookup returned 16 word-synced lines and the submitter credit `boidu`.
- **Better Lyrics API** is real but returned 401 on a live uncached request; its docs state keys are
  not being issued, so it is not a reliable fallback.
- **Last.fm** desktop auth needs an app API key **and** shared secret; from 1 December 2026 holding a
  key requires Last.fm Pro. No key is configured here, so the integration is blocked, not stubbed.
- **Deezer** exposes a keyless public search that returns `cover_xl` (1000 px). Revibe uses it only
  after strict title + artist matching; the album name is a ranking signal, not a requirement.
- **Apple Music artwork** requires a private `amp-api` bearer scraped from the web player. It is
  rejected rather than smuggled in. No Apple preview or search endpoint is present in Revibe.
- **Tidal artwork** needs OAuth client credentials and a developer app; none are available, so no
  speculative client was added.
- **Metrolist, BitChord and Vivi Music** were inspected. Their artwork handling confirms high-res
  YouTube promotion, square collection crops, palette sampling at a small fixed source, strict
  title/artist matching for Canvas, and community canvas manifests. Revibe adopts the static-artwork
  and palette techniques; it does not adopt their Canvas playback or token-scraping paths.

## 3. Provider interfaces (target)

```kotlin
interface MusicMetadataProvider { suspend fun artist(...); suspend fun release(...) }
interface ArtworkProvider      { suspend fun artwork(query: ArtworkQuery): ArtworkCandidate? }
interface LyricsProvider       { suspend fun lyrics(query: LyricsQuery): Lyrics? }
interface RecognitionProvider  { suspend fun identify(samples: AudioSamples): RecognizedTrack? }
interface ListeningHistoryProvider { suspend fun submit(listen: ListenEvent) }
interface ExternalAccountProvider  { suspend fun connect(): AccountSession }
interface VisualBackgroundProvider { suspend fun visual(track: Track): VisualAsset? }
```

Rules:

1. Providers return domain models; no vendor DTO reaches the UI.
2. Every call has a timeout, one retry policy, and a cache (memory + SQLite where useful).
3. A provider failure returns `null`/empty and never throws into playback.
4. Every provider is individually enableable; credentials are user-supplied and stored locally.
5. Source attribution is displayed where the licence requires it (Wikipedia, MusicBrainz, LRCLIB).
6. Matching uses identifiers first: recording/release MBID, ISRC, then artist+title+album+duration.
   Title-only matching is not acceptable.

## 4. Resolution order

**Artwork:** YouTube Music artwork → MusicBrainz release match → MusicBrainz recording match →
Cover Art Archive → Deezer 1000 px cover → gradient placeholder. Never show a different release's
cover.

**Lyrics:** LRCLIB timed → lrc.red TTML (title/artist/version/duration matched) → Unison (exact
video id + metadata match) → plain text → "No lyrics found". Cache by track ID with the winning
provider and raw TTML/LRC recorded; source credit and link are shown in the panel.

**Metadata:** YouTube Music (authoritative) → MusicBrainz (enrichment) → Last.fm (tags/similar) →
Wikipedia (biography). Enrichment never rewrites provider catalog facts silently.

## 5. Test requirements per provider

- Real request returning real data (or an explicitly skipped live test with the reason).
- Timeout path returns a fallback, not an exception.
- Cache hit avoids a second network call.
- Offline behaviour keeps playback and library usable.
- Wrong-song guard: a mismatched candidate is rejected.
- Rate limit: no more than the documented request rate.

YouTube Music artwork, MusicBrainz + Cover Art Archive and Deezer are integrated for static
artwork; the rate gate, strict matching and failure fallbacks are covered by tests. LRCLIB, lrc.red
and Unison lyrics are integrated and live-tested; see
[LYRICS_PROVIDER_ARCHITECTURE.md](LYRICS_PROVIDER_ARCHITECTURE.md). Spotify Canvas, Apple Music
motion artwork, Tidal, community Canvas and recognition are not integrated because they need
credentials, undocumented access or a video surface; a dependency or a key alone would not count as
integration.
