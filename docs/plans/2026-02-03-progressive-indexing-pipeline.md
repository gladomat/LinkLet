# Progressive Indexing Pipeline Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace monolithic `reindex()` with an always-fast, resumable two-pass pipeline (Scan → Pass1 → Pass2) that is idempotent under concurrent edits and retries.

**Architecture:** Introduce an `IndexScanService` that enumerates storage and enqueues queue items with fingerprint snapshots, extend `index_queue` with operations + leasing, add `IndexingState` for idle-window gating, and update Pass1/Pass2 processors + workers to honor leases, stale detection, and Pass2 gating. Backlink cleanup becomes orgId-based.

**Tech Stack:** Kotlin, Room (migrations), WorkManager, Hilt, Coroutines, Robolectric, MockK.

---

### Task 1: Extend queue enums + converters

**Files:**
- Modify: `app/src/main/java/com/gladomat/linklet/data/index/IndexQueueEntity.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/data/index/IndexTypeConverters.kt`
- Test: `tests/com/gladomat/linklet/data/index/IndexTypeConvertersTest.kt`

**Step 1: Write the failing test**
```kotlin
@Test
fun `index queue status and operation round trip`() {
    val converters = IndexTypeConverters()
    assertEquals(IndexQueueStatus.RUNNING, converters.toIndexQueueStatus("RUNNING"))
    assertEquals(IndexQueueOperation.DELETE, converters.toIndexQueueOperation("DELETE"))
}
```

**Step 2: Run test to verify it fails**
Run: `./gradlew test --tests "com.gladomat.linklet.data.index.IndexTypeConvertersTest"`  
Expected: FAIL (missing enum + converter for operation)

**Step 3: Write minimal implementation**
```kotlin
enum class IndexQueueStatus { PENDING, RUNNING, DONE, FAILED }
enum class IndexQueueOperation { UPSERT, DELETE }

fun toIndexQueueOperation(value: String?): IndexQueueOperation =
    value?.let { runCatching { IndexQueueOperation.valueOf(it) }.getOrDefault(IndexQueueOperation.UPSERT) }
        ?: IndexQueueOperation.UPSERT
fun fromIndexQueueOperation(value: IndexQueueOperation?): String? = value?.name
```

**Step 4: Run test to verify it passes**
Run: `./gradlew test --tests "com.gladomat.linklet.data.index.IndexTypeConvertersTest"`  
Expected: PASS

**Step 5: Commit**
```bash
git add app/src/main/java/com/gladomat/linklet/data/index/IndexQueueEntity.kt \
        app/src/main/java/com/gladomat/linklet/data/index/IndexTypeConverters.kt \
        tests/com/gladomat/linklet/data/index/IndexTypeConvertersTest.kt
git commit -m "refactor(index): add queue operation + running status converters"
```

---

### Task 2: Schema migration for queue leasing + link orgIds

**Files:**
- Modify: `app/src/main/java/com/gladomat/linklet/data/index/NoteDatabase.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/data/index/IndexQueueEntity.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/data/index/NoteEntity.kt`
- Test: `tests/com/gladomat/linklet/data/index/NoteDatabaseMigrationTests.kt` (new)

**Step 1: Write the failing test**
```kotlin
@Test
fun `migration 5 to 6 adds queue leasing and link orgId columns`() {
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NoteDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
    )
    val db = helper.createDatabase("migration-test", 5)
    db.execSQL("INSERT INTO links(source, target, alias) VALUES('a.org', 'b.org', NULL)")
    db.execSQL("INSERT INTO index_queue(path, pass, status, attempts) VALUES('a.org', 1, 'PENDING', 0)")
    db.close()

    val migrated = helper.runMigrationsAndValidate("migration-test", 6, true, NoteDatabase.MIGRATION_5_6)
    migrated.query("PRAGMA table_info('links')").use { cursor ->
        assertTrue(cursor.columnNames.contains("sourceOrgId"))
        assertTrue(cursor.columnNames.contains("targetOrgId"))
    }
    migrated.query("PRAGMA table_info('index_queue')").use { cursor ->
        assertTrue(cursor.columnNames.contains("operation"))
        assertTrue(cursor.columnNames.contains("lockedAtEpochMillis"))
        assertTrue(cursor.columnNames.contains("nextAttemptAtEpochMillis"))
    }
}
```

