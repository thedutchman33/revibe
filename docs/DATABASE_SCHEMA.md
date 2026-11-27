# Database Schema

Desktop storage is SQLite at `%USERPROFILE%\.revibe\revibe.db` (override the root with `REVIBE_HOME`).
Access goes through `LibraryStore`; the desktop implementation is `JdbcLibraryStore`. One connection,
serialised with a `Mutex`, WAL mode, 5 s busy timeout, all calls off the UI thread.

## Tables

### `tracks`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | TEXT PK | Namespaced: `ytm:track:…`, `local:<title>:<path>` |
| `title`, `artist`, `album` | TEXT NOT NULL | Real metadata, safe fallbacks for local files |
| `duration_ms` | INTEGER | 0 if unknown |
| `artwork_url` | TEXT | Remote URL or `file:` URI for embedded art |
| `stream_url` | TEXT | Local `file:` URI; legacy preview URLs may remain on archived rows |
| `source` | TEXT | `YOUTUBE_MUSIC`, `LOCAL`, `ARCHIVED`, or the inactive `SPOTIFY` |
| `artist_id`, `album_id` | TEXT | Navigation keys |
| `year`, `track_number`, `genre` | mixed | Optional |
| `explicit` | INTEGER | 0/1 |
| `local_path` | TEXT | Absolute path for local files |

### `liked_tracks`

`track_id` PK, `added_at`. The track row is upserted alongside so liked songs survive source changes.
Imported rows also store `source_platform`, `source_import_id`, `imported_at` and
`original_source_identifier`; re-liking natively clears them.

### `liked_albums`

`id` PK, title, artist, artwork_url, year, track_count, genre, artist_id, added_at, plus the same
four optional provenance columns as `liked_tracks`. Saved albums sync as `liked_album` entities.

### `followed_artists`

`id` PK, name, image_url, genre, added_at, plus optional provenance columns.

### `playlists`

`id` PK (UUID), name, description, created_at, updated_at, plus optional `source_platform`,
`source_import_id`, `imported_at` and `original_source_identifier`.

### `playlist_tracks`

`entry_id` TEXT PK (UUID), `playlist_id`, `track_id`, `position`, `added_at`, plus optional
`source_platform`, `source_import_id`, `imported_at`. A playlist may contain the same `track_id`
more than once: the entry id is the identity and `position` is the explicit order. Removing an
entry leaves position gaps, which are harmless; reordering rewrites positions for every entry.
Imported entries carry the import id so undo can remove exactly those entries on any device.

### `history`

`id` AUTOINCREMENT, `track_id`, `played_at`, `played_ms`, `completed`, `skipped`, `sync_id` (stable
UUID, unique index). Imported rows additionally keep `source_platform`, `source_import_id`,
`source_event_id`, `imported_at`, `source_uri`, `source_event_key`, `source_start_reason`,
`source_end_reason`, `source_shuffle` and `source_device`. Rows without `source_import_id` are
trimmed to the most recent 5,000; imported history is retained until the user undoes that import.
"Recently played" groups by track and orders by `MAX(played_at)`. Combined account activity uses
rows pulled from every signed-in device and reports native and imported play counts separately.

### `search_history`

`id` TEXT PK (UUID), `query_key` UNIQUE (trimmed lowercase), `query`, `searched_at`, `updated_at`.
Imported rows may also carry `source_platform`, `source_import_id`, `source_event_id`, `imported_at`
and `source_result_uris`. A new native search clears imported provenance. Only the newest 50
non-imported local terms are trimmed. Persisted so recents survive restart; cloud upload is opt-in.

### `lyrics_cache`

`track_id` PK, `plain`, `synced` (raw LRC), `source`, `fetched_at`.

### `settings`

`key` PK, `value`. Keys: `market`, `volume`, `showRightPanel`, `compactSidebar`, `leftPanelWidth`,
`rightPanelWidth`, `autoplayEnabled`, `syncSearchHistory`, `lastRoute`. Market, autoplay and the
search-history privacy flag are account preferences; volume and layout remain device-local.

### `scan_folders`

`path` PK, `added_at`.

### `local_files`

`path` PK, `mtime`. Used to skip unchanged files and identify files removed from disk.

### Import provenance

- `import_batches` — `id` PK, `source_platform` (`spotify` or `tune_my_music`), archive/file
  `fingerprint`, `imported_at`, `summary`, per-category counts (`playlists_count`, `tracks_count`,
  `likes_count`, `albums_count`, `artists_count`, `history_count`, `searches_count`) and
  `deleted_at` for undo; UNIQUE (`source_platform`, `fingerprint`). This is the idempotency key for
  repeated uploads, the Account → Import history row and a syncable metadata record.
