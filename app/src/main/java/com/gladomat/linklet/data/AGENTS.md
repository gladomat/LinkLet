# Data Layer

## Purpose

Everything below `data/` turns org files in the user's vault into queryable app state and keeps it in sync with a WebDAV remote. No UI or ViewModel code belongs here.

## Ownership

- `model/` — plain domain entities (`Note`, `NoteId`, `NoteLink`, `NoteIndexEntry`). No Android dependencies.
- `storage/` — `IStorage` abstraction over the vault; `DocumentTreeStorageImpl` (SAF) is what runs on device, `FileStorageImpl` is the plain-filesystem variant. All vault reads/writes go through `IStorage` — never touch files directly.
- `parser/` — org-mode parsing. `NoteMetadataParser` (cheap: title/id/filetags) feeds indexing pass 1; `org/OrgDocumentParser` (full block model) feeds the note view.
- `index/` — Room database + two-pass indexing pipeline. Owned by `index/AGENTS.md`.
- `sync/` — WebDAV sync engine, journal, conflict handling. Owned by `sync/AGENTS.md`.
- `settings/` — DataStore-backed repositories for WebDAV credentials, sync, and folder settings.
- `utils/` — org path/file helpers (`OrgFileUtils`, `OrgPathResolver`).

## Local Contracts

- Recoverable failures return `Result<T>`; only programmer errors throw.
- IO is `suspend` on injected dispatchers; reactive reads are `Flow`.
- SAF operations are slow (~50–100 ms per stat) and can hang: time-box them (`withTimeoutOrNull`) and never loop over the whole vault without a budget.
- `DocumentTreeStorageImpl.writeFileBytes` writing a dot-prefixed, extensionless filename (e.g. `.syncignore`, used by the in-app `.syncignore` editor) is unverified against a real on-device SAF tree URI — only the plain-filesystem `DocumentFile.fromFile` branch has automated coverage (`DocumentTreeStorageImplTests` — "writeFileBytes preserves a dot-prefixed extensionless filename exactly"), which can't reproduce a SAF provider's `DocumentsContract.createDocument()` possibly appending a mimeType-derived extension. Confirm on a physical device (`adb shell run-as com.gladomat.linklet ls -la <vault-path>` after a save) before treating this as settled.
- Interfaces are prefixed `I`, implementations end in `Impl`, and consumers inject the interface via Hilt (`app/di/AppModule.kt`).

## Verification

`./gradlew :app:testDebugUnitTest` — tests mirror this package tree under `tests/com/gladomat/linklet/data/`.

## Child DOX Index

- `index/AGENTS.md` — index_queue semantics, pass 1/2 pipeline, worker budgets and continuations.
- `sync/AGENTS.md` — sync_state lifecycle, operation journal, WebDAV specifics, indexing hand-off invariant.
