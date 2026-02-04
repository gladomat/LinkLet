package com.gladomat.linklet.data.index

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
class IndexQueueDaoTests {

    private lateinit var database: NoteDatabase
    private lateinit var indexQueueDao: IndexQueueDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        indexQueueDao = database.indexQueueDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `claimNext prefers pending then expired running then due failed`() = runTest {
        val now = 1_000L
        indexQueueDao.upsertAll(
            listOf(
                IndexQueueEntity(path = "a.org", pass = 1, status = IndexQueueStatus.PENDING, updatedAtEpochMillis = 1L),
                IndexQueueEntity(
                    path = "b.org",
                    pass = 1,
                    status = IndexQueueStatus.RUNNING,
                    lockedAtEpochMillis = now - 10_000,
                    updatedAtEpochMillis = 2L,
                ),
                IndexQueueEntity(
                    path = "c.org",
                    pass = 1,
                    status = IndexQueueStatus.FAILED,
                    nextAttemptAtEpochMillis = now - 1,
                    updatedAtEpochMillis = 3L,
                ),
            ),
        )

        val claimed = indexQueueDao.claimNext(pass = 1, now = now, leaseTimeoutMillis = 5_000)

        assertNotNull(claimed)
        assertEquals("a.org", claimed?.path)
    }
}
