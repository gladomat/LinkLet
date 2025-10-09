GitHub Issues Bootstrap

This repository includes a script to create labels, milestones, and MVP issues using the GitHub CLI.

Prerequisites
- Install GitHub CLI: https://cli.github.com/
- Authenticate: `gh auth login`

Usage
- From the repo root, run:
  - `bash scripts/bootstrap_github_issues.sh`
  - Optionally target a specific repo: `REPO=owner/name bash scripts/bootstrap_github_issues.sh`

Behavior
- Creates/updates labels and milestones (idempotent).
- Creates issues for M0–M7 if titles do not already exist.
- Safe to re-run; existing issues are skipped by exact title match.

Notes
- No due dates are set on milestones; adjust on GitHub as needed.
- Label set includes: type (feat/fix/refactor/docs/test/chore), area (parser/storage/index/domain/repository/service/ui/viewmodel/sync/ci/tooling/settings), priority (P0/P1/P2), and status (backlog/in-progress/blocked).

