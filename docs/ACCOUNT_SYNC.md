# Cloud Account and Multi-Device Sync

Revibe remains local-first. SQLite is the device library and still works with no account. Supabase
Auth provides accounts and Supabase Postgres is the central replication store. InnerTubeX remains the
only online catalog, stream resolver and native queue.

> **Cloud status:** the desktop client, SQLite v5 migration, two Supabase SQL/RLS migrations and
> mocked two-device tests are implemented. The SQL has **not** been applied to a real Supabase project
> and a real two-device cloud run has **not** been performed, because no project URL/anon key was
> supplied. Do not treat the mocked simulator as proof that RLS or GoTrue is configured correctly.

## Set up a project

1. Create a Supabase project.
2. Apply the versioned migrations with the Supabase CLI (the second one is required for 0.9.2):
   ```bash
   supabase link --project-ref <project-ref>
   supabase db push
   ```
   [`20261126000100_account_sync.sql`](../supabase/migrations/20261126000100_account_sync.sql)
   creates tables, triggers, RLS and the RPCs. 
   [`20261127000100_import_integrity.sql`](../supabase/migrations/20261127000100_import_integrity.sql)
   adds unique playlist entries, provenance, `saved_albums`, `import_batches`, RLS/grants and the
   updated replace/soft-delete RPCs. Existing `auth.users` rows are backfilled into profiles and
   preferences.
3. In Authentication, enable Email/Password and set the minimum password length to **8** so the
   server matches the desktop form. Configure the Site URL / redirect allow-list if email
   confirmation or password recovery should return to a hosted page.
