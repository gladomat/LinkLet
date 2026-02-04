package com.gladomat.linklet.data.index

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.gladomat.linklet.testing.Aarch64RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Aarch64RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class IndexingStateDaoTests {

    private lateinit var database: NoteDatabase
    private lateinit var stateDao: IndexingStateDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        stateDao = database.indexingStateDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `state dao upserts and reads singleton state`() = runTest {
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
}
