# Sync Cancellation Fix (WorkManager + Initial Full Sync) Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task.

**Goal:** Ensure sync continues when navigating away from Settings by moving manual sync to WorkManager, and automatically run an initial full sync (foreground) immediately after first WebDAV configuration.

**Architecture:** ViewModels never run `SyncEngine` directly. They enqueue WorkManager sync jobs via `SyncScheduler`. `SyncWorker` becomes the single entry-point that performs (optional) WebDAV sync + local reindex, with a foreground notification for initial/full sync runs.

**Tech Stack:** Kotlin, Hilt DI, WorkManager (`CoroutineWorker`), Robolectric unit tests, MockK, kotlinx-coroutines-test.

## Definitions

- **Initial sync:** First successful remote sync for the currently configured WebDAV root. Runs as a *foreground* WorkManager job.
- **Manual sync:** User-initiated “Sync now”. If initial sync has not completed yet, manual sync upgrades itself to initial sync.

---

### Task 1: Persist initial-sync completion state

**Files:**
- Modify: `app/src/main/java/com/gladomat/linklet/data/settings/WebDavSettingsRepository.kt`
- Test: `tests/com/gladomat/linklet/data/settings/WebDavSettingsRepositoryTests.kt`

**Step 1: Write failing test**
- Add a test asserting `hasCompletedInitialSync()` defaults to `false`, and becomes `true` after `markInitialSyncCompleted()`.

**Step 2: Run test to verify it fails**
- Run: `./gradlew :app:testDebugUnitTest --tests 'com.gladomat.linklet.data.settings.WebDavSettingsRepositoryTests'`
- Expected: FAIL (missing methods/keys).

**Step 3: Implement minimal code**
- Add preference key(s) for initial sync completion (and optionally root-path association).
- Add methods:
  - `fun hasCompletedInitialSync(rootPath: String): Boolean`
  - `fun markInitialSyncCompleted(rootPath: String)`
  - `fun resetInitialSyncCompleted()`

**Step 4: Run test to verify it passes**
- Run: `./gradlew :app:testDebugUnitTest --tests 'com.gladomat.linklet.data.settings.WebDavSettingsRepositoryTests'`
- Expected: PASS.

---

### Task 2: Add WorkManager sync types + foreground notification

**Files:**
- Modify: `app/src/main/java/com/gladomat/linklet/data/sync/SyncScheduler.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/data/sync/worker/SyncWorker.kt`
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/worker/SyncWorkType.kt`
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/worker/SyncWorkerNotifications.kt`

**Step 1: Implement minimal code**
- Add `SyncWorkType` (`INITIAL`, `MANUAL`).
- Add `SyncScheduler.scheduleInitial()` and `SyncScheduler.scheduleManual()`, both enqueueing `SyncWorker` with unique names (e.g. `LinkletInitialSync`, `LinkletManualSync`).
- Update `SyncWorker`:
  - Determine effective sync mode (`INITIAL` if requested initial, or if manual + initial not completed).
  - If effective sync is `INITIAL`, call `setForeground(...)` and update notification as progress changes.
  - Run `syncEngine.run(...)` if WebDAV is ready, then run `noteRepository.reindex()`.
  - On successful initial sync, call `markInitialSyncCompleted(...)`.

**Step 2: Run compilation**
- Run: `./gradlew :app:compileDebugKotlin`

---

### Task 3: Stop lifecycle cancellation by removing sync from SettingsViewModel

**Files:**
- Modify: `app/src/main/java/com/gladomat/linklet/viewmodel/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/app/di/AppModule.kt` (if DI needs updates)

**Step 1: Write failing test**
- Update `tests/com/gladomat/linklet/viewmodel/settings/SettingsViewModelTests.kt`:
  - `requestManualSync()` should call `SyncScheduler.scheduleManual()` and should *not* invoke `SyncEngine`.

**Step 2: Run test to verify it fails**
- Run: `./gradlew :app:testDebugUnitTest --tests 'com.gladomat.linklet.viewmodel.settings.SettingsViewModelTests'`
- Expected: FAIL (old dependencies/behavior).

**Step 3: Implement minimal code**
- Refactor `SettingsViewModel` to depend on `SyncScheduler` instead of directly running sync.
- Keep folder selection validation and the “directory change” dialog logic.

**Step 4: Run test to verify it passes**
- Run: `./gradlew :app:testDebugUnitTest --tests 'com.gladomat.linklet.viewmodel.settings.SettingsViewModelTests'`
- Expected: PASS.

---

### Task 4: Auto-trigger initial sync right after first WebDAV configuration

**Files:**
- Modify: `app/src/main/java/com/gladomat/linklet/viewmodel/settings/WebDavSettingsViewModel.kt`
- Test: `tests/com/gladomat/linklet/viewmodel/settings/WebDavSettingsViewModelTests.kt` (create if missing)

**Step 1: Write failing test**
- When `save()` transitions from “no settings saved” → “settings saved and enabled”, it should call `SyncScheduler.scheduleInitial()`.
- When settings already exist, `save()` should not auto-trigger initial sync.

**Step 2: Run test to verify it fails**
- Run: `./gradlew :app:testDebugUnitTest --tests 'com.gladomat.linklet.viewmodel.settings.WebDavSettingsViewModelTests'`
- Expected: FAIL (missing scheduling).

**Step 3: Implement minimal code**
- Inject `SyncScheduler`.
- In `save()`, load previous settings, save new settings, then conditionally schedule initial sync per requirements.

**Step 4: Run test to verify it passes**
- Run: `./gradlew :app:testDebugUnitTest --tests 'com.gladomat.linklet.viewmodel.settings.WebDavSettingsViewModelTests'`
- Expected: PASS.

---

### Task 5: Verification pass

**Files:**
- Verify: all changed files

**Step 1: Run full unit test suite**
- Run: `./gradlew test`
- Expected: PASS (warnings allowed).

**Step 2: Manual QA**
1. Configure WebDAV (first time): observe foreground notification and sync continues when navigating away.
2. With WebDAV already configured: tap “Sync now” and navigate away; sync continues.

