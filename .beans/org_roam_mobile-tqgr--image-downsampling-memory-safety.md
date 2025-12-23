---
# org_roam_mobile-tqgr
title: Image downsampling + memory safety
status: completed
type: task
priority: low
tags:
    - images
    - performance
    - ui
created_at: 2025-12-16T14:04:48Z
updated_at: 2025-12-23T13:57:43Z
parent: org_roam_mobile-4kf2
---

Prevent OOMs when rendering large images.

Acceptance:
- Decode with bounds + downsample to the on-screen size (or a safe max).
- Cache decoded bitmaps reasonably (in-memory, keyed by path + lastModified if available).
- Explicitly handle failures (e.g., show error state rather than crashing).
