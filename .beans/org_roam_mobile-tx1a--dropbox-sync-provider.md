---
# org_roam_mobile-tx1a
title: Dropbox sync provider
status: todo
type: feature
priority: normal
tags:
    - sync
    - dropbox
created_at: 2025-12-16T14:00:10Z
updated_at: 2025-12-16T14:00:10Z
---

Implement Dropbox provider for sync.

Source: `docs/TODO_SYNC.md`.

Acceptance:
- Supports list/download/upload/delete for a configured folder.
- Uses Dropbox API v2 with authenticated requests.
- Securely stores/refreshes token (DataStore/EncryptedSharedPreferences per existing settings patterns).
- Integrates with existing `SyncEngine` provider abstraction.
- Adds unit tests with mocked HTTP layer.
