package com.gladomat.linklet.domain.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gladomat.linklet.data.index.NoteDatabase
import com.gladomat.linklet.data.model.Note
import com.gladomat.linklet.data.parser.RegexParser
import com.gladomat.linklet.data.storage.IStorage
import com.gladomat.linklet.data.model.LinkTarget
import com.gladomat.linklet.data.sync.SyncScheduler
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class NoteRepositoryImplTests {

    private val parser = RegexParser()
    private val dispatcher = StandardTestDispatcher()
    private val syncScheduler = mockk<SyncScheduler>(relaxed = true)

    private lateinit var database: NoteDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `getNote returns parsed note when storage succeeds`() = runTest(dispatcher) {
        val storage = FakeStorage(mutableMapOf("path.org" to "#+title: Sample\nBody"))
        val repository = NoteRepositoryImpl(storage, parser, database.noteDao(), syncScheduler, dispatcher)

        val result = repository.getNote("path.org")

        assertTrue(result.isSuccess)
        val note = result.getOrThrow()
        assertEquals("Sample", note.title)
        assertEquals("Body", note.content.lines().last())
    }

    @Test
    fun `getNote propagates storage failure`() = runTest(dispatcher) {
        val storage = FakeStorage(mutableMapOf())
        val repository = NoteRepositoryImpl(storage, parser, database.noteDao(), syncScheduler, dispatcher)

        val result = repository.getNote("missing.org")

        assertTrue(result.isFailure)
    }

    @Test
    fun `reindex stores notes and backlinks`() = runTest(dispatcher) {
        val storage = FakeStorage(
            mutableMapOf(
                "a.org" to "#+title: A\n[[file:b.org][Alias]]",
                "b.org" to "#+title: B\nContent",
            ),
        )
        val repository = NoteRepositoryImpl(storage, parser, database.noteDao(), syncScheduler, dispatcher)

        repository.reindex().getOrThrow()

        val backlinks = repository.getBacklinks("b.org").getOrThrow()
        assertEquals(1, backlinks.size)
        val backlink = backlinks.first()
        assertEquals("a.org", backlink.source)
        assertEquals("Alias", backlink.alias)
        assertEquals("A", backlink.sourceTitle)

        val notes = repository.observeNotes().first()
        assertEquals(2, notes.size)
        val resolvedLink = notes.first { it.id.path == "a.org" }.links.first()
        assertEquals("b.org", resolvedLink.resolvedPath)
    }

    @Test
    fun `reindex resolves id links to note paths`() = runTest(dispatcher) {
        val storage = FakeStorage(
            mutableMapOf(
                "a.org" to """
                    #+title: Note A
                    :PROPERTIES:
                    :ID: note-a
                    :END:
                    [[id:note-b][Alias]]
                """.trimIndent(),
                "b.org" to """
                    #+title: Note B
                    :PROPERTIES:
                    :ID: note-b
                    :END:
                    Content
                """.trimIndent(),
            ),
        )
        val repository = NoteRepositoryImpl(storage, parser, database.noteDao(), syncScheduler, dispatcher)

        repository.reindex().getOrThrow()

        val backlinks = repository.getBacklinks("b.org").getOrThrow()
        assertEquals(1, backlinks.size)
        assertEquals("a.org", backlinks.first().source)
        assertEquals("Note A", backlinks.first().sourceTitle)

        val note = repository.getNote("a.org").getOrThrow()
        val firstLink = note.links.first()
        assertTrue(firstLink.target is LinkTarget.Id)
        assertEquals("b.org", firstLink.resolvedPath)
    }

    @Test
    fun `reindex resolves id links when id line is outside properties drawer`() = runTest(dispatcher) {
        val storage = FakeStorage(
            mutableMapOf(
                "a.org" to """
                    #+title: Note A
                    [[id:note-b][Alias]]
                """.trimIndent(),
                "b.org" to """
                    #+title: Note B
                    :ID: note-b
                    Content
                """.trimIndent(),
            ),
        )
        val repository = NoteRepositoryImpl(storage, parser, database.noteDao(), syncScheduler, dispatcher)

        repository.reindex().getOrThrow()

        val note = repository.getNote("a.org").getOrThrow()
        assertEquals("b.org", note.links.first().resolvedPath)
    }

    @Test
    fun `duplicateNote generates unique path and new id`() = runTest(dispatcher) {
        val files = mutableMapOf(
            "a.org" to """
                #+title: Note A
                :PROPERTIES:
                :ID: old-id
                :END:
                Body
            """.trimIndent(),
        )
        val storage = FakeStorage(files)
        val repository = NoteRepositoryImpl(storage, parser, database.noteDao(), syncScheduler, dispatcher)

        val first = repository.duplicateNote("a.org").getOrThrow()
        val second = repository.duplicateNote("a.org").getOrThrow()

        assertNotEquals("a.org", first)
        assertNotEquals(first, second)
        assertNotNull(files[first])
        assertNotNull(files[second])
        assertNotEquals(files["a.org"], files[first])
        assertNotEquals(files["a.org"], files[second])
        assertNull(Regex("""(?im)^\s*:ID:\s*old-id\s*$""").find(files[first]!!))
        assertTrue(Regex("""(?im)^\s*:ID:\s*[0-9a-f-]{36}\s*$""").containsMatchIn(files[first]!!))
    }

    @Test
    fun `duplicateNote preserves CRLF line endings`() = runTest(dispatcher) {
        val files = mutableMapOf(
            "a.org" to "#+title: A\r\n:PROPERTIES:\r\n:ID: old-id\r\n:END:\r\nBody\r\n",
        )
        val storage = FakeStorage(files)
        val repository = NoteRepositoryImpl(storage, parser, database.noteDao(), syncScheduler, dispatcher)

        val newPath = repository.duplicateNote("a.org").getOrThrow()
        val duplicated = files[newPath]!!

        assertTrue(duplicated.contains("\r\n"))
        assertTrue(duplicated.replace("\r\n", "").contains("\n").not())
    }

    @Test
    fun `updateNoteProperties preserves existing ID when not provided`() = runTest(dispatcher) {
        val files = mutableMapOf(
            "a.org" to """
                #+title: Note A
                :PROPERTIES:
                :ID: old-id
                :ROAM_ALIASES: OldAlias
                :END:
                Body
            """.trimIndent(),
        )
        val storage = FakeStorage(files)
        val repository = NoteRepositoryImpl(storage, parser, database.noteDao(), syncScheduler, dispatcher)

        repository.updateNoteProperties("a.org", mapOf("ROAM_REFS" to "https://example.com")).getOrThrow()

        val updated = files["a.org"]!!
        assertTrue(updated.contains(Regex("""(?im)^\s*:ID:\s*old-id\s*$""")))
        assertTrue(updated.contains(Regex("""(?im)^\s*:ROAM_REFS:\s*https://example\.com\s*$""")))
        assertTrue(updated.contains(":PROPERTIES:"))
        assertTrue(updated.contains(":END:"))
    }

    @Test
    fun `updateNoteTags removes filetags line without collapsing body newlines`() = runTest(dispatcher) {
        val files = mutableMapOf(
            "a.org" to """
                #+title: Note A
                #+filetags: :a:b:
                Line1

                Line2
            """.trimIndent(),
        )
        val storage = FakeStorage(files)
        val repository = NoteRepositoryImpl(storage, parser, database.noteDao(), syncScheduler, dispatcher)

        repository.updateNoteTags("a.org", emptyList()).getOrThrow()

        val updated = files["a.org"]!!
        assertTrue(updated.contains("#+filetags:").not())
        assertTrue(Regex("""Line1\r?\n\r?\nLine2""").containsMatchIn(updated))
    }

    @Test
    fun `updateNoteTags inserts normalized filetags line after title when missing`() = runTest(dispatcher) {
        val files = mutableMapOf(
            "a.org" to """
                #+title: Note A
                Body
            """.trimIndent(),
        )
        val storage = FakeStorage(files)
        val repository = NoteRepositoryImpl(storage, parser, database.noteDao(), syncScheduler, dispatcher)

        repository.updateNoteTags("a.org", listOf("Project", "my tag")).getOrThrow()

        val updated = files["a.org"]!!
        assertTrue(Regex("""(?im)^#\+filetags:\s*:project:mytag:\s*$""").containsMatchIn(updated))
    }

    @Test
    fun `renameNote updates backlinks target path`() = runTest(dispatcher) {
        val files = mutableMapOf(
            "a.org" to "#+title: A\n[[file:b.org][Alias]]",
            "b.org" to "#+title: B\nContent",
        )
        val storage = FakeStorage(files)
        val repository = NoteRepositoryImpl(storage, parser, database.noteDao(), syncScheduler, dispatcher)

        repository.reindex().getOrThrow()
        repository.renameNote("b.org", "c.org").getOrThrow()

        val backlinksOld = repository.getBacklinks("b.org").getOrThrow()
        assertTrue(backlinksOld.isEmpty())

        val backlinksNew = repository.getBacklinks("c.org").getOrThrow()
        assertEquals(1, backlinksNew.size)
        assertEquals("a.org", backlinksNew.first().source)
        assertEquals("Alias", backlinksNew.first().alias)
    }

    @Test
    fun `soft delete moves note to trash and excludes from index`() = runTest(dispatcher) {
        val files = mutableMapOf(
            "a.org" to "#+title: A\nContent",
        )
        val storage = FakeStorage(files)
        val repository = NoteRepositoryImpl(storage, parser, database.noteDao(), syncScheduler, dispatcher)

        repository.reindex().getOrThrow()
        assertEquals(listOf("a.org"), storage.files.keys.filter { it.endsWith(".org") }.toList())

        repository.deleteNoteSoft("a.org").getOrThrow()

        // File should now live under the trash dir with timestamp+slug naming.
        val trashedOrgFiles = storage.files.keys.filter { it.startsWith("_trash_bin/") && it.endsWith(".org") }
        assertEquals(1, trashedOrgFiles.size)
        assertTrue(Regex("""^_trash_bin/\d{14}_a\.org$""").containsMatchIn(trashedOrgFiles.first()))

        // Active index should be empty
        val notes = repository.listAllNotes()
        assertTrue(notes.isEmpty())
    }

    @Test
    fun `restore from trash returns original path and handles conflicts`() = runTest(dispatcher) {
        val files = mutableMapOf(
            "a.org" to "#+title: A\nContent",
        )
        val storage = FakeStorage(files)
        val repository = NoteRepositoryImpl(storage, parser, database.noteDao(), syncScheduler, dispatcher)

        repository.reindex().getOrThrow()
        repository.deleteNoteSoft("a.org").getOrThrow()

        val trashPath = storage.files.keys.first { it.startsWith("_trash_bin/") && it.endsWith(".org") }

        // First restore should go back to a.org
        val restoredPath1 = repository.restoreNoteFromTrash(trashPath).getOrThrow()
        assertEquals("a.org", restoredPath1)
        assertTrue(storage.files.containsKey("a.org"))

        // Move a.org to trash again, then create a conflicting new a.org before restoring.
        repository.deleteNoteSoft("a.org").getOrThrow()
        storage.files["a.org"] = "#+title: A\nConflicting New Note"

        // Now original a.org exists, so restore should pick a conflict-resolved name
        val trashPath2 = storage.files.keys.first { it.startsWith("_trash_bin/") && it.endsWith(".org") }
        val restoredPath2 = repository.restoreNoteFromTrash(trashPath2).getOrThrow()
        assertTrue(restoredPath2 != "a.org")
        assertTrue(restoredPath2.startsWith("a ("))
        assertTrue(storage.files.containsKey(restoredPath2))
    }

    @Test
    fun `restore legacy trash entry without metadata falls back to notes directory`() = runTest(dispatcher) {
        val files = mutableMapOf(
            // Existing active note establishes "notes/" as default repo dir.
            "notes/existing.org" to "#+title: Existing\nBody",
            // Legacy trash entry (no metadata) that only contains a filename.
            "_trash_bin/a.org" to "#+title: A\nBody",
        )
        val storage = FakeStorage(files)
        val repository = NoteRepositoryImpl(storage, parser, database.noteDao(), syncScheduler, dispatcher)

        repository.reindex().getOrThrow()

        val restored = repository.restoreNoteFromTrash("_trash_bin/a.org").getOrThrow()

        assertEquals("notes/a.org", restored)
        assertTrue(storage.files.containsKey("notes/a.org"))
        assertTrue(storage.files.containsKey("_trash_bin/a.org").not())
    }

    private class FakeStorage(
        val files: MutableMap<String, String>,
    ) : IStorage {
        override suspend fun listNotes(): Result<List<String>> =
            Result.success(files.keys.filter { it.endsWith(".org", ignoreCase = true) })

        override suspend fun readNote(path: String): Result<String> =
            files[path]?.let { Result.success(it) } ?: Result.failure(IOException("missing"))

        override suspend fun writeNote(path: String, content: String): Result<Unit> {
            files[path] = content
            return Result.success(Unit)
        }

        override suspend fun deleteNote(path: String): Result<Unit> {
            files.remove(path)
            return Result.success(Unit)
        }

        override suspend fun renameNote(oldPath: String, newPath: String): Result<Unit> {
            val content = files.remove(oldPath) ?: return Result.failure(IOException("Source not found"))
            files[newPath] = content
            return Result.success(Unit)
        }

        override suspend fun resolveUri(path: String): Result<android.net.Uri> =
            Result.failure(UnsupportedOperationException("Not used in these tests"))

        override suspend fun invalidateCache() {
            // No-op for fake
        }
    }
}
