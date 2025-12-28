---
# org_roam_mobile-j3ml
title: 'Plan: Implement Progressive and Resumable Indexing'
status: completed
type: task
priority: normal
created_at: 2025-12-27T21:41:37Z
updated_at: 2025-12-27T21:55:47Z
---

## Updated Plan (per review)

### Decisions / Scope (v1)
- Index **after sync**, but **progressive within indexing**.
- **No FTS** in this refactor.
- Note list search is **title + tags only**.
- Non-negotiable: **Room/DB is the single source of truth** for the note list.

---

## Minimal Resumable Pipeline

### Data Model (minimal, resumable)
1) Extend `notes` table to support metadata-first + tombstones:
- `path` (PK), `title`
- `orgId` (nullable), `fileTags` (serialized)
- `deletedAt` (nullable) for soft delete / tombstone
- `fingerprintMtime`, `fingerprintSize` (nullable)
- `pass1Status` + `pass2Status` (e.g. `PENDING/DONE/FAILED`) OR a boolean `linksReady` + `lastIndexError`

2) Add `index_queue` table (no `index_run` yet):
- `path` (PK)
- `pass` (`1` or `2`)
- `status` (`PENDING/DONE/FAILED`)
- `attempts`, `lastError`, `updatedAt`
- optional snapshot: `expectedMtime/expectedSize` to detect stale queue entries

### Orchestrator / Scheduling
- Introduce an indexing scheduler/service responsible for:
  - Reconciling authoritative file set from storage.
  - Enqueuing pass 1 work for changed/new notes.
  - Enqueuing pass 2 work for notes whose pass 1 completed.
- Use WorkManager with **unique work** per pass to avoid concurrent workers.

---

## Pass 1: Metadata-Only (Foreground-capable)
### Goal
Make notes browsable quickly via partial results.

### Steps
1) Reconcile file set:
- `storage.listNotes()` produces authoritative set.
- Any DB `notes.path` not present => set `deletedAt = now` (tombstone).
- Any new path => upsert skeleton row (title fallback = filename/path).

2) Fingerprinting + enqueue:
- Compare stored `(mtime,size)` vs current; enqueue pass1 only when changed/new.

3) Batch processing:
- Worker processes small batches (e.g. 25–100 files) per transaction.
- Parse only: `#+title`, `:ID:`, `#+filetags:`.
- Update `notes` row + mark `index_queue(pass=1)` DONE.

### Progress Reporting
- Derive progress from DB counts: `DONE / (DONE + PENDING + FAILED)` for pass 1.
- UI observes Room flows for both notes list and progress.

---

## UI / ViewModel
- Switch Note List to observe `Flow<List<NoteEntity>>` from Room.
- Display indexing progress from Room-derived counts (no separate "run" system).
- Ensure all screens degrade gracefully when pass 2 is pending:
  - backlinks can show "computing" state or empty + `linksReady=false`.

---

## Pass 2: Links / Backlinks (Background)
### Goal
Incrementally compute relationships without breaking partial UX.

### Steps
- Enqueue pass2 per note as soon as pass1 completes (or after pass1 queue empty).
- Extract outgoing links; resolve `id:` targets via `orgId` mapping stored in `notes`.
- Upsert `links` incrementally; do **not** clear entire table.
- Mark `linksReady=true` (or pass2 DONE) per note.

---

## Edge Cases / Correctness
- **Deletions:** handled by pass1 reconciliation via tombstones; optionally prune links where source/target is tombstoned.
- **Renames:** treat as delete+add; stable `orgId` keeps graph coherent once reindexed.
- **Partial failures:** queue row FAILED with error; UI continues; retries are explicit.
- **Sync/index races:** indexing is scheduled at end of sync; only one worker per pass via unique WorkManager.

---

## Deliverable Breakdown (small diffs)
1) Schema + DAOs + Room flows (notes + index_queue + progress queries)
2) Note list uses Room as source of truth + progress UI
3) Pass 1 worker (metadata-only, resumable)
4) Fingerprinting via storage metadata
5) Pass 2 worker (incremental links/backlinks) + graceful UI states