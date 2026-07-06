# LinkLet

[![Android CI](https://github.com/gladomat/LinkLet/actions/workflows/android.yml/badge.svg)](https://github.com/gladomat/LinkLet/actions/workflows/android.yml)
![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)
![Status](https://img.shields.io/badge/status-pre--launch-orange)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

An Android client for browsing, editing, and navigating an [org-mode](https://orgmode.org/)
note vault (org-roam style) that lives in a folder synced by an external tool: WebDAV,
Nextcloud, Syncthing, or similar.

Most org-roam tooling assumes a desktop and Emacs. LinkLet reads the same vault folder your
desktop already syncs. Read, edit, and follow `[[links]]` between notes on your phone.

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Build](#build)
  - [Run the tests](#run-the-tests)
- [Usage](#usage)
  - [Opening a vault](#opening-a-vault)
  - [Sync setup](#sync-setup)
  - [Controlling what syncs (`.syncignore`)](#controlling-what-syncs-syncignore)
- [Project Layout](#project-layout)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)
- [Acknowledgements](#acknowledgements)

## Features

- Browse and edit `.org` files from a local vault folder (SAF folder picker). No account,
  no cloud lock-in: your files stay wherever you already sync them.
- Org-mode rendering: headings, bullets, numbered lists, tables, emphasis, blocks,
  inline images, and `[[target][description]]` links. Links inside headings are clickable
  too, same as anywhere else in the note body.
- Backlink index (SQLite) with a two-pass, resumable indexing pipeline. Navigating between
  linked notes stays fast even in a large vault.
- Bidirectional WebDAV sync with 3-way merge conflict resolution. Edit offline on your
  phone and on your desktop; LinkLet reconciles both.
- In-note search (literal, whole-word, regex) with match highlighting and navigation.
- Section drawers/pills for collapsing large notes (properties, logbooks, etc.).
- User-editable `.syncignore` rules through an in-app editor (gitignore-lite syntax). Keep
  backups, archives, and other junk out of sync without touching a file manager.

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose, Material 3
- **Architecture:** MVVM + Hilt (dependency injection)
- **Storage:** Storage Access Framework (SAF) over a user-picked folder tree
- **Local index:** Room (SQLite), two-pass indexing pipeline
- **Background work:** WorkManager (periodic + manual sync)
- **Sync protocol:** WebDAV (Sardine client), 3-way merge via DiffMatchPatch
- **Testing:** JUnit4, MockK, Robolectric, Turbine, Compose UI testing

## Getting Started

### Prerequisites

- Android Studio (any recent version). You need its bundled JBR 21, not just the IDE.
- An Android device or emulator running API 26+ (minSdk 26).
- Optionally, a WebDAV endpoint (Nextcloud, ownCloud, or similar) if you want sync.

### Build

Build with Android Studio's bundled JBR 21. The system default `java` (often a newer JDK)
gets rejected by Gradle/AGP with a cryptic `> 25.0.1` failure:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug
```

### Run the tests

```sh
./gradlew :app:testDebugUnitTest
```

Some Robolectric-backed tests are skipped on Apple Silicon (arm64) hosts, so a green local
run is not full coverage. CI (`.github/workflows/android.yml`, x86 Ubuntu) runs the full suite.

## Usage

### Opening a vault

On first launch, pick the folder that holds your `.org` files: the same folder your sync
tool of choice (Nextcloud client, Syncthing, etc.) already keeps up to date. LinkLet
reads and writes through that folder directly. It never copies your notes elsewhere.

### Sync setup

Point the app at a WebDAV endpoint (Nextcloud, ownCloud, etc.) from Settings. Sync is
bidirectional: local changes upload, remote changes download, and conflicting edits get a
3-way merge with a conflict copy as fallback.

**What syncs:** every file under the vault root, by default. There is no file-extension
allowlist. A small built-in blocklist always excludes OS/editor junk (`.DS_Store`,
`Thumbs.db`, `desktop.ini`, `__MACOSX`, `.git`, `.trash`, `ltximg/`) and any path with a
dot- or underscore-prefixed segment (e.g. `_trash_bin`, `.git/config`).

### Controlling what syncs (`.syncignore`)

For anything else you don't want synced (backup files, databases, archives, etc.), add
rules to a `.syncignore` file at the vault root, or edit it right from the app: open
**Settings → WebDAV → Edit sync exclusions**. The in-app editor shows a dry-run preview of
what a rule change will include or exclude before you save. Gitignore-lite syntax:

- `#` starts a comment; blank lines are skipped.
- `*` matches within one path segment; `?` matches a single character.
- `**` matches across segments: a leading `**/` matches at any depth, a trailing `/**`
  matches everything under a directory.
- A pattern containing `/` (other than a trailing one) is anchored to the vault root; a bare
  name matches at any depth.
- A trailing `/` restricts the pattern to a directory and everything under it.
- `!`-negation is not supported.

A ready-to-copy starting point (matching what earlier versions of the app excluded by
default) is checked in at
[`docs/templates/syncignore-default.txt`](docs/templates/syncignore-default.txt). The
in-app editor seeds new vaults with it automatically. The file itself is never synced
(its leading dot already excludes it), so keep a copy on each device, or use the editor's
"Copy to clipboard" action to replicate it manually.

See [`docs/webdav-sync-current-implementation.md`](docs/webdav-sync-current-implementation.md)
for the full sync protocol write-up.

## Project Layout

Single Gradle module `:app` (Kotlin, Jetpack Compose, Hilt, Room, WorkManager):

```
app/src/main/java/com/gladomat/linklet/
├── data/          # model, storage (SAF), parser, index, sync, settings, utils
├── domain/        # repository + service interfaces
├── ui/            # Compose screens, components, theme
└── viewmodel/     # per-screen ViewModels
tests/             # unit + Robolectric tests, mirrors the package tree above
```

See [`AGENTS.md`](AGENTS.md) for the full module map and contribution conventions
(this is also the working contract for AI coding agents in this repo).

## Documentation

- [`docs/webdav-sync-current-implementation.md`](docs/webdav-sync-current-implementation.md): sync protocol details.
- [`docs/QA_FEATURE_MATRIX.md`](docs/QA_FEATURE_MATRIX.md): canonical feature/coverage matrix and defect register.
- [`docs/troubleshooting.md`](docs/troubleshooting.md): post-mortems and debugging heuristics.
- [`CHANGELOG.md`](CHANGELOG.md): notable changes by epic.

## Contributing

This is a pre-launch, single-maintainer project. Issues and PRs are welcome, but expect
review against the conventions in [`AGENTS.md`](AGENTS.md):

1. Match the existing layer boundaries (no UI/repository code crossing into `data/`).
2. Add or update unit tests for any new logic (`./gradlew :app:testDebugUnitTest` must pass).
3. Follow [Conventional Commits](https://www.conventionalcommits.org/) (`feat(scope): ...`,
   `fix(scope): ...`, etc.). See `AGENTS.md` §5 for the full convention and PR template.
4. Keep diffs focused: one concern per commit/PR.

## License

MIT. See [`LICENSE`](LICENSE).

## Acknowledgements

- [org-roam](https://www.orgroam.com/) and the wider Org-mode ecosystem, for the note
  format and linking model this app renders and navigates.
- [Sardine](https://github.com/thegrizzlylabs/sardine-android) for the WebDAV client.
- [diff-match-patch](https://github.com/google/diff-match-patch) for 3-way merge conflict
  resolution.
