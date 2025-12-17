---
# org_roam_mobile-ojlv
title: Render linked images inline in NoteView
status: completed
type: feature
priority: normal
tags:
    - ui
    - org
    - images
created_at: 2025-12-16T14:04:39Z
updated_at: 2025-12-16T15:34:54Z
parent: org_roam_mobile-4kf2
---

Render images referenced by Org links inside the note view.

Org semantics (baseline):
- Inline images are links to image files with *no description part* (e.g., `[[file:img.png]]`, not `[[file:img.png][desc]]`).
- Also support standalone image links on their own line, possibly preceded by `#+CAPTION:` / `#+NAME:` / `#+ATTR_*:`.

Acceptance:
- Detect image references (extensions: png/jpg/jpeg/gif/webp; case-insensitive).
- Supported link forms:
  - `[[file:relative/or/absolute.png]]` (no description)
  - `[[attachment:clipboard-20251128T115556.png]]`
  - standalone `./img/cat.jpg` when it appears alone on a line/paragraph.
- Render as an inline image block with aspect ratio preserved.
- Image width never exceeds the note content width (fit-to-width / "field of view").
- Apply alignment/width hints when present via `#+ATTR_ORG`/`#+ATTR_*` (see dedicated bean).
- Show placeholder while loading and an explicit error state when missing/undecodable.
- Tap opens the full-screen viewer; tap/back closes.

Out of scope (unless enabled later): inline rendering for links *with* descriptions; remote `http(s)` images.
