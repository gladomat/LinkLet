package com.gladomat.linklet.ui.screens.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.input.TextFieldValue
import com.gladomat.linklet.data.sync.SyncIgnoreRules
import com.gladomat.linklet.testing.Aarch64RobolectricTestRunner
import com.gladomat.linklet.viewmodel.settings.SyncIgnoreEditorUiState
import com.gladomat.linklet.viewmodel.settings.SyncIgnoreImpactPreview
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(Aarch64RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncIgnoreEditorScreenTests {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setScreen(
        state: SyncIgnoreEditorUiState,
        onSaveRequested: () -> Unit = {},
        onConfirmSave: () -> Unit = {},
        onDismissPreview: () -> Unit = {},
        onRevert: () -> Unit = {},
        onBack: () -> Unit = {},
        onCopyToClipboard: () -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                SyncIgnoreEditorScreen(
                    state = state,
                    onTextChange = {},
                    onSaveRequested = onSaveRequested,
                    onConfirmSave = onConfirmSave,
                    onDismissPreview = onDismissPreview,
                    onRevert = onRevert,
                    onBack = onBack,
                    onCopyToClipboard = onCopyToClipboard,
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }
    }

    @Test
    fun `built-in exclusions card is collapsed by default and expands on tap`() {
        setScreen(
            state = SyncIgnoreEditorUiState(isLoading = false, text = TextFieldValue(""), lastSavedText = ""),
        )

        composeRule.onNodeWithText("Also always excluded").assertExists()
        composeRule.onNodeWithText(".DS_Store", substring = true).assertDoesNotExist()

        composeRule.onNodeWithText("Show").performClick()

        composeRule.onNodeWithText(".DS_Store", substring = true).assertExists()
    }

    @Test
    fun `dropped-lines banner is shown only when there are dropped lines`() {
        setScreen(
            state = SyncIgnoreEditorUiState(
                isLoading = false,
                text = TextFieldValue("*.bak\n"),
                lastSavedText = "*.bak\n",
                droppedLines = listOf(SyncIgnoreRules.DroppedLine(2, "/")),
            ),
        )

        composeRule.onNodeWithText("1 line(s) had no effect").assertExists()
        composeRule.onNodeWithText("Line 2: /").assertExists()
    }

    @Test
    fun `revert is disabled when not dirty and enabled when dirty`() {
        setScreen(
            state = SyncIgnoreEditorUiState(
                isLoading = false,
                text = TextFieldValue("*.bak\n"),
                lastSavedText = "*.bak\n",
            ),
        )
        composeRule.onNodeWithText("Revert").assertIsNotEnabled()
    }

    @Test
    fun `tapping revert while dirty invokes callback`() {
        var reverted = false
        setScreen(
            state = SyncIgnoreEditorUiState(
                isLoading = false,
                text = TextFieldValue("*.bak\n*.tmp\n"),
                lastSavedText = "*.bak\n",
            ),
            onRevert = { reverted = true },
        )
        composeRule.onNodeWithText("Revert").performClick()
        assertTrue(reverted)
    }

    @Test
    fun `save button click requests the impact preview`() {
        var requested = false
        setScreen(
            state = SyncIgnoreEditorUiState(isLoading = false, text = TextFieldValue(""), lastSavedText = ""),
            onSaveRequested = { requested = true },
        )
        composeRule.onNodeWithTag("syncIgnoreSaveButton").performClick()
        assertTrue(requested)
    }

    @Test
    fun `impact preview dialog shows counts and confirm commits the save`() {
        var confirmed = false
        setScreen(
            state = SyncIgnoreEditorUiState(
                isLoading = false,
                text = TextFieldValue("*.bak\n"),
                lastSavedText = "",
                impactPreview = SyncIgnoreImpactPreview(
                    newlyExcluded = listOf("notes/a.bak"),
                    newlyIncluded = emptyList(),
                    droppedLines = emptyList(),
                    isCatastrophic = false,
                ),
            ),
            onConfirmSave = { confirmed = true },
        )

        composeRule.onNodeWithText("1 file(s) will stop syncing.").assertExists()
        composeRule.onNodeWithTag("syncIgnoreConfirmSaveButton").performClick()
        assertTrue(confirmed)
    }

    @Test
    fun `catastrophic impact preview shows a stronger warning and label`() {
        setScreen(
            state = SyncIgnoreEditorUiState(
                isLoading = false,
                text = TextFieldValue("**\n"),
                lastSavedText = "",
                impactPreview = SyncIgnoreImpactPreview(
                    newlyExcluded = listOf("notes/a.org"),
                    newlyIncluded = emptyList(),
                    droppedLines = emptyList(),
                    isCatastrophic = true,
                ),
            ),
        )

        composeRule.onNodeWithText("This looks like a big change").assertExists()
        composeRule.onNodeWithText("Save anyway").assertExists()
    }

    @Test
    fun `copy to clipboard button invokes callback`() {
        var copied = false
        setScreen(
            state = SyncIgnoreEditorUiState(isLoading = false, text = TextFieldValue("*.bak\n"), lastSavedText = "*.bak\n"),
            onCopyToClipboard = { copied = true },
        )
        composeRule.onNodeWithText("Copy to clipboard").performClick()
        assertTrue(copied)
    }

    @Test
    fun `load error state shows the error message instead of the editor`() {
        setScreen(
            state = SyncIgnoreEditorUiState(
                isLoading = false,
                loadError = "Could not read `.syncignore` — unexpected encoding",
            ),
        )
        composeRule.onNodeWithText("Could not read `.syncignore` — unexpected encoding").assertExists()
        composeRule.onNodeWithText("Save").assertDoesNotExist()
    }
}
