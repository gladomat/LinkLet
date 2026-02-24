---
status: draft
owner: engineering
last_reviewed: 2026-02-24
---

## PRD: Fast + Robust WebDAV Sync for LinkLet (Nextcloud-Optimized, Generic WebDAV-Safe) — Revised with Blocker Fixes

### Owner

You

### Target platform

Android (LinkLet), WebDAV backend (Nextcloud primary target; generic WebDAV fallback)

### Background (current implementation summary)

LinkLet’s sync is orchestrated via WorkManager and backed by Room `sync_state` per path. Remote discovery currently relies on recursive traversal using repeated `PROPFIND Depth: 1` per directory; execution is sequential; remote deletes are guarded with `If-Match` (ETag) and catastrophic-delete thresholds exist. Initial sync often runs as foreground work and blocks app usability.

This revised PRD strengthens the earlier design with concrete production-grade fixes: server-derived watermarks + skew overlap, explicit delta validity criteria, mandatory operation journal for crash-safe concurrency, tombstone GC, Stage B backpressure, and phase ordering so UX states ship before behavior changes.

---

## 1) Problem statement

### User-visible problems

1. **Initial sync is slow and blocks the app**

* Users can’t effectively use LinkLet during first sync.

2. **Sync breaks often**

* Timeouts/network drops/partial remote failures lead to brittle behavior and unclear recovery.

3. **Sync is inefficient at scale**

* Full recursive PROPFIND traversal does not scale for large trees.

### Engineering root causes

* No retained “server snapshot” as a stable base for three-way sync; discovery re-lists remote too often.
* No tiered delta discovery (Nextcloud SEARCH + trashbin) with safe invalidation logic.
* Sequential execution; no controlled concurrency; DB update model not concurrency-safe.
* Missing streaming + batched ingest for large PROPFIND results.

---

## 2) Goals and success metrics

### Goals

G1. **Time-to-first-usable** after enabling WebDAV: app usable immediately, with truthful “content availability” UX.

G2. **Fast delta sync** on Nextcloud using tiered discovery with explicit validation gates; efficient fallback.

G3. **Robustness**: resumable, crash-safe, idempotent operations even with concurrent execution.

G4. **Safety preserved**: keep existing guardrails (`If-Match`/412 conflict semantics, directory-change guard, local misconfiguration guard, catastrophic delete thresholds).

### Success metrics (targets)

* **App usable within** < 5 seconds after enabling WebDAV (UI responsive; sync continues).
* **Initial inventory (metadata snapshot)** for 10k items: < 5 minutes on typical Wi-Fi/LTE, resumable.
* **No-change sync**: < 10 seconds; minimal requests (capability cache + root probe + delta attempt).
* **10 changed files**: < 30 seconds; bounded concurrency; no full traversal.
* **Crash recovery**: kill app mid-execution → resume deterministically without duplicated side effects or missed operations.

---

## 3) Non-goals

* N1. Always mirroring all remote payloads without bounds (we aggressively prefetch *small note text*, but still budgeted and resumable).
* N2. Content-level merges (line/semantic). Conflicts remain file-level “keep both.”
* N3. Replacing WorkManager.

---

## 4) UX contract (must ship before behavior changes)

### 4.1 Note availability states (mandatory)

UI + model must represent:

* `STUB` — metadata exists locally, content not downloaded
* `AVAILABLE` — content present locally
* `PINNED_OFFLINE` — guaranteed offline
* `ERROR` — fetch failed (retry)

**Hard requirement:** No “empty note screen” without an explicit stub/needs-download indicator and a retry/download action.

### 4.2 Prefetch policy (Stage B) — aggressive for small text, bounded + backpressured

* Aggressively prefetch `.md/.txt/.org` (small note text) *in the background*.
* Lazy fetch applies primarily to large attachments/binaries.
* Prefetch must be **budgeted** (bytes/count per run) and **prioritized**:

  1. pinned
  2. recently opened
  3. recently modified
  4. remaining

### 4.3 Stage B backpressure (mandatory)

Prefetch must respect:

* metered network (default: do not prefetch on metered unless user opts in)
* battery saver / low battery thresholds
* WorkManager constraints (network type, charging, battery not low)
* runtime checks (ConnectivityManager metered status, BatteryManager, thermal if feasible)

---

## 5) Key design decisions (revised)

### D1. Retained server snapshot for three-way sync

Persist last-known remote state locally to avoid full recursion each run and to enable three-way reconciliation (local current + remote current + last sync base).

### D2. Tiered delta discovery with **server-derived watermark** + overlap window

