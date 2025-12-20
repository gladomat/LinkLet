package com.gladomat.linklet.ui.screens.note

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.junit4.createComposeRule
import com.gladomat.linklet.data.model.Note
import com.gladomat.linklet.data.model.NoteId
import com.gladomat.linklet.viewmodel.note.NoteViewUiState
import com.gladomat.linklet.viewmodel.note.NoteViewViewModel
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteViewScreenImageTests {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `tap inline image opens and closes fullscreen viewer`() {
        val resource = checkNotNull(javaClass.classLoader?.getResource("inline-image-test.png"))
        val uri = Uri.fromFile(File(resource.toURI()))

        val note = Note(
            id = NoteId("notes/inline-image.org"),
            title = "Inline Image",
            content = """
                * Images
                #+CAPTION: Inline image
                [[file:inline-image-test.png]]
            """.trimIndent(),
            links = emptyList(),
        )

        val state = NoteViewUiState.Success(
            note = note,
            backlinks = emptyList(),
            lastModified = null,
            isFavorite = false,
        )

        composeRule.setContent {
            MaterialTheme {
                NoteViewScreen(
                    state = state,
                    searchState = NoteViewViewModel.NoteSearchState(),
                    onOpenLink = {},
                    onOpenExternalLink = {},
                    resolveStorageUri = { Result.success(uri) },
                    onEditNote = {},
                    onNavigateHome = {},
                    onBack = {},
                    onShare = {},
                    onFavorite = {},
                    onCreateNote = {},
                    onRetry = {},
                    onDelete = {},
                    onDuplicate = {},
                    onRename = {},
                    onOpenSearch = {},
                    onCloseSearch = {},
                    onSearchQueryChange = {},
                    onClearSearch = {},
                    onSearchOptionsChange = {},
                    onPrevMatch = {},
                    onNextMatch = {},
                    onCopyToClipboard = {},
                    onCopyIdLink = {},
                    onCopyFileLink = {},
                    onUpdateProperties = {},
                    onUpdateTags = {},
                    allTags = emptyList(),
                )
            }
        }

        composeRule.onNodeWithText("Loading image…").assertExists()
        composeRule.waitForIdle()
        Thread.sleep(750)
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithText("Loading image…").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithText("Inline image").assertExists()
        composeRule.onNodeWithTag("org-inline-image").assertExists()
        composeRule.onNodeWithTag("org-inline-image").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("fullscreen-image-viewer").assertExists()

        composeRule.onNodeWithTag("fullscreen-image-viewer").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithTag("fullscreen-image-viewer").fetchSemanticsNodes().isEmpty())
    }
}
