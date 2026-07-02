package com.gladomat.linklet.data.index

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gladomat.linklet.data.parser.RegexParser
import com.gladomat.linklet.data.storage.IStorage
import com.gladomat.linklet.data.storage.StorageFileStat
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.gladomat.linklet.testing.Aarch64RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Aarch64RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = android.app.Application::class)
class IndexPass2ProcessorTests {

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
    fun `run upserts outgoing links and marks links ready`() = runTest {
        val content = """
            #+title: A
            [[file:b.org][B]]
        """.trimIndent()
        noteDao.insertNotes(listOf(NoteEntity(path = "a.org", title = "A", linksReady = false)))
        val storage = FakeStorage(mutableMapOf("a.org" to content))
        val processor = IndexPass2Processor(storage, RegexParser(), noteDao, indexQueueDao, database)

        processor.run().getOrThrow()

        val backlinks = noteDao.getBacklinks("b.org")
        assertEquals(1, backlinks.size)
        assertEquals("a.org", backlinks.first().source)

        val notes = noteDao.getAllNotes()
        assertTrue(notes.first { it.path == "a.org" }.linksReady)
        assertEquals(1, indexQueueDao.countByStatus(pass = 2, status = IndexQueueStatus.DONE))
    }

    @Test
    fun `run replaces existing links for note`() = runTest {
        noteDao.insertNotes(listOf(NoteEntity(path = "a.org", title = "A", linksReady = false)))
        noteDao.insertLinks(listOf(LinkEntity(source = "a.org", target = "old.org", alias = null)))
        val storage = FakeStorage(
            mutableMapOf(
                "a.org" to "[[file:new.org]]",
            ),
        )
        val processor = IndexPass2Processor(storage, RegexParser(), noteDao, indexQueueDao, database)

        processor.run().getOrThrow()

        assertEquals(0, noteDao.getBacklinks("old.org").size)
        assertEquals(1, noteDao.getBacklinks("new.org").size)
    }

    @Test
    fun `run resolves id links using orgId mapping`() = runTest {
        noteDao.insertNotes(
            listOf(
                NoteEntity(path = "a.org", title = "A", linksReady = false),
                NoteEntity(path = "target.org", title = "T", orgId = "id-123", linksReady = true),
            ),
        )
        val storage = FakeStorage(mutableMapOf("a.org" to "[[id:id-123][X]]"))
        val processor = IndexPass2Processor(storage, RegexParser(), noteDao, indexQueueDao, database)

        processor.run().getOrThrow()

        val backlinks = noteDao.getBacklinks("target.org")
        assertEquals(1, backlinks.size)
        assertEquals("a.org", backlinks.first().source)
    }

    @Test
    fun `run marks unresolved id link attempts done without marking links ready`() = runTest {
        noteDao.insertNotes(listOf(NoteEntity(path = "a.org", title = "A", linksReady = false)))
        val storage = FakeStorage(mutableMapOf("a.org" to "[[id:missing-id][X]]"))
        val processor = IndexPass2Processor(storage, RegexParser(), noteDao, indexQueueDao, database)

        processor.run().getOrThrow()

        assertEquals(0, noteDao.getBacklinks("any.org").size)
        assertEquals(0, indexQueueDao.countByStatus(pass = 2, status = IndexQueueStatus.PENDING))
        assertEquals(1, indexQueueDao.countByStatus(pass = 2, status = IndexQueueStatus.DONE))
        assertEquals(false, noteDao.getAllNotes().first { it.path == "a.org" }.linksReady)
    }

    @Test
    fun `run marks queue failed when note read fails and keeps existing links`() = runTest {
        noteDao.insertNotes(listOf(NoteEntity(path = "a.org", title = "A", linksReady = false)))
        noteDao.insertLinks(listOf(LinkEntity(source = "a.org", target = "kept.org", alias = null)))
        val storage = object : FakeStorage(mutableMapOf("a.org" to "placeholder")) {
            override suspend fun readNote(path: String): Result<String> = Result.failure(IOException("boom"))
        }
        val processor = IndexPass2Processor(storage, RegexParser(), noteDao, indexQueueDao, database)

        processor.run().getOrThrow()

        assertEquals(1, indexQueueDao.countByStatus(pass = 2, status = IndexQueueStatus.FAILED))
        assertEquals(1, noteDao.getBacklinks("kept.org").size)
        assertTrue(noteDao.getAllNotes().first { it.path == "a.org" }.linksReady.not())
    }

    @Test
    fun `run delete operation removes links by orgId`() = runTest {
        // A DELETE pass-2 op models a tombstoned note, which carries deletedAt. Without it the
        // note matches listNotesNeedingLinks and gets re-enqueued as UPSERT, clobbering the DELETE.
        noteDao.insertNotes(listOf(NoteEntity(path = "a.org", title = "A", orgId = "id-a", linksReady = false, deletedAt = 1L)))
        noteDao.insertLinks(listOf(LinkEntity(source = "a.org", target = "b.org", alias = null, sourceOrgId = "id-a")))
        indexQueueDao.upsert(
            IndexQueueEntity(
                path = "a.org",
                pass = 2,
                operation = IndexQueueOperation.DELETE,
                status = IndexQueueStatus.PENDING,
            ),
        )
        val processor = IndexPass2Processor(FakeStorage(mutableMapOf()), RegexParser(), noteDao, indexQueueDao, database)

        processor.run().getOrThrow()

        assertEquals(0, noteDao.getBacklinks("b.org").size)
        assertEquals(1, indexQueueDao.countByStatus(pass = 2, status = IndexQueueStatus.DONE))
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
}
