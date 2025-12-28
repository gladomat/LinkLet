---
# org_roam_mobile-nj5a
title: 'Progressive indexing v1: schema + queue + tombstones'
status: completed
type: task
priority: high
created_at: 2025-12-27T21:58:23Z
updated_at: 2025-12-27T22:10:40Z
parent: org_roam_mobile-i3pt
---

Add minimal resumable indexing data model: extend notes with orgId/fileTags/deletedAt/fingerprint fields; add index_queue table; add DAO queries for progress counts; migrations.