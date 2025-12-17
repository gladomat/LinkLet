---
# org_roam_mobile-qpq0
title: Load attachment content via storage/repository
status: completed
type: task
priority: normal
tags:
    - storage
    - repository
    - images
created_at: 2025-12-16T14:04:39Z
updated_at: 2025-12-16T15:34:54Z
parent: org_roam_mobile-4kf2
---

Add a minimal API to load non-note files (images) referenced from Org notes.

Context:
- `IStorage` currently only reads/writes note text.

Acceptance:
- Provide a safe way to resolve an image reference to either a `Uri` or an `InputStream`.
- Works for both filesystem storage and DocumentTree (SAF) storage.
- Prevents path traversal: image refs must resolve within the configured root.
- Includes unit tests for resolution rules.