**Step 2: Run test to verify it fails**
Run: `./gradlew test --tests "com.gladomat.linklet.data.index.NoteDatabaseMigrationTests"`  
Expected: FAIL (missing migration + columns)

**Step 3: Write minimal implementation**
```kotlin
@Entity(tableName = "links", primaryKeys = ["source", "target"])
data class LinkEntity(
    val source: String,
    val target: String,
    val alias: String?,
    val sourceOrgId: String? = null,
    val targetOrgId: String? = null,
)

@Entity(tableName = "index_queue", primaryKeys = ["path", "pass"])
data class IndexQueueEntity(
    val path: String,
    val pass: Int,
    val operation: IndexQueueOperation = IndexQueueOperation.UPSERT,
    val status: IndexQueueStatus = IndexQueueStatus.PENDING,
    val attempts: Int = 0,
    val lastError: String? = null,
    val updatedAtEpochMillis: Long? = null,
    val expectedMtime: Long? = null,
    val expectedSize: Long? = null,
    val expectedHash: String? = null,
    val lockedAtEpochMillis: Long? = null,
    val nextAttemptAtEpochMillis: Long? = null,
)
```
```kotlin
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `links` ADD COLUMN `sourceOrgId` TEXT")
        database.execSQL("ALTER TABLE `links` ADD COLUMN `targetOrgId` TEXT")
        database.execSQL("ALTER TABLE `index_queue` ADD COLUMN `operation` TEXT NOT NULL DEFAULT 'UPSERT'")
        database.execSQL("ALTER TABLE `index_queue` ADD COLUMN `lockedAtEpochMillis` INTEGER")
        database.execSQL("ALTER TABLE `index_queue` ADD COLUMN `nextAttemptAtEpochMillis` INTEGER")
        database.execSQL("ALTER TABLE `index_queue` ADD COLUMN `expectedHash` TEXT")
    }
}
```

**Step 4: Run test to verify it passes**
Run: `./gradlew test --tests "com.gladomat.linklet.data.index.NoteDatabaseMigrationTests"`  
Expected: PASS

**Step 5: Commit**
```bash
git add app/src/main/java/com/gladomat/linklet/data/index/NoteDatabase.kt \
        app/src/main/java/com/gladomat/linklet/data/index/IndexQueueEntity.kt \
        app/src/main/java/com/gladomat/linklet/data/index/NoteEntity.kt \
        tests/com/gladomat/linklet/data/index/NoteDatabaseMigrationTests.kt
git commit -m "refactor(index): add queue leasing + link orgId columns"
```

---

### Task 3: Add IndexingState persistence (idle window + scan checkpoints)

**Files:**
- Create: `app/src/main/java/com/gladomat/linklet/data/index/IndexingStateEntity.kt`
- Create: `app/src/main/java/com/gladomat/linklet/data/index/IndexingStateDao.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/data/index/NoteDatabase.kt`
- Test: `tests/com/gladomat/linklet/data/index/IndexingStateDaoTests.kt` (new)

**Step 1: Write the failing test**
```kotlin
@Test
fun `state dao upserts and reads singleton state`() = runTest {
    val stateDao = database.indexingStateDao()
    val state = IndexingStateEntity(
        id = 1,
        lastScanAtEpochMillis = 123L,
        lastPass1EnqueueAtEpochMillis = 456L,
        lastPass2RunAtEpochMillis = 789L,
    )
    stateDao.upsert(state)
    val loaded = stateDao.get()
    assertEquals(123L, loaded?.lastScanAtEpochMillis)
    assertEquals(456L, loaded?.lastPass1EnqueueAtEpochMillis)
}
```

**Step 2: Run test to verify it fails**
Run: `./gradlew test --tests "com.gladomat.linklet.data.index.IndexingStateDaoTests"`  
Expected: FAIL (dao/entity missing)

**Step 3: Write minimal implementation**
```kotlin
@Entity(tableName = "indexing_state")
data class IndexingStateEntity(
    @PrimaryKey val id: Int = 1,
    val lastScanAtEpochMillis: Long? = null,
    val lastPass1EnqueueAtEpochMillis: Long? = null,
    val lastPass2RunAtEpochMillis: Long? = null,
)

@Dao
interface IndexingStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: IndexingStateEntity)

    @Query("SELECT * FROM indexing_state WHERE id = 1")
    suspend fun get(): IndexingStateEntity?
}
```
Add `indexing_state` creation to migration `MIGRATION_5_6` and `NoteDatabase` entity list.

