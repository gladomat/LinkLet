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
class CapabilitiesCacheDaoTests {

    private lateinit var database: NoteDatabase
    private lateinit var dao: CapabilitiesCacheDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.capabilitiesCacheDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `upsert and read cache row`() = runTest {
        dao.upsert(
            CapabilitiesCacheEntity(
                rootId = "root-1",
                supportsSearch = true,
                supportsTrashbin = false,
                supportsFileId = true,
                checkedAtEpochMillis = 1000L,
                expiresAtEpochMillis = 2000L,
            ),
        )

        val saved = dao.get(rootId = "root-1")
        assertNotNull(saved)
        assertEquals(true, saved?.supportsSearch)
        assertEquals(false, saved?.supportsTrashbin)
    }
}
