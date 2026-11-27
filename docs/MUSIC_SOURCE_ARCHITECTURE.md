# Music Source Architecture

## Principle

The app knows one interface, `CatalogSource`. Providers are interchangeable; no UI or repository code
imports a provider-specific type. Adding an enrichment provider does not touch the player, library or
screens.

**YouTube Music is the primary and only playback catalog.** Enrichment providers (metadata, artwork,
lyrics, recognition) are separate interfaces; they may never become a playback source.

```kotlin
interface CatalogSource {
    val id: MusicSourceId
    val displayName: String
    val idPrefix: String                 // "ytm:"

    suspend fun searchTracks(query: String, market: String, limit: Int): List<Track>
    suspend fun searchAlbums(query: String, market: String, limit: Int): List<Album>
    suspend fun searchArtists(query: String, market: String, limit: Int): List<Artist>
    suspend fun album(albumId: String, market: String): AlbumDetail
    suspend fun artist(artistId: String, market: String): ArtistDetail
    suspend fun albumsForArtist(artistId: String, market: String, limit: Int): List<Album>
    suspend fun tracksForArtist(artistId: String, artistName: String, market: String, limit: Int): List<Track>

    suspend fun searchAll(query: String, market: String, limit: Int): SearchResults
}
```

## Unified models

`Track`, `Album` and `Artist` contain no provider JSON. Each carries:

- `id` — namespaced (`ytm:track:<videoId>`, `ytm:album:<browseId>`, `ytm:playlist:<id>`,
  `local:<path>`), so ids never collide.
- `source` — `MusicSourceId` (`YOUTUBE_MUSIC`, `LOCAL`, `ARCHIVED`, or the inactive `SPOTIFY`).
- `streamUrl` — a `file:` URI for local files. YouTube tracks leave it `null`; resolution is lazy
  through `TrackStreamResolver`.
- `artworkUrl` — highest available resolution.
- `artistId` / `albumId` — navigation keys, namespace-prefixed.
- metadata: duration, year, track number, genre, explicit.
- `playbackKind` — `FULL`, `LOCAL` or `UNAVAILABLE`.
- `isArchived` — a row retained from the retired preview catalog; never playable.

## Source layout

```
MusicSourceManager
└── YouTubeMusicSource   (search, browse, playlists, full streaming, native queue)

Enrichment (separate interfaces, never playback):
├── LRCLIB                     lyrics (active)
├── Wikipedia                  biography (active)
├── MusicBrainz / CAA          metadata + artwork (planned)
├── Last.fm / ListenBrainz     tags, similar artists, scrobbles (planned)
└── Spotify Web API            optional account metadata import (planned)
```

`MusicSourceManager` stays multi-source so a local or licensed catalog can be added without touching
the UI. Search runs sources in parallel, merges in priority order and de-duplicates by
`title|artist`. A failing source is logged and omitted; it never removes other results. Shelves use
the first source that returns anything.

Album/artist ids route to their owning prefix. Ids written by the retired catalog are rejected with
an explicit message instead of being sent to YouTube Music.

## YouTube Music source

- **Client:** InnerTubeX v0.13.1, anonymous. Created in `DesktopGraph`; the source lives in
  `commonMain` and knows no Compose types.
- **Search:** `WEB_REMIX` with YouTube Music's opaque filter params. Song results map from
  `musicResponsiveListItemRenderer`; artist results carry browse ids; albums are `MPRE` playlist-backed
  results presented as albums.
- **Browse:** album and playlist pages map header artwork, title, artist, year and tracks; playlist
  results are browsed as `VL<playlistId>`; artist pages map top songs plus albums/singles shelves.
- **Native queue:** `next(WEB_REMIX, videoId, playlistId = "RDAMVM<videoId>")` returns
  `musicQueueRenderer → playlistPanelRenderer` rows plus a continuation token. See
  [QUEUE_ENGINE.md](QUEUE_ENGINE.md).
- **Pagination:** `searchContinuation` exists in the source but is not exposed by the multi-source UI.
- **Mapping:** raw JSON is parsed into Revibe models immediately; InnerTubeX models never reach the
  screen. IDs are re-prefixed (`ytm:`).
- **Stream resolution:** `DesktopTrackStreamResolver` prefers itag 140 (`audio/mp4`, AAC), with the
  progressive itag 18 fallback. WebM/Opus/HLS/SABR are reported as `UNSUPPORTED_TRANSPORT` rather than
  handed to an engine that may not decode them.
- **Proxy:** `LocalStreamProxy` returns `http://127.0.0.1:<port>/stream.m4a`, forwards Range and
  headers, and keeps signed URLs out of the player.
- **Expiry:** URLs are cached briefly and re-resolved once after an engine error.

## Availability policy

| Source | Status | Reason |
| --- | --- | --- |
| YouTube Music (InnerTubeX) | Active | Anonymous web/player API; MP4/AAC only |
| Local Files | Active | User-owned files |
| Archived | Inactive (data only) | Retained rows from the removed preview catalog |
| Spotify | Inactive | Official API allows metadata/playlist import with OAuth, not audio streaming |

YouTube failures use precise states: `INITIALIZING`, `AUTHENTICATION_REQUIRED`, `STREAM_UNAVAILABLE`,
`UNSUPPORTED_TRANSPORT`, `NETWORK_ERROR`, `REGION_UNAVAILABLE`, `API_ERROR`, `AVAILABLE`. There is no
blanket `BLOCKED` state.

## Error handling

- HTTP non-2xx, timeouts and parse failures become a status plus a sanitized diagnostic (URLs replaced
  with `[url]`; no cookies/tokens logged). Search returns an explicit error state with retry.
- An album/artist page that fails does not remove working search results.
- Unsupported YouTube transports are reported with their MIME/codec; they are not silently dropped.
- Unplayable tracks keep the queue position; playback errors surface a toast and do not fake progress.
- A retired-catalog album/artist id fails with "Search YouTube Music to find it again"; it is never
  resolved against a live provider.
- No credentials, signed URLs or provider models are exposed to Compose.

## Adding a source

1. Implement `CatalogSource` for a catalog, or the relevant enrichment interface for metadata.
2. Map responses into the unified models, namespace ids, and set `MusicSourceId` / `PlaybackKind`.
3. Register it in `MusicSourceManager` / `AppGraph`; add its status to Settings.
4. Add contract tests for mapping, empty results, error responses and (if streaming) playback.
5. Document auth storage, rate limits, stream compatibility and terms in this folder.

Do not put API secrets in the client. A source requiring a secret must use a backend proxy with
server-side credentials.
