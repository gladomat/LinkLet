---
# org_roam_mobile-2kwi
title: Image rendering tests (Compose + Robolectric)
status: completed
type: task
priority: normal
tags:
    - tests
    - ui
    - images
created_at: 2025-12-16T14:04:39Z
updated_at: 2025-12-20T10:54:05Z
parent: org_roam_mobile-4kf2
---

Add tests for image detection and viewer behavior.

Acceptance:
- Unit tests for "is this Org link an image" detection.
- Compose UI tests (Robolectric where possible) for:
  - inline image appears for an image link
  - tap opens viewer and back/tap closes it
- Tests avoid emulator requirements.