**Step 4: Run test to verify it passes**
Run: `./gradlew test --tests "com.gladomat.linklet.data.index.IndexingStateDaoTests"`  
Expected: PASS

**Step 5: Commit**
```bash
git add app/src/main/java/com/gladomat/linklet/data/index/IndexingStateEntity.kt \
        app/src/main/java/com/gladomat/linklet/data/index/IndexingStateDao.kt \
        app/src/main/java/com/gladomat/linklet/data/index/NoteDatabase.kt \
        tests/com/gladomat/linklet/data/index/IndexingStateDaoTests.kt
git commit -m "feat(index): persist indexing state checkpoints"
```

---

### Task 4: Queue claiming + lease expiry logic in DAO

**Files:**
- Modify: `app/src/main/java/com/gladomat/linklet/data/index/IndexQueueDao.kt`
- Test: `tests/com/gladomat/linklet/data/index/IndexQueueDaoTests.kt` (new)

**Step 1: Write the failing test**
```kotlin
@Test
fun `claimNext prefers pending then expired running then due failed`() = runTest {
    val now = 1_000L
    indexQueueDao.upsertAll(
        listOf(
            IndexQueueEntity(path = "a.org", pass = 1, status = IndexQueueStatus.PENDING),
            IndexQueueEntity(path = "b.org", pass = 1, status = IndexQueueStatus.RUNNING, lockedAtEpochMillis = now - 10_000),
            IndexQueueEntity(path = "c.org", pass = 1, status = IndexQueueStatus.FAILED, nextAttemptAtEpochMillis = now - 1),
        ),
    )
    val claimed = indexQueueDao.claimNext(pass = 1, now = now, leaseTimeoutMillis = 5_000)
    assertEquals("a.org", claimed?.path)
}
```

**Step 2: Run test to verify it fails**
Run: `./gradlew test --tests "com.gladomat.linklet.data.index.IndexQueueDaoTests"`  
Expected: FAIL (claimNext missing)

**Step 3: Write minimal implementation**
```kotlin
@Query(
    """
    SELECT * FROM index_queue
    WHERE pass = :pass AND (
        status = 'PENDING' OR
        (status = 'RUNNING' AND lockedAtEpochMillis IS NOT NULL AND lockedAtEpochMillis <= :leaseExpiry) OR
        (status = 'FAILED' AND nextAttemptAtEpochMillis IS NOT NULL AND nextAttemptAtEpochMillis <= :now)
    )
    ORDER BY updatedAtEpochMillis
    LIMIT 1
    """,
)
suspend fun findClaimCandidate(pass: Int, now: Long, leaseExpiry: Long): IndexQueueEntity?

@Query(
    """
    UPDATE index_queue
    SET status = 'RUNNING', lockedAtEpochMillis = :now, attempts = attempts + 1, updatedAtEpochMillis = :now
    WHERE path = :path AND pass = :pass AND (
        status = 'PENDING' OR
        (status = 'RUNNING' AND lockedAtEpochMillis IS NOT NULL AND lockedAtEpochMillis <= :leaseExpiry) OR
        (status = 'FAILED' AND nextAttemptAtEpochMillis IS NOT NULL AND nextAttemptAtEpochMillis <= :now)
    )
    """,
)
suspend fun claim(path: String, pass: Int, now: Long, leaseExpiry: Long): Int
```
Provide a `suspend fun claimNext(...)` default method that loops once: candidate → claim → return entity when claimed.

**Step 4: Run test to verify it passes**
Run: `./gradlew test --tests "com.gladomat.linklet.data.index.IndexQueueDaoTests"`  
Expected: PASS

**Step 5: Commit**
```bash
git add app/src/main/java/com/gladomat/linklet/data/index/IndexQueueDao.kt \
        tests/com/gladomat/linklet/data/index/IndexQueueDaoTests.kt
git commit -m "feat(index): add queue leasing + claim semantics"
```

---

### Task 5: Implement IndexScanService (scan → enqueue)

