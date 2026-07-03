# LinkLet

An Android client for browsing, editing, and navigating an [org-mode](https://orgmode.org/)
note vault (org-roam style) that lives in a folder synced by an external tool — WebDAV,
Nextcloud, Syncthing, etc.

## Features

- Browse and edit `.org` files from a local vault folder (SAF folder picker).
- Org-mode rendering: headings, bullets, numbered lists, tables, emphasis, blocks,
  inline images, and `[[target][description]]` links — including links inside headings,
  which are clickable like anywhere else in the note body.
- Backlink index (SQLite) with a two-pass, resumable indexing pipeline.
- Bidirectional WebDAV sync with 3-way merge conflict resolution.
- In-note search (literal, whole-word, regex) with match highlighting and navigation.
- Section drawers/pills for collapsing large notes.

## Building

Build with Android Studio's bundled JBR 21 — the system default `java` (often a newer JDK)
is rejected by Gradle/AGP with a cryptic `> 25.0.1` failure:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug
```

Run unit tests the same way:

```sh
./gradlew :app:testDebugUnitTest
```

Some Robolectric-backed tests are skipped on Apple Silicon (arm64) hosts — a green local run
is not full coverage. CI (`.github/workflows/android.yml`, x86 Ubuntu) runs the full suite.

## Sync setup

Point the app at a WebDAV endpoint (Nextcloud, ownCloud, etc.) from Settings. Sync is
bidirectional: local changes upload, remote changes download, and conflicting edits get a
3-way merge with a conflict copy as fallback.

**What syncs:** every file under the vault root, by default — there is no file-extension
allowlist. A small built-in blocklist always excludes OS/editor junk (`.DS_Store`,
`Thumbs.db`, `desktop.ini`, `__MACOSX`, `.git`, `.trash`, `ltximg/`) and any path with a
dot- or underscore-prefixed segment (e.g. `_trash_bin`, `.git/config`).

**Excluding more, yourself:** drop a `.syncignore` file at the vault root for anything else
you don't want synced (backup files, databases, archives, etc.). It uses gitignore-lite
syntax:

- `#` starts a comment; blank lines are skipped.
- `*` matches within one path segment; `?` matches a single character.
- `**` matches across segments — a leading `**/` matches at any depth, a trailing `/**`
  matches everything under a directory.
- A pattern containing `/` (other than a trailing one) is anchored to the vault root; a bare
  name matches at any depth.
- A trailing `/` restricts the pattern to a directory and everything under it.
- `!`-negation is not supported.

A ready-to-copy starting point (matching what earlier versions of the app excluded by
default) is checked in at [`docs/templates/syncignore-default.txt`](docs/templates/syncignore-default.txt) —
copy it to your vault root as `.syncignore` and edit as needed. The file itself is never
synced (its leading dot already excludes it), so keep a copy on each device.

See [`docs/webdav-sync-current-implementation.md`](docs/webdav-sync-current-implementation.md)
for the full sync protocol write-up.

## Project layout

Single Gradle module `:app` (Kotlin, Jetpack Compose, Hilt, Room, WorkManager). Unit tests
live under top-level `tests/`. See [`AGENTS.md`](AGENTS.md) for the full module map and
contribution conventions.

## Documentation

- [`docs/webdav-sync-current-implementation.md`](docs/webdav-sync-current-implementation.md) — sync protocol details.
- [`docs/QA_FEATURE_MATRIX.md`](docs/QA_FEATURE_MATRIX.md) — canonical feature/coverage matrix and defect register.
- [`docs/troubleshooting.md`](docs/troubleshooting.md) — post-mortems and debugging heuristics.
- [`CHANGELOG.md`](CHANGELOG.md) — notable changes by epic.
