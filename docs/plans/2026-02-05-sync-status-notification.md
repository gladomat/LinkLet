# Sync Status Notification Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Persist the last sync issue, show an in-app snackbar, and deep-link system notifications to a dedicated Sync Status screen that allows clearing sync state and retrying.

**Architecture:** Add a small DataStore-backed `SyncStatusRepository` that stores the last actionable sync issue. `SyncWorker` writes status when directory changes are detected. UI reads the repository to show a snackbar and a new Sync Status screen. Notifications use BigTextStyle and a “Review” action to open the Sync Status screen via intent.

**Tech Stack:** Kotlin, Jetpack Compose, WorkManager, DataStore Preferences, Hilt, Robolectric tests.

---

### Task 1: Add sync status model + repository

**Files:**
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/SyncStatus.kt`
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/SyncStatusRepository.kt`
- Test: `tests/com/gladomat/linklet/data/sync/SyncStatusRepositoryTests.kt`

**Step 1: Write the failing test**

```kotlin
@RunWith(Aarch64RobolectricTestRunner::class)
class SyncStatusRepositoryTests {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun setAndClearStatusPersistsFields() = runTest {
        val repo = SyncStatusRepository(context)
        repo.clearStatus()

        repo.setStatus(
            SyncStatus(
                type = SyncStatusType.DIRECTORY_CHANGED,
                title = "Sync blocked",
                message = "Directory changed",
                oldPath = "/old",
                newPath = "/new",
                requiresAction = true,
                updatedAtEpochMillis = 1234L,
            ),
        )

        val status = repo.statusFlow.first()
        assertThat(status?.type).isEqualTo(SyncStatusType.DIRECTORY_CHANGED)
        assertThat(status?.oldPath).isEqualTo("/old")
        assertThat(status?.newPath).isEqualTo("/new")
        assertThat(status?.requiresAction).isTrue()

        repo.clearStatus()
        assertThat(repo.statusFlow.first()).isNull()
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.data.sync.SyncStatusRepositoryTests`
Expected: FAIL (class not found)

**Step 3: Write minimal implementation**

`SyncStatus.kt`:
```kotlin
enum class SyncStatusType { DIRECTORY_CHANGED, REQUIRES_CONFIRMATION }

data class SyncStatus(
    val type: SyncStatusType,
    val title: String,
    val message: String,
    val oldPath: String?,
    val newPath: String?,
    val requiresAction: Boolean,
    val updatedAtEpochMillis: Long,
)
```

`SyncStatusRepository.kt` (DataStore Preferences):
```kotlin
@Singleton
class SyncStatusRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.syncStatusDataStore

    private object Keys {
        val TYPE = stringPreferencesKey("type")
        val TITLE = stringPreferencesKey("title")
        val MESSAGE = stringPreferencesKey("message")
        val OLD_PATH = stringPreferencesKey("old_path")
        val NEW_PATH = stringPreferencesKey("new_path")
        val REQUIRES_ACTION = booleanPreferencesKey("requires_action")
        val UPDATED_AT = longPreferencesKey("updated_at")
    }

    val statusFlow: Flow<SyncStatus?> = dataStore.data.map { prefs ->
        val type = prefs[Keys.TYPE] ?: return@map null
        SyncStatus(
            type = SyncStatusType.valueOf(type),
            title = prefs[Keys.TITLE] ?: "",
            message = prefs[Keys.MESSAGE] ?: "",
            oldPath = prefs[Keys.OLD_PATH],
            newPath = prefs[Keys.NEW_PATH],
            requiresAction = prefs[Keys.REQUIRES_ACTION] ?: false,
            updatedAtEpochMillis = prefs[Keys.UPDATED_AT] ?: 0L,
        )
    }

    suspend fun setStatus(status: SyncStatus) { /* edit prefs */ }
    suspend fun clearStatus() { /* clear prefs */ }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.data.sync.SyncStatusRepositoryTests`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/gladomat/linklet/data/sync/SyncStatus.kt \
        app/src/main/java/com/gladomat/linklet/data/sync/SyncStatusRepository.kt \
        tests/com/gladomat/linklet/data/sync/SyncStatusRepositoryTests.kt

