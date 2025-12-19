# Sync Non-Org Attachments Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Sync all files under the notes root (org and non-org) with default ignore rules for transient/system paths.

**Architecture:** Extend storage and sync discovery to operate on all files, add a shared ignore filter, and adjust upload/download to handle binary content for non-org files while keeping existing reconciliation and guardrails.

**Tech Stack:** Kotlin, coroutines, Android storage (File + SAF), WebDAV (Sardine), Room.

---

### Task 1: Add shared sync path filter

**Files:**
- Create: `app/src/main/java/com/gladomat/linklet/data/sync/SyncPathFilter.kt`
- Test: `app/src/test/java/com/gladomat/linklet/data/sync/SyncPathFilterTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.gladomat.linklet.data.sync

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncPathFilterTest {
    @Test
    fun `accepts normal paths and rejects dot or underscore folders`() {
        assertTrue(SyncPathFilter.shouldInclude("notes/foo.org"))
        assertTrue(SyncPathFilter.shouldInclude("notes/images/cat.png"))
        assertFalse(SyncPathFilter.shouldInclude(".git/config"))
        assertFalse(SyncPathFilter.shouldInclude("_private/secret.pdf"))
        assertFalse(SyncPathFilter.shouldInclude("notes/.attachments/file.png"))
        assertFalse(SyncPathFilter.shouldInclude("notes/_cache/file.bin"))
    }

    @Test
    fun `rejects known system files`() {
        assertFalse(SyncPathFilter.shouldInclude(".DS_Store"))
        assertFalse(SyncPathFilter.shouldInclude("Thumbs.db"))
        assertFalse(SyncPathFilter.shouldInclude("desktop.ini"))
        assertFalse(SyncPathFilter.shouldInclude("__MACOSX/thing"))
        assertFalse(SyncPathFilter.shouldInclude(".trash/item"))
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.gladomat.linklet.data.sync.SyncPathFilterTest`
Expected: FAIL (missing `SyncPathFilter`).

**Step 3: Write minimal implementation**

```kotlin
package com.gladomat.linklet.data.sync

object SyncPathFilter {
    private val blockedFileNames = setOf(
        ".DS_Store",
        "Thumbs.db",
        "desktop.ini",
        "__MACOSX",
        ".git",
        ".trash",
    )

    fun shouldInclude(path: String): Boolean {
        val normalized = path.trim('/')
        if (normalized.isEmpty()) return false
        val segments = normalized.split('/').filter { it.isNotEmpty() }
        if (segments.any { it.startsWith('.') || it.startsWith('_') }) return false
        if (segments.any { blockedFileNames.contains(it) }) return false
        return true
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.gladomat.linklet.data.sync.SyncPathFilterTest`
Expected: PASS.

**Step 5: Commit**

```bash
git add app/src/main/java/com/gladomat/linklet/data/sync/SyncPathFilter.kt app/src/test/java/com/gladomat/linklet/data/sync/SyncPathFilterTest.kt
git commit -m "feat(sync): add path filter for attachments"
```

---

### Task 2: Extend storage with listFiles + binary IO

**Files:**
- Modify: `app/src/main/java/com/gladomat/linklet/data/storage/IStorage.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/data/storage/FileStorageImpl.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/data/storage/DocumentTreeStorageImpl.kt`
- Test: `app/src/test/java/com/gladomat/linklet/data/storage/FileStorageImplTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.gladomat.linklet.data.storage

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileStorageImplTest {
    @Test
    fun `listFiles includes non-org files`() {
        val tempDir = createTempDirectory().toFile()
        File(tempDir, "a.org").writeText("hi")
        File(tempDir, "img.png").writeBytes(byteArrayOf(1, 2, 3))
        val storage = FileStorageImpl(tempDir)

        val files = storage.listFiles().getOrThrow()
        assertTrue(files.contains("a.org"))
        assertTrue(files.contains("img.png"))
    }

    @Test
    fun `binary write and read round trip`() {
        val tempDir = createTempDirectory().toFile()
        val storage = FileStorageImpl(tempDir)
        val bytes = byteArrayOf(5, 6, 7, 8)

        storage.writeFileBytes("bin/data.bin", bytes).getOrThrow()
        val result = storage.readFileBytes("bin/data.bin").getOrThrow()
        assertEquals(bytes.toList(), result.toList())
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.gladomat.linklet.data.storage.FileStorageImplTest`
Expected: FAIL (missing listFiles/readFileBytes/writeFileBytes).

**Step 3: Write minimal implementation**

- Add to `IStorage`:

```kotlin
suspend fun listFiles(): Result<List<String>>
suspend fun readFileBytes(path: String): Result<ByteArray>
suspend fun writeFileBytes(path: String, content: ByteArray): Result<Unit>
```

