package com.gladomat.linklet.data.storage

import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class FileStorageImplTests {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()

    @After
    fun tearDown() {
        tempDir.delete()
    }

    @Test
    fun `listNotes returns only org files`() = runTest(dispatcher) {
        val root = tempDir.newFolder("notes")
        File(root, "first.org").writeText("content")
        File(root, "second.txt").writeText("ignore")

        val storage = FileStorageImpl(baseDir = root, dispatcher = dispatcher)

        val result = storage.listNotes().getOrThrow()

        assertEquals(listOf("first.org"), result)
    }

    @Test
    fun `writeNote persists content and readNote retrieves it`() = runTest(dispatcher) {
        val root = tempDir.newFolder("notes")
        val storage = FileStorageImpl(baseDir = root, dispatcher = dispatcher)

        val path = "sub/entry.org"
        val content = "#+title: Sample\nBody"

        storage.writeNote(path, content).getOrThrow()
        val readBack = storage.readNote(path).getOrThrow()

        assertEquals(content, readBack)
        assertTrue(File(root, path).exists())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `readNote prevents path traversal`() = runTest(dispatcher) {
        val root = tempDir.newFolder("notes")
        val storage = FileStorageImpl(baseDir = root, dispatcher = dispatcher)

        storage.readNote("../escape.org").getOrThrow()
    }
}
