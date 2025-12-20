---
# org_roam_mobile-5fz1
title: Debug SAF image loading failure
status: completed
type: bug
priority: high
tags:
    - images
    - storage
    - debugging
created_at: 2025-12-17T15:21:06Z
updated_at: 2025-12-17T15:23:26Z
---

Add debug logging around image URI resolution/loading and fix SAF image openInputStream failures (e.g., by using AssetFileDescriptor fallback) when rendering inline images.