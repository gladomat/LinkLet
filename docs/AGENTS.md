# Docs

## Purpose

Durable project documentation: QA records, design plans, research, and post-mortems. Working notes that stop being true get deleted, not archived.

## Ownership

- `QA_FEATURE_MATRIX.md` — **canonical** feature × coverage matrix and defect register (DEF-xxx). Update when features or coverage change.
- `QA_TEST_PLAN.md` — scenario inventory companion to the matrix.
- `PROGRESS.md` — milestone log (M0–M7).
- `TODO_SYNC.md` — open sync work items.
- `troubleshooting.md` — post-mortems and debugging heuristics; append new post-mortems here.
- `webdav-sync-current-implementation.md` / `webdav-sync-improvement-prd.md` — sync protocol deep-dive and its PRD.
- `plans/` — one design doc per feature, written before implementation; they are historical once shipped and may lag the code.
- `research/` — external research reports.
- `github_issues.md` — issue-tracker snapshot (may be stale).
- `templates/` — ready-to-copy user-facing config templates (e.g. `syncignore-default.txt`), referenced from the root `README.md`. `syncignore-default.txt` is also the single source of truth for the in-app `.syncignore` editor's seed content — a Gradle task (`app/build.gradle.kts`) copies it to `app/src/main/assets/syncignore-default.txt` before the asset merge; edit only the `docs/templates/` copy.

## Local Contracts

- Plans describe intent at writing time; the code and the nearest AGENTS.md are the source of truth for current behavior. Don't "fix" old plans — write a new doc or update the owning AGENTS.md.
- `docs/CHANGELOG.md` duplicates the root `CHANGELOG.md`; prefer the root file for new entries.