**Files:**
- Create: `app/src/main/java/com/gladomat/linklet/data/index/IndexScanService.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/data/index/NoteDao.kt`
- Test: `tests/com/gladomat/linklet/data/index/IndexScanServiceTests.kt` (new)

**Step 1: Write the failing test**
```kotlin
@Test
fun `scan enqueues pass1 for changed files and tombstones missing`() = runTest {
    noteDao.insertNotes(listOf(NoteEntity(path = "gone.org", title = "Gone", orgId = "gone-id")))
    val storage = FakeStorage(mutableMapOf("a.org" to "#+title: A"))
    val scanner = IndexScanService(storage, noteDao, indexQueueDao, indexingStateDao, clock = { 1000L })

    scanner.scan().getOrThrow()

    assertEquals(1, indexQueueDao.countByPass(pass = 1))
    val deleted = noteDao.getAllNotes().first { it.path == "gone.org" }
    assertNotNull(deleted.deletedAt)
}
```

**Step 2: Run test to verify it fails**
Run: `./gradlew test --tests "com.gladomat.linklet.data.index.IndexScanServiceTests"`  
Expected: FAIL (service missing)

**Step 3: Write minimal implementation**
```kotlin
class IndexScanService(
    private val storage: IStorage,
    private val noteDao: NoteDao,
    private val indexQueueDao: IndexQueueDao,
    private val indexingStateDao: IndexingStateDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun scan(): Result<Unit> = runCatching {
        storage.invalidateCache()
        val now = clock()
        val activePaths = storage.listNotes().getOrThrow()
        val activeSet = activePaths.toSet()
        val existing = noteDao.getAllNotes().associateBy { it.path }
        existing.values.filter { it.deletedAt == null && it.path !in activeSet }
            .forEach { noteDao.markDeleted(it.path, now) }

        val enqueue = activePaths.mapNotNull { path ->
            val stat = storage.statNote(path).getOrNull()
            val current = existing[path]
            val unchanged = stat != null &&
                current != null &&
                current.deletedAt == null &&
                current.fingerprintMtime == stat.lastModifiedEpochMillis &&
                current.fingerprintSize == stat.sizeBytes
            if (unchanged) null else IndexQueueEntity(
                path = path,
                pass = 1,
                operation = IndexQueueOperation.UPSERT,
                status = IndexQueueStatus.PENDING,
                expectedMtime = stat?.lastModifiedEpochMillis,
                expectedSize = stat?.sizeBytes,
                updatedAtEpochMillis = now,
            )
        }
        if (enqueue.isNotEmpty()) indexQueueDao.upsertAll(enqueue)
        indexingStateDao.upsert(IndexingStateEntity(lastScanAtEpochMillis = now, lastPass1EnqueueAtEpochMillis = now))
    }
}
```

**Step 4: Run test to verify it passes**
Run: `./gradlew test --tests "com.gladomat.linklet.data.index.IndexScanServiceTests"`  
Expected: PASS

**Step 5: Commit**
```bash
git add app/src/main/java/com/gladomat/linklet/data/index/IndexScanService.kt \
        app/src/main/java/com/gladomat/linklet/data/index/NoteDao.kt \
        tests/com/gladomat/linklet/data/index/IndexScanServiceTests.kt
git commit -m "feat(index): add scan service for pass1 enqueue"
```

---

### Task 6: Update Pass1 processor to use queue ops + enqueue Pass2

**Files:**
- Modify: `app/src/main/java/com/gladomat/linklet/data/index/IndexPass1Processor.kt`
- Modify: `tests/com/gladomat/linklet/data/index/IndexPass1ProcessorTests.kt`

**Step 1: Write the failing test**
```kotlin
@Test
fun `pass1 enqueues pass2 upsert when metadata changes`() = runTest {
    val storage = FakeStorage(mutableMapOf("a.org" to "#+title: A\n:PROPERTIES:\n:ID: id-1\n:END:"))
    val processor = IndexPass1Processor(storage, noteDao, indexQueueDao, database)

    processor.run().getOrThrow()

    assertEquals(1, indexQueueDao.countByStatus(pass = 2, status = IndexQueueStatus.PENDING))
}
```

**Step 2: Run test to verify it fails**
Run: `./gradlew test --tests "com.gladomat.linklet.data.index.IndexPass1ProcessorTests"`  
Expected: FAIL (no Pass2 enqueue)