git commit -m "feat(sync): add sync status repository"
```

---

### Task 2: Update sync notification builder for deep-link + BigTextStyle

**Files:**
- Modify: `app/src/main/java/com/gladomat/linklet/data/sync/worker/SyncWorkerNotifications.kt`
- Test: `tests/com/gladomat/linklet/data/sync/worker/SyncWorkerNotificationsTests.kt`

**Step 1: Write the failing test**

```kotlin
@RunWith(Aarch64RobolectricTestRunner::class)
class SyncWorkerNotificationsTests {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun buildStatusNotificationIncludesReviewIntent() {
        val notification = SyncWorkerNotifications.buildStatus(
            context = context,
            title = "Sync blocked",
            text = "Directory changed",
            navTarget = "sync_status",
        )

        val contentIntent = notification.contentIntent
        assertThat(contentIntent).isNotNull()
        val intent = Shadows.shadowOf(contentIntent).savedIntent
        assertThat(intent.getStringExtra("nav_target")).isEqualTo("sync_status")

        val extras = notification.extras
        assertThat(extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString())
            .contains("Directory changed")
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.data.sync.worker.SyncWorkerNotificationsTests`
Expected: FAIL (method missing)

**Step 3: Write minimal implementation**

Add a new builder:
```kotlin
fun buildStatus(
    context: Context,
    title: String,
    text: String,
    navTarget: String,
): Notification { /* BigTextStyle + contentIntent + action */ }
```

Use:
- `NotificationCompat.BigTextStyle().bigText(text)`
- `PendingIntent.getActivity(...)` with `EXTRA_NAV_TARGET = navTarget`
- `.addAction(0, "Review", pendingIntent)`

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.data.sync.worker.SyncWorkerNotificationsTests`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/gladomat/linklet/data/sync/worker/SyncWorkerNotifications.kt \
        tests/com/gladomat/linklet/data/sync/worker/SyncWorkerNotificationsTests.kt

git commit -m "feat(sync): add reviewable status notification"
```

---

### Task 3: Persist status in SyncWorker on directory change

**Files:**
- Modify: `app/src/main/java/com/gladomat/linklet/data/sync/worker/SyncWorker.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/data/sync/SyncEngine.kt` (optional if extra data needed)
- Test: `tests/com/gladomat/linklet/data/sync/worker/SyncWorkerStatusMappingTests.kt`

**Step 1: Write the failing test**

```kotlin
class SyncWorkerStatusMappingTests {
    @Test
    fun directoryChangedExceptionMapsToStatus() {
        val status = SyncWorkerStatusMapper.fromDirectoryChanged(
            oldPath = "/old",
            newPath = "/new",
            message = "changed",
        )
        assertThat(status.type).isEqualTo(SyncStatusType.DIRECTORY_CHANGED)
        assertThat(status.requiresAction).isTrue()
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.data.sync.worker.SyncWorkerStatusMappingTests`
Expected: FAIL (mapper missing)

**Step 3: Write minimal implementation**

Create small internal mapper in `SyncWorker.kt` (or a new small file) so the mapping logic is testable, then call it in the `SyncDirectoryChangedException` branch:
```kotlin
syncStatusRepository.setStatus(
    SyncStatus(
        type = SyncStatusType.DIRECTORY_CHANGED,
        title = "Sync blocked",
        message = remoteError.message ?: "Directory changed",
        oldPath = remoteError.oldPath,
        newPath = remoteError.newPath,
        requiresAction = true,
        updatedAtEpochMillis = System.currentTimeMillis(),
    )
)
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.data.sync.worker.SyncWorkerStatusMappingTests`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/gladomat/linklet/data/sync/worker/SyncWorker.kt \
        tests/com/gladomat/linklet/data/sync/worker/SyncWorkerStatusMappingTests.kt

git commit -m "feat(sync): persist directory change status"
```

---

### Task 4: Add Sync Status screen + ViewModel

**Files:**
- Create: `app/src/main/java/com/gladomat/linklet/viewmodel/sync/SyncStatusViewModel.kt`
- Create: `app/src/main/java/com/gladomat/linklet/ui/screens/sync/SyncStatusScreen.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/ui/MainActivity.kt`
- Test: `tests/com/gladomat/linklet/viewmodel/sync/SyncStatusViewModelTests.kt`

**Step 1: Write the failing test**

```kotlin
class SyncStatusViewModelTests {
    @Test
    fun clearAndContinueClearsStatusAndReschedules() = runTest {
        val repo = FakeSyncStatusRepository(status = someStatus())
        val dao = FakeSyncStateDao()
        val scheduler = FakeSyncScheduler()

        val vm = SyncStatusViewModel(repo, dao, scheduler)
        vm.clearAndContinue()

        assertThat(repo.cleared).isTrue()
        assertThat(dao.cleared).isTrue()
        assertThat(scheduler.manualScheduled).isTrue()
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.viewmodel.sync.SyncStatusViewModelTests`
Expected: FAIL (class missing)

**Step 3: Write minimal implementation**

`SyncStatusViewModel`:
```kotlin
@HiltViewModel
class SyncStatusViewModel @Inject constructor(
    private val syncStatusRepository: SyncStatusRepository,
    private val syncStateDao: SyncStateDao,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {
    val status = syncStatusRepository.statusFlow.stateIn(...)

    fun clearAndContinue() = viewModelScope.launch {
        syncStateDao.clearAllStates()
        syncStatusRepository.clearStatus()
        syncScheduler.scheduleManual()
    }

    fun dismiss() = viewModelScope.launch { syncStatusRepository.clearStatus() }
}
```

`SyncStatusScreen` shows current status and buttons; if null, show empty state.

`MainActivity` adds a `Routes.SYNC_STATUS` composable and handles `EXTRA_NAV_TARGET` in `onCreate` and `onNewIntent` to navigate to that route.

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.viewmodel.sync.SyncStatusViewModelTests`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/gladomat/linklet/viewmodel/sync/SyncStatusViewModel.kt \
        app/src/main/java/com/gladomat/linklet/ui/screens/sync/SyncStatusScreen.kt \
        app/src/main/java/com/gladomat/linklet/ui/MainActivity.kt \
        tests/com/gladomat/linklet/viewmodel/sync/SyncStatusViewModelTests.kt

git commit -m "feat(ui): add sync status screen"
```

---

### Task 5: In-app snackbar + navigation hook

**Files:**
- Modify: `app/src/main/java/com/gladomat/linklet/viewmodel/NoteListViewModel.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/ui/screens/NoteListScreen.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/ui/MainActivity.kt`
- Test: `tests/com/gladomat/linklet/viewmodel/NoteListViewModelTests.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun emitsSnackbarWhenSyncStatusRequiresAction() = runTest {
    val repo = FakeSyncStatusRepository(status = someStatus(requiresAction = true))
    val vm = NoteListViewModel(..., syncStatusRepository = repo, ...)
    assertThat(vm.snackbarMessage.first()).contains("Sync needs confirmation")
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.viewmodel.NoteListViewModelTests`
Expected: FAIL (missing flow)

**Step 3: Write minimal implementation**

- Inject `SyncStatusRepository` into `NoteListViewModel`.
- Observe `statusFlow` and set `_snackbarMessage` when `requiresAction == true`.
- In `NoteListScreen`, add snackbar action “Review” that calls `onOpenSyncStatus`.
- Update `NoteListRoute` + `MainActivity` to pass `onOpenSyncStatus` and navigate to `Routes.SYNC_STATUS`.

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.viewmodel.NoteListViewModelTests`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/gladomat/linklet/viewmodel/NoteListViewModel.kt \
        app/src/main/java/com/gladomat/linklet/ui/screens/NoteListScreen.kt \
        app/src/main/java/com/gladomat/linklet/ui/MainActivity.kt \
        tests/com/gladomat/linklet/viewmodel/NoteListViewModelTests.kt

git commit -m "feat(ui): show sync status snackbar"
```

---

### Task 6: End-to-end verification

**Step 1: Run unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (note: requires writable ANDROID_SDK_ROOT)

**Step 2: Manual QA**
- Change WebDAV directory to trigger initial sync error.
- Confirm system notification expands and “Review” opens Sync Status screen.
- Confirm in-app snackbar appears at bottom with “Review”.
- Confirm “Clear & Continue” clears sync state and reschedules sync.

**Step 3: Commit**

```bash
git add docs/plans/2026-02-05-sync-status-notification.md

git commit -m "docs: add sync status notification plan"
```
