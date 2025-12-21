---
# org_roam_mobile-qy7q
title: Fix width-hint invalid fallback + test stability
status: in-progress
type: bug
created_at: 2025-12-21T12:12:48Z
updated_at: 2025-12-21T12:12:48Z
---

Follow-up to org_roam_mobile-5ck6 review.\n\n## Issues\n- Invalid width hints currently clamp to tiny widths (0%->1%, -10->1dp); spec says invalid should fall back to full width.\n- Add missing dp-width + invalid-width tests.\n- Reduce Thread.sleep flakiness in Robolectric compose tests by using waitUntil with explicit conditions.\n\n## Checklist\n- [ ] Add failing tests for invalid width hints fallback\n- [ ] Implement fallback-to-full-width for invalid width hints\n- [ ] Add dp width hint test\n- [ ] Replace Thread.sleep with waitUntil where feasible\n- [ ] Run targeted tests\n