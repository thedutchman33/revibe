# Revibe Documentation

Start with the README at the repository root, then:

| Document | Contents |
| --- | --- |
| [REVIBE_ARCHITECTURE.md](REVIBE_ARCHITECTURE.md) | Modules, layers, threading, decisions, multiplatform path |
| [REVIBE_DIRECTORY_TREE.md](REVIBE_DIRECTORY_TREE.md) | Actual file tree and what is intentionally absent |
| [REPOSITORY_RESEARCH.md](REPOSITORY_RESEARCH.md) | Findings from Metrolist, InnerTubeX, BitChord, VIVI Music |
| [DEPENDENCY_MATRIX.md](DEPENDENCY_MATRIX.md) | Toolchain, libraries, services and licence flags |
| [LICENSE_AND_COMPLIANCE.md](LICENSE_AND_COMPLIANCE.md) | GPL analysis, service terms, privacy, prohibited behaviour |
| [MUSIC_SOURCE_ARCHITECTURE.md](MUSIC_SOURCE_ARCHITECTURE.md) | Provider contract, active/blocked sources, error handling |
| [PLAYBACK_ENGINE.md](PLAYBACK_ENGINE.md) | Engine contract, JavaFX implementation, queue semantics, gaps |
| [LYRICS_PROVIDER_ARCHITECTURE.md](LYRICS_PROVIDER_ARCHITECTURE.md) | LRCLIB, LRC/word-sync parsing, caching, provider model |
| [METADATA_AND_ARTWORK.md](METADATA_AND_ARTWORK.md) | Metadata sources, artwork pipeline, palette extraction |
| [WINDOWS_UI_DESIGN_SYSTEM.md](WINDOWS_UI_DESIGN_SYSTEM.md) | Colour, type, layout, components, motion, accessibility |
| [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md) | Tables, indexes, retention, migration notes |
| [IMPLEMENTATION_ROADMAP.md](IMPLEMENTATION_ROADMAP.md) | Phase-by-phase status and next five tasks |
| [BUILD_AND_RELEASE.md](BUILD_AND_RELEASE.md) | Build commands, runtime layout, verification, release checklist |

## Status in one line

Windows desktop app with real search, real artwork, real lyrics, real preview/local playback,
playlists, queue and SQLite persistence. YouTube/InnerTube streaming is intentionally blocked on
terms; FLAC/OGG/OPUS, a native engine, SMTC and other platforms are next.
