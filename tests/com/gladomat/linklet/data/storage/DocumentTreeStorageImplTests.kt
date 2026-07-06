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

    /**
     * Regression guard for the `.syncignore` in-app editor (see `SyncIgnoreEditorViewModel`):
     * `writeFileBytes` must preserve a dot-prefixed filename with no extension exactly, both on
     * first write and on overwrite. This only exercises the plain-filesystem `DocumentFile`
     * branch (`fromFile`, used here and by `FileStorageImpl`) — `createFile()` on that branch
     * ignores the mimeType entirely (`RawDocumentFile` just does `File.createNewFile()`), so it
     * cannot reproduce the real risk this guards against: a real SAF tree-provider's
     * `DocumentsContract.createDocument()` may append an extension derived from the mimeType if
     * the display name doesn't already end with it. `writeFileBytes` picks
     * `"application/octet-stream"`, which `MimeTypeMap` has no registered extension for — the
     * same choice already relied on in production for arbitrary downloaded attachments
     * (`SyncEngine.kt`) — so no known Android `DocumentsProvider` has an extension to append.
     * That assumption is still UNVERIFIED against a real on-device SAF tree URI (no device/adb
     * available in this environment); confirm once on a physical device before relying on it
     * further: write `.syncignore` via the app, then `adb shell run-as com.gladomat.linklet ls
     * -la <vault-tree-path>` (or inspect via the Nextcloud/Files app) and confirm the name is
     * exactly `.syncignore`, not `.syncignore.bin` or similar.
     */
    @Test
    fun `writeFileBytes preserves a dot-prefixed extensionless filename exactly`() = runTest(dispatcher) {
        val root = tempDir.newFolder("vault")
        folderSettingsRepository.setFolderUri(Uri.fromFile(root))
        val storage = DocumentTreeStorageImpl(context, folderSettingsRepository, dispatcher)

        val bytes = "*.bak\n".toByteArray()
        storage.writeFileBytes(".syncignore", bytes).getOrThrow()

        val written = File(root, ".syncignore")
        assertTrue(written.exists())
        assertEquals(".syncignore", written.name)
        assertEquals("*.bak\n", written.readText())

        // Overwrite must reuse the same file, not create a second one alongside it.
        storage.writeFileBytes(".syncignore", "*.tmp\n".toByteArray()).getOrThrow()
        assertEquals(listOf(".syncignore"), root.listFiles()?.map { it.name })
        assertEquals("*.tmp\n", File(root, ".syncignore").readText())
    }
}
