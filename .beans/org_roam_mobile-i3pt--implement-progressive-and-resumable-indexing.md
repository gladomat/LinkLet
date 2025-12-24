---
# org_roam_mobile-i3pt
title: Implement Progressive and Resumable Indexing
status: todo
type: epic
priority: high
created_at: 2025-12-24T10:02:07Z
updated_at: 2025-12-24T10:02:07Z
---

## Goal
Transform the initial indexing process into a progressive, interruptible, and resumable pipeline. This will allow users to start using the app within minutes, even with large repositories, by showing partial results and deferring heavy computations (like backlinks) to background passes.

## Requirements
- **Progressive Indexing:** Index notes as they arrive and show partial results to the user.
- **Resumability:** Persist checkpoints so indexing can resume after app restarts or navigation.
- **Performance Optimizations:** Use bulk inserts, tune SQLite pragmas, and implement fingerprinting to avoid re-parsing unchanged files.
- **Two-Pass Strategy:** 
    - Pass 1 (High Priority): Extract minimal metadata (title, tags, IDs) to enable browsing.
    - Pass 2 (Background): Compute backlinks, full-text search indices, and other heavy structures.

## Checklist
- [ ] Design the indexing pipeline (Download -> Parse -> Index Job per file).
- [ ] Implement indexing checkpoints to support resumability.
- [ ] Update UI to show partial progress (e.g., 'Indexed X / Y notes').
- [ ] Implement fingerprinting (mtime + size + hash) to skip unchanged files.
- [ ] Optimize Room/SQLite performance (Bulk inserts, WAL mode, pragmas).
- [ ] Refactor parser for streaming/linear-time link extraction.
- [ ] Split indexing into Two-Pass strategy (Metadata vs. Backlinks/FTS).
- [ ] Implement Pass 2 as a background/idle task using WorkManager or similar.