---
# LinkLet-qj5w
title: Reduce FlowPreview and shadowed extension warnings
status: completed
type: task
priority: normal
created_at: 2026-02-05T23:13:36Z
updated_at: 2026-02-05T23:16:21Z
---

Kotlin compile emits FlowPreview opt-in warnings in MainActivity/NoteViewScreen due to NoteViewViewModel being annotated @FlowPreview, and a warning in NoteEditViewModel about removeLastOrNull extension being shadowed by a member. Scope FlowPreview opt-in to only the debounce usage (or replace with stable alternative) and remove redundant extension.