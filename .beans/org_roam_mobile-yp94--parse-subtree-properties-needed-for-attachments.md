---
# org_roam_mobile-yp94
title: Parse subtree properties needed for attachments
status: completed
type: task
priority: normal
tags:
    - org
    - parser
created_at: 2025-12-16T14:19:22Z
updated_at: 2025-12-16T15:34:54Z
parent: org_roam_mobile-4kf2
---

Ensure the Org parse/render pipeline can determine the "current node" properties for an `attachment:` link.

Acceptance:
- For each rendered section/subtree, expose at least:
  - effective `:ID:`
  - effective `:DIR:` (if present)
  - the note file path (for relative resolution)
- No UI coupling: parser/data model carries the metadata; UI just consumes it.
- Tests cover property inheritance/precedence as needed.
