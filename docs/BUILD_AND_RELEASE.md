# Build and Release

## Requirements

- Windows 10/11 (this milestone).
- JDK 21 or newer; verified with JDK 23. `jvmTarget` is 21.
- Network access for Gradle dependencies and catalog search.
- **VLC 3.0.x runtime** for the native engine and the native-format tests. The app still builds and
  runs without it, falling back to JavaFX. The development runtime used here is in the ignored
  `.tooling/vlc/vlc-3.0.22` directory; it is not committed. No FFmpeg or GStreamer install is needed.
  Set `REVIBE_VLC_HOME` to the VLC directory containing `libvlc.dll` and `plugins/`.
- **Inno Setup 6** for the standalone per-user `.exe` installer. It is a build-time dependency only
  and is not shipped. It is the release/update format. The legacy Compose `packageMsi`/`packageExe`
  tasks remain available for local experiments but are not published by the release workflow.

The Gradle wrapper (8.14) is committed. Kotlin 2.4.21, Compose Multiplatform 1.12.2, Ktor 3.6.0,
coroutines 1.11.0, serialization 1.12.0. InnerTubeX `v0.13.1` is resolved from JitPack; the repository
is scoped to the `innertubex`/`quackjs` groups in `settings.gradle.kts`.

## Commands

```bash
# Compile
./gradlew :desktopApp:compileKotlinDesktop

# Run on LibVLC if available; JavaFX otherwise
./gradlew :desktopApp:run

# Force the fallback
REVIBE_PLAYBACK_ENGINE=javafx ./gradlew :desktopApp:run

# All desktop tests
./gradlew :shared:desktopTest

# Specific suites
./gradlew :shared:desktopTest --tests "com.revibe.CatalogSmokeTest"
./gradlew :shared:desktopTest --tests "com.revibe.PlaybackEngineTest"
./gradlew :shared:desktopTest --tests "com.revibe.LocalLibraryTest"
./gradlew :shared:desktopTest --tests "com.revibe.playback.VlcPlaybackEngineTest"
./gradlew :shared:desktopTest --tests "com.revibe.youtube.*"

# Debug packages (not the release path)
./gradlew :desktopApp:packageMsi
./gradlew :desktopApp:packageExe
# Output: desktopApp/build/compose/binaries/main/

# Standalone per-user EXE with Start Menu + desktop shortcuts
powershell -ExecutionPolicy Bypass -File tools/build-windows-installer.ps1
# Output: desktopApp/build/installer/Revibe-<version>-Setup.exe

# Release-equivalent EXE with the update feed baked in (unsigned opt-in)
powershell -ExecutionPolicy Bypass -File tools/build-windows-installer.ps1 `
  -Version 0.10.0 `
  -ManifestUrl "https://github.com/OWNER/REPO/releases/latest/download/update-manifest.json" `
  -AllowUnsignedUpdates
