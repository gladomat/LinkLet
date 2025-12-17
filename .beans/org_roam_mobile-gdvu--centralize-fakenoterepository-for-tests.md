---
# org_roam_mobile-gdvu
title: Centralize FakeNoteRepository for tests
status: todo
type: task
priority: low
tags:
    - tests
    - repository
created_at: 2025-12-16T14:00:30Z
updated_at: 2025-12-16T14:00:30Z
---

Introduce a shared `FakeNoteRepository` test double to avoid duplicating ad-hoc `object : INoteRepository` stubs.

Context: `docs/troubleshooting.md` section "Interface Fragility in Tests".

Acceptance:
- Add `FakeNoteRepository` under `tests/` mirroring package structure.
- Update existing viewmodel/repository tests to use it.
- Interface changes require updating only the fake in one place.
