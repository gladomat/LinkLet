# Sync Engine

## Purpose

Bidirectional WebDAV sync between the local vault and a remote server: change detection, upload/download planning, 3-way merge conflict resolution (DiffMatchPatch), and hand-off to the indexing pipeline.

## Ownership

- `SyncEngine` — the core state machine; operates on `sync_state` rows (lifecycle, pendingAction, hashes).
- `db/` — `SyncStateDao`, `OperationJournalDao` and entities.
- `webdav/` — Sardine-based client, PROPFIND parsing, Basic/Digest auth.
- `delta/` — Nextcloud delta API client (cheaper change listing where available).
- `planner/ReconcilePlanner`, `snapshot/`, `executor/JournalExecutor` — plan/execute reconciliation.
- `worker/SyncWorker` — WorkManager entry point (manual + periodic), foreground notifications.
- `capabilities/`, `prefetch/`, `gc/`, `metrics/` — probing, prefetch policy, garbage collection, metrics.

## Local Contracts

- **Every successful download and every conflict-copy write MUST be followed by `indexQueueDao.upsert(path, pass=1, PENDING)`.** `sync_state` alone does not make a note visible — only the index does. This invariant broke once (notes present in `sync_state` as ACTIVE but never indexed); do not reintroduce it.
- `SyncEngine`'s `operationJournalDao` and `indexQueueDao` constructor params are nullable only for test convenience; production Hilt injection always provides them.
- After a sync completes, `SyncWorker` schedules indexing pass 1 — a sync that skips this fails the run.
- Remote change detection is hash/ETag-based; a path whose recorded hash matches the remote is never re-downloaded. Remediation for missed files therefore goes through the index cold scan, not through re-sync.
- Conflict copies are real notes: they get their own `sync_state` row and index enqueue.
- `SyncPathFilter` has no file-extension allowlist — every file under the vault root syncs by default, filtered only by a small built-in junk blocklist plus the optional user `.syncignore` (gitignore-lite, parsed by `SyncIgnoreRules`, reloaded fresh at the start of every `SyncEngine.run()` and set on `SyncPathFilter.ignoreRules`). Directory descent (`isDirectoryTraversable`) MUST NOT gate on file extension — it broke remote discovery of every non-root attachment folder once; do not reintroduce an extension check there.

## Work Guidance

- Network calls are time-boxed; treat server quirks (see `docs/webdav-sync-current-implementation.md`) as data, not exceptions to hide.
- New sync behavior needs a `SyncEngineTests` case using the in-memory fakes in `tests/.../sync/`.

## Verification

`tests/com/gladomat/linklet/data/sync/` (~30 test classes across db/, executor/, planner/, webdav/, worker/). On-device debugging recipes: `FINDINGS.md` and `docs/troubleshooting.md`.
