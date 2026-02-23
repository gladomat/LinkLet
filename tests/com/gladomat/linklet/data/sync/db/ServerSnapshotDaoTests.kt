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
class ServerSnapshotDaoTests {

    private lateinit var database: NoteDatabase
    private lateinit var dao: ServerSnapshotDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.serverSnapshotDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `upsert and query by rootId and path`() = runTest {
        dao.upsertAll(
            listOf(
                ServerSnapshotEntity(
                    rootId = "root-1",
                    path = "a.org",
                    href = "https://dav/root/a.org",
                    etag = "etag-1",
                    isDir = false,
                    fileId = "42",
                    deletedAtEpochMillis = null,
                    lastSeenAtEpochMillis = 1000L,
                ),
            ),
        )

        val saved = dao.getByPath(rootId = "root-1", path = "a.org")
        assertNotNull(saved)
        assertEquals("etag-1", saved?.etag)
    }
}
