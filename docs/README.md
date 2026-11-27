# Revibe Documentation

Start with the README at the repository root, then:

Top-level status: [PROJECT_STATUS.md](../PROJECT_STATUS.md) · changes: [CHANGELOG.md](../CHANGELOG.md).

**Current milestones:** [ACCOUNT_SYNC.md](ACCOUNT_SYNC.md) — Supabase accounts, SQLite outbox and
multi-device sync; [AUTOMATIC_UPDATES.md](AUTOMATIC_UPDATES.md) — push-to-main GitHub Releases,
HTTPS manifest, verified Inno EXE download, optional Authenticode pin and real upgrade testing. Both
documents separate tested logic from still-pending live-service work. Previous visual QA remains in
[VISUAL_QA_0_8.md](VISUAL_QA_0_8.md); background research is in
[RESEARCH_REPORT_0_7.md](RESEARCH_REPORT_0_7.md).

| Document | Contents |
| --- | --- |
| [RESEARCH_REPORT_0_7.md](RESEARCH_REPORT_0_7.md) | Queue investigation, homepage/sidebar findings, provider decisions, next stages |
| [QUEUE_ENGINE.md](QUEUE_ENGINE.md) | Native `next()`/continuation contract, manual priority, invariants, live evidence |
| [PROVIDER_INTEGRATION_MATRIX.md](PROVIDER_INTEGRATION_MATRIX.md) | Verified providers, auth, limits, licences/terms, status |
| [ARTWORK_PROVIDER_ARCHITECTURE.md](ARTWORK_PROVIDER_ARCHITECTURE.md) | Artwork chain (YouTube → MusicBrainz/CAA → Deezer), high-res selection, cache, tests, unsupported sources |
| [MUSIC_METADATA_ARCHITECTURE.md](MUSIC_METADATA_ARCHITECTURE.md) | Authority model, matching, Last.fm/ListenBrainz, privacy |
| [LYRICS_PROVIDER_ARCHITECTURE.md](LYRICS_PROVIDER_ARCHITECTURE.md) | LRCLIB/lrc.red/Unison resolver, TTML/LRC parsing, matching, credits |
| [VISUAL_QA_0_8.md](VISUAL_QA_0_8.md) | Real-app captures, fixed defects, unverified states |
| [SPOTIFY_CANVAS_INTEGRATION.md](SPOTIFY_CANVAS_INTEGRATION.md) | Verified lack of official Canvas API, refusal, artwork alternative |
| [DYNAMIC_HOMEPAGE.md](DYNAMIC_HOMEPAGE.md) | Maturity states, section eligibility/ordering, test matrix |
| [REVIBE_ARCHITECTURE.md](REVIBE_ARCHITECTURE.md) | Modules, layers, threading, decisions, multiplatform path |
| [MUSIC_SOURCE_ARCHITECTURE.md](MUSIC_SOURCE_ARCHITECTURE.md) | Catalog contract, single playback source, archived rows, error handling |
| [PLAYBACK_ENGINE.md](PLAYBACK_ENGINE.md) | Engine contract, FFmpeg + Java Sound, preload/seek, latency, gaps |
| [METADATA_AND_ARTWORK.md](METADATA_AND_ARTWORK.md) | Active metadata/artwork sources and pipeline |
| [DEPENDENCY_MATRIX.md](DEPENDENCY_MATRIX.md) | Toolchain, libraries, services, licence flags |
| [ACCOUNT_SYNC.md](ACCOUNT_SYNC.md) | Supabase setup, account/session security, sync cycle, conflicts, tests, pending live E2E |
| [AUTOMATIC_UPDATES.md](AUTOMATIC_UPDATES.md) | Automatic release versioning, update manifest, security, Inno EXE handoff, CI upgrade test |
| [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md) | Local and cloud tables, indexes, migrations, retention, sync bookkeeping |
| [LICENSE_AND_COMPLIANCE.md](LICENSE_AND_COMPLIANCE.md) | GPL analysis, service terms, privacy, prohibited behaviour |
| [IMPLEMENTATION_ROADMAP.md](IMPLEMENTATION_ROADMAP.md) | Current stage status and exit criteria |
| [BUILD_AND_RELEASE.md](BUILD_AND_RELEASE.md) | Build commands, runtime layout, verification, release checklist |
| [REPOSITORY_RESEARCH.md](REPOSITORY_RESEARCH.md) | Reference repository findings and API checks |
| [WINDOWS_UI_DESIGN_SYSTEM.md](WINDOWS_UI_DESIGN_SYSTEM.md) | Colour, type, layout, components, motion, accessibility |
| [spotify-reference/README.md](spotify-reference/README.md) | Live Windows Spotify audit index and unverified states |
| [revibe-layout/README.md](revibe-layout/README.md) | Shell verification captures and measured states |

Historical planning documents (kept for traceability, not current status):
[REVIBE_UI_IMPLEMENTATION_PLAN.md](REVIBE_UI_IMPLEMENTATION_PLAN.md),
[REVIBE_QUEUE_ENGINE_PLAN.md](REVIBE_QUEUE_ENGINE_PLAN.md),
[REVIBE_RECOMMENDATION_ENGINE_PLAN.md](REVIBE_RECOMMENDATION_ENGINE_PLAN.md),
[REVIBE_PHASE_ROADMAP.md](REVIBE_PHASE_ROADMAP.md),
[REVIBE_DIRECTORY_TREE.md](REVIBE_DIRECTORY_TREE.md).

## Status in one line

Windows desktop app with real YouTube Music search, playlists, full AAC playback and native Up Next
queue through InnerTubeX; bundled FFmpeg 8.1.2 audio with Java Sound output; local library; LRCLIB lyrics with
lrc.red and Unison fallbacks; SQLite likes, playlists and history; maturity-driven home, real
recent-playlist recency, miniplayer and right sidebar with history timestamps. YouTube Music artwork
is primary, with MusicBrainz/CAA and Deezer fallbacks implemented. Supabase accounts and multi-device
sync are implemented on the client with a
versioned RLS migration and mocked two-device tests; a real Supabase project run is still pending.
Every push to `main` automatically tests, builds the Inno EXE, validates a clean install and a real
upgrade from the previous release, then tags and publishes; installed builds update through the same
verified EXE path. Code signing remains pending, so the release opts in to SHA-256-only verification.
Last.fm, ListenBrainz, Spotify import, Canvas and animated artwork, recognition and device selection
remain unsupported or in progress.