- `import_records` — PK (`source_platform`, `source_id`, `entity_type`), `local_id`, `import_id`,
  `imported_at`, `original_uri`. Maps a Spotify playlist/entry/like/album/follow to the local row
  that was created, so a retry or a second archive does not duplicate it and undo is ownership-scoped.

Undo deletes only rows whose provenance points at that import. Imported playlist entries are removed
by `playlist_tracks.source_import_id`; entries added natively afterwards have no import id and stay.
Playlists survive undo when they still contain native entries. The original archive/ZIP is never
uploaded, and neither table stores archive bytes, a Spotify password or a cookie.

### Sync bookkeeping

- `sync_outbox` — `seq` autoincrement, `entity_type`, `entity_id`, `operation` (`upsert`/`delete`),
  `changed_at`, `account_id`; UNIQUE (`entity_type`, `entity_id`). One pending row per entity.
- `sync_tombstones` — soft deletions awaiting upload; PK (`entity_type`, `entity_id`).
- `sync_state` — device id, active account id/email, per-account bootstrap marker,
  `preferences_set`, `last_synced_at`, `last_pull_at`.
- `sync_cursors` — reserved per-stream cursors.

## Indexes

- `history(played_at DESC)`
- `history(sync_id)` UNIQUE
- `playlist_tracks(playlist_id, position)`
- `tracks(local_path)`
- `search_history(searched_at DESC)`
- `sync_outbox(changed_at)`
- `sync_tombstones(changed_at)`

## Migrations

`JdbcLibraryStore` runs idempotent migrations on open:

1. `history.played_ms`, `history.completed`, `history.skipped` added when missing.
2. **Schema v2:** `history.sync_id` added and backfilled with UUIDs, unique index created;
   `search_history`, `sync_outbox`, `sync_tombstones`, `sync_state` and `sync_cursors` created.
3. **Schema v3:** `playlist_plays` created for the home recency grid, with a played-at index.
   Rows are written only when a playlist is played and removed with the playlist.
4. **Schema v4:** `import_batches` and `import_records` created. `history` gains the source/provenance
   columns listed above; `search_history` gains its import source columns. Imported rows are
   excluded from the normal history/search trimming. `PRAGMA user_version = 4` is set.
5. **Schema v5:** `playlist_tracks` is rebuilt transactionally with a UUID `entry_id` primary key;
   every pre-existing entry and its position/added-at is copied before the old table is dropped.
   Playlists, playlist entries, likes, albums, artists, history and searches gain provenance columns;
   `import_batches` gains the seven category counts and `deleted_at`. Existing batches with no counts
   are backfilled once from `import_records`/provenance at startup and persisted, so history reads do
   not derive them each time. `PRAGMA user_version = 5` is set.
6. Layout widths below the new `LEFT_MIN` are migrated back to the default by
   `SettingsRepository`.

There is no general migration framework yet; migrations are ordered idempotent methods. Add
`user_version` checks before shipping any future incompatible change.

## Cloud schema

The versioned Supabase migrations are:

- [`20261126000100_account_sync.sql`](../supabase/migrations/20261126000100_account_sync.sql) —
  profiles, playlists, playlist tracks, liked songs, followed artists, listening/search history,
  preferences, devices and the atomic sync RPCs.
- [`20261127000100_import_integrity.sql`](../supabase/migrations/20261127000100_import_integrity.sql) —
  playlist `entry_id` identity (duplicates allowed), provenance columns on every synced record,
  `saved_albums`, `import_batches`, RLS/grants and updated replace/soft-delete RPCs.

All account tables are protected by RLS (`auth.uid() = user_id`). Track metadata is stored as
sanitized JSON without stream URLs or local paths. See [ACCOUNT_SYNC.md](ACCOUNT_SYNC.md) for setup
and conflict semantics.

## Design notes

- Catalog metadata and user data are separate concepts: a track row is a cache, while `liked_tracks`,
  `playlist_tracks` and `history` are user intent. Deleting the cache must not delete intent, so no
  foreign keys are declared and ordering tables keep their own ids.
- Upserts use SQLite `ON CONFLICT … DO UPDATE` and preserve existing artwork/stream URLs when the new
  payload has none.
- Multi-row writes run in transactions.
- Removing a local track deletes its `tracks` and `local_files` rows; liked/history rows may remain
  for catalog tracks with the same id namespace, but local ids are path-specific and become
  unavailable.
- Archived rows are retained deliberately; the UI labels them and playback refuses them.
- Sync is additive: local writes also queue a cloud operation, but the app remains fully usable
  signed out or offline. Pulled rows are applied without re-enqueueing, preventing echo loops.
- Local-only and archived tracks never leave the device; playlists with local files retain those
  rows after a remote playlist update.

## Multiplatform plan

The schema is deliberately free of desktop-only types. Android can implement `LibraryStore` with Room
using the same tables and columns; iOS can use SQLDelight. Repositories and UI do not change.
