---
# org_roam_mobile-o90o
title: 'Support attachment: image links'
status: completed
type: task
priority: normal
tags:
    - org
    - images
    - storage
created_at: 2025-12-16T14:14:07Z
updated_at: 2025-12-16T15:34:54Z
parent: org_roam_mobile-4kf2
---

Implement Org-style `attachment:` image links using Org attach semantics.

Confirmed convention (your notes):
- ID-derived attachment directories are under `data/` next to the Org file using `data/<first2>/<rest>/`.

Acceptance:
- Resolve `[[attachment:NAME.png]]` relative to the *current node’s* attachment directory.
- Resolution order (MVP):
  1) If the current node has `:DIR:`, use it (relative to note dir).
  2) Else if the current node has `:ID:`, use `data/<first2>/<rest>/` next to the Org file.
  3) Else: render an explicit "missing attachment context" error.
- Prevent traversal and absolute paths; must remain under configured storage root.
- Works for both FileStorage and DocumentTree (SAF) storage.
- Unit tests cover DIR vs ID behavior and traversal rejection.
