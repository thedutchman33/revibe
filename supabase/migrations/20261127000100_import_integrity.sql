-- Revibe 0.9.2: import provenance, duplicate playlist entries, saved albums
-- and import-batch metadata for multi-device sync.
--
-- Safe to apply after 20261126000100_account_sync.sql. Existing playlist rows
-- get one generated entry UUID each; track ids and positions are preserved.

-- ------------------------------------------------ playlist entry identity
alter table public.playlist_tracks add column if not exists entry_id uuid;
update public.playlist_tracks set entry_id = gen_random_uuid() where entry_id is null;
alter table public.playlist_tracks alter column entry_id set default gen_random_uuid();
alter table public.playlist_tracks alter column entry_id set not null;

do $$
declare
    pk_name text;
begin
    select conname into pk_name
    from pg_constraint
    where conrelid = 'public.playlist_tracks'::regclass and contype = 'p';
    if pk_name is not null and pk_name <> 'playlist_tracks_pkey' then
        execute format('alter table public.playlist_tracks drop constraint %I', pk_name);
    elsif pk_name is not null then
        alter table public.playlist_tracks drop constraint playlist_tracks_pkey;
    end if;
    alter table public.playlist_tracks add constraint playlist_tracks_pkey primary key (playlist_id, entry_id);
exception
    when duplicate_table then null;
    when duplicate_object then null;
end;
$$;

create unique index if not exists playlist_tracks_entry on public.playlist_tracks (entry_id);

-- ------------------------------------------------------------- provenance
alter table public.playlists add column if not exists source_platform text;
alter table public.playlists add column if not exists source_import_id text;
alter table public.playlists add column if not exists imported_at timestamptz;
alter table public.playlists add column if not exists original_source_identifier text;

alter table public.playlist_tracks add column if not exists source_platform text;
alter table public.playlist_tracks add column if not exists source_import_id text;
alter table public.playlist_tracks add column if not exists imported_at timestamptz;

alter table public.liked_songs add column if not exists source_platform text;
alter table public.liked_songs add column if not exists source_import_id text;
alter table public.liked_songs add column if not exists imported_at timestamptz;
alter table public.liked_songs add column if not exists original_source_identifier text;

alter table public.followed_artists add column if not exists source_platform text;
alter table public.followed_artists add column if not exists source_import_id text;
alter table public.followed_artists add column if not exists imported_at timestamptz;
alter table public.followed_artists add column if not exists original_source_identifier text;

alter table public.listening_history add column if not exists source_platform text;
alter table public.listening_history add column if not exists source_import_id text;
alter table public.listening_history add column if not exists imported_at timestamptz;
alter table public.listening_history add column if not exists source_event_id text;
alter table public.listening_history add column if not exists original_source_identifier text;

alter table public.search_history add column if not exists source_platform text;
alter table public.search_history add column if not exists source_import_id text;
alter table public.search_history add column if not exists imported_at timestamptz;
alter table public.search_history add column if not exists source_event_id text;
alter table public.search_history add column if not exists original_source_identifier text;

-- ------------------------------------------------------------- saved albums
create table if not exists public.saved_albums (
    user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
    album_id text not null,
    title text not null default '',
    artist text not null default '',
    artwork_url text,
    year integer,
    track_count integer not null default 0,
    genre text,
    artist_id text,
    added_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    source_platform text,
    source_import_id text,
    imported_at timestamptz,
    original_source_identifier text,
    primary key (user_id, album_id)
);

