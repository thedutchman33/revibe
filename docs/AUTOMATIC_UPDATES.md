# Automatic Windows Updates

Revibe checks a remotely hosted HTTPS manifest, verifies a downloaded MSI and
pins the Authenticode publisher before handing installation to Windows
Installer. The app never replaces its own running files and never deletes the
user data directory.

**Status:** client, manifest parser, release workflow and safety boundaries are
implemented. The update logic has automated tests. A real signed MSI upgrade and
restart on a packaged build has **not** been run yet; see
[Verified and pending](#verified-and-pending).

## Version source

`gradle.properties` is the single source of truth:

```properties
revibeVersion=0.9.0
revibeUpdateManifestUrl=
revibePublisher=
```

Gradle generates `com.revibe.RevibeBuildInfo` for the app and uses the same
`revibeVersion` for Compose's `packageVersion`. A release build passes the feed
URL and publisher at build time:

```bash
./gradlew :desktopApp:packageMsi \
  -PrevibeVersion=0.10.0 \
  -PrevibeUpdateManifestUrl=https://github.com/<owner>/<repo>/releases/latest/download/update-manifest.json \
  -PrevibePublisher="CN=Revibe, O=Revibe"
```

Environment overrides for development and testing:

| Variable | Purpose |
| --- | --- |
| `REVIBE_UPDATE_MANIFEST_URL` | Overrides the baked HTTPS manifest URL |
| `REVIBE_UPDATE_PUBLISHER` | Overrides the pinned signer subject/CN |

With a blank manifest URL, update checks stay disabled and the Settings update
row is hidden. The top-bar update control remains visible and explains that
updates apply to installed builds. The repository contains no personal GitHub
URL. The release workflow supplies the repository-specific URL.

## Manifest

The manifest is ordinary JSON:

```json
{
  "latestVersion": "0.10.0",
  "minimumVersion": "0.9.0",
  "downloadUrl": "https://github.com/owner/repo/releases/download/v0.10.0/Revibe-0.10.0.msi",
  "releaseNotes": "Faster sync.\nSafer update handoff.",
  "mandatory": false,
  "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
  "publishedAt": "2026-11-27T12:00:00Z"
}
```

- `latestVersion` is SemVer. A leading `v` is accepted.
- `minimumVersion` is the oldest version allowed to remain installed. If the
  installed version is below it, the update is mandatory even when
  `mandatory` is false. Use `0.0.0` when no floor is needed.
- `mandatory` forces the update independently of `minimumVersion`.
- `downloadUrl` must be HTTPS and end in `.msi`.
- `sha256` must be 64 lowercase hexadecimal characters. It is required for an
  available update.
- `releaseNotes` may be empty. `publishedAt` is display metadata.
- Unknown JSON fields are ignored for forward compatibility.

The manifest is capped at 1 MB and is fetched with `Cache-Control: no-cache`.
The feed itself is protected by HTTPS; the installer is additionally protected
by SHA-256 and Authenticode. There is no separate detached manifest signature:
manifest authenticity relies on the HTTPS origin, and the pinned publisher and
hash prevent launching an installer that is not from the expected signer.

## Check lifecycle

`UpdateService` is created once in `DesktopGraph` and started from
`AppViewModel`:

1. On startup, read `updates/install-result.json` written by the previous
   installer handoff. A successful result shows "Updated to Revibe x"; a failure
   shows the Windows Installer exit code and offers retry.
2. Wait 10 seconds, then check the manifest. This never blocks window creation,
   catalog loading or playback.
3. Check again every 6 hours while the app runs.
4. The update control left of Home can check immediately.
5. A manual check bypasses "Remind me later" and "Skip this version" for the
   notification, but never bypasses mandatory policy.

Network work is in the graph's background scope with timeouts (25 seconds for
the manifest). Errors become UI state, not crashes. An update being available
never pauses, seeks or otherwise alters the player.

## User interface

The control immediately left of the Home button is the primary update entry
point. It reads "Check updates" in its idle state, changes to "Update
available" when a release is ready, shows download/verification progress, and
starts installation when clicked. Updates are not checked from Settings in a
development build, so the obsolete "not configured" status is not shown there.

Settings → About Revibe keeps a compact update row when a feed is configured:

- current version;
- short status and Check/Update/Retry action;
- download, verification and installation progress.

Release notes and the optional Remind me later / Skip this version actions are
shown in the non-modal notification above the player. It never covers transport
controls. Mandatory updates have no skip/later controls but still do not stop
playback or close the app automatically.

Visual QA in the live app covered the development-build state, the new
Settings/Account layouts and the top-bar "Check updates" control at 1920×1140
(`build/visual-qa/settings-after-bottom.png`,
`account-signed-in-after.png`, `home-update-button.png`). The
"Update available" label, the available-update notification and the
signed-installer progress state were **not** visually exercised because no real
release feed or signed MSI exists yet; unit state transitions stand in for them
and are not claimed as a live UI run.

## Download and installation safety

1. **HTTPS only.** The manifest and installer must start with `https://`.
   Redirects are followed, then the final URI is checked again; non-HTTPS
   redirects are rejected.
2. **Streaming download.** The installer is written to
   `%USERPROFILE%\.revibe\updates\downloads\Revibe-<version>.msi.part`, hashed
   while streaming, then atomically renamed. Interruption deletes the partial
   and final files.
