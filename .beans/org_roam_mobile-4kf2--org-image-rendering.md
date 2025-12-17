---
# org_roam_mobile-4kf2
title: Org image rendering
status: todo
type: epic
priority: normal
tags:
    - ui
    - org
    - images
created_at: 2025-12-16T14:04:22Z
updated_at: 2025-12-16T14:04:22Z
---

Add first-class image support for Org notes.

Goal:
- Render images referenced by Org links (e.g., `[[file:foo.png]]`) directly in the note view.
- Tap image to open a full-screen, zoomable viewer.

Notes:
- Current renderer uses `OrgBlockRenderers.kt` + `OrgTextFormatter.kt` for link handling.
- Current storage abstraction (`IStorage`) only supports note text, so loading image bytes likely needs a small new API in storage/repository.
