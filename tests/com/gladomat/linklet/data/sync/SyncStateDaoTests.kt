package com.gladomat.linklet.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gladomat.linklet.data.index.NoteDatabase
import com.gladomat.linklet.data.index.SyncStateDao
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
@Config(manifest = Config.NONE)
class SyncStateDaoTests {

    private lateinit var database: NoteDatabase
    private lateinit var dao: SyncStateDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.syncStateDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `upsert saves state and get returns it`() = runTest {
        val state = SyncStateEntity(
            path = "notes/a.org",
            lastKnownHash = "hash",
            remoteId = "remote",
            pendingAction = SyncPendingAction.UPLOAD,
        )

        dao.upsert(state)

        val saved = dao.getState("notes/a.org")
        requireNotNull(saved)
        assertEquals("hash", saved.lastKnownHash)
        assertEquals(SyncPendingAction.UPLOAD, saved.pendingAction)
    }

    @Test
    fun `getPendingStates returns entries with pending actions`() = runTest {
        val ready = SyncStateEntity(path = "notes/ready.org")
        val pending = SyncStateEntity(
            path = "notes/pending.org",
            pendingAction = SyncPendingAction.UPLOAD,
        )
        dao.upsert(ready)
        dao.upsert(pending)

        val pendingStates = dao.getPendingStates()

        assertEquals(1, pendingStates.size)
        assertEquals("notes/pending.org", pendingStates.first().path)
    }

    @Test
    fun `updatePendingAction updates action and error`() = runTest {
        val state = SyncStateEntity(path = "notes/error.org", pendingAction = SyncPendingAction.UPLOAD)
        dao.upsert(state)

        dao.updatePendingAction(
            path = "notes/error.org",
            action = SyncPendingAction.NONE,
            lastError = "network",
        )

        val updated = dao.getState("notes/error.org")
        requireNotNull(updated)
        assertEquals(SyncPendingAction.NONE, updated.pendingAction)
        assertEquals("network", updated.lastError)
    }

    @Test
    fun `getAllStates returns every row`() = runTest {
        dao.upsert(SyncStateEntity(path = "a", pendingAction = SyncPendingAction.NONE))
        dao.upsert(SyncStateEntity(path = "b", pendingAction = SyncPendingAction.UPLOAD))

        val states = dao.getAllStates()

        assertEquals(2, states.size)
        assertTrue(states.any { it.path == "a" })
        assertTrue(states.any { it.path == "b" })
    }
}
