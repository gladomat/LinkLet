---
# org_roam_mobile-7lpn
title: 'Optional: remote http(s) inline images'
status: todo
type: task
priority: low
tags:
    - images
    - network
    - ui
created_at: 2025-12-16T14:14:07Z
updated_at: 2025-12-16T14:14:07Z
parent: org_roam_mobile-4kf2
---

Optional support for rendering remote `http(s)` image links inline.

Notes:
- User notes are currently local-only; keep this low priority.

Acceptance:
- Only load over `https` by default (or make scheme allowlist explicit).
- Uses a safe image loader with caching and size limits.
- Explicit UI for failures/offline.
