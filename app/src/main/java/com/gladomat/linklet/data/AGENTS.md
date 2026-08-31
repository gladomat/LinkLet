# Data Layer

## Purpose

Everything below `data/` turns org files in the user's vault into queryable app state and keeps it in sync with a WebDAV remote. No UI or ViewModel code belongs here.

## Ownership

- `model/` — plain domain entities (`Note`, `NoteId`, `NoteLink`, `NoteIndexEntry`). No Android dependencies. `NoteIndexEntry.orgId` is populated from `NoteEntity.orgId` in `NoteRepositoryImpl.toIndexEntry()` — it can be null for notes not yet indexed by pass 1 (or after a full `reindex()`, which currently drops it; see Local Contracts).
- `storage/` — `IStorage` abstraction over the vault; `DocumentTreeStorageImpl` (SAF) is what runs on device, `FileStorageImpl` is the plain-filesystem variant. All vault reads/writes go through `IStorage` — never touch files directly.
- `parser/` — org-mode parsing. `NoteMetadataParser` (cheap: title/id/filetags) feeds indexing pass 1; `org/OrgDocumentParser` (full block model) feeds the note view.
- `index/` — Room database + two-pass indexing pipeline. Owned by `index/AGENTS.md`.
- `sync/` — WebDAV sync engine, journal, conflict handling. Owned by `sync/AGENTS.md`.
- `graph/` — pure-Kotlin force-directed layout engine for the note graph view. Owned by `graph/AGENTS.md`.
- `settings/` — DataStore-backed repositories for WebDAV credentials, sync, folder, and appearance settings. `AppearanceSettingsRepository` persists the active `ThemeId` (AMBERLINK/EVERFOREST/MODUS_OPERANDI/CATPPUCCIN_MOCHA/TOKYO_NIGHT), which `ui/theme/ThemeRegistry` resolves to a token set. Stored by enum name, falling back to `AMBERLINK` when the stored value is unknown, so removing or renaming an entry degrades instead of crashing. `ThemeMode`/`ThemePalette` in the same file are the superseded light-dark and accent-palette settings: still persisted, no longer read by the UI.
- `utils/` — org path/file helpers (`OrgFileUtils`, `OrgPathResolver`). `OrgFileUtils.buildNoteLink(title, orgId, path)` is the single canonical place that builds `[[id:...][label]]` / `[[file:...][label]]` link syntax — any screen inserting or copying a link to another note must call it rather than hand-rolling the bracket/label escaping.

## Local Contracts

- Recoverable failures return `Result<T>`; only programmer errors throw.
- IO is `suspend` on injected dispatchers; reactive reads are `Flow`.
- SAF operations can hang: time-box them (`withTimeoutOrNull`) and never loop over the whole vault without a budget. Never use `DocumentFile.findFile`/`listFiles()+name` on vault dirs — each child costs one binder query (~1–4 s on a 900-file folder); go through `DocumentTreeStorageImpl.findChild` (direct child-id lookup, ~5 ms, for `com.android.externalstorage.documents`; listing fallback elsewhere) and `listChildren` (one cursor per directory).
- `DocumentTreeStorageImpl.writeFileBytes` with a dot-prefixed, extensionless filename (`.syncignore`) keeps the exact name on a real `com.android.externalstorage.documents` tree (verified on device 2026-08-26); `DocumentTreeStorageImplTests` covers only the `DocumentFile.fromFile` branch.
- Interfaces are prefixed `I`, implementations end in `Impl`, and consumers inject the interface via Hilt (`app/di/AppModule.kt`).
- `androidx.documentfile:documentfile` is an explicit `1.1.0` dependency in `app/build.gradle.kts`: `1.0.0`'s `fromTreeUri()` ignores document URIs (resolves every child to the tree root), which `findChild`/`documentFileFor` rely on.
- Known bug: `NoteRepositoryImpl.reindex()` builds each `NoteEntity` with only `path`/`title`/`availability`/`source`, and `insertNotes()` is `OnConflictStrategy.REPLACE` — a full reindex silently resets `orgId`/`fileTags`/`linksReady`/fingerprint fields to their defaults for every existing note. Not yet fixed (its only call site, `LocalFolderSync.pull()`, appears unwired elsewhere in the app). Fix before wiring `reindex()`/`LocalFolderSync` into any reachable path.

## Verification

`./gradlew :app:testDebugUnitTest` — tests mirror this package tree under `tests/com/gladomat/linklet/data/`.

## Child DOX Index

- `index/AGENTS.md` — index_queue semantics, pass 1/2 pipeline, worker budgets and continuations.
- `sync/AGENTS.md` — sync_state lifecycle, operation journal, WebDAV specifics, indexing hand-off invariant.
- `graph/AGENTS.md` — Barnes-Hut force layout engine (pure Kotlin, no Android deps, testable without Robolectric).
