---
# org_roam_mobile-5nhm
title: Fix NoteViewScreenImageTests failure
status: todo
type: bug
priority: normal
created_at: 2025-12-23T18:59:22Z
updated_at: 2025-12-23T18:59:26Z
---

Unit tests sometimes fail at tests/com/gladomat/linklet/ui/screens/note/NoteViewScreenImageTests.kt:90 (tap inline image opens/closes fullscreen viewer). Seen when running Gradle unit tests. Investigate UI test flakiness or regression.