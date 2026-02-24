# WebDAV Sync: Current Implementation

This document describes how WebDAV sync is implemented right now in LinkLet, based on the current code.

## High-Level Architecture

WebDAV sync is implemented as a three-part pipeline:

1. **Configuration + readiness**
- `WebDavSettingsRepository` stores encrypted credentials and endpoint configuration.
- `WebDavRemoteSyncProvider` only runs when settings are both `enabled` and `isConfigured()`.

2. **Sync planning + execution**
- `SyncEngine` performs discovery, reconciliation, and execution against local storage + remote metadata.
- Per-file sync state is persisted in Room (`sync_state` table via `SyncStateEntity` and `SyncStateDao`).

3. **Background orchestration**
- WorkManager workers (`SyncWorker`, `SyncEnqueueWorker`) schedule and run sync for initial/manual/periodic jobs.
- On successful remote sync, indexing is always scheduled afterward.

Core files:
- `app/src/main/java/com/gladomat/linklet/data/sync/WebDavRemoteSyncProvider.kt`
- `app/src/main/java/com/gladomat/linklet/data/sync/SyncEngine.kt`
- `app/src/main/java/com/gladomat/linklet/data/sync/worker/SyncWorker.kt`
- `app/src/main/java/com/gladomat/linklet/data/settings/WebDavSettingsRepository.kt`
- `app/src/main/java/com/gladomat/linklet/data/index/SyncStateDao.kt`
- `app/src/main/java/com/gladomat/linklet/data/sync/SyncState.kt`

## WebDAV Settings and Readiness

### Stored settings
`WebDavSettingsRepository` persists:
- `baseUrl`
- `rootPath`
- `username`
- `password`
- `enabled`
- `lastSyncedRootPath`
- per-root `initial sync completed` flags

Credentials are stored in `EncryptedSharedPreferences` via `MasterKey`.

### Resilience on encrypted prefs failure
If encrypted preferences cannot be opened (`AEADBadTagException`), the module:
- clears encrypted prefs and keysets,
- recreates fresh encrypted prefs,
- writes a one-time `RESET_DUE_TO_ERROR` flag.

`WebDavSettingsViewModel` consumes that flag and surfaces a message telling the user to re-enter settings.

### Initial sync markers
Initial sync completion is tracked per normalized root path (`webdav_initial_sync_completed_<rootPath>`), not globally.

## WebDAV Client Implementation (`WebDavRemoteSyncProvider`)

### HTTP stack + auth
Provider uses Sardine (`OkHttpSardine`) with OkHttp and supports:
- Digest auth
- Basic auth
- Auth caching (`CachingAuthenticatorDecorator`, `AuthenticationCacheInterceptor`)

Timeouts are 30s for connect/read/write.

### URL/path handling
Path logic is careful about encoding:
- path segments are percent-encoded safely,
- `%2F` inside a segment is preserved (not decoded into a real separator),
- relative and absolute `href` values from PROPFIND are normalized.

This avoids accidental path structure corruption for names containing encoded slash sequences.

### Remote listing
`listRemoteNotes()`:
- recursively traverses directories using repeated `PROPFIND Depth: 1` (`sardine.list(url, 1)`),
- collects only files (not directories),
- maps each file to `RemoteNoteMetadata(path, remoteId, fingerprint, lastModifiedEpochMillis)`.

`fingerprint` is normalized from server ETag (weak prefix + quotes removed).

### Remote filtering
Both local and remote paths pass through `SyncPathFilter.shouldInclude(...)`.
Current allowlist includes:
- `org`
- `png`, `jpg`, `jpeg`, `gif`, `webp`, `svg`, `pdf`
- `bib`

Paths with dot/underscore-prefixed segments (for example `_trash_bin`, `.git`) are excluded.

### Download
`download(path, remoteId)` performs GET on the built URL and returns an InputStream.

### Upload
`upload(...)` flow:
1. Ensures parent directories exist remotely (creates each parent collection).
2. Writes incoming stream to a temp file.
3. Executes PUT.
4. Captures ETag from PUT response via an interceptor if present.
5. If PUT response has no ETag, performs `PROPFIND Depth: 0` fallback to fetch ETag.
6. Returns `RemoteUploadResult(remoteId = path, fingerprint = etag)`.

