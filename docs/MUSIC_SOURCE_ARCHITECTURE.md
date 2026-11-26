# Music Source Architecture

## Principle

The app knows one interface, `CatalogSource`. Providers are interchangeable; no UI or repository code
imports a provider-specific type. Adding a source must not require touching the player, library or
screens.

```kotlin
interface CatalogSource {
    val id: MusicSourceId
    val displayName: String
    suspend fun searchTracks(query: String, market: String, limit: Int): List<Track>
    suspend fun searchAlbums(query: String, market: String, limit: Int): List<Album>
    suspend fun searchArtists(query: String, market: String, limit: Int): List<Artist>
    suspend fun album(albumId: String, market: String): AlbumDetail
    suspend fun artist(artistId: String, market: String): ArtistDetail
    suspend fun albumsForArtist(artistId: String, market: String, limit: Int): List<Album>
    suspend fun tracksForArtist(artistId: String, artistName: String, market: String, limit: Int): List<Track>
}
```

## Unified models

`Track`, `Album` and `Artist` contain no provider JSON. Each carries:

- `id` — namespaced (`itunes:track:1440…`, `local:/path/file.mp3`), so ids never collide between
  sources.
- `source` — `MusicSourceId`, which drives availability badges and playability.
- `streamUrl` — a resolved, time-limited preview URL for catalog items, or a `file:` URI for local
  files. Never invented.
- `artworkUrl` — highest available resolution, not a UI-size thumbnail.
- `artistId` / `albumId` — navigation keys, namespace-prefixed.
- metadata: duration, year, track number, genre, explicit.

`Track.isPreview` is a first-class property: the player bar and track rows label 30-second catalog
playback, and album pages explain it. Nothing pretends to be full streaming.

## Active source: iTunes Search

- Search: `/search?term=&entity=song|album|musicArtist&country=&limit=`
- Detail: `/lookup?id=&entity=song|album&country=`
- Artwork: `100x100bb` URL rewritten to `600x600bb`.
- Previews: 30-second `previewUrl` (M4A over HTTPS).
- No key, no auth, no user data sent.
- Content type is `text/javascript`; the Ktor client registers that content type for JSON.
- Requests are cached in memory per session (search 40 entries, album/artist detail maps) to limit
  repeat calls.

Market/storefront is a user setting (default IN) and is sent as the ISO country code. Changing it
reloads home and search.

## Local source

`LocalLibrary` is separate from `CatalogSource` because it is file-system work, not a remote catalog.
Produced tracks use `source = LOCAL`, a `file:` stream URL, and an extracted embedded-artwork URL.
See `docs/PLAYBACK_ENGINE.md` for codec coverage.

## Availability policy

`MusicSourceId` carries a status that the Settings screen renders:

| Source | Status | Reason |
| --- | --- | --- |
| iTunes Catalog | Active | Public search API; previews only |
| Local Files | Active | User-owned files |
| YouTube Music | Blocked | Third-party streaming is outside YouTube's terms; InnerTube keys are not public or licensed |
| Spotify | Inactive | Official API allows metadata/playlist import with OAuth, not audio streaming |

A provider is never added to satisfy a checklist. If a source cannot be used within its terms, the
seam exists but the implementation does not ship.

## Error handling

- HTTP non-2xx and parse failures are logged and return an empty result rather than crashing.
- Empty results surface as an explicit empty state with retry, not as a spinner that never ends.
- Unplayable tracks have `streamUrl = null`; the queue controller reports a clear error and advances
  only on explicit user action.
- Expiring preview URLs are re-resolved by opening the album/track again in this milestone; persisted
  catalog URLs are not assumed permanent.

## Adding a permitted source

1. Implement `CatalogSource` in `commonMain` (or platform source set).
2. Map responses into the unified models, namespace ids, and set `MusicSourceId`.
3. Register it in `CatalogRepository`/`AppGraph` and add its status to Settings.
4. Add contract tests for mapping, empty results and error responses.
5. Document auth storage, rate limits and terms in this folder.

Do not put API secrets in the client. A source requiring a secret must use a backend proxy with
server-side credentials.