3. **SHA-256.** The downloaded hash and the hash computed from disk must both
   match the manifest. Mismatches discard the file.
4. **Authenticode.** PowerShell's `Get-AuthenticodeSignature` must return
   `Valid`; the signer subject must contain the publisher pinned in the build.
   A blank pin blocks installation rather than trusting any signer.
5. **Re-hash before launch.** The file is hashed again after signature
   verification so a changed file is never launched.
6. **Separate installer process.** The app writes a generated PowerShell
   helper, launches it, and calls the normal window shutdown path. The helper
   waits for the app PID, runs:
   `msiexec /i <verified.msi> /passive /norestart`
   then writes `updates/install-result.json` and relaunches Revibe.
7. **No direct file replacement.** Revibe never writes into its running
   installation directory.
8. **Recovery.** A failed installer relaunches the old app, which reads the
   result and shows an error with retry. A success is confirmed on the relaunch.
9. **No automatic installation in a dev build.** If a packaged launcher path
   cannot be identified, installation is blocked with a clear message; the
   downloaded file is discarded.

`/passive` shows the Windows Installer progress. The app's own state says
"Installing" while the handoff starts, then the app closes. The detailed
installation progress lives in the Windows Installer window; it is not
scraped from MSI internals.

## Data preservation

The updater only writes under `%USERPROFILE%\.revibe\updates`:

```
%USERPROFILE%\.revibe\
├── revibe.db                 SQLite: playlists, likes, follows, history, search history, settings
├── account.session           DPAPI-encrypted Supabase session
├── cache\art\                artwork cache
└── updates\
    ├── state.json            skip, reminder, last check
    ├── install-result.json   last Windows Installer result
    ├── updater.log           handoff log
    └── downloads\            verified installer / partial files
```

No update path deletes, moves or recreates `revibe.db`, `account.session`, the
local music folder or the user data directory. The MSI installs application
binaries in the normal Windows install location. Unit tests verify that an
update flow leaves an existing SQLite file byte-for-byte unchanged.

## GitHub Releases workflow

`.github/workflows/release.yml` runs on a `v*` tag or manual dispatch. It:

1. resolves SemVer, minimum version and mandatory flag;
2. builds the MSI with the repository's stable manifest URL and publisher pin;
3. signs the MSI with `signtool` and verifies the result;
4. computes SHA-256;
5. writes `update-manifest.json`;
6. publishes the MSI, manifest and release notes with `gh release create`.

Required repository configuration:

| Name | Type | Purpose |
| --- | --- | --- |
| `WINDOWS_PUBLISHER` | repository variable | Exact signer subject/CN, e.g. `CN=Revibe, O=Revibe` |
| `WINDOWS_CERTIFICATE_BASE64` | secret | Base64 PFX for Authenticode |
| `WINDOWS_CERTIFICATE_PASSWORD` | secret | PFX password |

The workflow fails rather than publishing an unsigned release. Release notes
come from `release-notes/<version>.md`; the manual dispatch text is a fallback.
The stable feed URL is:

```
https://github.com/<owner>/<repo>/releases/latest/download/update-manifest.json
```

This means the URL baked into a build is repository-specific only through the
release workflow, not hardcoded in source.

## Verified and pending

Automated coverage:

- SemVer precedence, including prereleases.
- Manifest parsing and defaults.
- Optional update, no-update and mandatory-from-minimum policy.
- Skip and reminder persistence/notification suppression.
- Download progress visibility.
- Interrupted download handling.
- SHA-256 mismatch rejection and discard.
- Invalid publisher rejection.
- Installer rejection and failure-result recovery.
- Successful install result confirmation.
- Database file preservation.
- Unpackaged builds refusing to launch an installer.
- Real `Get-AuthenticodeSignature` rejection of an unsigned file.

Not yet verified:

- A real signed MSI produced by the workflow.
- Upgrade of a previously installed MSI on a clean Windows machine.
- Windows Installer elevation/`/passive` behavior in the chosen install scope.
- Relaunch and install-result confirmation after a real upgrade.
- A real GitHub release/manifest endpoint and CDN redirects.
- Update while music is playing in a packaged build (the update path does not
  touch the player, but this end-to-end case is not claimed).
- Mandatory-update UX against a real feed.
- MSIX/Windows App Installer packaging.

## MSIX investigation

MSIX with Windows App Installer is a plausible future path: automatic updates,
package identity and rollback are first-class. It is not a drop-in replacement
for the current Compose Desktop MSI:

- MSIX requires a trusted code-signing certificate and package identity; the
  current build is a jpackage/Compose MSI with no package identity.
- App Installer updates require a signed `.appinstaller`/MSIX feed and a
  compatible install location/identity. The existing per-user `jpackage`
  launcher and `%USERPROFILE%\.revibe` data path must be revalidated.
- Native dependencies (LibVLC, JavaFX/Skiko, media keys) must be staged and
  tested inside the package.
- The current MSI handoff, `/passive` progress and install-result recovery would
  be replaced rather than layered on top.

No packaging format was switched. The MSI path remains the release target until
a packaged MSIX spike proves compatible with playback, local data and the
existing installer lifecycle.
