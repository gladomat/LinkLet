---
# org_roam_mobile-uphc
title: Scheduled sync via WorkManager
status: todo
type: task
priority: normal
tags:
    - sync
    - workmanager
created_at: 2025-12-16T14:00:15Z
updated_at: 2025-12-16T14:00:15Z
---

Add background scheduled sync using WorkManager.

Source: `docs/TODO_SYNC.md`.

Acceptance:
- Periodic work runs `SyncEngine` when enabled.
- Constraints: network connected (and any existing app constraints).
- Retries/backoff are explicit; failures are surfaced via logs/notification/snackbar plumbing (consistent with current patterns).
- Adds tests (Robolectric/unit) covering scheduling + retry/error paths.
- Adds a small conflict/error retry matrix test set for `SyncEngine`.