Delta discovery uses:

* Nextcloud SEARCH for changes (fast path)
* Nextcloud trashbin for deletions (fast path)
* fallback scan (ETag-bubbled for Nextcloud; bounded BFS for generic)

**Watermark source:** must be derived from the server (not device clock). Use:

* HTTP `Date` header of the response that completed the delta phase, or
* `getlastmodified` of root after delta phase

**Skew overlap:** always query deltas from `(lastDeltaServerTime - SKEW_MARGIN)` (e.g., 10 seconds), and deduplicate client-side.

### D3. ETag bubbling fallback on Nextcloud; explicit generic behavior

* On Nextcloud: use ETag bubbling to recurse only into changed folders.
* On generic WebDAV: do **not** trust bubbling; use bounded BFS traversal (resumable) as defined fallback.

### D4. Concurrency requires a mandatory operation journal + single DB writer

* Operation journal is **mandatory** before introducing concurrent execution.
* Network workers can run concurrently, but all DB mutations go through a **single-writer DB queue**.
* Each completed operation updates all relevant tables in **one Room transaction**.

### D5. Tombstones with GC (mandatory)

Deleted entries/tombstones must be garbage collected with explicit TTL; no unbounded growth.

### D6. Concrete Delta Validity Gate (no “vibes”)

Invalidation must be based on explicit criteria (see FR3).

---

## 6) Data model and storage requirements

### 6.1 Tables (Room)

#### `server_snapshot` (mandatory)

Represents remote “current” view (files + directories).

Fields (indicative):

* `rootId` (normalized root key)
* `path` (relative)
* `href` (normalized remote identifier)
* `etag` (normalized)
* `lastModifiedEpochMillis` (from server)
* `size`
* `isDir`
* `fileId` (Nextcloud `oc:fileid`, nullable)
* `deletedAtEpochMillis` (nullable; tombstone marker)
* `lastSeenAtEpochMillis` (for sweeps/GC)

**Indexes (mandatory, called out explicitly):**

* `(rootId, path)` unique
* `(rootId, isDir, etag)` to accelerate ETag-bubbled traversal
* `(rootId, fileId)` for rename/move detection where available
* `(rootId, deletedAtEpochMillis)` for tombstone GC
* `(rootId, lastSeenAtEpochMillis)` for sweep logic

#### `sync_watermark` (mandatory, per root)

* `rootId`
* `lastDeltaServerTimeEpochMillis` (server-derived)
* `lastFullSweepServerTimeEpochMillis` (server-derived)
* `deltaValidityState` (VALID/INVALID + reason code)
* `capabilitiesCache` (or separate table; see below)

#### `capabilities_cache` (mandatory, with negative caching)

* `rootId`
* `supportsSearch` + `supportsSearchCheckedAt`
* `supportsTrashbin` + `supportsTrashbinCheckedAt`
* `supportsFileId` + `supportsFileIdCheckedAt`
* Negative cache TTL (e.g., 24h) for “not supported”

#### `operation_journal` (mandatory before Phase 4)

Each planned op is persisted.

Fields (indicative):

* `opId` (uuid)
* `rootId`
* `opType` (DOWNLOAD/UPLOAD/DELETE/CONFLICT_COPY/etc.)
* `path` (target path at time of planning)
* `remoteId`/`href` (if known)
* `fileId` (if known)
* `expectedRemoteEtag` (precondition)
* `expectedLocalFingerprint` (optional)
* `status` (PLANNED/RUNNING/SUCCEEDED/FAILED/RETRY)
* `attemptCount`, `nextAttemptAt`
* `lastErrorCode`, `lastErrorClass` (TRANSIENT/PERMANENT/REMOTE_CHANGED)
* `createdAt`, `updatedAt`

#### Existing `sync_state` (retained, but clarified)

Keep your existing per-path base/lifecycle state; this PRD requires that `sync_state`, `server_snapshot`, and local metadata updates are applied transactionally.

---

## 7) Functional requirements

### FR1. Initial sync is staged and non-blocking

* Enabling WebDAV immediately makes the app usable.
* Stage A builds snapshot (metadata only) in background; resumable.
* Stage B prefetches small note content under constraints and budgets.
* UI reflects `STUB` state until content arrives.

### FR2. Streaming + batch ingestion (memory safety)

* Inventory worker must use a pull parser (XmlPullParser) and flush to Room in batches (e.g., 500 rows).
* Must not build full in-memory lists for large PROPFIND results.

### FR3. Delta discovery with explicit validity criteria (mandatory)

