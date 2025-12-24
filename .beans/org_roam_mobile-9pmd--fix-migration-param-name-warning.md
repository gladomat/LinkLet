---
# org_roam_mobile-9pmd
title: Fix Migration param name warning
status: todo
type: task
priority: normal
created_at: 2025-12-23T16:24:59Z
updated_at: 2025-12-24T10:09:26Z
---

Build warning about Migration parameter name mismatch (supertype uses 'db'). Update param names in app/src/main/java/com/gladomat/linklet/data/index/NoteDatabase.kt at the two Migration overrides (around lines 23 and 49).