```

`revibeVersion`, `revibeUpdateManifestUrl`, `revibePublisher` and
`revibeAllowUnsignedUpdates` live in `gradle.properties` for development. The release workflow
overrides them from the computed tag. The same version drives the app image, installer filename,
`packageVersion` and generated `RevibeBuildInfo` object.

## Automatic release pipeline

`.github/workflows/release.yml` owns releases. A push to `main` computes the next SemVer from the
latest tag and conventional commit subjects, runs the full test suite, builds the EXE, performs a
clean install and a real EXE upgrade on a fresh Windows runner, and publishes only if every job
passes. The workflow does not commit a version bump back to `main`, so it cannot loop. Failed tests
or install checks stop before tagging. See [AUTOMATIC_UPDATES.md](AUTOMATIC_UPDATES.md) for the
manifest, signing policy, update handoff and manual dry-run instructions.

## Runtime layout

```
%USERPROFILE%\.revibe\
├── revibe.db        SQLite (WAL, -shm, -wal alongside)
├── account.session  DPAPI-encrypted Supabase session (when signed in)
├── cache\art\       embedded artwork extracted from local files
└── updates\         manifest state, install result, verified EXE and updater log
```

Application binaries are installed by the Inno Setup EXE outside this data directory. The updater
only writes under `updates\`; it never deletes or recreates the database, session or local music
references.
Set `REVIBE_HOME` to relocate everything (useful for tests and portable builds). Set
`REVIBE_CONTACT_EMAIL` before distribution so the lrc.red User-Agent carries the required contact
address; the app still identifies itself by repository URL when it is unset.

## Build facts and constraints

- JavaFX is declared with a platform classifier (`win`, `mac`, `mac-aarch64`, `linux`,
  `linux-aarch64`) computed from the host OS/arch. Without the classifier the artifact has no classes.
- The bundled runtime must include `jdk.httpserver`; `LocalStreamProxy` uses
  `com.sun.net.httpserver.HttpServer`. Without it the packaged launcher exits before creating a
  window even though the development build works against the full JDK.
- LibVLC is loaded from an explicit VLC home. `VlcNativeLocator` searches `REVIBE_VLC_HOME`,
  `VLC_HOME`, the project `.tooling` runtime and standard OS install locations. VLC 3 removed the
  `--plugin-path` CLI option, so the engine sets `VLC_PLUGIN_PATH` through JNA before creating the
  factory.
- JavaFX prints "Unsupported JavaFX configuration: classes were loaded from unnamed module" when used
  as the fallback from the classpath. It is a warning; playback and tests work. A jlink/module-path
  setup would remove it and shrink the runtime.
- Native tests require a matching 64-bit VLC runtime. They fail with a clear message if it is absent;
  the fallback JavaFX test does not need VLC.
- The Android SDK is present on the development machine but no `androidTarget()` exists yet, so the
  build never configures Android plugins.
- `compose.desktop.currentOs` pulls the Skiko native runtime for the host only.
- Tests share one JavaFX toolkit. Do **not** call `Platform.exit()` inside a test class: it kills the
  engine for later classes. The test worker exits after the suite; the app releases the engine on
  window close.
- Live YouTube tests depend on InnerTube's current web response and the network. They fail with the
  actual status/reason rather than being skipped silently.
- The in-app window capture helper is development-only (`tools/*.ps1`) and is not packaged.

## Verification performed

| Check | Result |
| --- | --- |
| `:desktopApp:compileKotlinDesktop` | Passed |
| LRC parser test (line + word sync) | Passed |
| Playback engine test (bundled local MP3, position advances) | Passed |
| Archived-catalog migration (likes, playlists, history preserved) | Passed |
| Native queue order + continuation | Passed (unit and live) |
| Local library test (scan, skip unchanged, remove missing) | Passed |
| YouTube search / mapping / resolution / compatibility / queue / lyrics | Passed (live network) |
| YouTube playback through JavaFX + proxy | Passed (fallback path) |
| LibVLC real-media matrix (M4A/AAC/MP3/WAV/FLAC/OGG/WebM/HLS) | Passed |
| LibVLC seek/pause/end/error/live YouTube | Passed (`VlcPlaybackEngineTest`, 9 checks) |
| YouTube playlist search/details and SQLite import | Passed |
| Expired signed-URL recovery | Passed (`ExpiredStreamRecoveryTest`) |
| Full `:shared:desktopTest` | 181 tests, 0 failures (46 suites) |
| Automatic update logic | Passed (unit tests; see `docs/AUTOMATIC_UPDATES.md`) |
| Real Inno EXE upgrade/relaunch | Passed by the release workflow's previous-release upgrade job |
| Unsigned EXE SHA-256 update path | Passed; Authenticode remains optional until a certificate exists |
| App launch on LibVLC + in-app YouTube playback | Verified via window capture |
| Playlist tab/import visual capture | Not captured; source and SQLite paths tested |
| Standalone per-user EXE | Built from the app image with Inno Setup 6 |
| Install without admin | Passed; installs to `%LOCALAPPDATA%\Programs\Revibe` by default |
| Start Menu + desktop shortcuts | Passed; both created and removed on uninstall |
| Launch without Java/JDK/Gradle | Passed with PATH reduced to `C:\Windows\System32;C:\Windows` |
| Installed playback / login / Google / playlists / settings | Passed on the installed build |
| Uninstall | Passed; install directory and shortcuts removed, `REVIBE_HOME` data preserved |
| Clean Windows runner | Passed in `Release Revibe`: tests, build, clean install, real EXE upgrade and uninstall |

## Release checklist

- [x] Run the full test suite on a clean checkout.
- [x] Verify the EXE on a machine without a JDK and with a stripped PATH.
- [x] Run the full test suite, build the EXE, install it on a clean runner, upgrade from the
      previous release through the production updater helper, and publish only on success.
- [x] Verify the updater with the actual Inno Setup EXE, result marker, relaunch and data
      preservation (see `docs/AUTOMATIC_UPDATES.md`).
- [ ] Add Authenticode signing and a stable application icon. The pipeline currently opts in to the
      unsigned SHA-256-only EXE path; a publisher pin upgrades this to signature verification.
- [ ] Ship the full GPL-3.0 text, InnerTubeX/QuackJS/vlcj notices and corresponding source offer.
- [ ] Install or bundle a compatible LibVLC 3.0.x runtime and include VLC's notices/source offer.
- [ ] Review third-party notices (JavaFX classpath exception, LGPL jaudiotagger/JNativeHook/JNA).
- [ ] Add a privacy note (search terms go to YouTube Music; track/artist names go to
      LRCLIB/Wikipedia; no telemetry).
- [ ] Confirm YouTube Music and LRCLIB/Wikipedia usage terms for the intended distribution model.
- [ ] Decide crash reporting (currently none).
- [ ] Verify first-run experience, empty library, offline start and network failure.
