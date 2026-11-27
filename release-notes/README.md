# Release notes

Release notes are generated automatically by `.github/workflows/release.yml`
from the non-merge commits since the previous `vX.Y.Z` tag. Features, fixes,
performance changes and other changes are grouped into Markdown and used for
both the GitHub Release body and the `releaseNotes` field in
`update-manifest.json`.

No `release-notes/<version>.md` file is required for a release. Existing files
in this directory are historical snapshots, including the manually written
`0.9.0.md`.

The workflow also attaches the installer, `SHA256SUMS.txt` and
`update-manifest.json`. It only publishes after the full test suite, clean
install, and real previous-release EXE upgrade pass.
