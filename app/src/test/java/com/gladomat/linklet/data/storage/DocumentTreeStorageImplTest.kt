package com.gladomat.linklet.data.storage

import android.content.Context
import android.net.Uri
import com.gladomat.linklet.data.settings.FolderSettingsRepository
import io.mockk.coEvery
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import com.gladomat.linklet.testing.Aarch64RobolectricTestRunner
import androidx.test.core.app.ApplicationProvider

@RunWith(Aarch64RobolectricTestRunner::class)
class DocumentTreeStorageImplTest {

    @Test
    fun `listNotes and readNote work with file Uri`() {
        val tempDir = Files.createTempDirectory("doc-tree-storage-test").toFile()
        File(tempDir, "roam_notes/one.org").apply {
            parentFile?.mkdirs()
            writeText("#+title: One\nbody")
        }
        File(tempDir, "roam_notes/two.txt").apply {
            parentFile?.mkdirs()
            writeText("ignore")
        }
        File(tempDir, "roam_notes/sub/three.org").apply {
            parentFile?.mkdirs()
            writeText("#+title: Three\nbody")
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val folderSettingsRepository = mockk<FolderSettingsRepository>()
        coEvery { folderSettingsRepository.currentFolderUri() } returns Uri.fromFile(tempDir)

        val storage = DocumentTreeStorageImpl(
            context = context,
            folderSettingsRepository = folderSettingsRepository,
        )

        runBlocking {
            val notes = storage.listNotes().getOrThrow()
            assertTrue(notes.contains("roam_notes/one.org"))
            assertTrue(notes.contains("roam_notes/sub/three.org"))
            assertTrue(notes.none { it.endsWith(".txt") })

            // Proves the URI cache is used: after listing, reading should succeed even if
            // folder URI becomes unavailable (would otherwise throw "Folder not selected").
            coEvery { folderSettingsRepository.currentFolderUri() } returns null
            val content = storage.readNote("roam_notes/sub/three.org").getOrThrow()
            assertEquals("#+title: Three\nbody", content)

            storage.invalidateCache()
            coEvery { folderSettingsRepository.currentFolderUri() } returns Uri.fromFile(tempDir)
            val contentAfterInvalidate = storage.readNote("roam_notes/one.org").getOrThrow()
            assertEquals("#+title: One\nbody", contentAfterInvalidate)
        }
    }
}
