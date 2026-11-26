# Database Schema

Desktop storage is SQLite at `%USERPROFILE%\.revibe\revibe.db` (override the root with `REVIBE_HOME`).
Access goes through `LibraryStore`; the desktop implementation is `JdbcLibraryStore`. One connection,
serialised with a `Mutex`, WAL mode, 5 s busy timeout, all calls off the UI thread.

## Tables

### `tracks`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | TEXT PK | Namespaced: `itunes:track:…`, `local:<title>:<path>` |
| `title`, `artist`, `album` | TEXT NOT NULL | Real metadata, safe fallbacks for local files |
| `duration_ms` | INTEGER | 0 if unknown |
| `artwork_url` | TEXT | Remote URL or `file:` URI for embedded art |
| `stream_url` | TEXT | Preview URL or local `file:` URI |
| `source` | TEXT | `ITUNES`, `LOCAL`, … |
| `artist_id`, `album_id` | TEXT | Navigation keys |
| `year`, `track_number`, `genre` | mixed | Optional |
| `explicit` | INTEGER | 0/1 |
| `local_path` | TEXT | Absolute path for local files |

### `liked_tracks`

`track_id` PK, `added_at`. The track row is upserted alongside so liked songs survive source changes.

### `liked_albums`

`id` PK, title, artist, artwork_url, year, track_count, genre, artist_id, added_at.

### `followed_artists`

`id` PK, name, image_url, genre, added_at.

### `playlists`

`id` PK (UUID), name, description, created_at, updated_at.

### `playlist_tracks`

`playlist_id`, `track_id`, `position`, `added_at`; PK (`playlist_id`, `track_id`). Ordering is by
`position`. Removing a track leaves gaps, which is harmless; reordering rewrites all positions.

### `history`

`id` AUTOINCREMENT, `track_id`, `played_at`. Trimmed to the most recent 1,000 rows on each insert.
"Recently played" groups by track and orders by `MAX(played_at)` so repeats do not flood the shelf.

### `lyrics_cache`

`track_id` PK, `plain`, `synced` (raw LRC), `source`, `fetched_at`.

### `settings`

`key` PK, `value`. Keys: `market`, `volume`, `showRightPanel`, `compactSidebar`.

### `scan_folders`

`path` PK, `added_at`.

### `local_files`

`path` PK, `mtime`. Used to skip unchanged files and identify files removed from disk.

## Indexes

- `history(played_at DESC)`
- `playlist_tracks(playlist_id, position)`
- `tracks(local_path)`

## Design notes

- Catalog metadata and user data are separate concepts: a track row is a cache, while `liked_tracks`,
  `playlist_tracks` and `history` are user intent. Deleting the cache must not delete intent, so no
  foreign keys are declared and ordering tables keep their own ids.
- Upserts use SQLite `ON CONFLICT … DO UPDATE` and preserve existing artwork/stream URLs when the new
  payload has none.
- Multi-row writes (like + track, playlist insert + timestamp, local batch) run in transactions.
- Removing a local track deletes its `tracks` and `local_files` rows; liked/history rows may remain
  for catalog tracks with the same id namespace, but local ids are path-specific and simply become
  unavailable.
- There is no migration framework yet because this is schema version 1. Before shipping updates, add
  a `user_version` pragma and ordered migrations (or move to SQLDelight/Room, which provide them).

## Multiplatform plan

The schema is deliberately free of desktop-only types. Android can implement `LibraryStore` with Room
using the same tables and columns; iOS can use SQLDelight. Repositories and UI do not change. Exporting
the schema as SQL from this document is enough to generate the Room entities.
