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
│   ├── commonMain/  models, sources, repositories, player controller, lyrics, Compose UI
│   └── desktopMain/ SQLite store, JavaFX engine, Skia artwork, scanner, Windows glue
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
    ↓
Contracts (commonMain)             CatalogSource, ArtistKnowledgeSource, LibraryStore,
    ↓                              PlaybackEngine, ArtworkLoader, LocalLibrary, FolderPicker
Desktop implementations (desktopMain)
    ITunesCatalogSource (common)   WikipediaArtistKnowledge (common)  ← HTTP via Ktor
    JdbcLibraryStore               JavaFxPlaybackEngine
    SkiaArtworkLoader              DesktopLocalLibrary
    SwingFolderPicker              DesktopMediaKeys
```

`AppViewModel` is a single explicit state holder rather than dozens of tiny view models. This keeps
loading/error state visible and avoids a DI framework at this size. `AppGraph` is the composition
root; `createDesktopGraph()` is the only place that constructs concrete platform classes.

## Threading

- Compose state is written from the Main dispatcher in `AppViewModel`.
- HTTP and disk work runs on `Dispatchers.IO` / `Dispatchers.Default`.
- The SQLite connection is serialised with a `Mutex` and used only off the UI thread.
- JavaFX callbacks arrive on the JavaFX application thread; `PlayerController` publishes them through
  a `StateFlow`, which Compose collects.

## Counterfactual / decisions

| Decision | Chosen | Why |
| --- | --- | --- |
| Catalog for milestone 1 | iTunes Search API | Keyless, real metadata/artwork, 30s previews, stable from this machine |
| YouTube Music via InnerTubeX | Not shipped | Third-party streaming outside YouTube's terms; see LICENSE_AND_COMPLIANCE |
| Desktop audio | JavaFX Media | No external runtime needed, real decoder, local + HTTP |
| Persistence | Hand-written SQLite DAO behind `LibraryStore` | Avoids codegen coupling in milestone 1; schema is portable |
| DI | Manual `AppGraph` | One module, ~15 objects; Koin can be introduced with Android/iOS |
| Image loading | Custom Skia loader | Fewer dependencies, palette extraction in the same decode |
| UI toolkit | Compose Multiplatform | Reuse for macOS/Linux/Android later |

## Multiplatform path

To add Android:
1. Add `androidTarget()` and an `androidMain` source set to `shared`.
2. Implement `PlaybackEngine` with Media3 and `LibraryStore` with Room (schema is already compatible).
3. Reuse `commonMain` UI and every repository unchanged.
4. Replace the iTunes source or add a second `CatalogSource`; the UI already consumes the interface.

iOS: AVFoundation engine, same repositories, Compose or SwiftUI shell.

## Failure behaviour

- Catalog failures are non-fatal and surfaced with retry.
- Playback errors are shown as a toast; the queue keeps its position.
- A missing preview URL is a skipped/unplayable state, never a fake progress bar.
- Lyrics fall back from exact match to search, then to "no lyrics found".
- Local scan removes entries whose files disappeared and skips unchanged mtimes.
