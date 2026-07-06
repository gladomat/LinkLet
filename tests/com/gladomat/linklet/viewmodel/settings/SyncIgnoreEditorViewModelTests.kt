package com.gladomat.linklet.viewmodel.settings

import android.content.Context
import androidx.compose.ui.text.input.TextFieldValue
import com.gladomat.linklet.data.storage.IStorage
import com.gladomat.linklet.data.sync.SyncScheduler
import com.gladomat.linklet.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncIgnoreEditorViewModelTests {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private fun viewModel(
        storage: IStorage,
        syncScheduler: SyncScheduler = mockk(relaxed = true),
        context: Context = mockk(relaxed = true),
    ) = SyncIgnoreEditorViewModel(storage, syncScheduler, context)

    @Test
    fun `loads existing syncignore content and treats it as last-saved`() = runTest(dispatcherRule.dispatcher) {
        val storage = mockk<IStorage>()
        coEvery { storage.readFileBytes(".syncignore") } returns Result.success("*.bak\n".toByteArray())

        val viewModel = viewModel(storage)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals("*.bak\n", state.text.text)
        assertEquals("*.bak\n", state.lastSavedText)
        assertFalse(state.isDirty)
    }

    @Test
    fun `edits mark state dirty until reverted`() = runTest(dispatcherRule.dispatcher) {
        val storage = mockk<IStorage>()
        coEvery { storage.readFileBytes(".syncignore") } returns Result.success("*.bak\n".toByteArray())

        val viewModel = viewModel(storage)
        advanceUntilIdle()

        viewModel.onTextChange(TextFieldValue("*.bak\n*.tmp\n"))
        assertTrue(viewModel.state.value.isDirty)

        viewModel.revert()
        assertEquals("*.bak\n", viewModel.state.value.text.text)
        assertFalse(viewModel.state.value.isDirty)
    }

    @Test
    fun `requestSave computes impact preview without writing`() = runTest(dispatcherRule.dispatcher) {
        val storage = mockk<IStorage>()
        coEvery { storage.readFileBytes(".syncignore") } returns Result.success("".toByteArray())
        coEvery { storage.listFiles() } returns Result.success(listOf("notes/a.org", "notes/a.bak"))

        val viewModel = viewModel(storage)
        advanceUntilIdle()

        viewModel.onTextChange(TextFieldValue("*.bak\n"))
        viewModel.requestSave()
        advanceUntilIdle()

        val preview = viewModel.state.value.impactPreview
        checkNotNull(preview)
        assertEquals(listOf("notes/a.bak"), preview.newlyExcluded)
        assertTrue(preview.newlyIncluded.isEmpty())
        assertFalse(preview.isCatastrophic)
    }

    @Test
    fun `catastrophic pattern is flagged even with few tracked files`() = runTest(dispatcherRule.dispatcher) {
        val storage = mockk<IStorage>()
        coEvery { storage.readFileBytes(".syncignore") } returns Result.success("".toByteArray())
        coEvery { storage.listFiles() } returns Result.success(listOf("notes/a.org"))

        val viewModel = viewModel(storage)
        advanceUntilIdle()

        viewModel.onTextChange(TextFieldValue("**\n"))
        viewModel.requestSave()
        advanceUntilIdle()

        assertTrue(checkNotNull(viewModel.state.value.impactPreview).isCatastrophic)
    }

    @Test
    fun `confirmSave writes bytes, schedules sync, and clears dirty state`() = runTest(dispatcherRule.dispatcher) {
        val storage = mockk<IStorage>()
        val syncScheduler = mockk<SyncScheduler>(relaxed = true)
        coEvery { storage.readFileBytes(".syncignore") } returns Result.success("".toByteArray())
        coEvery { storage.listFiles() } returns Result.success(emptyList())
        coEvery { storage.writeFileBytes(".syncignore", any()) } returns Result.success(Unit)

        val viewModel = viewModel(storage, syncScheduler)
        advanceUntilIdle()

        viewModel.onTextChange(TextFieldValue("*.bak\n"))
        viewModel.requestSave()
        advanceUntilIdle()
        viewModel.confirmSave()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isDirty)
        assertEquals("*.bak\n", state.lastSavedText)
        assertEquals("Saved · sync scheduled", state.message)
        verify(exactly = 1) { syncScheduler.scheduleManual() }
    }

    @Test
    fun `dropped lines are reported after save`() = runTest(dispatcherRule.dispatcher) {
        val storage = mockk<IStorage>()
        coEvery { storage.readFileBytes(".syncignore") } returns Result.success("".toByteArray())
        coEvery { storage.listFiles() } returns Result.success(emptyList())
        coEvery { storage.writeFileBytes(".syncignore", any()) } returns Result.success(Unit)

        val viewModel = viewModel(storage)
        advanceUntilIdle()

        viewModel.onTextChange(TextFieldValue("*.bak\n/\n"))
        viewModel.requestSave()
        advanceUntilIdle()
        viewModel.confirmSave()
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.droppedLines.size)
    }

    @Test
    fun `seeds from bundled asset when no syncignore exists yet`() = runTest(dispatcherRule.dispatcher) {
        val storage = mockk<IStorage>()
        coEvery { storage.readFileBytes(".syncignore") } returns
            Result.failure(com.gladomat.linklet.data.storage.NoteNotFoundException(".syncignore"))

        val assets = mockk<android.content.res.AssetManager>()
        every { assets.open("syncignore-default.txt") } returns "*.tmp\n".byteInputStream()
        val context = mockk<Context>()
        every { context.assets } returns assets

        val viewModel = viewModel(storage, context = context)
        advanceUntilIdle()

        assertEquals("*.tmp\n", viewModel.state.value.text.text)
        assertEquals("*.tmp\n", viewModel.state.value.lastSavedText)
    }
}
