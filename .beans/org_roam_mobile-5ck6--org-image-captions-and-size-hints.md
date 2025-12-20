---
# org_roam_mobile-5ck6
title: Org image captions and size hints
status: completed
type: feature
priority: low
tags:
    - org
    - images
    - ui
created_at: 2025-12-16T14:04:55Z
updated_at: 2025-12-20T12:10:33Z
parent: org_roam_mobile-4kf2
---

Support basic Org-ish image presentation metadata.

Ideas (pick minimal subset):
- Treat `[[file:img.png][caption]]` as a caption under the image.
- Support `#+CAPTION:` above an image link.
- Support a simple `#+ATTR_ORG: :width <N>` (dp or percent) hint.

Acceptance:
- Defaults remain fit-to-width when no metadata is present.
- No regression for existing link rendering.