- Implement in `FileStorageImpl` using `File.readBytes()` / `File.writeBytes()` and include all files (no extension filter).
- Implement in `DocumentTreeStorageImpl` using content resolver input/output streams; create files with `application/octet-stream` when needed.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.gladomat.linklet.data.storage.FileStorageImplTest`
Expected: PASS.

**Step 5: Commit**

```bash
git add app/src/main/java/com/gladomat/linklet/data/storage/IStorage.kt \
  app/src/main/java/com/gladomat/linklet/data/storage/FileStorageImpl.kt \
  app/src/main/java/com/gladomat/linklet/data/storage/DocumentTreeStorageImpl.kt \
  app/src/test/java/com/gladomat/linklet/data/storage/FileStorageImplTest.kt

git commit -m "feat(storage): add listFiles and binary io"
```

---

### Task 3: Update WebDAV listing to include all files + filter

**Files:**
- Modify: `app/src/main/java/com/gladomat/linklet/data/sync/WebDavRemoteSyncProvider.kt`
- Test: `app/src/test/java/com/gladomat/linklet/data/sync/WebDavRemoteSyncProviderTest.kt` (new test, if feasible with fakes) OR extend filter tests.

**Step 1: Write the failing test**

If WebDAV integration tests are hard to mock, extend `SyncPathFilterTest` with a unit-level test that ensures `.org` filtering is NOT used anymore in list mapping logic (use a helper to test a mapping function extracted from `listRemoteNotes`).

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.gladomat.linklet.data.sync.SyncPathFilterTest`
Expected: FAIL (new expectations).

**Step 3: Write minimal implementation**

- Remove the `.org`-only check in `listRemoteNotes`.
- Apply `SyncPathFilter.shouldInclude(relativePath)` before adding to results.
- Ensure WebDAV listing is recursive (use depth that includes subfolders). If the current depth does not include files in subfolders, increase to a safe recursive depth or explicitly recurse.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.gladomat.linklet.data.sync.SyncPathFilterTest`
Expected: PASS.

**Step 5: Commit**

```bash
git add app/src/main/java/com/gladomat/linklet/data/sync/WebDavRemoteSyncProvider.kt \
  app/src/test/java/com/gladomat/linklet/data/sync/SyncPathFilterTest.kt

git commit -m "feat(sync): include non-org files in webdav listing"
```

---

### Task 4: Update sync discovery + binary handling

**Files:**
- Modify: `app/src/main/java/com/gladomat/linklet/data/sync/SyncEngine.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/data/sync/NoteHashCalculator.kt`
- Test: `app/src/test/java/com/gladomat/linklet/data/sync/SyncEngineDiscoveryTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.gladomat.linklet.data.sync

import kotlin.test.Test
import kotlin.test.assertTrue

class SyncEngineDiscoveryTest {
    @Test
    fun `discovery includes non-org files`() {
        val paths = listOf("a.org", "img/cat.png", "_ignored/skip.bin")
        val filtered = paths.filter { SyncPathFilter.shouldInclude(it) }
        assertTrue(filtered.contains("img/cat.png"))
        assertTrue(!filtered.contains("_ignored/skip.bin"))
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.gladomat.linklet.data.sync.SyncEngineDiscoveryTest`
Expected: FAIL (missing test file).

**Step 3: Write minimal implementation**

- Replace `storage.listNotes()` with `storage.listFiles()` and filter via `SyncPathFilter`.
- Update `LocalFile` to hold either string content or byte content; or store bytes and compute hash from bytes.
- Update upload/download to use `readNote`/`writeNote` for `.org`, `readFileBytes`/`writeFileBytes` otherwise.
- Extend hash calculator to accept `ByteArray`.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.gladomat.linklet.data.sync.SyncEngineDiscoveryTest`
Expected: PASS.

**Step 5: Commit**

```bash
git add app/src/main/java/com/gladomat/linklet/data/sync/SyncEngine.kt \
  app/src/main/java/com/gladomat/linklet/data/sync/NoteHashCalculator.kt \
  app/src/test/java/com/gladomat/linklet/data/sync/SyncEngineDiscoveryTest.kt

git commit -m "feat(sync): sync non-org files end-to-end"
```

---

### Task 5: Verification

**Step 1: Run relevant tests**

Run: `./gradlew test --tests com.gladomat.linklet.data.sync.SyncPathFilterTest --tests com.gladomat.linklet.data.storage.FileStorageImplTest --tests com.gladomat.linklet.data.sync.SyncEngineDiscoveryTest`
Expected: PASS.

**Step 2: Commit any fixes**

```bash
git add -u
git commit -m "test(sync): verify non-org sync coverage"
```

