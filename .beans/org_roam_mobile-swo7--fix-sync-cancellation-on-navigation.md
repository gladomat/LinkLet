---
# org_roam_mobile-swo7
title: Fix sync cancellation on navigation
status: completed
type: bug
priority: normal
created_at: 2025-12-23T16:27:35Z
updated_at: 2025-12-23T17:48:34Z
---

Sync bug: Starting sync from Settings, then navigating back to main screen cancels sync. Logs show JobCancellationException in SyncEngine, SettingsViewModel (timestamp 2025-12-23 17:26:24.391).

Root cause: manual sync ran in SettingsViewModel.viewModelScope; leaving Settings cancels the job.

Implementation plan: docs/plans/2025-12-23-fix-sync-cancellation.md

## Checklist
- [x] Persist initial sync completion state (WebDavSettingsRepository)
- [x] Add WorkManager initial/manual sync types + foreground notification
- [x] Refactor SettingsViewModel to enqueue WorkManager, not run SyncEngine
- [x] Auto-trigger initial sync right after first WebDAV configuration
- [x] Add/update unit tests for ViewModels and settings state
- [x] Run ./gradlew test (1 unrelated failure tracked in org_roam_mobile-ypt4)