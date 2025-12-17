---
# org_roam_mobile-0a6d
title: Persist note favorites
status: todo
type: feature
priority: normal
tags:
    - note
    - repository
created_at: 2025-12-16T14:00:25Z
updated_at: 2025-12-16T14:00:25Z
---

Persist and restore `isFavorite` for notes.

Source: TODOs in `app/src/main/java/com/gladomat/linklet/viewmodel/note/NoteViewViewModel.kt`.

Acceptance:
- Favorite state is stored (Room entity or other existing persistence layer).
- `NoteViewViewModel.loadNote()` loads persisted favorite state.
- `toggleFavorite()` updates persistence and UI state.
- Adds repository + viewmodel tests for favorite lifecycle.
