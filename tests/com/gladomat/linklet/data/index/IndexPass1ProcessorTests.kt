package com.gladomat.linklet.data.index

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gladomat.linklet.data.storage.IStorage
import com.gladomat.linklet.data.storage.StorageFileStat
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.gladomat.linklet.testing.Aarch64RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Aarch64RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = android.app.Application::class)
class IndexPass1ProcessorTests {

    private lateinit var database: NoteDatabase
    private lateinit var noteDao: NoteDao
    private lateinit var indexQueueDao: IndexQueueDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        noteDao = database.noteDao()
        indexQueueDao = database.indexQueueDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `run stores metadata and marks queue done`() = runTest {
        val content = """
            #+title: Sample Note
            #+filetags: :kotlin:async:
            :PROPERTIES:
            :ID: abc-123
            :END:
            Body
        """.trimIndent()
        val storage = FakeStorage(mutableMapOf("a.org" to content))
        val processor = IndexPass1Processor(storage, noteDao, indexQueueDao, database)

        processor.run().getOrThrow()

        val notes = noteDao.getAllNotes()
        assertEquals(1, notes.size)
        val note = notes.first()
        assertEquals("a.org", note.path)
        assertEquals("Sample Note", note.title)
        assertEquals("abc-123", note.orgId)
        assertEquals(listOf("kotlin", "async"), note.fileTags)
        assertNull(note.deletedAt)

        val doneCount = indexQueueDao.countByStatus(pass = 1, status = IndexQueueStatus.DONE)
        assertEquals(1, doneCount)
    }

    @Test
    fun `run tombstones notes not present in storage`() = runTest {
        noteDao.insertNotes(listOf(NoteEntity(path = "gone.org", title = "Gone")))
        val storage = FakeStorage(mutableMapOf())
        val processor = IndexPass1Processor(storage, noteDao, indexQueueDao, database)

        processor.run().getOrThrow()

        val notes = noteDao.getAllNotes()
        assertEquals(1, notes.size)
        assertNotNull(notes.first().deletedAt)
    }

    @Test
    fun `run skips unchanged notes based on fingerprint`() = runTest {
        val content = "#+title: New Title\n#+filetags: :kotlin:\n:PROPERTIES:\n:ID: id-1\n:END:"
        val storage = FakeStorage(mutableMapOf("a.org" to content))
        noteDao.insertNotes(
            listOf(
                NoteEntity(
                    path = "a.org",
                    title = "Old Title",
                    orgId = "id-1",
                    fileTags = emptyList(),
                    deletedAt = null,
                    fingerprintMtime = 10L,
                    fingerprintSize = content.toByteArray(Charsets.UTF_8).size.toLong(),
                    linksReady = false,
                ),
            ),
        )
        val processor = IndexPass1Processor(storage, noteDao, indexQueueDao, database)

        processor.run().getOrThrow()

        assertEquals(0, indexQueueDao.countByPass(pass = 1))
        val notes = noteDao.getAllNotes()
        assertEquals("Old Title", notes.first().title)
    }

    @Test
    fun `run marks queue failed when note read fails`() = runTest {
        val storage = object : FakeStorage(mutableMapOf("missing.org" to "placeholder")) {
            override suspend fun readNote(path: String): Result<String> = Result.failure(IOException("boom"))
        }
        val processor = IndexPass1Processor(storage, noteDao, indexQueueDao, database)

        processor.run().getOrThrow()

        assertEquals(1, indexQueueDao.countByStatus(pass = 1, status = IndexQueueStatus.FAILED))
    }

    @Test
    fun `run enqueues pass2 when metadata changes`() = runTest {
        val content = """
            #+title: Sample Note
            :PROPERTIES:
            :ID: abc-123
            :END:
        """.trimIndent()
        val storage = FakeStorage(mutableMapOf("a.org" to content))
        val processor = IndexPass1Processor(storage, noteDao, indexQueueDao, database)

        processor.run().getOrThrow()

        assertEquals(1, indexQueueDao.countByStatus(pass = 2, status = IndexQueueStatus.PENDING))
    }

    @Test
    fun `run processes notes when initial scan exceeds processing budget`() = runTest {
        val storage = SlowStatStorage(
            mutableMapOf(
                "a.org" to "#+title: A",
                "b.org" to "#+title: B",
                "c.org" to "#+title: C",
            ),
        )
        val processor = IndexPass1Processor(storage, noteDao, indexQueueDao, database)

        processor.run(timeBudgetMillis = 5L).getOrThrow()

        assertEquals(3, indexQueueDao.countByPass(pass = 1))
        assertEquals(1, noteDao.getAllNotes().size)
        assertEquals(1, indexQueueDao.countByStatus(pass = 1, status = IndexQueueStatus.DONE))
    }

    @Test
    fun `run does not stat every new note before first insert`() = runTest {
        val storage = SlowStatStorage(
            mutableMapOf(
                "a.org" to "#+title: A",
                "b.org" to "#+title: B",
                "c.org" to "#+title: C",
            ),
        )
        val processor = IndexPass1Processor(storage, noteDao, indexQueueDao, database)

        processor.run(timeBudgetMillis = 5L).getOrThrow()

        assertEquals(1, noteDao.getAllNotes().size)
        assertEquals(1, storage.statCalls)
    }

    private open class FakeStorage(
        private val files: MutableMap<String, String>,
    ) : IStorage {
        override suspend fun listNotes(): Result<List<String>> =
            Result.success(files.keys.filter { it.endsWith(".org", ignoreCase = true) })

        override suspend fun listFiles(): Result<List<String>> =
            Result.success(files.keys.toList())

        open override suspend fun readNote(path: String): Result<String> =
            files[path]?.let { Result.success(it) } ?: Result.failure(IOException("missing"))

        override suspend fun readFileBytes(path: String): Result<ByteArray> =
            readNote(path).map { it.toByteArray(Charsets.UTF_8) }

        override suspend fun statNote(path: String): Result<StorageFileStat> =
            readFileBytes(path).map { bytes ->
                StorageFileStat(
                    lastModifiedEpochMillis = 10L,
                    sizeBytes = bytes.size.toLong(),
                )
            }

        override suspend fun writeNote(path: String, content: String): Result<Unit> {
            files[path] = content
            return Result.success(Unit)
        }

        override suspend fun writeFileBytes(path: String, content: ByteArray): Result<Unit> =
            writeNote(path, content.toString(Charsets.UTF_8))

        override suspend fun deleteNote(path: String): Result<Unit> {
            files.remove(path)
            return Result.success(Unit)
        }

        override suspend fun renameNote(oldPath: String, newPath: String): Result<Unit> {
            val content = files.remove(oldPath) ?: return Result.failure(IOException("missing"))
            files[newPath] = content
            return Result.success(Unit)
        }

        override suspend fun resolveUri(path: String): Result<android.net.Uri> =
            Result.failure(UnsupportedOperationException("Not used in these tests"))

        override suspend fun invalidateCache() = Unit
    }

    private class SlowStatStorage(
        files: MutableMap<String, String>,
    ) : FakeStorage(files) {
        var statCalls = 0
            private set

        override suspend fun statNote(path: String): Result<StorageFileStat> {
            statCalls += 1
            Thread.sleep(10L)
            return super.statNote(path)
        }
    }
}
