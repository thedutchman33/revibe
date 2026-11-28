# Build and Release

## Requirements

- Windows 10/11 (this milestone).
- JDK 21 or newer; verified with JDK 23. `jvmTarget` is 21.
- Network access for Gradle dependencies and catalog search.
- **No external media runtime.** FFmpeg 8.1.2 and JavaCPP Windows x64 natives are resolved from
  Maven Central and bundled in the app. No VLC, GStreamer, JavaFX or `ffmpeg.exe` install is needed.
  JavaCPP extracts the native DLLs to `%USERPROFILE%\.javacpp\cache` on first use. `jna-platform` is
  still used for DPAPI and the Windows AppUserModelID.
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

# Run
./gradlew :desktopApp:run

# All desktop tests (live checks opt out with -PrevibeLiveTests=false)
./gradlew :shared:desktopTest
./gradlew :shared:desktopTest -PrevibeLiveTests=false

# Specific suites
./gradlew :shared:desktopTest --tests "com.revibe.CatalogSmokeTest"
./gradlew :shared:desktopTest --tests "com.revibe.playback.FfmpegPlaybackEngineTest"
./gradlew :shared:desktopTest --tests "com.revibe.LocalLibraryTest"
./gradlew :shared:desktopTest --tests "com.revibe.youtube.*"

# Live prefetch/seek probe (real YouTube Music + real Java Sound)
REVIBE_LATENCY_PROBE=1 ./gradlew :shared:desktopTest \
  --tests "com.revibe.playback.PreloadDurabilityProbe" \
  -PrevibeLiveTests=true --rerun-tasks

# Debug packages (not the release path)
./gradlew :desktopApp:packageMsi
./gradlew :desktopApp:packageExe
# Output: desktopApp/build/compose/binaries/main/

# Standalone per-user EXE with Start Menu + desktop shortcuts.
# Defaults to the production update feed and unsigned SHA-256 policy.
powershell -ExecutionPolicy Bypass -File tools/build-windows-installer.ps1
# Output: desktopApp/build/installer/Revibe-<version>-Setup.exe

