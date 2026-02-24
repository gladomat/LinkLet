---
status: draft
owner: engineering
last_reviewed: 2026-02-24
---

# WebDAV Sync Improvements (PRD) Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make WebDAV sync fast (Nextcloud-optimized), robust (resumable + crash-safe), and UX-correct (explicit note availability states, no blank note screens) per `docs/webdav-sync-improvement-prd.md`.

**Architecture:** Keep WorkManager orchestration, but split the sync pipeline into explicit stages with persisted state: (1) capability probing with TTL caching, (2) Stage A snapshot builder that streams PROPFIND results into `server_snapshot` + creates local UI stubs, (3) delta discovery (SEARCH + trashbin) with server-derived watermarks and explicit validity gating, (4) three-way reconcile planner that persists an `operation_journal`, (5) bounded concurrent network executor with a single DB writer applying results transactionally, (6) Stage B prefetcher that downloads small note text under budgets/constraints, and (7) tombstone GC + periodic consistency sweeps.

**Tech Stack:** Kotlin, Room (migrations + DAOs), WorkManager, OkHttp + MockWebServer tests, XmlPullParser, coroutines (Semaphore/Channel), Hilt.

## Scope Anchors (Current Code)

Core current files (do not rewrite blindly; refactor incrementally):
- `app/src/main/java/com/gladomat/linklet/data/sync/SyncEngine.kt`
- `app/src/main/java/com/gladomat/linklet/data/sync/WebDavRemoteSyncProvider.kt`
- `app/src/main/java/com/gladomat/linklet/data/sync/RemoteSyncProvider.kt`
- `app/src/main/java/com/gladomat/linklet/data/index/NoteDatabase.kt`
- `app/src/main/java/com/gladomat/linklet/data/index/NoteEntity.kt`
- `app/src/main/java/com/gladomat/linklet/data/index/NoteDao.kt`
- `app/src/main/java/com/gladomat/linklet/domain/repository/NoteRepositoryImpl.kt`
- `app/src/main/java/com/gladomat/linklet/data/sync/worker/SyncWorker.kt`
- `app/src/main/java/com/gladomat/linklet/data/sync/SyncScheduler.kt`
- `app/src/main/java/com/gladomat/linklet/viewmodel/note/NoteViewViewModel.kt`
- `app/src/main/java/com/gladomat/linklet/ui/screens/note/NoteViewScreen.kt`

Key existing tests to extend:
- `tests/com/gladomat/linklet/data/sync/SyncEngineTests.kt`
- `tests/com/gladomat/linklet/data/sync/SyncEngineFreshInstallTests.kt`
- `tests/com/gladomat/linklet/data/sync/WebDavRemoteSyncProviderTests.kt`
- `tests/com/gladomat/linklet/data/index/NoteDatabaseMigrationTests.kt`
- `tests/com/gladomat/linklet/viewmodel/settings/WebDavSettingsViewModelTests.kt`

## Root ID / Normalization (Needed For New Tables)

Define a stable `rootId` used by `server_snapshot`, `sync_watermark`, `capabilities_cache`, `operation_journal`.

Proposed:
- `rootId = sha256("${baseUrl.trimEnd('/')}\n${normalizedRootPath}\n${username}")` as lowercase hex.
- Implement helper: `app/src/main/java/com/gladomat/linklet/data/sync/RootId.kt`.

## Phase 0: Instrumentation (PRD Phase 0)

### Task 1: Add sync stage timing + request counters (log-only first)

