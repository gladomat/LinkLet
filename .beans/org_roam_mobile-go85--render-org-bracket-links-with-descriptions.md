---
# org_roam_mobile-go85
title: Render Org bracket links with descriptions
status: todo
type: bug
priority: normal
tags:
    - org
    - ui
    - links
created_at: 2025-12-16T16:05:34Z
updated_at: 2025-12-16T16:05:34Z
---

Render Org bracket links like `[[https://example.com][click here]]` showing only the description text.

Acceptance:
- In note view, display "click here" as the link text (not the raw Org markup).
- Tapping opens the URL.
- Do not regress existing id/file link rendering.
- Add/extend unit tests for Org text formatting.
