# Release notes

The Windows release workflow uses `release-notes/<version>.md` for both the
GitHub release page and the `releaseNotes` field in `update-manifest.json`.

Example:

```text
# Revibe 0.10.0

- Update checks now run in the background.
- Playlist sync reliability improvements.
```

Create a file named after the version without a leading `v` (for example
`0.10.0.md`). If the file is absent, a manual workflow dispatch can provide
fallback text. The workflow does not publish an unsigned MSI.