4. For Google sign-in, follow [Google sign-in](#google-sign-in-oauth) below.
5. Launch Revibe with the **publishable/anon** key:
   ```bash
   REVIBE_SUPABASE_URL="https://<project-ref>.supabase.co" \
   REVIBE_SUPABASE_ANON_KEY="<publishable-or-anon-key>" \
   ./gradlew :desktopApp:run
   ```
   Log in from the profile menu → **Account & sync**. Service-role keys are rejected at startup.

The desktop app talks to GoTrue and PostgREST directly through Ktor; it does not bundle a Supabase SDK
and never ships a service-role key.

## Google sign-in (OAuth)

Revibe uses Supabase's supported PKCE flow for desktop. The consent page always opens in the system
browser; there is no embedded WebView, no Google password field and no cookie scraping.

1. **Google Cloud** → APIs & Services → Credentials → **Create OAuth client ID** → *Web application*.
   Add the authorized redirect URI shown by Supabase: `https://<project-ref>.supabase.co/auth/v1/callback`.
   The client secret stays in Google Cloud/Supabase, never in the desktop app.
2. **Supabase** → Authentication → Providers → **Google**: enable it and paste the client ID and
   client secret from Google Cloud.
3. **Supabase** → Authentication → URL Configuration:
   - Site URL: the hosted page users return to after email confirmation/recovery, or
     `http://127.0.0.1:8765/auth/callback` for a local-only setup.
   - Redirect URLs: add exactly `http://127.0.0.1:8765/auth/callback`.
     Revibe binds its loopback listener to `127.0.0.1:8765`; a different port requires rebuilding with
     a different `DesktopOAuthLauncher.DEFAULT_PORT` and updating this allow-list.
4. Restart Revibe with `REVIBE_SUPABASE_URL` and `REVIBE_SUPABASE_ANON_KEY`. The Google button opens
   the browser, validates OAuth `state`, exchanges the one-time code with the PKCE verifier and stores
   the resulting session through the existing DPAPI store. Supabase's social `/authorize` endpoint
   uses its own internal state, so Revibe carries its CSRF value in the loopback redirect URL; Supabase
   preserves that query when it appends the code.

If the project, provider or redirect URL is missing, the app shows a setup error and does **not**
pretend a Google login succeeded. Cancelling the Google page returns "Google sign-in was cancelled.";
an abandoned round trip times out after three minutes.

Never ship a `service_role`/`sb_secret_...` key in the desktop build. `CloudConfig` rejects it at
startup because it would bypass every RLS policy.

### Password reset

The **Forgot password?** link asks GoTrue to send a recovery email. Completing the reset happens on
the URL configured as the Supabase Site URL; the desktop app does not render its own new-password
form because that would require a second recovery redirect contract. Configure the Site URL before
testing recovery.

## What syncs

| Data | Cloud table | Notes |
| --- | --- | --- |
| Playlists + names/descriptions | `playlists` | Client UUID is the primary key; provenance columns |
| Playlist entries and order | `playlist_tracks` | `entry_id` identity allows repeated tracks; atomic replace RPC; per-entry provenance |
| Liked songs | `liked_songs` | Track metadata JSON, no stream URL; provenance |
| Saved albums | `saved_albums` | Album metadata only; provenance |
| Followed artists | `followed_artists` | Provenance |
| Listening history | `listening_history` | Stable UUID per play, original event id when present, provenance; feeds the same activity model |
| Search history | `search_history` | **Opt-in**; off by default; provenance |
| Import batch metadata | `import_batches` | Source, fingerprint, date, category counts and undo state; never archive bytes |
| Market / autoplay / search-history preference | `user_preferences` | |
| Profile name / avatar URL | `profiles` | Email comes from Auth |
| Device registration | `user_devices` | Device id, name, platform, last seen |

Device-only settings stay in SQLite: volume, audio output/engine, panel widths, compact sidebar,
right-panel visibility, miniplayer/window layout and the last route.

Track payloads use a dedicated `CloudTrack` shape with **no `streamUrl` and no `localPath` fields**.
Local and archived tracks are filtered out of playlist/like/history uploads. Streams are always
resolved fresh by InnerTubeX on the device that plays them.

## Local schema additions

SQLite schema version is now `PRAGMA user_version = 5`. Schema v2 added:

- `history.sync_id` — stable UUID added to existing rows; unique index.
- `search_history` — de-duplicated persisted queries (newest 50 locally).
- `sync_outbox` — one pending row per entity, with account id and change timestamp.
- `sync_tombstones` — soft deletions that must propagate.

Schema v3 adds `playlist_plays` for local home recency. It is not synced. Schema v4 adds
`import_batches`/`import_records` and import provenance columns on history and search rows, and keeps
imported history out of the normal 5,000-row trim.

Schema v5 rebuilds `playlist_tracks` transactionally with a UUID `entry_id` primary key and copies
every existing row, position and timestamp. It adds provenance to playlists, playlist entries, likes,
albums, artists, history and searches, plus category counts and `deleted_at` on import batches.
Repeated tracks are now separate entries. Imported rows use the same sync entities and payloads as
native rows; the archive and unrelated Spotify data are never uploaded. Imported and native activity
counts remain separately visible on `UserActivitySnapshot`. Batches created before v5 have their
category counts backfilled once at startup and persisted, so history reads only use stored values.

Sync bookkeeping:
- `sync_state` — device id, active account/email, bootstrap marker, last sync/pull times,
  explicit-preferences flag.
- `sync_cursors` — reserved per-stream cursors; the current engine uses the pull timestamp.

Existing tables and rows are never recreated. The migration adds columns/tables and generates a
UUID for each existing history row.

## Account and session security

- Passwords are sent only to GoTrue; Revibe never stores or logs them.
- Access and refresh tokens are stored in `account.session`, encrypted with Windows DPAPI
  (`Crypt32Util`) for the current Windows user. If DPAPI is unavailable, session persistence is
  skipped rather than writing plaintext.
- Tokens are refreshed under a mutex 60 seconds before expiry. A failed refresh clears the session
  and moves the UI to “session expired”.
- Every account table has RLS with `auth.uid() = user_id` on select/insert/update/delete. Writes also
  include `user_id` and are checked by `WITH CHECK`.
- The two RPCs are `security invoker`, validate ownership and are not executable by `anon`.
- Only SQLite stores local paths, and those rows are never put in a cloud payload.

## One library, one account per device

The local tables predate accounts and have no `user_id` columns. The first successful sign-in binds
the SQLite library to that Supabase user. A different account is rejected before upload/download and
the UI signs it out. This prevents one account's local rows being uploaded to another account. It does
not provide OS-level privacy on a shared Windows login; local data remains readable offline.

## First-login migration

`SyncStore.associateAccount` claims existing outbox rows for the user. `bootstrapAccount` then enqueues
every existing playlist, like, saved album, follow, import batch, history row and (if enabled) search
term once per account. IDs are preserved so playlists/tracks do not duplicate. Local rows are never
deleted after upload.
Each outbox row is cleared only after its own upload succeeds, so a crash or network failure leaves a
recoverable queue. Re-running bootstrap is idempotent.

## Sync cycle and conflicts

1. Push up to 500 pending upserts, then up to 200 tombstones.
2. Pull incremental changes since the previous cycle start minus 5 seconds. Preferences are pulled
   before search history so a privacy opt-in applies in the same cycle.
3. Apply pulls in pages of 500; remote playlist tracks are fetched per changed playlist.
4. Record the cycle start as the next pull cursor and the completion time as “last synchronized”.

Push-before-pull plus a per-entity pending check gives deterministic last-writer-wins:
- If a local entity is still pending, an incoming remote copy is skipped.
- A later push overwrites the earlier one; the other device applies it on its next pull.
- Playlist entry lists are replaced atomically per playlist using `entry_id`, so repeated tracks and
  explicit positions survive. Two devices editing the **same** playlist offline resolve at
  whole-playlist granularity, so the later push wins and the earlier edit's entry changes are not
  merged.
- Provenance travels with each synced record. Imported playlist entries remain identifiable by
  `source_import_id`, which lets Undo run from a second device without deleting native entries.
- A rename after another device deleted the playlist resurrects it (the upsert clears `deleted_at`).
- Search terms are unique per `(user_id, query_key)`, so duplicate offline searches merge server-side.

The engine runs in the background every 60 seconds and backs off up to 5 minutes after failures. It
never blocks the player or Compose UI. Network failures leave changes queued and set the UI to
**Offline**; a later retry uploads them.

## UI

Profile menu → **Account & sync** provides:
- A Login / Sign up segmented header (Login selected by default) with Google OAuth, email/password,
  Show/Hide controls, an eight-character signup minimum, confirmation matching and inline validation.
  The primary action is red for this screen only; Revibe's lime accent is unchanged elsewhere.
- Email confirmation state and a password-reset email request.
- Signed-in profile, avatar/initials and profile editing.
- Sync phase (up to date, syncing, offline, error), last synchronized time, pending count,
  device id, manual **Sync now**, search-history privacy toggle and sign out.
- A sync status dot on the profile avatar and a status line in the menu.
- A cloud-not-configured explanation with the required environment variables.
- **Account → Import history**, showing source, import date and counts for playlists, tracks, likes,
  artists, albums, listening events and searches. Undo is confirmation-guarded and removes only rows
  owned by that import; the playlist and any later native entries remain.

## Tests

`JdbcSyncStoreTest` — search history persistence/de-duplication, account claiming/ownership guard,
playlist snapshot filtering/duplicate entries, remote playlist application preserving local files,
saved-album sync, remote album/import-batch application, pre-v5 migration with order preservation,
tombstones, history sync ids, preferences, schema idempotence.

`ImportStoreTest` — repeated playlist entries through add/remove/reorder, undo ownership for
playlists/entries/likes/albums/artists/history/searches, native-entry survival, provenance across a
store restart, one-time backfill and persistence of pre-v5 import-batch counts, repeated-import
batch merging, and imported/native activity counts.

`AccountImportHistoryUiTest` — desktop Compose coverage for the Account import-history card:
counts, refresh, undo confirmation, cancellation, and the exact batch passed to undo.

`ImportParsingTest` — overlapping account and extended-history exports pair one play without
collapsing distinct same-minute plays; source event ids and source package are retained.

`AuthApiTest` — password grant, confirmation-required signup, friendly auth errors, expired refresh
clears the session, restore refreshes and fetches a profile, service-role key rejection, the PKCE code
exchange, Google sign-in through a fake launcher, the not-configured setup error, a cancelled round
trip and sign-out clearing the session.

`AuthValidationTest` — email format, empty inputs, the eight-character minimum and password
confirmation.

`DesktopOAuthLauncherTest` — a real loopback HTTP listener: PKCE challenge matches the verifier, a
matching state is accepted, a mismatched state is rejected, provider denial maps to cancellation and
an abandoned round trip times out.

`AccountAuthUiTest` — Compose coverage for default Login tab, tab switching, signup field reveal,
empty/invalid input errors, signup submission values, per-field Show/Hide, login and signup loading
labels, server/setup banners, forgot-password validation and the Google action.

`ImportFlowUiTest` — the seven guided export steps, the three guide actions, selected-file progress,
and the success card (name, tracks added, review count, unmatched list, open/import-another/done).

`DpapiAuthSessionStoreTest` — the session file is encrypted, contains no plaintext tokens and
round-trips through save/load/clear on Windows.

`CloudSyncApiTest` — playlist payload strips stream/local fields, search payload omits a client UUID,
soft-deleted pulls, 401 and offline classification.

`SyncEngineTwoDeviceTest` — two independent SQLite libraries against an in-memory PostgREST stub:
second-device playlists/likes/albums/follows/history/search, imported records and provenance, an
undo performed on the second device, combined activity, edits, simultaneous renames, offline queue
and retry, delete-vs-rename resurrection and cross-account isolation.

These tests exercise the client contract. They do **not** apply the SQL migration or validate RLS,
GoTrue email delivery or real PostgREST behavior.

## Still pending

- Google OAuth has **not** been run against a real Supabase project: no project URL, anon key, Google
  client or allow-listed redirect URL was supplied. The loopback listener and PKCE exchange are tested
  locally, but provider consent, the Supabase redirect and the Google Cloud client configuration are
  unverified.
- Email confirmation and recovery emails have not been delivered or clicked in a real project, so the
  hosted Site URL behaviour remains unverified.
- Apply both migrations to the actual Supabase project and run the ten-step two-device acceptance flow.
  This is blocked until `REVIBE_SUPABASE_URL` and `REVIBE_SUPABASE_ANON_KEY` (or a linked project)
  are supplied; the mocked simulator is not a cloud test.
- Confirm RLS with two real users and direct PostgREST requests.
- Verify Supabase email confirmation and password-recovery redirects for the desktop flow.
- Run a real expired-token/refresh-revocation test against GoTrue.
- Profile edits are immediate PostgREST calls, not queued offline; a failed edit shows an error and
  can be retried manually.
- No Realtime subscription; background polling is the replication mechanism.
- `user_events` was not added because the current activity model derives entirely from history,
  likes, follows, saved albums and playlists.
