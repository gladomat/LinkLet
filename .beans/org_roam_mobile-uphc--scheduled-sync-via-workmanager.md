---
# org_roam_mobile-uphc
title: Scheduled sync via WorkManager
status: completed
type: task
priority: high
tags:
    - sync
    - workmanager
    - ui
created_at: 2025-12-16T14:00:15Z
updated_at: 2025-12-25T10:47:32Z
---

Add background scheduled sync using WorkManager with UI feedback.

Source: `docs/TODO_SYNC.md`.

## Acceptance Criteria

- [x] Periodic work runs `SyncEngine` when WebDAV is enabled.
- [x] Opportunistic sync on app open (one-shot).
- [x] Constraints: network connected.
- [x] Retries/backoff are explicit; failures surfaced via snackbar.
- [x] UI shows linear progress indicator during sync.
- [ ] Pull-to-refresh triggers immediate sync. (Blocked: Material3 API not available in current BOM)
- [x] Conflict count shown via snackbar after sync.
- [x] Adds tests (Robolectric/unit) covering scheduling + retry/error paths.

---

## Current State Analysis

### ✅ Already Implemented
| Component | Location | Notes |
|:---|:---|:---|
| `SyncWorker` | `data/sync/worker/SyncWorker.kt` | Calls `SyncEngine.run()` |
| `SyncScheduler` | `data/sync/SyncScheduler.kt` | Has `schedulePeriodic()` and `scheduleImmediate()` |
| `SyncEngine` | `data/sync/SyncEngine.kt` | Full reconciliation logic |
| WorkManager config | `app/LinkLetApp.kt` | HiltWorkerFactory configured |

### ❌ Missing / Not Activated
| Gap | Details |
|:---|:---|
| `schedulePeriodic()` never called | Must be activated on app start when WebDAV enabled |
| `scheduleImmediate()` on app open | Currently only triggered by local edits |
| UI feedback during sync | No progress indicator in `NoteListScreen` |
| Pull-to-refresh | `LazyColumn` has no `SwipeRefresh` wrapper |
| Conflict snackbar | `SyncSummary.resolvedConflicts` not surfaced to UI |

---

## Implementation Checklist

### Phase 1: Activate Sync (High Priority)

#### 1.1 Activate periodic sync on app start
**File**: `app/src/main/java/com/gladomat/linklet/app/LinkLetApp.kt`

```kotlin
@Inject lateinit var syncScheduler: SyncScheduler
@Inject lateinit var webDavSettingsRepository: WebDavSettingsRepository

override fun onCreate() {
    super.onCreate()
    // Schedule periodic sync if WebDAV is configured and enabled
    CoroutineScope(Dispatchers.IO).launch {
        val settings = webDavSettingsRepository.currentSettings()
        if (settings?.enabled == true && settings.isConfigured()) {
            syncScheduler.schedulePeriodic()
        }
    }
}
```

#### 1.2 Trigger opportunistic sync on app open
**File**: `viewmodel/NoteListViewModel.kt`

```kotlin
@Inject constructor(
    private val repository: INoteRepository,
    private val syncScheduler: SyncScheduler,  // Add injection
)

init {
    refresh()
    syncScheduler.scheduleImmediate()  // Add this line
}
```

---

### Phase 2: UI Feedback (Medium Priority)

#### 2.1 Observe WorkManager status in ViewModel
**File**: `viewmodel/NoteListViewModel.kt`

```kotlin
private val syncWorkInfo = WorkManager.getInstance(application)
    .getWorkInfosForUniqueWorkLiveData("LinkletImmediateSync")
    .asFlow()

val isSyncing: StateFlow<Boolean> = syncWorkInfo
    .map { workInfos -> workInfos.any { it.state == WorkInfo.State.RUNNING } }
    .stateIn(viewModelScope, SharingStarted.Eagerly, false)
```

#### 2.2 Add linear progress indicator
**File**: `ui/screens/NoteListScreen.kt`

Inside `Scaffold`, above the search bar:
```kotlin
val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

if (isSyncing) {
    LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary
    )
}
```

#### 2.3 Add pull-to-refresh
**File**: `ui/screens/NoteListScreen.kt`

Wrap `LazyColumn` with:
```kotlin
PullToRefreshBox(
    isRefreshing = isSyncing,
    onRefresh = { viewModel.triggerSync() }
) {
    LazyColumn { ... }
}
```

---

### Phase 3: Conflict/Error Snackbar (Low Priority)

#### 3.1 Surface sync summary to UI
**File**: `viewmodel/NoteListViewModel.kt`

After sync completes, check:
```kotlin
if (summary.resolvedConflicts > 0) {
    _snackbarMessage.value = "${summary.resolvedConflicts} conflict(s) resolved"
}
```

---

### Phase 4: Tests

#### 4.1 Scheduler tests
**File**: `tests/.../SyncSchedulerTests.kt` (new)

- Test `schedulePeriodic()` enqueues work with correct constraints
- Test `scheduleImmediate()` enqueues one-time work
- Test duplicate calls don't create multiple workers

#### 4.2 Retry matrix tests
**File**: `tests/.../SyncEngineTests.kt` (extend)

Add test matrix for:
- Network error → retry
- 401/403 → fail (no retry)
- 412 Precondition Failed → mark for re-sync
- Catastrophic delete → fail

---

## Architecture Diagram

```
┌─────────────────┐      ┌──────────────┐
│  LinkLetApp     │─────▶│ SyncScheduler│
│  (onCreate)     │      │              │
└─────────────────┘      └──────┬───────┘
                                │
                    schedulePeriodic() / scheduleImmediate()
                                │
                                ▼
                    ┌───────────────────────┐
                    │     WorkManager       │
                    │  PeriodicWorkRequest  │
                    │  OneTimeWorkRequest   │
                    └───────────┬───────────┘
                                │
                                ▼
                    ┌───────────────────────┐
                    │     SyncWorker        │
                    └───────────┬───────────┘
                                │
                                ▼
                    ┌───────────────────────┐
                    │     SyncEngine        │
                    │  - discoverState()    │
                    │  - reconcile()        │
                    │  - execute()          │
                    └───────────┬───────────┘
                                │
                                ▼
                    ┌───────────────────────┐
                    │  WebDavRemoteSyncProv │
                    └───────────────────────┘
```

---

## Open Questions

1. Should periodic sync be configurable (interval, enabled/disabled toggle)?
2. Should we add a notification for background sync completion?
3. First sync after install can be slow—show determinate progress?
