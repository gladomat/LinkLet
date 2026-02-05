---
# LinkLet-2xhm
title: Improve sync foreground notification UX
status: completed
type: bug
created_at: 2026-02-05T14:41:42Z
updated_at: 2026-02-05T14:41:42Z
---

User reports foreground sync notification is not scrollable, tapping just opens app, and lacks actions. Wants it to appear at bottom like in-app 'Sync Scheduled' notification.

## Checklist
- [x] Reproduce and inspect current foreground notification config
- [x] Compare against in-app 'Sync Scheduled' notification style/placement
- [x] Identify root cause differences (channel importance, style, content, actions)
- [x] Add failing test(s) or adjust existing tests to capture desired behavior
- [x] Implement minimal fix in sync notification builder
- [x] Run relevant unit tests
