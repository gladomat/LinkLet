package com.gladomat.linklet.data.storage

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.gladomat.linklet.data.settings.FolderSettingsRepository
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import com.gladomat.linklet.testing.Aarch64RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Aarch64RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DocumentTreeStorageImplTests {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var folderSettingsRepository: FolderSettingsRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        folderSettingsRepository = FolderSettingsRepository(context)
    }

    @After
    fun tearDown() = runTest {
        folderSettingsRepository.clearFolderUri()
        tempDir.delete()
    }

    @Test
    fun `listNotes returns only org files`() = runTest(dispatcher) {
        val root = tempDir.newFolder("notes")
        File(root, "first.org").writeText("content")
        File(root, "second.txt").writeText("ignore")

        folderSettingsRepository.setFolderUri(Uri.fromFile(root))
        val storage = DocumentTreeStorageImpl(context, folderSettingsRepository, dispatcher)

        val result = storage.listNotes().getOrThrow()

        assertEquals(listOf("first.org"), result)
    }

    @Test
    fun `writeNote persists content and readNote retrieves it`() = runTest(dispatcher) {
        val root = tempDir.newFolder("notes")
        folderSettingsRepository.setFolderUri(Uri.fromFile(root))
        val storage = DocumentTreeStorageImpl(context, folderSettingsRepository, dispatcher)

        val path = "sub/entry.org"
        val content = "#+title: Sample\nBody"

        storage.writeNote(path, content).getOrThrow()
        val readBack = storage.readNote(path).getOrThrow()

        assertEquals(content, readBack)
        assertTrue(File(root, path).exists())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `readNote fails when note is outside tree`() = runTest(dispatcher) {
        val root = tempDir.newFolder("notes")
        folderSettingsRepository.setFolderUri(Uri.fromFile(root))
        val storage = DocumentTreeStorageImpl(context, folderSettingsRepository, dispatcher)

        storage.readNote("../escape.org").getOrThrow()
    }
}