**Files:**
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/metrics/SyncMetrics.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/data/sync/SyncEngine.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/data/sync/WebDavRemoteSyncProvider.kt`

**Step 1: Write failing unit test for metrics helper**

Create:
- `tests/com/gladomat/linklet/data/sync/metrics/SyncMetricsTests.kt`

```kotlin
@Test
fun `metrics aggregates counts and durations`() {
    val m = InMemorySyncMetrics()
    m.increment("http_PROPFIND")
    m.timing("stage_discovery_ms", 123)
    assertEquals(1, m.snapshot().counts["http_PROPFIND"])
    assertEquals(123, m.snapshot().timingsMs["stage_discovery_ms"])
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.data.sync.metrics.SyncMetricsTests`
Expected: FAIL (classes not found).

**Step 3: Implement minimal metrics**

Create `SyncMetrics` interface + `InMemorySyncMetrics` (debug-only use), with `snapshot()` for tests.

**Step 4: Wire into `SyncEngine` and provider**

- Add coarse timings around discovery/reconcile/execute.
- Increment counters for `PROPFIND/GET/PUT/DELETE` calls (best-effort via explicit call sites).

**Step 5: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.data.sync.metrics.SyncMetricsTests`
Expected: PASS

**Step 6: Commit**

Run:
```bash
git add app/src/main/java/com/gladomat/linklet/data/sync/metrics/SyncMetrics.kt \
  app/src/main/java/com/gladomat/linklet/data/sync/SyncEngine.kt \
  app/src/main/java/com/gladomat/linklet/data/sync/WebDavRemoteSyncProvider.kt \
  tests/com/gladomat/linklet/data/sync/metrics/SyncMetricsTests.kt
git commit -m "chore(sync): add basic sync metrics hooks"
```

## Phase 1: UX Availability States + Snapshot Schema + Streaming Ingestion (PRD Phase 1)

### Task 2: Add note availability states (STUB/AVAILABLE/PINNED_OFFLINE/ERROR) without breaking indexing

Problem: current indexing clears `notes` and will delete remote stubs unless we separate sources.

**Design choice:**
- Add `notes.source` column: `LOCAL` vs `REMOTE_STUB`.
- Add `notes.availability` column: `AVAILABLE/STUB/PINNED_OFFLINE/ERROR`.
- Indexer clears only `LOCAL` notes and links, leaving `REMOTE_STUB` rows untouched.

**Files:**
- Modify: `app/src/main/java/com/gladomat/linklet/data/index/NoteEntity.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/data/index/NoteDao.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/domain/repository/NoteRepositoryImpl.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/data/index/NoteDatabase.kt`
- Test: `tests/com/gladomat/linklet/data/index/NoteDatabaseMigrationTests.kt`

**Step 1: Write failing migration test (6 -> 7)**

Update `tests/com/gladomat/linklet/data/index/NoteDatabaseMigrationTests.kt`:

```kotlin
@Test
fun `migration 6 to 7 adds availability and source columns to notes`() {
    val db = helper.createDatabase("migration-test-6-7", 6)
    db.execSQL("INSERT INTO notes(path, title, fileTags, linksReady) VALUES('a.org','A','',0)")
    db.close()

    val migrated = helper.runMigrationsAndValidate("migration-test-6-7", 7, true, NoteDatabase.MIGRATION_6_7)
    migrated.query("PRAGMA table_info('notes')").use { cursor ->
        val names = cursor.columnNames.toSet()
        assertTrue(names.contains("availability"))
        assertTrue(names.contains("source"))
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.data.index.NoteDatabaseMigrationTests`
Expected: FAIL (migration missing; version mismatch).

**Step 3: Implement enums + Room columns**

- Add enums in `NoteEntity.kt`:
  - `enum class NoteAvailability { STUB, AVAILABLE, PINNED_OFFLINE, ERROR }`
  - `enum class NoteSource { LOCAL, REMOTE_STUB }`
- Add fields to `NoteEntity` with defaults:
  - `val availability: NoteAvailability = NoteAvailability.AVAILABLE`
  - `val source: NoteSource = NoteSource.LOCAL`

**Step 4: Add DAO methods to preserve stubs**

In `NoteDao.kt` add:
- `DELETE FROM notes WHERE source = 'LOCAL'` (new method)
- Keep `observeActiveNotes()` but update it to include stubs (still `deletedAt IS NULL`).

**Step 5: Update repository indexing to stop clearing stubs**

In `NoteRepositoryImpl.reindex()`:
- Replace `noteDao.clearNotes()` with `noteDao.clearLocalNotes()` (new DAO method).
- Replace `noteDao.clearLinks()` with `noteDao.clearLinks()` (links remain local-only).
- When inserting local notes, set `source=LOCAL`, `availability=AVAILABLE`.

**Step 6: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.data.index.NoteDatabaseMigrationTests`
Expected: PASS

**Step 7: Commit**

```bash
git add app/src/main/java/com/gladomat/linklet/data/index/NoteEntity.kt \
  app/src/main/java/com/gladomat/linklet/data/index/NoteDao.kt \
  app/src/main/java/com/gladomat/linklet/domain/repository/NoteRepositoryImpl.kt \
  app/src/main/java/com/gladomat/linklet/data/index/NoteDatabase.kt \
  tests/com/gladomat/linklet/data/index/NoteDatabaseMigrationTests.kt
git commit -m "feat(ui): add note availability and stub source"
```

### Task 3: Make note view UX explicit for STUB and ERROR

**Files:**
- Modify: `app/src/main/java/com/gladomat/linklet/viewmodel/note/NoteViewViewModel.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/ui/screens/note/NoteViewScreen.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/domain/repository/INoteRepository.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/domain/repository/NoteRepositoryImpl.kt`
- Test: `tests/com/gladomat/linklet/viewmodel/note/NoteViewViewModelTests.kt`

**Step 1: Introduce a repository API that can return availability**

Add to `INoteRepository`:
- `suspend fun getNoteAvailability(path: String): Result<NoteAvailability>`

Implement in `NoteRepositoryImpl` by reading `notes` table (add a DAO query `SELECT availability FROM notes WHERE path = :path`).

**Step 2: Write failing ViewModel test**

In `tests/com/gladomat/linklet/viewmodel/note/NoteViewViewModelTests.kt` add:

```kotlin
@Test
fun `stub note shows stub UI instead of error`() = runTest {
    // repository.getNoteAvailability returns STUB; repository.getNote fails
    // assert NoteViewUiState shows explicit stub state with retry/download action enabled.
}
```

**Step 3: Implement UI state for stub**

In `NoteViewUiState` (if needed) add:
- `data class Stub(val path: String, val message: String) : NoteViewUiState`

Update `NoteViewViewModel.loadNote()`:
- Call `getNoteAvailability(notePath)` first.
- If `STUB`, set state to `Stub(...)` and stop.
- If `ERROR`, show retry + message.
- Else proceed with current `getNote()`.

**Step 4: Update `NoteViewScreen`**

Add a dedicated UI for `Stub` state:
- Show "Not downloaded yet" + `Download` button + `Retry` button.
- No rendering of empty content.

**Step 5: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.viewmodel.note.NoteViewViewModelTests`
Expected: PASS

**Step 6: Commit**

```bash
git add app/src/main/java/com/gladomat/linklet/domain/repository/INoteRepository.kt \
  app/src/main/java/com/gladomat/linklet/domain/repository/NoteRepositoryImpl.kt \
  app/src/main/java/com/gladomat/linklet/viewmodel/note/NoteViewViewModel.kt \
  app/src/main/java/com/gladomat/linklet/ui/screens/note/NoteViewScreen.kt \
  tests/com/gladomat/linklet/viewmodel/note/NoteViewViewModelTests.kt
git commit -m "feat(ui): show explicit stub note state"
```

### Task 4: Add `server_snapshot`, `sync_watermark`, `capabilities_cache` tables + DAOs (Room v7)

**Files:**
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/db/ServerSnapshotEntity.kt`
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/db/SyncWatermarkEntity.kt`
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/db/CapabilitiesCacheEntity.kt`
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/db/ServerSnapshotDao.kt`
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/db/SyncWatermarkDao.kt`
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/db/CapabilitiesCacheDao.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/data/index/NoteDatabase.kt`
- Test: `tests/com/gladomat/linklet/data/sync/db/ServerSnapshotDaoTests.kt`
- Test: `tests/com/gladomat/linklet/data/sync/db/CapabilitiesCacheDaoTests.kt`

**Step 1: Write failing DAO test scaffolds**

Create `ServerSnapshotDaoTests.kt`:

```kotlin
@Test
fun `upsert and query by rootId and path`() = runTest {
    dao.upsertAll(listOf(ServerSnapshotEntity(rootId="r", path="a.org", href="h", etag="e", isDir=false)))
    assertNotNull(dao.getByPath(rootId="r", path="a.org"))
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.data.sync.db.ServerSnapshotDaoTests`
Expected: FAIL (entities/dao missing).

**Step 3: Implement entities + indices**

Implement per PRD (minimum set now; add fields as needed):
- `ServerSnapshotEntity`:
  - primary key `(rootId, path)` (use `@Entity(primaryKeys = ["rootId", "path"])`)
  - indices:
    - unique `(rootId, path)` implied by primary keys
    - `(rootId, isDir, etag)`
    - `(rootId, fileId)`
    - `(rootId, deletedAtEpochMillis)`
    - `(rootId, lastSeenAtEpochMillis)`
- `SyncWatermarkEntity` keyed by `rootId`
- `CapabilitiesCacheEntity` keyed by `rootId`

**Step 4: Update `NoteDatabase` entities + version/migration**

- Bump `NoteDatabase` version to `7`.
- Add `MIGRATION_6_7`:
  - `ALTER TABLE notes ADD COLUMN availability TEXT NOT NULL DEFAULT 'AVAILABLE'`
  - `ALTER TABLE notes ADD COLUMN source TEXT NOT NULL DEFAULT 'LOCAL'`
  - `CREATE TABLE server_snapshot (...)`
  - `CREATE INDEX ...` per PRD
  - `CREATE TABLE sync_watermark (...)`
  - `CREATE TABLE capabilities_cache (...)`

**Step 5: Run tests**

Run:
- `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.data.index.NoteDatabaseMigrationTests`
- `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.data.sync.db.ServerSnapshotDaoTests`

Expected: PASS

**Step 6: Commit**

```bash
git add app/src/main/java/com/gladomat/linklet/data/sync/db \
  app/src/main/java/com/gladomat/linklet/data/index/NoteDatabase.kt \
  tests/com/gladomat/linklet/data/sync/db
git commit -m "feat(sync): add server snapshot and watermark tables"
```

### Task 5: Build a streaming PROPFIND parser (XmlPullParser) + batch ingestion

**Files:**
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/webdav/PropfindResponseParser.kt`
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/webdav/WebDavPropfindClient.kt`
- Test: `tests/com/gladomat/linklet/data/sync/webdav/PropfindResponseParserTests.kt`
- Add test resource: `tests/resources/webdav/propfind-multistatus-2-items.xml`

**Step 1: Write failing parser test using a saved XML fixture**

```kotlin
@Test
fun `parser streams responses without holding full list`() {
    val xml = javaClass.classLoader!!.getResource("webdav/propfind-multistatus-2-items.xml")!!.readText()
    val items = PropfindResponseParser.parse(xml.byteInputStream()).toList()
    assertEquals(2, items.count { !it.isDir })
}
```

**Step 2: Run test**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.data.sync.webdav.PropfindResponseParserTests`
Expected: FAIL.

**Step 3: Implement parser**

Implement `PropfindResponseParser.parse(InputStream): Sequence<PropfindItem>` using `XmlPullParser`:
- yield each `<d:response>` with:
  - `href`
  - `etag` (normalized)
  - `lastModifiedEpochMillis` (parse RFC1123)
  - `size` (`getcontentlength` optional)
  - `isDir` based on `resourcetype/collection`
  - `fileId` from `oc:fileid` when present

**Step 4: Implement `WebDavPropfindClient`**

Use OkHttp directly (do not depend on Sardine list) so we can:
- set `Depth` header
- control property list (min properties)
- capture `Date` header for server time later
- stream response body to parser

**Step 5: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.data.sync.webdav.PropfindResponseParserTests`
Expected: PASS

**Step 6: Commit**

```bash
git add app/src/main/java/com/gladomat/linklet/data/sync/webdav \
  tests/com/gladomat/linklet/data/sync/webdav \
  tests/resources/webdav/propfind-multistatus-2-items.xml
git commit -m "feat(sync): add streaming PROPFIND parser"
```

### Task 6: Stage A snapshot builder worker (resumable) that populates `server_snapshot` and creates `REMOTE_STUB` notes

**Files:**
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/snapshot/SnapshotBuilder.kt`
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/worker/WebDavSnapshotWorker.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/data/sync/SyncScheduler.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/viewmodel/settings/WebDavSettingsViewModel.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/data/sync/worker/SyncWorker.kt` (optional: stop forcing FG for Stage A)
- Test: `tests/com/gladomat/linklet/data/sync/snapshot/SnapshotBuilderTests.kt`

**Step 1: Write failing SnapshotBuilder test**

```kotlin
@Test
fun `snapshot builder writes server_snapshot rows and creates stub notes`() = runTest {
    // Given a mocked WebDavPropfindClient returning a small tree
    // When snapshot runs
    // Then server_snapshot has expected rows and notes table has REMOTE_STUB + availability=STUB.
}
```

**Step 2: Implement SnapshotBuilder**

Rules:
- Must batch DB writes (e.g., 500 snapshot rows per transaction).
- Must persist a traversal queue for resumability:
  - simplest: `server_snapshot` rows for directories + a separate `snapshot_queue` table (optional), or
  - store pending dirs in `WorkManager` input data chunks (less robust).

Prefer: create `snapshot_queue` table:
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/db/SnapshotQueueEntity.kt`
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/db/SnapshotQueueDao.kt`
(If you add this, bump DB version to 7 in the same migration to avoid extra churn.)

**Step 3: Integrate WorkManager**

- Add unique work name: `LinkletWebDavSnapshot`.
- When user saves WebDAV settings for the first time, enqueue snapshot work instead of full sync.
- Keep `SyncWorker` for reconcile/execute; snapshot should not require a foreground service.

**Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.data.sync.snapshot.SnapshotBuilderTests`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/gladomat/linklet/data/sync/snapshot \
  app/src/main/java/com/gladomat/linklet/data/sync/worker/WebDavSnapshotWorker.kt \
  app/src/main/java/com/gladomat/linklet/data/sync/SyncScheduler.kt \
  app/src/main/java/com/gladomat/linklet/viewmodel/settings/WebDavSettingsViewModel.kt \
  tests/com/gladomat/linklet/data/sync/snapshot/SnapshotBuilderTests.kt
git commit -m "feat(sync): stage A snapshot worker with stub notes"
```

## Phase 2: Nextcloud ETag-Bubbled Traversal Fallback (PRD Phase 2)

### Task 7: Root ETag probe (Depth: 0) and conditional traversal

**Files:**
- Modify: `app/src/main/java/com/gladomat/linklet/data/sync/snapshot/SnapshotBuilder.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/data/sync/webdav/WebDavPropfindClient.kt`
- Test: `tests/com/gladomat/linklet/data/sync/snapshot/SnapshotBuilderTests.kt`

**Step 1: Write failing test**

Add test case:
- Given stored root ETag in `sync_watermark` or `server_snapshot`
- When root ETag unchanged
- Then snapshot builder performs no recursive traversal (or minimal validation).

**Step 2: Implement Depth: 0 PROPFIND**

Add `propfindDepth0(url)` returning root item (and capture headers for server time).

**Step 3: Implement bubbling for Nextcloud**

Strategy:
- For each directory, if its ETag matches the last stored ETag in `server_snapshot`, skip listing it.
- Only recurse into dirs with changed ETag.
- For generic servers: do not bubble; keep bounded BFS.

**Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.data.sync.snapshot.SnapshotBuilderTests`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/gladomat/linklet/data/sync/snapshot/SnapshotBuilder.kt \
  app/src/main/java/com/gladomat/linklet/data/sync/webdav/WebDavPropfindClient.kt \
  tests/com/gladomat/linklet/data/sync/snapshot/SnapshotBuilderTests.kt
git commit -m "feat(sync): nextcloud etag-bubbled traversal"
```

## Phase 3: Delta Discovery (SEARCH + Trashbin) With Server Watermarks + Validity Gates (PRD Phase 3)

### Task 8: Capabilities probe with TTL + negative caching

**Files:**
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/capabilities/CapabilitiesProbe.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/data/sync/db/CapabilitiesCacheDao.kt`
- Test: `tests/com/gladomat/linklet/data/sync/capabilities/CapabilitiesProbeTests.kt`

**Step 1: Write failing tests**

- Cached hit returns without network.
- Expired negative cache triggers re-probe.

**Step 2: Implement probe**

Minimum viable probing (pragmatic):
- SEARCH support:
  - try a minimal SEARCH request against the WebDAV root; 400/405/501 => unsupported
- Trashbin support:
  - try PROPFIND against expected Nextcloud trashbin URL; 404/401/403 => unsupported (cache result)
- fileId support:
  - include `oc:fileid` in PROPFIND property list; if always missing, mark unsupported

**Step 3: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.data.sync.capabilities.CapabilitiesProbeTests`
Expected: PASS

**Step 4: Commit**

```bash
git add app/src/main/java/com/gladomat/linklet/data/sync/capabilities \
  tests/com/gladomat/linklet/data/sync/capabilities
git commit -m "feat(sync): add capabilities probe with TTL caching"
```

### Task 9: Server-derived watermark capture + overlap window + dedupe keys

**Files:**
- Modify: `app/src/main/java/com/gladomat/linklet/data/sync/db/SyncWatermarkDao.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/data/sync/webdav/WebDavPropfindClient.kt`
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/delta/DeltaTime.kt`
- Test: `tests/com/gladomat/linklet/data/sync/delta/WatermarkTests.kt`

**Step 1: Write failing tests**

- Uses HTTP `Date` header as watermark (not device clock).
- Applies `SKEW_MARGIN_MS` overlap and dedupes duplicates.

**Step 2: Implement watermark**

- Extend WebDavPropfindClient to return both items and response `Date` header as epoch millis (parse RFC1123).
- Store watermark only after successful delta phase.

**Step 3: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.data.sync.delta.WatermarkTests`
Expected: PASS

**Step 4: Commit**

```bash
git add app/src/main/java/com/gladomat/linklet/data/sync/db/SyncWatermarkDao.kt \
  app/src/main/java/com/gladomat/linklet/data/sync/webdav/WebDavPropfindClient.kt \
  app/src/main/java/com/gladomat/linklet/data/sync/delta/DeltaTime.kt \
  tests/com/gladomat/linklet/data/sync/delta/WatermarkTests.kt
git commit -m "feat(sync): server-derived watermarks with skew overlap"
```

### Task 10: SEARCH + trashbin delta fetch with explicit validity gates (FR3)

**Files:**
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/delta/NextcloudDeltaClient.kt`
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/delta/DeltaValidity.kt`
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/delta/DeltaApplier.kt`
- Test: `tests/com/gladomat/linklet/data/sync/delta/DeltaValidityTests.kt`

**Step 1: Write failing tests covering hard/soft invalidation**

Cases from PRD:
- hard: capability revoked; repeated 5xx/429 beyond budget; parse errors
- soft: delta too old; root ETag changed but SEARCH returns 0; trashbin insufficient

**Step 2: Implement validity state + reason codes**

- Persist to `sync_watermark.deltaValidityState` and reason.

**Step 3: Implement delta fetch**

- SEARCH changes since `(lastDeltaServerTime - SKEW_MARGIN_MS)` (Nextcloud fast path).
- Trashbin deletions since same window.
- Deduplicate by stable key:
  - prefer `(fileId, etag)`
  - else `(href, etag)`
  - else `(path, etag)`

**Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.data.sync.delta.DeltaValidityTests`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/gladomat/linklet/data/sync/delta \
  tests/com/gladomat/linklet/data/sync/delta
git commit -m "feat(sync): implement delta discovery with validity gates"
```

## Phase 4: Operation Journal + Deterministic Concurrency (PRD Phase 4)

### Task 11: Add `operation_journal` table + DAO (Room v8)

**Files:**
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/db/OperationJournalEntity.kt`
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/db/OperationJournalDao.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/data/index/NoteDatabase.kt`
- Test: `tests/com/gladomat/linklet/data/index/NoteDatabaseMigrationTests.kt`
- Test: `tests/com/gladomat/linklet/data/sync/db/OperationJournalDaoTests.kt`

**Step 1: Write failing migration test (7 -> 8)**

Add:
- `migration 7 to 8 creates operation_journal`

**Step 2: Implement entity + migration**

- Bump DB version to `8`.
- Add `MIGRATION_7_8` creating table + indices needed for claiming work by status/nextAttemptAt.

**Step 3: Run tests**

Run:
- `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.data.index.NoteDatabaseMigrationTests`
- `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.data.sync.db.OperationJournalDaoTests`

**Step 4: Commit**

```bash
git add app/src/main/java/com/gladomat/linklet/data/index/NoteDatabase.kt \
  app/src/main/java/com/gladomat/linklet/data/sync/db/OperationJournalEntity.kt \
  app/src/main/java/com/gladomat/linklet/data/sync/db/OperationJournalDao.kt \
  tests/com/gladomat/linklet/data/index/NoteDatabaseMigrationTests.kt \
  tests/com/gladomat/linklet/data/sync/db/OperationJournalDaoTests.kt
git commit -m "feat(sync): add operation journal table"
```

### Task 12: Refactor reconcile into a planner that writes journal entries (idempotent)

**Files:**
- Modify: `app/src/main/java/com/gladomat/linklet/data/sync/SyncEngine.kt`
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/planner/ReconcilePlanner.kt`
- Test: `tests/com/gladomat/linklet/data/sync/planner/ReconcilePlannerTests.kt`

**Step 1: Write failing planner test**

```kotlin
@Test
fun `planner writes journal ops for remote-only notes as downloads`() = runTest {
    // seed server_snapshot with remote file; local storage empty
    // assert operation_journal has DOWNLOAD op planned
}
```

**Step 2: Implement planner**

Inputs:
- local current (from `IStorage.listFiles` + stat/hash on demand)
- `sync_state`
- `server_snapshot` (remote current)

Output:
- `operation_journal` rows, with preconditions:
  - deletes include expectedRemoteEtag when known
  - uploads include expectedLocalFingerprint/hash when needed

**Step 3: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.data.sync.planner.ReconcilePlannerTests`
Expected: PASS

**Step 4: Commit**

```bash
git add app/src/main/java/com/gladomat/linklet/data/sync/SyncEngine.kt \
  app/src/main/java/com/gladomat/linklet/data/sync/planner/ReconcilePlanner.kt \
  tests/com/gladomat/linklet/data/sync/planner/ReconcilePlannerTests.kt
git commit -m "refactor(sync): plan ops via operation journal"
```

### Task 13: Bounded concurrent executor + single DB writer with transactional apply

**Files:**
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/executor/JournalExecutor.kt`
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/executor/DbWriter.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/data/sync/worker/SyncWorker.kt`
- Test: `tests/com/gladomat/linklet/data/sync/executor/JournalExecutorTests.kt`

**Step 1: Write failing crash recovery test**

Scenario:
- create 10 planned ops
- executor starts, mark 3 RUNNING, then simulate crash (cancel coroutine)
- restart executor and assert it resumes remaining ops deterministically without duplicating side effects

**Step 2: Implement executor**

Rules:
- Claim ops in small batches (status=PLANNED and `nextAttemptAt <= now`) and mark RUNNING.
- Execute network work with bounded concurrency (Semaphore).
- Send results to `DbWriter` via `Channel`.

**Step 3: Implement DB writer**

Rules:
- Single coroutine consumer.
- For each op result, run one Room transaction updating:
  - local file metadata (as needed)
  - `sync_state`
  - `server_snapshot`
  - journal status + error fields

**Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.data.sync.executor.JournalExecutorTests`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/gladomat/linklet/data/sync/executor \
  app/src/main/java/com/gladomat/linklet/data/sync/worker/SyncWorker.kt \
  tests/com/gladomat/linklet/data/sync/executor/JournalExecutorTests.kt
git commit -m "feat(sync): add concurrent journal executor with single db writer"
```

## Phase 5: Stage B Prefetch (PRD Phase 5)

### Task 14: Add pinned/offline policy + prefetch worker under constraints

**Files:**
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/prefetch/PrefetchPolicy.kt`
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/worker/PrefetchWorker.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/data/sync/SyncScheduler.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/data/index/NoteEntity.kt`
- Test: `tests/com/gladomat/linklet/data/sync/prefetch/PrefetchWorkerTests.kt`

**Step 1: Implement runtime backpressure checks**

Use:
- `ConnectivityManager` metered check (default: skip on metered)
- `BatteryManager` battery not low (and WorkManager constraints)

**Step 2: Prioritization**

Minimum viable ordering (until "recently opened" exists):
1. `availability == PINNED_OFFLINE`
2. most recently modified (from server snapshot mtime)
3. remaining stubs

**Step 3: Budgeting**

Add caps per run:
- max files (e.g., 50)
- max bytes (e.g., 5 MB)
- only `.md/.txt/.org` and size <= threshold

**Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.data.sync.prefetch.PrefetchWorkerTests`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/gladomat/linklet/data/sync/prefetch \
  app/src/main/java/com/gladomat/linklet/data/sync/worker/PrefetchWorker.kt \
  app/src/main/java/com/gladomat/linklet/data/sync/SyncScheduler.kt \
  tests/com/gladomat/linklet/data/sync/prefetch/PrefetchWorkerTests.kt
git commit -m "feat(sync): add stage B prefetch worker with budgets"
```

## Phase 6: Tombstone GC + Periodic Sweeps (PRD Phase 6)

### Task 15: Tombstone TTL and GC worker

**Files:**
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/gc/TombstoneGc.kt`
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/worker/TombstoneGcWorker.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/data/sync/db/ServerSnapshotDao.kt`
- Test: `tests/com/gladomat/linklet/data/sync/gc/TombstoneGcTests.kt`

**Step 1: Add DAO query for purge**

`DELETE FROM server_snapshot WHERE rootId = :rootId AND deletedAtEpochMillis IS NOT NULL AND deletedAtEpochMillis < :cutoff`

**Step 2: Implement worker scheduling**

- Run after successful full sweep.
- Also schedule periodically (e.g., weekly) with constraints.

**Step 3: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.data.sync.gc.TombstoneGcTests`
Expected: PASS

**Step 4: Commit**

```bash
git add app/src/main/java/com/gladomat/linklet/data/sync/gc \
  app/src/main/java/com/gladomat/linklet/data/sync/worker/TombstoneGcWorker.kt \
  app/src/main/java/com/gladomat/linklet/data/sync/db/ServerSnapshotDao.kt \
  tests/com/gladomat/linklet/data/sync/gc/TombstoneGcTests.kt
git commit -m "feat(sync): tombstone GC with TTL"
```

## Final Verification Checklist (Before Calling It Done)

Run:
- `./gradlew :app:testDebugUnitTest`

Manually (device/emulator if available):
- Enable WebDAV and confirm the app stays responsive immediately.
- Confirm note list shows stub items with explicit "not downloaded" state.
- Confirm opening a stub note shows a stub UI, not a blank/error-only screen.
- Confirm initial Stage A snapshot completes and is resumable after force-stop.
- Confirm no-change sync is fast (few requests) and does not traverse full tree.
