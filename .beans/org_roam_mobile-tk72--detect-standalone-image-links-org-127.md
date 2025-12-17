---
# org_roam_mobile-tk72
title: Detect standalone image links (Org 12.7)
status: completed
type: task
priority: normal
tags:
    - org
    - images
    - parser
created_at: 2025-12-16T14:14:07Z
updated_at: 2025-12-16T15:34:54Z
parent: org_roam_mobile-4kf2
---

Detect standalone image links like `./img/cat.jpg` when they appear on a line by themselves (or as the only non-whitespace in a paragraph).

Acceptance:
- Standalone file paths are treated as links for image rendering only when:
  - they have an image extension, and
  - they are not surrounded by other text.
- Does not interfere with existing paragraph/link formatting.
- Unit tests cover positive and negative cases.
