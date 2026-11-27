# Automatic Windows Updates

Revibe checks an HTTPS manifest, downloads a verified Windows installer, and
hands installation to a separate process after the app closes. Release builds
use the per-user Inno Setup EXE published by GitHub Actions. Account data,
playlists, settings, the local library and artwork cache live outside the
install directory and are never touched by the updater.

**Status:** implemented. The release pipeline tests a clean install and a real
upgrade from the previous release using the production updater script against
the actual Inno Setup EXE. The first build that can use this path is the first
release published after `v0.9.0`; `v0.9.0` itself is MSI-only and cannot upgrade
itself to an EXE release.

## Versioning and releases

The Git tag is the released version. `gradle.properties` keeps `0.9.0` as the
development default, while CI passes the computed version into Gradle at build
time:

```powershell
powershell -File tools/build-windows-installer.ps1 `
  -Version 0.10.0 `
  -ManifestUrl "https://github.com/thedutchman33/revibe/releases/latest/download/update-manifest.json" `
  -AllowUnsignedUpdates
```

Every push to `main` runs `.github/workflows/release.yml`. The workflow:

1. finds the newest `vX.Y.Z` tag and the commits since it;
2. increments the version from commit subjects:
   - `feat:` → minor (`0.9.0` → `0.10.0`);
   - `fix:`, `perf:`, `docs:`, `ci:` or any other conventional type → patch;
   - `type!:` or `BREAKING CHANGE` → minor while on `0.x`, major from `1.0.0`;
3. runs the full desktop test suite on Windows with VLC installed;
4. builds the app image and `Revibe-<version>-Setup.exe` with the update feed
   baked in;
5. performs a clean install/launch/uninstall on a fresh Windows runner;
6. downloads the previous GitHub release, installs it, runs the production
   updater helper against the new Inno EXE, and verifies the version, install
   directory, database, marker data and relaunch;
7. only if every job passed, creates the tag at the tested commit and publishes
   the release with the EXE, `SHA256SUMS.txt` and `update-manifest.json`.

It does **not** commit a version bump back to `main`, so it cannot trigger
itself. A concurrency group serializes releases. Before publishing, the job
rechecks that the tag and release do not exist and that the version is newer
than the latest tag. If HEAD is already tagged, the run is a no-op. Failed tests
or failed install checks stop the pipeline before any tag is created.

To test the whole pipeline without publishing, run the workflow manually with
`dry_run` enabled. A manual `version` input can recover a specific release, but
it must be greater than the latest tag.

## Manifest

The manifest is attached to every release:

```json
{
  "latestVersion": "0.10.0",
  "minimumVersion": "0.0.0",
  "downloadUrl": "https://github.com/thedutchman33/revibe/releases/download/v0.10.0/Revibe-0.10.0-Setup.exe",
  "releaseNotes": "Generated release notes…",
  "mandatory": false,
  "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
  "installerType": "inno-exe",
  "publishedAt": "2026-11-27T12:00:00Z"
}
```

- `downloadUrl` must use HTTPS and end in `.exe` or `.msi`.
- `sha256` is required and must be 64 hexadecimal characters.
- `minimumVersion` forces an update when the installed version is below it.
- `releaseNotes` is generated from the commits in the release.
- Unknown fields are ignored.

The stable feed URL is:

```text
https://github.com/thedutchman33/revibe/releases/latest/download/update-manifest.json
```

`REVIBE_UPDATE_MANIFEST_URL` overrides the baked URL for development. A blank
URL disables checks. `REVIBE_UPDATE_PUBLISHER` overrides the pinned signer.
`REVIBE_ALLOW_UNSIGNED_UPDATES` overrides the unsigned-release policy.

## Installation safety

1. The manifest and installer must use HTTPS. Redirects are followed and the
   final URI is checked again.
2. The installer streams to
   `%USERPROFILE%\.revibe\updates\downloads\Revibe-<version>-Setup.exe.part`
   and is atomically renamed only after a complete download.
3. Both the streaming hash and the hash read from disk must match the manifest
   SHA-256. A mismatch discards the file.
4. If `revibePublisher` is set, Windows Authenticode must return `Valid` and the
   signer subject must contain that exact publisher. A configured-but-invalid
   signature is always rejected.
5. Release builds currently set `revibeAllowUnsignedUpdates=true` because there
   is no code-signing certificate. With that explicit opt-in, an unsigned EXE is
   accepted only after the SHA-256 checks against the HTTPS GitHub release
   manifest. A build without either a publisher pin or the opt-in refuses to
   install.
6. The file is hashed again immediately before launch.
7. The app writes a PowerShell helper, starts it, and then closes through the
   normal window/tray shutdown path. The helper waits for the app PID and runs:
   `Revibe-<version>-Setup.exe /VERYSILENT /SUPPRESSMSGBOXES /NORESTART /SP- /CLOSEAPPLICATIONS`
   (MSI files still use `msiexec /i … /passive /norestart` for compatibility.)
8. The helper writes `updates/install-result.json` and relaunches Revibe. The
   next launch confirms the installed version or reports the failure and offers
   a retry.

The installer uses the same stable Inno AppId and upgrades the existing
per-user install in place. It creates no new data directory. The uninstaller
removes only the application directory and shortcuts.

## Data preservation

The updater writes only under `%USERPROFILE%\.revibe\updates`:

```text
%USERPROFILE%\.revibe\
├── revibe.db                 SQLite: playlists, likes, follows, history, settings
├── account.session           DPAPI-encrypted Supabase session
├── cache\art\                artwork cache
└── updates\
    ├── state.json            skip, reminder, last check
    ├── install-result.json   last installer result
    ├── updater.log           handoff log
    └── downloads\            verified installer / partial files
```

The CI upgrade test creates a database and a marker file in the data directory,
installs the new EXE over the previous release through the production helper,
then verifies both still exist, the install directory is unchanged, the registry
version is the new version, and the upgraded app launches. The clean-install
test also uninstalls and verifies that `revibe.db` survives.

## Unsigned-build trade-off

The EXE and manifest are both hosted under the same GitHub repository. SHA-256
protects against corrupted or truncated downloads, and HTTPS protects transport,
but neither is a substitute for an independently held code-signing key. Until an
Authenticode certificate is configured, a compromise of the GitHub account could
replace both the manifest and installer. The pipeline makes the unsigned mode an
explicit release-build setting (`revibeAllowUnsignedUpdates=true`) rather than a
development default. Adding a certificate and setting `revibePublisher` restores
the stronger pinned-signer check without changing the update flow.

## Verified and pending

Covered by automated tests and CI:

- SemVer precedence, manifest parsing, mandatory policy, skip/remind.
- EXE acceptance and rejection of non-installer URLs.
- SHA-256 mismatch and discard.
- Authenticode rejection when a publisher is pinned.
- Unsigned-mode opt-in and refusal without it.
- Production updater PowerShell syntax and Inno silent switches.
- Real Inno EXE install over the previous release, result JSON, registry
  version, unchanged install directory, data preservation and relaunch.
- Clean install without Java/JDK/Gradle, shortcuts and uninstall cleanup.

Pending or out of scope:

- Authenticode signing and a signed end-to-end release.
- Visual click-through of the update banner on a headless runner; the UI is
  unit-tested and the actual handoff is exercised by CI.
- MSIX/App Installer packaging.
- The `v0.9.0` → EXE bootstrap, which must be a one-time manual install because
  `v0.9.0` only accepts MSI downloads.
