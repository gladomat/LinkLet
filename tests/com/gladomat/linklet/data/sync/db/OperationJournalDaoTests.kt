package com.gladomat.linklet.data.sync.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gladomat.linklet.data.index.NoteDatabase
import com.gladomat.linklet.testing.Aarch64RobolectricTestRunner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Aarch64RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class OperationJournalDaoTests {

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
    fun `upsert and query operation by id`() = runTest {
        val now = 1_700_000_000_000L
        dao.upsert(
            OperationJournalEntity(
                operationId = "op-1",
                rootId = "root-1",
                path = "a.org",
                operationType = "DOWNLOAD",
                status = "PLANNED",
                nextAttemptAtEpochMillis = now,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )

        val saved = dao.getById("op-1")
        assertNotNull(saved)
        assertEquals("DOWNLOAD", saved?.operationType)
        assertEquals("PLANNED", saved?.status)
    }

    @Test
    fun `findReady returns only ready operations ordered by priority then creation time`() = runTest {
        val now = 1_700_000_010_000L
        dao.upsertAll(
            listOf(
                OperationJournalEntity(
                    operationId = "op-late",
                    rootId = "root-1",
                    path = "late.org",
                    operationType = "DOWNLOAD",
                    status = "PLANNED",
                    priority = 1,
                    nextAttemptAtEpochMillis = now + 1_000L,
                    createdAtEpochMillis = now - 30L,
                    updatedAtEpochMillis = now,
                ),
                OperationJournalEntity(
                    operationId = "op-mid",
                    rootId = "root-1",
                    path = "mid.org",
                    operationType = "DOWNLOAD",
                    status = "PLANNED",
                    priority = 5,
                    nextAttemptAtEpochMillis = now,
                    createdAtEpochMillis = now - 20L,
                    updatedAtEpochMillis = now,
                ),
                OperationJournalEntity(
                    operationId = "op-top",
                    rootId = "root-1",
                    path = "top.org",
                    operationType = "UPLOAD",
                    status = "PLANNED",
                    priority = 10,
                    nextAttemptAtEpochMillis = now - 1L,
                    createdAtEpochMillis = now - 10L,
                    updatedAtEpochMillis = now,
                ),
            ),
        )

        val ready = dao.findReady(rootId = "root-1", status = "PLANNED", nowEpochMillis = now, limit = 10)
        assertEquals(listOf("op-top", "op-mid"), ready.map { it.operationId })
    }
}
