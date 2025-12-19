---
# org_roam_mobile-c9xk
title: Sync non-org attachments in subfolders
status: completed
type: feature
priority: normal
tags:
    - sync
    - storage
    - attachments
created_at: 2025-12-16T16:05:34Z
updated_at: 2025-12-19T17:39:22Z
---

Synchronize non-Org files (images/attachments) that live alongside notes in subfolders.

Problem:
- Current sync/indexing focuses on `*.org` notes; attachments referenced by `file:`/`attachment:` may be missing on device.

Acceptance:
- Extend sync providers (at least WebDAV) to include selected non-org file types under the notes root.
- Preserve directory structure.
- Avoid syncing transient/system folders if applicable (configurable ignore list).
- Add tests for sync discovery + transfer of non-org files.

Notes:
- Keep guardrails (delete thresholds, conflict behavior) consistent with note sync.
