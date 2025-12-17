---
# org_roam_mobile-f0a6
title: LaTeX formula conversions
status: todo
type: feature
priority: normal
tags:
    - org
    - latex
    - ui
created_at: 2025-12-16T15:55:03Z
updated_at: 2025-12-16T15:55:03Z
---

Support LaTeX formula rendering/conversion in Org notes.

Scope ideas (confirm when implementing):
- Detect inline math like `$...$` and display math like `$$...$$` / `\[...\]`.
- Support Org-native fragments like `\(...\)`.
- Render in NoteView (Compose) with proper baseline alignment and text wrapping.
- Tap-to-expand or full-screen preview for long formulas.
- Include tests for parsing/detection and rendering fallbacks.

Out of scope (unless later): exporting back to LaTeX/HTML.
