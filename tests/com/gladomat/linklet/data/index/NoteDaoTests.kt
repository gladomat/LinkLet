package com.gladomat.linklet.data.index

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gladomat.linklet.data.index.NoteDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import com.gladomat.linklet.testing.Aarch64RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Aarch64RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class NoteDaoTests {

    private lateinit var database: NoteDatabase
    private lateinit var dao: NoteDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java).build()
        dao = database.noteDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert and retrieve notes and backlinks`() = runTest {
        val notes = listOf(
            NoteEntity(path = "a.org", title = "A"),
            NoteEntity(path = "b.org", title = "B"),
        )
        val links = listOf(
            LinkEntity(source = "a.org", target = "b.org", alias = "Alias"),
        )

        dao.insertNotes(notes)
        dao.insertLinks(links)

        val storedNotes = dao.getAllNotes()
        assertEquals(2, storedNotes.size)

        val backlinks = dao.getBacklinks("b.org")
        assertEquals(1, backlinks.size)
        assertEquals("a.org", backlinks.first().source)
        assertEquals("Alias", backlinks.first().alias)
    }
}
