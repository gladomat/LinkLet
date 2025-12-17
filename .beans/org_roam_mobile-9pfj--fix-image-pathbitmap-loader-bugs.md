---
# org_roam_mobile-9pfj
title: Fix image path/bitmap loader bugs
status: completed
type: bug
priority: high
tags:
    - images
    - ui
    - storage
created_at: 2025-12-16T15:41:55Z
updated_at: 2025-12-16T15:45:15Z
---

Address code review findings: allow '.' segments in OrgPathResolver (no traversal), fix BitmapFactory null handling and sample size calculation, and extend supported image extensions safely.