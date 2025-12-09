package com.gladomat.linklet.domain.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gladomat.linklet.data.index.NoteDatabase
import com.gladomat.linklet.data.model.Note
import com.gladomat.linklet.data.parser.RegexParser
import com.gladomat.linklet.data.storage.IStorage
import com.gladomat.linklet.data.model.LinkTarget
import com.gladomat.linklet.data.sync.SyncScheduler
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class NoteRepositoryImplTests {

    private val parser = RegexParser()
    private val dispatcher = StandardTestDispatcher()
    private val syncScheduler = mockk<SyncScheduler>(relaxed = true)

    private lateinit var database: NoteDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `getNote returns parsed note when storage succeeds`() = runTest(dispatcher) {
        val storage = FakeStorage(mutableMapOf("path.org" to "#+title: Sample\nBody"))
        val repository = NoteRepositoryImpl(storage, parser, database.noteDao(), syncScheduler, dispatcher)

        val result = repository.getNote("path.org")

        assertTrue(result.isSuccess)
        val note = result.getOrThrow()
        assertEquals("Sample", note.title)
        assertEquals("Body", note.content.lines().last())
    }

    @Test
    fun `getNote propagates storage failure`() = runTest(dispatcher) {
        val storage = FakeStorage(mutableMapOf())
        val repository = NoteRepositoryImpl(storage, parser, database.noteDao(), syncScheduler, dispatcher)

        val result = repository.getNote("missing.org")

        assertTrue(result.isFailure)
    }

    @Test
    fun `reindex stores notes and backlinks`() = runTest(dispatcher) {
        val storage = FakeStorage(
            mutableMapOf(
                "a.org" to "#+title: A\n[[file:b.org][Alias]]",
                "b.org" to "#+title: B\nContent",
            ),
        )
        val repository = NoteRepositoryImpl(storage, parser, database.noteDao(), syncScheduler, dispatcher)

        repository.reindex().getOrThrow()

        val backlinks = repository.getBacklinks("b.org").getOrThrow()
        assertEquals(1, backlinks.size)
        val backlink = backlinks.first()
        assertEquals("a.org", backlink.source)
        assertEquals("Alias", backlink.alias)
        assertEquals("A", backlink.sourceTitle)

        val notes = repository.observeNotes().first()
        assertEquals(2, notes.size)
        val resolvedLink = notes.first { it.id.path == "a.org" }.links.first()
        assertEquals("b.org", resolvedLink.resolvedPath)
    }

    @Test
    fun `reindex resolves id links to note paths`() = runTest(dispatcher) {
        val storage = FakeStorage(
            mutableMapOf(
                "a.org" to """
                    #+title: Note A
                    :PROPERTIES:
                    :ID: note-a
                    :END:
                    [[id:note-b][Alias]]
                """.trimIndent(),
                "b.org" to """
                    #+title: Note B
                    :PROPERTIES:
                    :ID: note-b
                    :END:
                    Content
                """.trimIndent(),
            ),
        )
        val repository = NoteRepositoryImpl(storage, parser, database.noteDao(), syncScheduler, dispatcher)

        repository.reindex().getOrThrow()

        val backlinks = repository.getBacklinks("b.org").getOrThrow()
        assertEquals(1, backlinks.size)
        assertEquals("a.org", backlinks.first().source)
        assertEquals("Note A", backlinks.first().sourceTitle)

        val note = repository.getNote("a.org").getOrThrow()
        val firstLink = note.links.first()
        assertTrue(firstLink.target is LinkTarget.Id)
        assertEquals("b.org", firstLink.resolvedPath)
    }

    private class FakeStorage(
        private val files: MutableMap<String, String>,
    ) : IStorage {
        override suspend fun listNotes(): Result<List<String>> = Result.success(files.keys.toList())

        override suspend fun readNote(path: String): Result<String> =
            files[path]?.let { Result.success(it) } ?: Result.failure(IOException("missing"))

        override suspend fun writeNote(path: String, content: String): Result<Unit> {
            files[path] = content
            return Result.success(Unit)
        }

        override suspend fun deleteNote(path: String): Result<Unit> {
            files.remove(path)
            return Result.success(Unit)
        }

        override suspend fun invalidateCache() {
            // No-op for fake
        }
    }
}