If PUT returns 404, provider retries once after re-running remote directory creation.

### Delete (safe delete)
`delete(path, remoteId, fingerprint)`:
- if fingerprint is present, it sends `If-Match` with quoted ETag,
- executes DELETE,
- maps HTTP 412 behavior to `RemoteChangedException`.

So remote deletes are optimistic-locked when state has a fingerprint.

### Connection test
`testConnection(...)` runs a lightweight list against root (`Depth: 0`) to validate endpoint/auth.

## Sync State Model

`sync_state` stores per-path metadata:
- lifecycle (`ACTIVE`, `DELETED_LOCALLY`, `DELETED_REMOTELY`)
- `remoteId`
- `remoteFingerprint` (used as ETag/fingerprint)
- `localContentHash` (SHA-256)
- `lastKnownHash`
- `remoteETag` (currently effectively duplicate of fingerprint)
- `lastSyncedAtEpochMillis`
- `pendingAction`
- `lastError`
- `base_content` (present in schema; not actively used by sync engine)

Hashing uses SHA-256 (`NoteHashCalculator`).

## Sync Engine (`SyncEngine`)

`SyncEngine.run(...)` uses a 3-phase model.

## Phase A: Discovery

Collects:
- local files from `IStorage.listFiles()` (excluding `_trash_bin` and filtered by `SyncPathFilter`)
- remote files from provider listing
- existing sync states from Room (excluding `_trash_bin` states)

Fresh-install detection:
- if `syncStateDao.count() == 0`, engine considers it a fresh install and avoids hashing all local files for speed.

Local misconfiguration guard:
- if local file list is empty but previously synced states exist (`lastSyncedAtEpochMillis != null`), engine throws `LocalStorageMisconfiguredException` to avoid accidental destructive reconciliation.

Directory-change guard (WebDAV root change):
- when provider is WebDAV and sync states exist, engine compares current normalized root path vs last synced root path.
- If changed (or migration case where old path was never stored), it throws `SyncDirectoryChangedException` and blocks sync.

## Phase B: Reconciliation

For each path in union(local, remote, state), engine plans operations.

### Fresh install behavior
- local + remote both exist: **download remote to original path** (`"Fresh Install: Server wins"`), optionally backup differing local as conflict copy.
- local only: upload.
- remote only: download.

### Normal behavior (state exists/non-exists)
If no state:
- local+remote: conflict operation.
- local only: upload.
- remote only: download.

If state exists:
- both local+remote present:
  - both changed vs state: conflict.
  - only local changed: upload.
  - only remote changed: download.
  - unchanged: no-op (or update state if lifecycle not ACTIVE).
- local present, remote missing:
  - usually soft-delete local (move to `_trash_bin`) when file had remoteId.
  - retry upload if never had remoteId.
- local missing, remote present:
  - usually delete remote if state indicates it was synced before.
  - if never synced before, download (resurrect).
- both missing: cleanup orphan sync state row.

## Phase C: Execution

Operations are executed sequentially.

### Guard rails for remote deletes
Before execution, planned `DeleteRemote` operations are checked:
- abort if remote listing size is 0 but deletes are planned,
- abort if deleting 100% of remote files,
- abort if deleting >50 files,
- abort if deleting >50% of remote files,
- require confirmation exception if deleting >20%.

Exceptions:
- `CatastrophicDeleteException`
- `RequiresConfirmationException`

### Upload execution
- reads local bytes,
- computes hash,
- uploads via provider,
- updates state to ACTIVE and stores returned fingerprint/etag.

### Download execution
- downloads bytes,
- if `backupLocalIfDifferent=true`, creates `(Conflicted Copy YYYY-MM-DD HH-mm)` and marks that copy `pendingAction=UPLOAD`,
- writes downloaded bytes to original path,
- updates sync state fingerprint/hash/remoteId.

### Conflict operation
Current strategy is **copy local + accept remote**:
1. Save local content to conflicted-copy path and mark it pending upload.
2. Download remote version into original path.

### Local soft delete operation
When remote is missing but local remains synced:
- file is moved to `_trash_bin/<path>` locally,
- original state lifecycle becomes `DELETED_REMOTELY`.

### Remote delete execution
Deletes remote via provider using stored ETag/fingerprint when available; on success, state row is removed.

