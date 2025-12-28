---
# org_roam_mobile-w2md
title: 'Progressive indexing v1: Pass 2 link/backlink worker'
status: completed
type: feature
priority: normal
created_at: 2025-12-27T21:58:36Z
updated_at: 2025-12-28T10:17:57Z
parent: org_roam_mobile-i3pt
---

Implement Pass 2 background worker: parse outgoing links, resolve id: via orgId mapping, incremental upsert into links table (no global clears), per-note linksReady status; UI/backlinks degrade gracefully when pending.