-- ----------------------------------------------------------- import batches
create table if not exists public.import_batches (
    -- Text matches the local import id format and preserves existing batches.
    id text primary key,
    user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
    source_platform text not null,
    fingerprint text not null,
    imported_at timestamptz not null default now(),
    summary text not null default '',
    playlists_count integer not null default 0,
    tracks_count integer not null default 0,
    likes_count integer not null default 0,
    albums_count integer not null default 0,
    artists_count integer not null default 0,
    history_count integer not null default 0,
    searches_count integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

create index if not exists saved_albums_user_updated on public.saved_albums (user_id, updated_at);
create index if not exists import_batches_user_updated on public.import_batches (user_id, updated_at);

-- --------------------------------------------------------------- ownership
alter table public.saved_albums enable row level security;
alter table public.import_batches enable row level security;

do $$
declare
    table_name text;
begin
    foreach table_name in array array['saved_albums', 'import_batches'] loop
        execute format('drop policy if exists "owner select" on public.%I', table_name);
        execute format(
            'create policy "owner select" on public.%I for select using ((select auth.uid()) = user_id)',
            table_name
        );
        execute format('drop policy if exists "owner insert" on public.%I', table_name);
        execute format(
            'create policy "owner insert" on public.%I for insert with check ((select auth.uid()) = user_id)',
            table_name
        );
        execute format('drop policy if exists "owner update" on public.%I', table_name);
        execute format(
            'create policy "owner update" on public.%I for update using ((select auth.uid()) = user_id)
             with check ((select auth.uid()) = user_id)',
            table_name
        );
        execute format('drop policy if exists "owner delete" on public.%I', table_name);
        execute format(
            'create policy "owner delete" on public.%I for delete using ((select auth.uid()) = user_id)',
            table_name
        );
    end loop;
end;
$$;

revoke all on public.saved_albums, public.import_batches from anon;
grant select, insert, update, delete on public.saved_albums, public.import_batches to authenticated;

-- Keep updated_at current for the new tables.
drop trigger if exists set_updated_at on public.saved_albums;
create trigger set_updated_at before insert or update on public.saved_albums
for each row execute function public.touch_updated_at();
drop trigger if exists set_updated_at on public.import_batches;
create trigger set_updated_at before insert or update on public.import_batches
for each row execute function public.touch_updated_at();

-- ------------------------------------------- atomic playlist track replace
-- Repeated track ids are allowed: the entry id is the identity, and position
-- is explicit. Existing entries not present in the payload are soft-deleted.
create or replace function public.sync_replace_playlist_tracks(
    p_playlist_id uuid,
    p_tracks jsonb
)
returns void
language plpgsql
security invoker
set search_path = public
as $$
declare
    uid uuid := auth.uid();
    incoming_ids uuid[];
begin
    if uid is null then
        raise exception 'not authenticated';
    end if;
    if not exists (
        select 1 from public.playlists
        where id = p_playlist_id and user_id = uid and deleted_at is null
    ) then
        raise exception 'playlist not found';
    end if;

    select coalesce(array_agg((item ->> 'entry_id')::uuid), '{}'::uuid[])
    into incoming_ids
    from jsonb_array_elements(coalesce(p_tracks, '[]'::jsonb)) as item;

    update public.playlist_tracks
    set deleted_at = now()
    where playlist_id = p_playlist_id
      and user_id = uid
      and deleted_at is null
      and not (entry_id = any(incoming_ids));

    insert into public.playlist_tracks (
        playlist_id, entry_id, track_id, user_id, position, track, added_at,
        source_platform, source_import_id, imported_at
    )
    select p_playlist_id,
           (item ->> 'entry_id')::uuid,
           item ->> 'track_id',
           uid,
           coalesce((item ->> 'position')::integer, 1),
           coalesce(item -> 'track', '{}'::jsonb),
           coalesce((item ->> 'added_at')::timestamptz, now()),
           item ->> 'source_platform',
           item ->> 'source_import_id',
           (item ->> 'imported_at')::timestamptz
    from jsonb_array_elements(coalesce(p_tracks, '[]'::jsonb)) as item
    on conflict (playlist_id, entry_id) do update
    set track_id = excluded.track_id,
        position = excluded.position,
        track = excluded.track,
        added_at = excluded.added_at,
        source_platform = excluded.source_platform,
        source_import_id = excluded.source_import_id,
        imported_at = excluded.imported_at,
        deleted_at = null,
        updated_at = now();

    update public.playlists set updated_at = now() where id = p_playlist_id and user_id = uid;
end;
$$;

-- ------------------------------------------------------- whitelisted deletes
create or replace function public.sync_soft_delete(p_table text, p_id text)
returns void
language plpgsql
security invoker
set search_path = public
as $$
declare
    uid uuid := auth.uid();
begin
    if uid is null then
        raise exception 'not authenticated';
    end if;

    case p_table
        when 'playlists' then
            update public.playlists set deleted_at = now()
            where id = p_id::uuid and user_id = uid and deleted_at is null;
            update public.playlist_tracks set deleted_at = now()
            where playlist_id = p_id::uuid and user_id = uid and deleted_at is null;
        when 'liked_songs' then
            update public.liked_songs set deleted_at = now()
            where track_id = p_id and user_id = uid and deleted_at is null;
        when 'saved_albums' then
            update public.saved_albums set deleted_at = now()
            where album_id = p_id and user_id = uid and deleted_at is null;
        when 'followed_artists' then
            update public.followed_artists set deleted_at = now()
            where artist_id = p_id and user_id = uid and deleted_at is null;
        when 'import_batches' then
            update public.import_batches set deleted_at = now()
            where id = p_id and user_id = uid and deleted_at is null;
        when 'listening_history' then
            update public.listening_history set deleted_at = now()
            where id = p_id::uuid and user_id = uid and deleted_at is null;
        when 'search_history' then
            update public.search_history set deleted_at = now()
            where id = p_id::uuid and user_id = uid and deleted_at is null;
        else
            raise exception 'unsupported table: %', p_table;
    end case;
end;
$$;

revoke all on function public.sync_replace_playlist_tracks(uuid, jsonb) from public;
revoke all on function public.sync_soft_delete(text, text) from public;
grant execute on function public.sync_replace_playlist_tracks(uuid, jsonb) to authenticated;
grant execute on function public.sync_soft_delete(text, text) to authenticated;

comment on column public.playlist_tracks.entry_id is
    'Unique playlist-entry identity. Allows the same track to appear more than once.';
comment on column public.import_batches.summary is
    'Import counts and bounded source notes only; never the source ZIP or unrelated personal data.';