### End-of-run bookkeeping
On successful WebDAV sync:
- repository updates `lastSyncedRootPath` to current normalized root path.
- `SyncSummary` is returned (mostly based on `pendingAction` counts).

## Worker Orchestration

### Work types
`SyncWorkType`:
- `INITIAL`
- `MANUAL`
- `PERIODIC`

### Scheduling
`SyncScheduler`:
- `scheduleInitial()` enqueues one-time `SyncWorker` with network constraint.
- `scheduleManual()` enqueues one-time `SyncWorker` (no explicit network constraint).
- `schedulePeriodic(interval)` enqueues periodic `SyncEnqueueWorker` with network constraint.

Periodic worker does not sync directly; it enqueues one unique one-time sync work (`ExistingWorkPolicy.KEEP`) to prevent overlap.

### Effective type logic in worker
`SyncWorker` can force INITIAL mode when:
- requested type is INITIAL, or
- initial sync not marked completed for current root and provider is ready with network.

### Sync execution in worker
`SyncWorker`:
1. computes readiness (`webDavReady`, network),
2. optionally enters foreground service mode for INITIAL,
3. runs remote sync (`SyncEngine.run`) when eligible,
4. always schedules indexing pass1 afterward,
5. handles errors with notifications + work result (`failure`/`retry`).

Remote/network handling:
- if WebDAV not ready, worker skips remote sync and still schedules indexing.
- `IOException` returns `Result.retry()`.
- auth errors (401/403) return failure notification.

### Initial sync completion
After successful INITIAL sync with remote attempt, worker marks initial sync complete for current root path.

## UI and Status Surfacing

### Progress
`SyncWorker` publishes progress in work data:
- phase (`DISCOVERY`, `RECONCILE`, `EXECUTE`, `REINDEX`)
- completed/total/message

`NoteListViewModel` observes unique one-time sync work and derives:
- `isSyncing`
- `syncProgress` (fraction for determinate bar)

### Notifications
- Initial sync uses foreground ongoing notification.
- Result/status notifications are posted for success/failure/blocking situations.
- Directory-change notification deep-links to sync status screen via `SyncStatusNavigation` intent extra.

### Persisted sync status
`SyncStatusRepository` persists latest actionable status in DataStore.
Current mapper sets status for `DIRECTORY_CHANGED`.
`NoteListViewModel` shows snackbar action to open sync status screen when action is required.

`SyncStatusViewModel.clearAndContinue()`:
- clears sync state table,
- schedules manual sync,
- clears stored status.

## Trigger Points in App Flow

Sync is triggered from multiple places:
- App startup: `NoteListViewModel` schedules immediate sync.
- Saving/renaming/deleting/restoring notes: repository schedules immediate sync.
- Settings screen manual sync button.
- WebDAV settings first-time save (configured+enabled): schedules initial sync.
- App-wide periodic setup in `LinkLetApp` observes settings and enables/disables periodic scheduling.

## Current Behavioral Characteristics and Gaps

1. `MergeStrategy` exists but is not integrated into `SyncEngine` conflict resolution; conflict handling is file-level copy+replace.
2. `base_content` exists in schema but is not used in reconciliation logic.
3. `SyncStatusType.REQUIRES_CONFIRMATION` exists, but current worker only persists directory-change status. Delete-threshold confirmation currently surfaces via worker failure/notification, not persisted status mapping.
4. `SettingsUiState.directoryChangeDialog` plumbing exists in UI, but no code currently populates it from worker failures.
5. `SyncPendingAction` is set in some paths (for conflict copies) but most run-time actions are executed immediately; summary counts can be low/zero after successful operations.
6. WebDAV listing is recursive Depth-1 traversal per directory (simple and robust, but potentially expensive on very large trees).
7. Manual sync work has no explicit network constraint at scheduling time (worker itself checks connectivity for non-initial runs).

## Practical Summary

The current implementation is a stateful bidirectional file sync built around:
- Room-backed per-path sync metadata,
- ETag-based remote fingerprinting,
- safe remote delete via `If-Match`,
- guarded delete thresholds,
- deterministic conflict handling via conflicted local copies,
- WorkManager-based background orchestration,
- post-sync indexing integration.

It is functional and defensive for destructive operations, with known gaps mainly around richer conflict merge usage and fuller UI/status wiring for all non-success sync outcomes.
