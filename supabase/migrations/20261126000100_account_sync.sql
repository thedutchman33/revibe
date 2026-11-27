-- Revibe account, cloud persistence and multi-device sync.
-- Run with the Supabase CLI (`supabase db push`) or paste into the SQL editor.
-- Only the publishable/anon key is used by the desktop client; every table is
-- protected by Row Level Security and every write validates auth.uid().

create extension if not exists pgcrypto;

-- ----------------------------------------------------------------- profiles
create table if not exists public.profiles (
    id uuid primary key references auth.users (id) on delete cascade,
    email text not null default '',
    display_name text,
    avatar_url text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- ---------------------------------------------------------------- playlists
create table if not exists public.playlists (
    id uuid primary key,
    user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
    name text not null check (char_length(name) between 1 and 200),
    description text not null default '',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

create table if not exists public.playlist_tracks (
    playlist_id uuid not null references public.playlists (id) on delete cascade,
    track_id text not null,
    user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
    position integer not null check (position > 0),
    track jsonb not null default '{}'::jsonb,
    added_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    primary key (playlist_id, track_id)
);

-- --------------------------------------------------------------- liked songs
create table if not exists public.liked_songs (
    user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
    track_id text not null,
    track jsonb not null default '{}'::jsonb,
    added_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    primary key (user_id, track_id)
);

-- ----------------------------------------------------------- followed artists
create table if not exists public.followed_artists (
    user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
    artist_id text not null,
    name text not null default '',
    image_url text,
    genre text,
    added_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    primary key (user_id, artist_id)
);

-- ---------------------------------------------------------- listening history
create table if not exists public.listening_history (
    id uuid primary key,
    user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
    track_id text not null,
    track jsonb not null default '{}'::jsonb,
    played_at timestamptz not null,
    played_ms bigint not null default 0 check (played_ms >= 0),
    completed boolean not null default false,
    skipped boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

-- ------------------------------------------------------------- search history
create table if not exists public.search_history (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
    query_key text not null,
    query text not null check (char_length(query) between 1 and 300),
    searched_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    unique (user_id, query_key)
);

-- ------------------------------------------------------------ user preferences
create table if not exists public.user_preferences (
    user_id uuid primary key default auth.uid() references auth.users (id) on delete cascade,
    market text not null default 'IN',
    autoplay_enabled boolean not null default true,
    sync_search_history boolean not null default false,
    updated_at timestamptz not null default now()
);

-- ------------------------------------------------------------------ devices
create table if not exists public.user_devices (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
    device_id text not null,
    name text not null default '',
    platform text not null default '',
    last_seen_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (user_id, device_id)
);

-- ------------------------------------------------------------------- indexes
create index if not exists playlists_user_updated on public.playlists (user_id, updated_at);
create index if not exists playlist_tracks_user_updated on public.playlist_tracks (user_id, updated_at);
create index if not exists liked_songs_user_updated on public.liked_songs (user_id, updated_at);
create index if not exists followed_artists_user_updated on public.followed_artists (user_id, updated_at);
create index if not exists listening_history_user_played on public.listening_history (user_id, played_at desc);
create index if not exists listening_history_user_updated on public.listening_history (user_id, updated_at);
create index if not exists search_history_user_searched on public.search_history (user_id, searched_at desc);
create index if not exists search_history_user_updated on public.search_history (user_id, updated_at);
create index if not exists user_devices_user on public.user_devices (user_id);

-- --------------------------------------------------------------- updated_at
create or replace function public.touch_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

do $$
declare
    table_name text;
begin
    foreach table_name in array array[
        'profiles', 'playlists', 'playlist_tracks', 'liked_songs',
        'followed_artists', 'listening_history', 'search_history',
        'user_preferences', 'user_devices'
    ] loop
        execute format('drop trigger if exists set_updated_at on public.%I', table_name);
        execute format(
            'create trigger set_updated_at before insert or update on public.%I
             for each row execute function public.touch_updated_at()',
            table_name
        );
    end loop;
end;
$$;

-- ------------------------------------------------------------- profile setup
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.profiles (id, email)
    values (new.id, coalesce(new.email, ''))
    on conflict (id) do nothing;
    insert into public.user_preferences (user_id)
    values (new.id)
    on conflict (user_id) do nothing;
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_user();

-- Backfill accounts that existed before this migration was applied.
insert into public.profiles (id, email)
select id, coalesce(email, '') from auth.users
on conflict (id) do nothing;
insert into public.user_preferences (user_id)
select id from auth.users
on conflict (user_id) do nothing;

-- ---------------------------------------------------------------------- RLS
alter table public.profiles enable row level security;
alter table public.playlists enable row level security;
alter table public.playlist_tracks enable row level security;
alter table public.liked_songs enable row level security;
alter table public.followed_artists enable row level security;
alter table public.listening_history enable row level security;
alter table public.search_history enable row level security;
alter table public.user_preferences enable row level security;
alter table public.user_devices enable row level security;

drop policy if exists "profiles are self-readable" on public.profiles;
create policy "profiles are self-readable" on public.profiles
    for select using ((select auth.uid()) = id);
drop policy if exists "profiles are self-updatable" on public.profiles;
create policy "profiles are self-updatable" on public.profiles
    for update using ((select auth.uid()) = id) with check ((select auth.uid()) = id);
drop policy if exists "profiles are self-insertable" on public.profiles;
create policy "profiles are self-insertable" on public.profiles
    for insert with check ((select auth.uid()) = id);

do $$
declare
    table_name text;
begin
    foreach table_name in array array[
        'playlists', 'playlist_tracks', 'liked_songs', 'followed_artists',
        'listening_history', 'search_history', 'user_preferences', 'user_devices'
    ] loop
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

-- The anon role may only reach GoTrue (signup/login/reset). It has no table access.
revoke all on public.profiles, public.playlists, public.playlist_tracks,
    public.liked_songs, public.followed_artists, public.listening_history,
    public.search_history, public.user_preferences, public.user_devices
    from anon;
grant select, insert, update, delete on public.profiles, public.playlists,
    public.playlist_tracks, public.liked_songs, public.followed_artists,
    public.listening_history, public.search_history, public.user_preferences,
    public.user_devices
    to authenticated;

-- --------------------------------------------- atomic playlist track replace
-- The client sends the complete ordered track list for one playlist. Removed
-- tracks are soft-deleted in the same transaction so other devices see the
-- removal on their next incremental pull.
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

    update public.playlist_tracks
    set deleted_at = now()
    where playlist_id = p_playlist_id and user_id = uid and deleted_at is null;

    insert into public.playlist_tracks (playlist_id, track_id, user_id, position, track, added_at)
    select p_playlist_id,
           item ->> 'track_id',
           uid,
           coalesce((item ->> 'position')::integer, 1),
           coalesce(item -> 'track', '{}'::jsonb),
           coalesce((item ->> 'added_at')::timestamptz, now())
    from jsonb_array_elements(coalesce(p_tracks, '[]'::jsonb)) as item
    on conflict (playlist_id, track_id) do update
    set position = excluded.position,
        track = excluded.track,
        added_at = excluded.added_at,
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
        when 'followed_artists' then
            update public.followed_artists set deleted_at = now()
            where artist_id = p_id and user_id = uid and deleted_at is null;
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

comment on table public.playlist_tracks is
    'Cloud metadata only. track jsonb is sanitized by the client: no stream URLs and no local file paths.';
