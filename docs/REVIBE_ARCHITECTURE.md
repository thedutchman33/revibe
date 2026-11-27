# Revibe Architecture

## Goals

1. A genuinely usable Windows desktop music app first.
2. Shared business logic, platform-specific audio/OS integration.
3. Honest integrations: nothing claimed as live unless it is.
4. A UI that is an original design with a music-player interaction model.

## Modules

```
Revibe/
├── shared/          Kotlin Multiplatform module
│   ├── commonMain/  models, sources, source manager, YouTube adapter, repositories,
│   │                player controller, lyrics, Compose UI
│   └── desktopMain/ SQLite store, FFmpeg engine + Java Sound, signed-stream headers,
│                    Skia artwork, scanner, Windows glue
├── desktopApp/      Compose Desktop host, frameless window, media keys
├── docs/            research, design, schema and roadmap documents
├── tools/           development helpers (window capture)
└── gradle/          wrapper + version catalog
```

Why only two modules: the app is small enough that micro-modules add build time without isolation.
`MusicSource`, `LibraryStore`, `PlaybackEngine`, `ArtworkLoader`, `LocalLibrary` and `FolderPicker`
are the seams that matter, and they are interfaces, not Gradle modules.

## Layers

```
Compose UI (commonMain)
    ↓ state + callbacks
AppViewModel (commonMain)          navigation, screen loading, UI state
    ↓
Repositories (commonMain)          CatalogRepository, LibraryRepository, LyricsRepository
    ↓                              MusicSourceManager (merge + priority)
Sources (commonMain)               YouTubeMusicSource (single playback catalog)
    ↓
Contracts (commonMain)             CatalogSource, TrackStreamResolver, ArtistKnowledgeSource,
    ↓                              LibraryStore, PlaybackEngine, ArtworkLoader, LocalLibrary
Desktop implementations (desktopMain)
    InnerTubeX client (common)     WikipediaArtistKnowledge (common)  ← HTTP via Ktor
    DesktopTrackStreamResolver     DesktopStreamHeaders
    JdbcLibraryStore               DesktopPlaybackEngineFactory
    SkiaArtworkLoader
    SwingFolderPicker
    DesktopPlaybackEngineFactory     -> FfmpegPlaybackEngine + JavaSoundSink
    DesktopLocalLibrary              DesktopMediaKeys
```

`AppViewModel` is a single explicit state holder rather than dozens of tiny view models. This keeps
loading/error state visible and avoids a DI framework at this size. `AppGraph` is the composition
root; `createDesktopGraph()` is the only place that constructs concrete platform classes.

## Threading

- Compose state is written from the Main dispatcher in `AppViewModel`.
- HTTP and disk work runs on `Dispatchers.IO` / `Dispatchers.Default`.
- The SQLite connection is serialised with a `Mutex` and used only off the UI thread.
- Each FFmpeg player owns one grabber, a decode thread, a render thread and one Java Sound line. An
  engine worker serializes load/play/pause/seek commands, and listener callbacks are published through
  a `StateFlow` that Compose collects. Engine calls stay behind the small `PlaybackEngine` interface,
  so neither UI code nor queue logic sees a native library type.

## Counterfactual / decisions

| Decision | Chosen | Why |
| --- | --- | --- |
| Primary catalog | YouTube Music via InnerTubeX v0.13.1 | Real full-length catalog, live search/browse, mapped into Revibe models |
| Fallback catalog | None | The preview-only catalog was retired; archived rows are data, not a source |
| YouTube transport | Progressive MP4/AAC direct in FFmpeg + resolver headers | Keeps signed URLs out of the UI and gives FFmpeg a seekable input; SABR still needs a dedicated transport |
| Desktop audio | FFmpeg via JavaCV + Java Sound | One bundled decoder path, no external VLC install; Revibe owns the clock, seek and output buffer |
| YouTube playlists | Map to `Album` + `isPlaylist`, import through `LibraryRepository` | Reuses the existing detail and playlist screens without a parallel model |
| Persistence | Hand-written SQLite DAO behind `LibraryStore` | Avoids codegen coupling in milestone 1; schema is portable |
| DI | Manual `AppGraph` | One module, ~15 objects; Koin can be introduced with Android/iOS |
| Image loading | Custom Skia loader | Fewer dependencies, palette extraction in the same decode |
| UI toolkit | Compose Multiplatform | Reuse for macOS/Linux/Android later |

## Multiplatform path

To add Android:
1. Add `androidTarget()` and an `androidMain` source set to `shared`.
2. Implement `PlaybackEngine` with Media3 and `LibraryStore` with Room (schema is already compatible).
3. Reuse `commonMain` UI and every repository unchanged.
4. Add another `CatalogSource` and register it in `MusicSourceManager`; the UI already consumes the
   interface. YouTube Music is the reference implementation.

iOS: AVFoundation engine, same repositories, Compose or SwiftUI shell.

## Failure behaviour

- Catalog failures are non-fatal and surfaced with retry; one source failing does not remove the
  other source's results.
- YouTube failures carry a specific status (`NETWORK_ERROR`, `REGION_UNAVAILABLE`,
  `AUTHENTICATION_REQUIRED`, `STREAM_UNAVAILABLE`, `UNSUPPORTED_TRANSPORT`, `API_ERROR`), not a
  blanket blocked state.
- Playback errors are shown as a toast; the queue keeps its position. YouTube URLs are re-resolved
  once before an error is shown.
- A missing stream URL is a skipped/unplayable state, never a fake progress bar.
- YouTube playlist import is an explicit user action. It creates a local playlist, copies the
  currently fetched tracks through `LibraryRepository`, and leaves the remote playlist untouched.
- Lyrics fall back from exact match to search, then to "no lyrics found".
- Local scan removes entries whose files disappeared and skips unchanged mtimes.