Tiered attempt order:

1. Delta via SEARCH + trashbin (if supported and valid)
2. Nextcloud fallback via ETag-bubbled partial scan
3. Generic fallback via resumable bounded BFS

**Hard invalidation (mark delta INVALID immediately):**

* Capability probe indicates SEARCH/trashbin unsupported or revoked (after TTL recheck)
* HTTP errors beyond retry budget (e.g., repeated 5xx/429)
* Parse errors / truncated responses

**Soft invalidation (force fallback scan this cycle):**

* `nowServer - lastDeltaServerTime > MAX_DELTA_AGE` (e.g., 7 days) → fallback scan
* Root folder ETag changed but SEARCH returns 0 results → fallback scan
* Trashbin cannot cover watermark window (missing/disabled/empty-retention mismatch) → fallback scan for deletions

**Deduplication requirement:** because of overlap window, reconcile must dedupe by stable key:

* Nextcloud: `(fileId, etag)` preferred
* else: `(href, etag)` fallback
* else: `(path, etag)` last resort

### FR4. Server-derived watermarking + skew overlap (mandatory)

* Do not use device clock for `lastDelta*`.
* Capture watermark from server (HTTP `Date` or root `getlastmodified`).
* Use overlap margin (5–10 seconds) in delta windows.
* Store the watermark only when delta phase completes successfully.

### FR5. Deletes remain safe and conservative

* Preserve current optimistic delete: `DELETE` with `If-Match` when fingerprint exists; 412 → remote-changed exception and conflict resolution path.
* Catastrophic delete thresholds remain; confirmation required as today.

### FR6. Rename/move detection must not explode request budget

* Fast path may treat renames as delete+create if needed.
* If using `fileId`, rename detection must avoid N+1:

  * Prefer batching lookups (if supported), or
  * Defer rename resolution to periodic consistency sweep.

### FR7. Concurrent execution is deterministic and crash-safe (mandatory)

* Network concurrency is bounded (configurable).
* DB updates are serialized through single writer.
* Each op result is applied in a single transaction updating:

  * local file metadata
  * `sync_state`
  * `server_snapshot`
  * relevant watermarks/status
* Operation journal drives idempotency and recovery:

  * on restart, unfinished ops resume or revalidate preconditions and replan.

### FR8. Tombstone GC strategy (mandatory)

* Tombstones must have TTL:

  * purge tombstones older than `2 × fullConsistencySweepInterval` (or fixed minimum, e.g., 14 days)
* GC runs:

  * after successful full sweep, and
  * periodically (WorkManager) under constraints

### FR9. Status persistence (expanded)

Persist actionable statuses beyond directory-change:

* requires confirmation
* auth invalid / permission errors
* repeated transient failures/backoff
* local misconfiguration guard triggers

---

## 8) Non-functional requirements

### NFR1 Performance

* Minimal PROPFIND property sets (etag/mtime/size/fileid where applicable).
* Connection reuse + bounded concurrency.
* Avoid full-content download during Stage A.

### NFR2 Reliability

* Resumable inventory traversal (persisted directory queue).
* Idempotent operations with journal and preconditions.
* Deterministic recovery after crash.

### NFR3 Safety

* Preserve existing guardrails: directory-change guard, local misconfiguration guard, catastrophic delete guard.

---

## 9) Architecture (revised)

### 9.1 Pipeline

**A) Capability Probe (cached + negative cached)**

* Detect SEARCH, trashbin, fileId property support with TTL; don’t re-probe every sync.

**B) Stage A: Snapshot Builder (inventory + refresh)**

* Nextcloud:

  * Root ETag probe (`Depth: 0`) to decide whether to traverse
  * ETag-bubbled traversal (`Depth: 1`), recurse only into changed folders
* Generic:

  * resumable bounded BFS traversal
* Streaming parse + batch DB writes

**C) Delta Updater (when supported)**

* SEARCH changes since `(lastDeltaServerTime - skewMargin)`
* trashbin deletions since `(lastDeltaServerTime - skewMargin)`
* Apply FR3 validity criteria; on invalidation → fallback scan

**D) Reconcile Planner (three-way)**
Inputs:

* local current
* `sync_state` base/lifecycle
* `server_snapshot` (remote current)
  Output:
* persisted `operation_journal` entries

**E) Executor**

* bounded network workers execute journal ops
* results sent to DB writer

**F) DB Writer (single writer)**

* applies each op result transactionally (multi-table)
* updates server-derived watermark only when delta phase completes

**G) Stage B: Prefetcher**