# Local-only installer without update checks
powershell -ExecutionPolicy Bypass -File tools/build-windows-installer.ps1 -DevelopmentBuild
```

`revibeVersion`, `revibeUpdateManifestUrl`, `revibePublisher` and
`revibeAllowUnsignedUpdates` live in `gradle.properties` for development. The release workflow
overrides them from the computed tag. The same version drives the app image, installer filename,
`packageVersion` and generated `RevibeBuildInfo` object.

## Automatic release pipeline

`.github/workflows/release.yml` owns releases. A push to `main` computes the next SemVer from the
latest tag and conventional commit subjects, runs the full test suite, builds the EXE, performs a
clean install and a real EXE upgrade on a fresh Windows runner, and publishes only if every job
passes. Breaking changes on `0.x` intentionally bump the minor version; a deliberate
major release is started manually with the exact `version` input and `dry_run` disabled.
The workflow does not commit a version bump back to `main`, so it cannot loop. Failed tests
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

- The app image bundles `ffmpeg-8.1.2-1.5.14-windows-x86_64.jar`,
  `javacpp-1.5.14-windows-x86_64.jar`, their Java classes and the `jniav*`/`jnijavacpp` JNI DLLs.
  `FfmpegPlaybackEngine` uses JavaCV only; there is no vlcj, LibVLC or JavaFX dependency.
- The bundled runtime must include `jdk.httpserver` for the Supabase OAuth loopback callback
  (`DesktopOAuthLauncher` uses `com.sun.net.httpserver.HttpServer`). Playback itself opens the signed
  stream directly in FFmpeg and does not run a local HTTP server. `java.sql`, `java.naming` and
  `jdk.unsupported` are also required.
- `jna-platform` is kept for DPAPI (`Crypt32Util`) and the Windows AppUserModelID/taskbar identity.
  It is not a VLC dependency.
- The first FFmpeg load extracts/probes native libraries through JavaCPP and can take a couple of
  seconds in a fresh JVM. The engine warms that path on a preloader thread at startup. Subsequent
  loads use cached natives and are much faster.
- Deterministic FFmpeg tests use an injected paced `AudioSink`, so they need no audio device. The live
  YouTube test and the durability probe use the real Windows Java Sound line and real signed streams.
- Live YouTube tests depend on InnerTube's current web response and the network. They fail with the
  actual status/reason rather than being skipped silently. They are excluded from CI with
  `-PrevibeLiveTests=false` because GitHub datacenter IPs currently receive SABR-only responses.
- The Android SDK is present on the development machine but no `androidTarget()` exists yet, so the
  build never configures Android plugins.
- `compose.desktop.currentOs` pulls the Skiko native runtime for the host only.
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
| FFmpeg format matrix (M4A/AAC, AAC/ADTS, MP3, WAV, FLAC, OGG, WebM/Opus) | Passed (`FfmpegPlaybackEngineTest`) |
| FFmpeg seek/pause/end/error/prefetch/restart/rapid switching/long session | Passed (13 deterministic checks) |
| Live YouTube AAC playback, seek and preload durability | Passed (`FfmpegPlaybackEngineTest`, `PreloadDurabilityProbe`) |
| YouTube playlist search/details and SQLite import | Passed |
| Expired signed-URL recovery | Passed (`ExpiredStreamRecoveryTest`) |
| `:shared:desktopTest -PrevibeLiveTests=false` | 197 tests, 0 failures; artwork uses deterministic fixtures, live MusicBrainz is opt-in |
| Live YouTube transport checks | Opt-in with `-PrevibeLiveTests=true`; GitHub datacenter IPs currently receive SABR-only responses |
| Automatic update logic | Passed (unit tests; see `docs/AUTOMATIC_UPDATES.md`) |
| Real Inno EXE upgrade/relaunch | Passed by the release workflow's previous-release upgrade job |
| Unsigned EXE SHA-256 update path | Passed; Authenticode remains optional until a certificate exists |
| Installed-app playback through the real UI | Passed; play, pause/resume, forward/backward seeks within 14 ms, next/previous, rapid switching, restart-to-zero, artwork/lyrics and sustained playback on itag 251 Opus 48 kHz streams |
| App launch with bundled FFmpeg | Passed; installed test build loaded `avcodec-62`, `avformat-62`, `avutil-60` and `swresample-6` |
| Playlist tab/import visual capture | Not captured; source and SQLite paths tested |
| Standalone per-user EXE | `Revibe-1.0.0-Setup.exe`, 148,282,573 bytes, SHA-256 `52f49a1779a2d91a7fecd935936e5ffe311fd4f97554d032dc7c8b05c02e44bf` |
| Release 1.0.0 version metadata | Installer file/product version, installed `Revibe.exe` and in-app About all report 1.0.0 |
| Release 1.0.0 clean install | Passed in an isolated directory with a unique AppId; existing 0.11.1 install and data untouched, no stderr |
| Release 1.0.0 icon QA | Google mark and Spotify import glyph upright in normal and hover states at 1440×900 logical |
| Clean test install | Passed from an equivalent test installer with a unique AppId; fresh DB, window titled `Revibe`, no stderr |
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
- [ ] Ship the full GPL-3.0 text, InnerTubeX/QuackJS notices and corresponding source offer.
- [ ] Include the Apache-2.0 JavaCV/JavaCPP notices and the LGPL-2.1+ FFmpeg notice/source offer.
- [ ] Review third-party notices (LGPL jaudiotagger/JNativeHook, Apache-2.0 JNA).
- [ ] Add a privacy note (search terms go to YouTube Music; track/artist names go to
      LRCLIB/Wikipedia; no telemetry).
- [ ] Confirm YouTube Music and LRCLIB/Wikipedia usage terms for the intended distribution model.
- [ ] Decide crash reporting (currently none).
- [ ] Verify first-run experience, empty library, offline start and network failure.
