# Revibe Directory Tree

Generated from the working tree at milestone 1. Build outputs, the Gradle distribution,
reference checkouts and supplied media are git-ignored.

```
Revibe/
├── README.md
├── LICENSE
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── docs/
│   ├── README.md
│   ├── REVIBE_ARCHITECTURE.md
│   ├── REVIBE_DIRECTORY_TREE.md
│   ├── REPOSITORY_RESEARCH.md
│   ├── DEPENDENCY_MATRIX.md
│   ├── LICENSE_AND_COMPLIANCE.md
│   ├── MUSIC_SOURCE_ARCHITECTURE.md
│   ├── PLAYBACK_ENGINE.md
│   ├── LYRICS_PROVIDER_ARCHITECTURE.md
│   ├── METADATA_AND_ARTWORK.md
│   ├── WINDOWS_UI_DESIGN_SYSTEM.md
│   ├── DATABASE_SCHEMA.md
│   ├── IMPLEMENTATION_ROADMAP.md
│   └── BUILD_AND_RELEASE.md
├── tools/
│   ├── capture-window.ps1
│   └── list-windows.ps1
├── shared/
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/com/revibe/
│       │   ├── AppGraph.kt
│       │   ├── catalog/
│       │   │   ├── CatalogSource.kt
│       │   │   ├── CatalogRepository.kt
│       │   │   ├── ITunesCatalogSource.kt
│       │   │   └── WikipediaArtistKnowledge.kt
│       │   ├── image/ArtworkLoader.kt
│       │   ├── library/
│       │   │   ├── LibraryStore.kt
│       │   │   └── LibraryRepository.kt
│       │   ├── local/LocalLibrary.kt
│       │   ├── lyrics/Lyrics.kt
│       │   ├── model/Models.kt
│       │   ├── playback/
│       │   │   ├── PlaybackEngine.kt
│       │   │   └── PlayerController.kt
│       │   ├── settings/SettingsRepository.kt
│       │   └── ui/
│       │       ├── AppState.kt
│       │       ├── RevibeApp.kt
│       │       ├── components/
│       │       │   ├── Artwork.kt
│       │       │   ├── Cards.kt
│       │       │   ├── Chrome.kt
│       │       │   ├── Primitives.kt
│       │       │   └── RightPanel.kt
│       │       ├── screens/
│       │       │   ├── DetailScreens.kt
│       │       │   ├── HomeScreen.kt
│       │       │   ├── LibraryScreens.kt
│       │       │   └── SearchScreen.kt
│       │       └── theme/Theme.kt
│       ├── desktopMain/kotlin/com/revibe/
│       │   ├── DesktopGraph.kt
│       │   ├── data/JdbcLibraryStore.kt
│       │   ├── image/SkiaArtworkLoader.kt
│       │   ├── local/DesktopLocalLibrary.kt
│       │   ├── playback/JavaFxPlaybackEngine.kt
│       │   └── platform/DesktopPlatform.kt
│       └── desktopTest/kotlin/com/revibe/
│           ├── CatalogSmokeTest.kt
│           ├── LocalLibraryTest.kt
│           └── PlaybackEngineTest.kt
└── desktopApp/
    ├── build.gradle.kts
    └── src/desktopMain/kotlin/com/revibe/desktop/Main.kt
```

## Not present (intentionally, for now)

- `androidApp/`, `iosApp/` — no targets until each platform's audio engine and store are implemented.
- `backend/` — Listen Together and sync are Phase 7; no client secrets belong here before that.
- `listenTogetherProto/`, `musicSources/`, `lyricsProviders/` Gradle modules — these are interfaces in
  `shared` today; splitting them into modules would add build cost without isolation benefits yet.
- `packaging/` — Compose Desktop's `nativeDistributions` covers MSI/EXE generation.
- Spotify binaries, APK, EXE and reference repositories are not committed.
