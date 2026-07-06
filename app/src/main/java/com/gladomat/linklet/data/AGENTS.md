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
- `settings/` — DataStore-backed repositories for WebDAV credentials, sync, and folder settings.
- `utils/` — org path/file helpers (`OrgFileUtils`, `OrgPathResolver`). `OrgFileUtils.buildNoteLink(title, orgId, path)` is the single canonical place that builds `[[id:...][label]]` / `[[file:...][label]]` link syntax — any screen inserting or copying a link to another note must call it rather than hand-rolling the bracket/label escaping.

## Local Contracts

- Recoverable failures return `Result<T>`; only programmer errors throw.
- IO is `suspend` on injected dispatchers; reactive reads are `Flow`.
- SAF operations are slow (~50–100 ms per stat) and can hang: time-box them (`withTimeoutOrNull`) and never loop over the whole vault without a budget.
- `DocumentTreeStorageImpl.writeFileBytes` writing a dot-prefixed, extensionless filename (e.g. `.syncignore`, used by the in-app `.syncignore` editor) is unverified against a real on-device SAF tree URI — only the plain-filesystem `DocumentFile.fromFile` branch has automated coverage (`DocumentTreeStorageImplTests` — "writeFileBytes preserves a dot-prefixed extensionless filename exactly"), which can't reproduce a SAF provider's `DocumentsContract.createDocument()` possibly appending a mimeType-derived extension. Confirm on a physical device (`adb shell run-as com.gladomat.linklet ls -la <vault-path>` after a save) before treating this as settled.
- Interfaces are prefixed `I`, implementations end in `Impl`, and consumers inject the interface via Hilt (`app/di/AppModule.kt`).
- Known bug: `NoteRepositoryImpl.reindex()` builds each `NoteEntity` with only `path`/`title`/`availability`/`source`, and `insertNotes()` is `OnConflictStrategy.REPLACE` — a full reindex silently resets `orgId`/`fileTags`/`linksReady`/fingerprint fields to their defaults for every existing note. Not yet fixed (its only call site, `LocalFolderSync.pull()`, appears unwired elsewhere in the app). Fix before wiring `reindex()`/`LocalFolderSync` into any reachable path.

## Verification

`./gradlew :app:testDebugUnitTest` — tests mirror this package tree under `tests/com/gladomat/linklet/data/`.

## Child DOX Index

- `index/AGENTS.md` — index_queue semantics, pass 1/2 pipeline, worker budgets and continuations.
- `sync/AGENTS.md` — sync_state lifecycle, operation journal, WebDAV specifics, indexing hand-off invariant.
- `graph/AGENTS.md` — Barnes-Hut force layout engine (pure Kotlin, no Android deps, testable without Robolectric).
