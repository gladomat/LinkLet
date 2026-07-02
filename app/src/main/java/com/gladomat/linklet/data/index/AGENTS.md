# Indexing Pipeline

## Purpose

Turns vault files into rows in the Room database (`NoteDatabase`) via a durable, interruptible two-pass queue. Pass 1 extracts note metadata (title, org id, filetags) into `notes`; pass 2 extracts links into `links` and flips `linksReady`.

## Ownership

- `IndexQueueDao` / `IndexQueueEntity` — the `index_queue` table, keyed by (path, pass). Statuses: PENDING → RUNNING → DONE / FAILED. `MAX_ATTEMPTS = 5`; a FAILED row at the cap is terminal and skipped.
- `IndexPass1Processor` / `IndexPass2Processor` — queue drainers.
- `worker/IndexPass1Worker`, `worker/IndexPass2Worker` — WorkManager wrappers (unique work, scheduled by `IndexingScheduler`).

## Local Contracts

The pipeline assumes the app process can die at any moment. Every step must persist progress before it can be lost:

- **Anything that creates, downloads, or restores a note file MUST upsert a (path, pass=1, PENDING) row into `index_queue`.** The cold scan is a reconciliation fallback, not the primary discovery path. `SyncEngine` does this after downloads and conflict copies.
- Pass 1 `run()` order: recover stale RUNNING rows → if PENDING rows exist, drain them and return (no scan) → otherwise cold-scan:
  1. new/resurrected paths are enqueued in one durable batch *before* any stat calls;
  2. known files are swept least-recently-verified first, bounded by `timeBudgetMillis`, flushing every `SWEEP_FLUSH_BATCH` rows;
  3. unchanged files get their DONE row's `updatedAtEpochMillis` touched — that touch is what advances the sweep rotation across truncated runs. Do not remove it.
- `run()` returns `Outcome(scanTruncated)`; the worker schedules a continuation when the sweep was truncated OR pending rows remain. Workers run in ~20 s budget slices and chain via `enqueueUniqueWork(APPEND_OR_REPLACE)` — never rely on `Result.retry()` backoff for forward progress.
- The sweep must never flip a PENDING/RUNNING row to DONE (a concurrent sync may have re-enqueued the path).
- Change detection is fingerprint-based (`fingerprintMtime` + `fingerprintSize` vs `statNote`). A missing file during processing is a tombstone (`markDeleted` + pass 2 DELETE), not a retry.

## Work Guidance

- Keep per-item work idempotent: reprocessing a DONE path must be harmless.
- Stat/read calls go through `withTimeoutOrNull` with the existing `STORAGE_*_TIMEOUT_MILLIS` constants.

## Verification

`tests/com/gladomat/linklet/data/index/` (9 test classes). Timing-sensitive tests must stay host-speed-robust: make sleeps ≥2× the budget so truncation is guaranteed, and don't assert exact processed counts under a budget.
