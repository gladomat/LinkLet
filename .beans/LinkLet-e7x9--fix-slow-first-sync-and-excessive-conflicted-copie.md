---
# LinkLet-e7x9
title: Fix slow first sync and excessive conflicted copies
status: in-progress
type: bug
created_at: 2026-02-05T16:54:02Z
updated_at: 2026-02-05T16:54:02Z
---

User reports two-pass sync feels too slow on large note sets: first pass takes a long time and notes don't appear quickly. Additionally, when notes were pre-copied to device outside the app, sync creates conflicted copies for many notes (e.g., "(Conflicted Copy 2026-02-05 17-53)"). Need to redesign initial sync to be incremental/streaming and improve conflict detection/bootstrapping to avoid conflicts for identical or pre-seeded files.\n\n## Checklist\n- [x] Inspect current sync implementation (two-pass) and conflict resolver logic\n- [ ] Identify why initial pass blocks UI/DB updates and propose incremental indexing/insertion\n- [x] Add logic to bootstrap local sync state from remote when local file matches remote (avoid conflicted copy)\n- [x] Add content-hash/ETag-based equivalence check to suppress conflicts when identical\n- [x] Add tests for conflict scenarios and first-run behavior\n- [x] Exclude common backup artifacts (e.g., .pwb) from sync
- [x] Ensure remote parent directories exist before upload (avoid 404)
- [x] Tighten sync scope to org + common attachments (avoid syncing random app artifacts)
- [ ] Validate on sample large vault and provide migration notes
