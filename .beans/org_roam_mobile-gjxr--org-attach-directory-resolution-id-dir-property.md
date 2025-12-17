---
# org_roam_mobile-gjxr
title: Org attach directory resolution (ID + DIR property)
status: completed
type: task
priority: normal
tags:
    - org
    - images
    - repository
    - storage
created_at: 2025-12-16T14:19:22Z
updated_at: 2025-12-16T15:34:54Z
parent: org_roam_mobile-4kf2
---

Define and implement attachment directory resolution for `attachment:` links.

Confirmed convention (your notes):
- ID-based attachment directories always live under `data/` next to the Org file.
- Sharding: `data/<first2>/<rest-of-id>/` (e.g., `6fa9...` → `data/6f/a9215e-.../`).

`:DIR:` interpretation:
- If present, treat as a path relative to the Org file directory.
- Disallow absolute paths and traversal (`..`).

Acceptance:
- Provide pure functions:
  - `orgAttachIdToRelativeDir(id: String): String` (returns `data/<first2>/<rest>/`)
  - `resolveAttachmentDir(notePath: String, nodeId: String?, dirProperty: String?): String?`
  - `resolveAttachmentPath(notePath, nodeId, dirProperty, attachmentName): String?`
- Works for both FileStorage and DocumentTree (SAF) storage.
- Unit tests cover sharding example + traversal rejection + edge cases.
