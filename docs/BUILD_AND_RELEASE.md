# Build and Release

## Requirements

- Windows 10/11 (this milestone).
- JDK 21 or newer; verified with JDK 23. `jvmTarget` is 21.
- Network access for Gradle dependencies and catalog search.
- No VLC, FFmpeg, Android Studio or Android SDK is required for the desktop build.

The Gradle wrapper (8.14) is committed. Compose Multiplatform 1.8.2, Kotlin 2.1.21, Ktor 3.1.3.

## Commands

```bash
# Compile
./gradlew :desktopApp:compileKotlinDesktop

# Run
./gradlew :desktopApp:run

# All desktop tests
./gradlew :shared:desktopTest

# Specific suites
./gradlew :shared:desktopTest --tests "com.revibe.CatalogSmokeTest"
./gradlew :shared:desktopTest --tests "com.revibe.PlaybackEngineTest"
./gradlew :shared:desktopTest --tests "com.revibe.LocalLibraryTest"

# Package
./gradlew :desktopApp:packageMsi
./gradlew :desktopApp:packageExe
# Output: desktopApp/build/compose/binaries/main/
```

## Runtime layout

```
%USERPROFILE%\.revibe\
├── revibe.db        SQLite (WAL, -shm, -wal alongside)
└── cache\art\       embedded artwork extracted from local files
```

Set `REVIBE_HOME` to relocate everything (useful for tests and portable builds).

## Build facts and constraints

- JavaFX is declared with a platform classifier (`win`, `mac`, `mac-aarch64`, `linux`,
  `linux-aarch64`) computed from the host OS/arch. Without the classifier the artifact has no classes.
- JavaFX prints "Unsupported JavaFX configuration: classes were loaded from unnamed module" when run
  from the classpath. It is a warning; playback and tests work. A jlink/module-path setup would remove
  it and shrink the runtime.
- The Android SDK is present on the development machine but no `androidTarget()` exists yet, so the
  build never configures Android plugins.
- `compose.desktop.currentOs` pulls the Skiko native runtime for the host only.
- Tests that use JavaFX must shut the toolkit down (`Platform.exit()`) or the test worker stays alive.
- The in-app window capture helper is development-only (`tools/*.ps1`) and is not packaged.

## Verification performed

| Check | Result |
| --- | --- |
| `:desktopApp:compileKotlinDesktop` | Passed |
| Catalog smoke test (raw HTTP + mapped tracks) | Passed |
| LRC parser test (line + word sync) | Passed |
| Playback engine test (real preview, position advances) | Passed |
| Local library test (scan, skip unchanged, remove missing) | Passed |
| App launch + live home feed with real artwork | Verified via window capture |
| MSI/EXE packaging | Not run in this milestone |

## Release checklist

- [ ] Run the full test suite on a clean checkout.
- [ ] Verify MSI/EXE on a machine without a JDK.
- [ ] Add code signing and a stable application icon.
- [ ] Review third-party notices (JavaFX classpath exception, LGPL jaudiotagger/JNativeHook).
- [ ] Add a privacy note (search terms go to Apple/LRCLIB/Wikipedia; no telemetry).
- [ ] Confirm iTunes API usage terms for the intended distribution model.
- [ ] Decide update mechanism and crash reporting (currently none).
- [ ] Verify first-run experience, empty library, offline start and network failure.
