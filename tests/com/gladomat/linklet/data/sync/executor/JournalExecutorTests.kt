package com.gladomat.linklet.data.sync.executor

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gladomat.linklet.data.index.NoteDatabase
import com.gladomat.linklet.data.sync.db.OperationJournalDao
import com.gladomat.linklet.data.sync.db.OperationJournalEntity
import com.gladomat.linklet.testing.Aarch64RobolectricTestRunner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Aarch64RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class JournalExecutorTests {

    private lateinit var database: NoteDatabase
    private lateinit var dao: OperationJournalDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.operationJournalDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `executor marks successful operations as done`() = runTest {
        val now = 1_700_001_000_000L
        dao.upsertAll(
            listOf(
                plannedOperation("op-1", now),
                plannedOperation("op-2", now),
            ),
        )
        val executor = JournalExecutor(
            operationJournalDao = dao,
            workerId = "worker-1",
        )

        executor.execute(
            rootId = "root-1",
            limit = 10,
            concurrency = 2,
            nowEpochMillis = now,
        ) { Result.success(Unit) }

        assertEquals("DONE", dao.getById("op-1")?.status)
        assertEquals("DONE", dao.getById("op-2")?.status)
    }

    @Test
    fun `executor marks failures with error message`() = runTest {
        val now = 1_700_001_200_000L
        dao.upsert(plannedOperation("op-fail", now))
        val executor = JournalExecutor(
            operationJournalDao = dao,
            workerId = "worker-2",
        )

        executor.execute(
            rootId = "root-1",
            limit = 10,
            concurrency = 1,
            nowEpochMillis = now,
        ) { Result.failure(IllegalStateException("boom")) }

        val saved = dao.getById("op-fail")
        assertEquals("FAILED", saved?.status)
        assertEquals("boom", saved?.lastError)
    }

    private fun plannedOperation(operationId: String, now: Long): OperationJournalEntity =
        OperationJournalEntity(
            operationId = operationId,
            rootId = "root-1",
            path = "notes/$operationId.org",
            operationType = "DOWNLOAD",
            status = "PLANNED",
            priority = 1,
            nextAttemptAtEpochMillis = now,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
}