**Step 3: Write minimal implementation**
```kotlin
val claimed = indexQueueDao.claimNext(pass = PASS_1, now = now, leaseTimeoutMillis = LEASE_TIMEOUT)
// ...
if (changed) {
    noteDao.insertNotes(listOf(noteEntity))
    indexQueueDao.upsert(
        IndexQueueEntity(
            path = entry.path,
            pass = PASS_2,
            operation = IndexQueueOperation.UPSERT,
            status = IndexQueueStatus.PENDING,
            expectedMtime = entry.expectedMtime,
            expectedSize = entry.expectedSize,
            updatedAtEpochMillis = now,
        ),
    )
}
```
Handle stale fingerprints by updating the queue snapshot and continuing.

**Step 4: Run test to verify it passes**
Run: `./gradlew test --tests "com.gladomat.linklet.data.index.IndexPass1ProcessorTests"`  
Expected: PASS

**Step 5: Commit**
```bash
git add app/src/main/java/com/gladomat/linklet/data/index/IndexPass1Processor.kt \
        tests/com/gladomat/linklet/data/index/IndexPass1ProcessorTests.kt
git commit -m "feat(index): pass1 uses queue ops and enqueues pass2"
```

---

### Task 7: Update Pass2 processor for orgId links + delete ops

**Files:**
- Modify: `app/src/main/java/com/gladomat/linklet/data/index/IndexPass2Processor.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/data/index/NoteDao.kt`
- Modify: `tests/com/gladomat/linklet/data/index/IndexPass2ProcessorTests.kt`

**Step 1: Write the failing test**
```kotlin
@Test
fun `pass2 delete operation removes links by orgId`() = runTest {
    noteDao.insertNotes(listOf(NoteEntity(path = "a.org", title = "A", orgId = "id-a")))
    noteDao.insertLinks(listOf(LinkEntity(source = "a.org", target = "b.org", alias = null, sourceOrgId = "id-a")))
    indexQueueDao.upsert(IndexQueueEntity(path = "a.org", pass = 2, operation = IndexQueueOperation.DELETE))
    val processor = IndexPass2Processor(storage, RegexParser(), noteDao, indexQueueDao, database)

    processor.run().getOrThrow()

    assertEquals(0, noteDao.getBacklinks("b.org").size)
}
```

**Step 2: Run test to verify it fails**
Run: `./gradlew test --tests "com.gladomat.linklet.data.index.IndexPass2ProcessorTests"`  
Expected: FAIL (delete op ignored)

**Step 3: Write minimal implementation**
```kotlin
when (entry.operation) {
    IndexQueueOperation.DELETE -> {
        val orgId = noteDao.findOrgIdByPath(entry.path)
        if (orgId != null) {
            noteDao.deleteLinksByOrgId(orgId)
        } else {
            noteDao.deleteLinksBySource(entry.path)
            noteDao.deleteLinksByTarget(entry.path)
        }
        indexQueueDao.upsert(entry.copy(status = IndexQueueStatus.DONE, updatedAtEpochMillis = now))
    }
    IndexQueueOperation.UPSERT -> { /* existing logic with orgId fields */ }
}
```
Ensure inserted `LinkEntity` includes `sourceOrgId` + `targetOrgId`.

**Step 4: Run test to verify it passes**
Run: `./gradlew test --tests "com.gladomat.linklet.data.index.IndexPass2ProcessorTests"`  
Expected: PASS

**Step 5: Commit**
```bash
git add app/src/main/java/com/gladomat/linklet/data/index/IndexPass2Processor.kt \
        app/src/main/java/com/gladomat/linklet/data/index/NoteDao.kt \
        tests/com/gladomat/linklet/data/index/IndexPass2ProcessorTests.kt
git commit -m "feat(index): pass2 handles orgId links and deletions"
```

---

### Task 8: Worker gating + time budget

**Files:**
- Modify: `app/src/main/java/com/gladomat/linklet/data/index/worker/IndexPass1Worker.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/data/index/worker/IndexPass2Worker.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/data/index/IndexingScheduler.kt`
- Test: `tests/com/gladomat/linklet/data/index/IndexWorkerGatingTests.kt` (new)