* budgeted + constrained (metered/battery/doze)
* updates note states STUB→AVAILABLE

---

## 10) Phased implementation plan (re-ordered for safe rollout)

### Phase 0 — Instrumentation (mandatory)

Metrics:

* request counts by method
* bytes transferred
* stage durations
* retry/error distributions
* DB write latency

### Phase 1 — UX states + snapshot schema + streaming ingestion (must ship together)

Deliver:

* `STUB/AVAILABLE/PINNED_OFFLINE/ERROR` UI/state plumbing
* `server_snapshot`, `sync_watermark`, `capabilities_cache` schema + indices
* Stage A inventory worker: streaming parse + batch inserts + resumable traversal queue

### Phase 2 — Nextcloud ETag-bubbled traversal (fallback engine)

Deliver:

* Root ETag probe
* Partial traversal only into changed folders
* Explicit generic fallback path remains bounded BFS (no bubbling assumptions)

### Phase 3 — Delta discovery with server-derived watermark + validity gates

Deliver:

* SEARCH + trashbin deltas
* server-derived watermark capture + skew overlap + dedupe
* explicit invalidation rules + reason codes
* stale-snapshot handling: if delta too old → fallback scan

### Phase 4 — Operation journal + deterministic concurrency (journal is prerequisite)

Deliver:

* `operation_journal` schema + planner writes
* bounded concurrent executor
* single DB writer + transactional updates
* crash recovery via journal

### Phase 5 — Stage B prefetch (constrained, budgeted)

Deliver:

* aggressive small-text prefetch with WorkManager constraints + runtime backpressure checks
* prioritization and budgets
* user toggles for metered networks

### Phase 6 — Tombstone GC + periodic sweeps

Deliver:

* tombstone TTL + GC worker
* periodic full consistency sweep under constraints
* stale snapshot test harness/regression checks

---

## 11) Risks and mitigations (revised)

### R1 Watermark drift (clock skew) → missed changes

**Mitigation:** server-derived watermark + overlap + dedupe (FR4).

### R2 Tombstone bloat

**Mitigation:** TTL + scheduled GC (FR8).

### R3 Delta fragility (SEARCH/trashbin incomplete)

**Mitigation:** explicit validity criteria + fallback scan (FR3).

### R4 Concurrency without crash-safe ops

**Mitigation:** mandatory op journal + single DB writer + transactions (FR7).

### R5 Prefetch burns battery/data and gets killed

**Mitigation:** WorkManager constraints + metered/battery checks + budgets (FR1/4.3).

### R6 Rename detection causes N+1 query storm

**Mitigation:** batch or defer to sweep; keep fast path simple (FR6).

---

## 12) Test plan (expanded)

### Correctness

* local↔remote create/update/delete
* conflict (edit both sides): keep both; preserve local copy
* directory-change guard triggers
* local misconfiguration guard triggers
* catastrophic delete confirmation gating

### Delta validity + watermark

* device clock skew simulation: ensure no missed changes (server-derived watermark)
* overlap dedupe correctness
* SEARCH returns 0 but root ETag changed → fallback engaged
* stale snapshot (weeks) → delta invalidated → fallback scan succeeds (mandatory test)

### Resilience

* kill app mid-inventory → resume traversal
* kill app mid-execution with concurrent workers → journal recovery consistent
* repeated 429/5xx → backoff + eventual recovery, no state corruption

### Performance

* datasets: 1k / 10k / 50k
* measure:

  * snapshot build time + memory footprint
  * no-change sync request count + time
  * 10-change sync time
  * ETag-bubbled fallback vs bounded BFS

---

## 13) Deliverables (final)

1. Room schema + migrations:

* `server_snapshot` (with indices)
* `sync_watermark`
* `capabilities_cache` (negative caching)
* `operation_journal` (mandatory)
* (optional) separate folder table only if needed; otherwise folders are rows in `server_snapshot`

2. Stage A snapshot builder:

* streaming XmlPullParser ingest
* batch writes
* resumable traversal

3. Nextcloud fallback:

* ETag-bubbled partial scan

4. Delta path:

* SEARCH + trashbin
* server-derived watermark + overlap + dedupe
* explicit validity criteria + reason codes

5. Reconcile planner:

* three-way using snapshot + sync_state
* writes op journal

6. Executor:

* bounded concurrency
* single DB writer + transactional updates
* crash recovery via journal

7. Stage B prefetch:

* constrained, budgeted, prioritized
* truthful UI states

8. Tombstone GC + periodic sweep:

* TTL, GC worker
* sweep scheduling
* stale snapshot regression test
