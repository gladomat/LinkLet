---
# org_roam_mobile-8mrs
title: Implement Two-Pass Background Indexing
status: todo
type: feature
created_at: 2025-12-24T10:05:04Z
updated_at: 2025-12-24T10:05:04Z
parent: org_roam_mobile-i3pt
---

Split the indexing process into two distinct passes to unblock the user faster.
- **Pass 1 (Foreground/Immediate):** Extract minimal metadata (Title, ID, Tags) for browsing.
- **Pass 2 (Background/Idle):** Compute full graph data (Backlinks, outgoing links) and FTS content.
- orchestrate Pass 2 using WorkManager.