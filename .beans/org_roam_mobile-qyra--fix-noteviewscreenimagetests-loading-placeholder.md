---
# org_roam_mobile-qyra
title: Fix NoteViewScreenImageTests loading placeholder
status: completed
type: bug
priority: normal
created_at: 2025-12-23T17:22:16Z
updated_at: 2025-12-23T18:55:35Z
---

Test failure: NoteViewScreenImageTests.tap inline image opens and closes fullscreen viewer fails because "Loading image…" text is missing. Repro: ./gradlew test (fails in :app:testDebugUnitTest). Investigate Compose/Robolectric timing or placeholder rendering; make test resilient to fast image load.