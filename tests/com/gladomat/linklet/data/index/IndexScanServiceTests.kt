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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.gladomat.linklet.testing.Aarch64RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Aarch64RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class IndexScanServiceTests {

    private lateinit var database: NoteDatabase
    private lateinit var noteDao: NoteDao
    private lateinit var indexQueueDao: IndexQueueDao
    private lateinit var indexingStateDao: IndexingStateDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        noteDao = database.noteDao()
        indexQueueDao = database.indexQueueDao()
        indexingStateDao = database.indexingStateDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `scan enqueues pass1 for changed files and tombstones missing`() = runTest {
        noteDao.insertNotes(listOf(NoteEntity(path = "gone.org", title = "Gone", orgId = "gone-id")))
        val storage = FakeStorage(mutableMapOf("a.org" to "#+title: A"))
        val scanner = IndexScanService(
            storage = storage,
            noteDao = noteDao,
            indexQueueDao = indexQueueDao,
            indexingStateDao = indexingStateDao,
            clock = { 1_000L },
        )

        scanner.scan().getOrThrow()

        assertEquals(1, indexQueueDao.countByPass(pass = 1))
        val deleted = noteDao.getAllNotes().first { it.path == "gone.org" }
        assertNotNull(deleted.deletedAt)
    }

    private open class FakeStorage(
        private val files: MutableMap<String, String>,
    ) : IStorage {
        override suspend fun listNotes(): Result<List<String>> =
            Result.success(files.keys.filter { it.endsWith(".org", ignoreCase = true) })

        override suspend fun listFiles(): Result<List<String>> =
            Result.success(files.keys.toList())

        override suspend fun readNote(path: String): Result<String> =
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
