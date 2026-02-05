---
# LinkLet-83ex
title: Fix InvalidForegroundServiceTypeException on Android 14
status: completed
type: bug
priority: normal
created_at: 2026-02-04T21:15:12Z
updated_at: 2026-02-05T14:35:11Z
---

Crash: InvalidForegroundServiceTypeException when WorkManager starts SystemForegroundService with type none on targetSdk 34.

## Checklist
- [x] Reproduce the crash and identify the foreground service start path
- [x] Add a failing test for required foreground service type
- [x] Implement minimal fix (foreground service type + manifest/permissions)
- [x] Verify fix on the failing path
- [x] Commit changes and bean