**Step 1: Write the failing test**
```kotlin
@Test
fun `pass2 worker retries when pass1 pending or idle window not met`() = runTest {
    indexQueueDao.upsert(IndexQueueEntity(path = "a.org", pass = 1, status = IndexQueueStatus.PENDING))
    indexingStateDao.upsert(IndexingStateEntity(lastPass1EnqueueAtEpochMillis = clock()))
    val worker = IndexPass2Worker(appContext, params, processor, indexQueueDao, indexingStateDao, clock)

    val result = worker.doWork()
    assertEquals(Result.retry(), result)
}
```

**Step 2: Run test to verify it fails**
Run: `./gradlew test --tests "com.gladomat.linklet.data.index.IndexWorkerGatingTests"`  
Expected: FAIL (worker gating missing)

**Step 3: Write minimal implementation**
```kotlin
if (indexQueueDao.countByStatus(1, IndexQueueStatus.PENDING) > 0) return Result.retry()
val state = indexingStateDao.get()
val idleEnough = state?.lastPass1EnqueueAtEpochMillis?.let { now - it > IDLE_WINDOW_MS } ?: true
if (!idleEnough) return Result.retry()
```
Add time budget loop to Pass1 worker; if pending remains, return `Result.retry()`.

**Step 4: Run test to verify it passes**
Run: `./gradlew test --tests "com.gladomat.linklet.data.index.IndexWorkerGatingTests"`  
Expected: PASS

**Step 5: Commit**
```bash
git add app/src/main/java/com/gladomat/linklet/data/index/worker/IndexPass1Worker.kt \
        app/src/main/java/com/gladomat/linklet/data/index/worker/IndexPass2Worker.kt \
        app/src/main/java/com/gladomat/linklet/data/index/IndexingScheduler.kt \
        tests/com/gladomat/linklet/data/index/IndexWorkerGatingTests.kt
git commit -m "feat(index): add pass2 gating and pass1 time budget"
```

---

### Task 9: Replace repository reindex with pass1 scheduling

**Files:**
- Modify: `app/src/main/java/com/gladomat/linklet/domain/repository/NoteRepositoryImpl.kt`
- Modify: `tests/com/gladomat/linklet/domain/repository/NoteRepositoryImplTests.kt`

**Step 1: Write the failing test**
```kotlin
@Test
fun `saveNote schedules pass1 instead of reindex`() = runTest(dispatcher) {
    val indexingScheduler = mockk<IndexingScheduler>(relaxed = true)
    val repository = NoteRepositoryImpl(storage, parser, noteDao, indexQueueDao, syncScheduler, dispatcher, indexingScheduler)

    repository.saveNote("a.org", "#+title: A").getOrThrow()

    verify { indexingScheduler.schedulePass1() }
}
```

**Step 2: Run test to verify it fails**
Run: `./gradlew test --tests "com.gladomat.linklet.domain.repository.NoteRepositoryImplTests"`  
Expected: FAIL (reindex still used)

**Step 3: Write minimal implementation**
```kotlin
// Inject IndexingScheduler into repository
backgroundScope.launch {
    indexingScheduler.schedulePass1()
    syncScheduler.scheduleImmediate()
}
```
Replace `reindex()` calls in save/delete/restore flows with pass1 scheduling.

**Step 4: Run test to verify it passes**
Run: `./gradlew test --tests "com.gladomat.linklet.domain.repository.NoteRepositoryImplTests"`  
Expected: PASS

**Step 5: Commit**
```bash
git add app/src/main/java/com/gladomat/linklet/domain/repository/NoteRepositoryImpl.kt \
        tests/com/gladomat/linklet/domain/repository/NoteRepositoryImplTests.kt
git commit -m "refactor(repository): schedule pass1 instead of reindex"
```

---

## Notes & Constants

- **Lease timeout:** 10 minutes  
- **Idle window:** 10 seconds (strict pass1_pending == 0)  
- **Threshold knob:** define `PASS2_PENDING_THRESHOLD = 0` for future tuning  
- **Queue attempts backoff:** `nextAttemptAt = now + min(BASE * 2^attempts, CAP)`  

---

## Execution Handoff

Plan complete and saved to `docs/plans/2026-02-03-progressive-indexing-pipeline.md`. Two execution options:

1. **Subagent-Driven (this session)** — I dispatch a fresh subagent per task, review between tasks.
2. **Parallel Session (separate)** — Open a new session and use superpowers:executing-plans for batch execution.

Which approach?
