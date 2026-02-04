---
# LinkLet-lkae
title: Fix Conscrypt UnsatisfiedLinkError in tests
status: completed
type: bug
priority: normal
created_at: 2026-02-04T00:15:21Z
updated_at: 2026-02-04T00:31:19Z
---

Resolve unit test failure due to missing conscrypt native library on Debian.

## Checklist
- [x] Reproduce the UnsatisfiedLinkError in tests
- [x] Use existing failing Robolectric tests as the red case
- [x] Implement the minimal fix (dependency/config)
- [x] Re-run the failing test(s) to verify fix
- [x] Update any relevant docs if needed
- [x] Commit code + bean file
