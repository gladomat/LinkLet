---
# org_roam_mobile-24vv
title: Show note last-modified from storage metadata
status: todo
type: task
priority: normal
tags:
    - viewmodel
    - storage
created_at: 2025-12-16T14:00:20Z
updated_at: 2025-12-16T14:00:20Z
---

Populate `NoteViewUiState.Success.lastModified` using file metadata instead of `null`.

Source: `app/src/main/java/com/gladomat/linklet/viewmodel/note/NoteViewViewModel.kt`.

Acceptance:
- `repository.getNote(...)` or a dedicated API provides last-modified timestamp.
- Uses SAF/DocumentFile metadata when applicable; falls back safely for file-based storage.
- UI displays a meaningful value and tests cover both storage backends.
