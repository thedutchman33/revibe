# Music Metadata Architecture

Status: **Planned** for MusicBrainz/Last.fm/ListenBrainz. YouTube Music metadata and Wikipedia
biographies are **Active**.

## Authority model

| Field | Authority | Enrichment |
| --- | --- | --- |
| Track title, artist, album, duration, artwork | YouTube Music (InnerTubeX) | none silently overwrite |
| Recording / release identifiers | MusicBrainz | stored when matched |
| Genres / tags | YouTube Music (when present) → MusicBrainz → Last.fm | merge, never invent |
| Similar artists | Last.fm / MusicBrainz relationships | display only |
| Biography | Wikipedia (CC BY-SA 4.0) | attributed |
| Listening statistics | Local SQLite; optional ListenBrainz | opt-in |
| Recognition | Not implemented | requires licensed provider |

YouTube Music remains the factual catalog. Enrichment adds fields; it does not replace provider
values that the user can already see.

## Matching

```
1. exact recording MBID (if cached)
2. exact release MBID (album pages)
3. ISRC (when available from metadata)
4. normalised artist + title + duration (≤2 s tolerance for library rows)
5. album + track number + duration
```

Normalisation: case-fold, trim, collapse whitespace, strip "feat." variants for matching only,
remove trailing "Remastered"/"Live" markers when comparing. Reject a candidate when the duration
differs by more than 2 seconds and no strong identifier matched.

## Service adapters

### MusicBrainz

- Base: `https://musicbrainz.org/ws/2`, JSON via `fmt=json`.
- No key; meaningful `User-Agent` required.
- Hard limit: 1 request/second across the app. Use a single serialised queue plus cache.
- Lookups: `artist?query=...`, `release?query=...`, `recording?query=...` with `limit` kept small.
- Store MBIDs on the album/artist record when matched so future lookups skip search.

### Last.fm

- Base: `https://ws.audioscrobbler.com/2.0/`.
- Requires an API key (user- or build-supplied). Non-commercial terms; no bursts.
- Methods: `artist.getInfo`, `artist.getSimilar`, `artist.getTopTags`, `track.getInfo`.
- Never used to determine playback or queue order.

### ListenBrainz

- Base: `https://api.listenbrainz.org/1/`.
- Public reads need no token; `POST /1/submit-listens` needs `Authorization: Token <token>`.
- Submit only after half the track or 4 minutes (whichever is lower), per the API docs.
- Rate limit: 1 request/second. Off by default; explicit opt-in in Settings.

### Wikipedia

- Existing `WikipediaArtistKnowledge`: `page/summary`, attributed "Wikipedia, CC BY-SA 4.0".
- Used for biography and artist image fallback only.

## Caching and failure

- `metadata_cache(key, payload_json, provider, fetched_at, expires_at)` in SQLite.
- Positive results: 30 days. Negative results: 1 day. Artist images: revalidated on demand.
- Timeouts 6 s, one retry with backoff for 5xx, none for 4xx.
- Any failure degrades to "no enrichment"; the track still plays and the page still renders.
- Enrichment runs off the UI thread and is cancellable when the track changes.

## Privacy

- MusicBrainz/CAA requests contain only the artist/album/title being looked up.
- Last.fm/ListenBrainz are disabled until the user supplies a key/token and opts in.
- Tokens are stored in the local settings store; they are never logged or shown in diagnostics.
- No listening data is sent anywhere by default.

## Tests

1. Artist lookup returns a real MBID and is cached (live test, opt-in).
2. Second lookup makes no HTTP request.
3. A 503 response leaves the page usable and reports "enrichment unavailable".
4. Duration mismatch rejects an otherwise similar recording.
5. Last.fm without a key is reported as not configured, not as a failure.
6. ListenBrainz submission is refused until opt-in and a token exist.
