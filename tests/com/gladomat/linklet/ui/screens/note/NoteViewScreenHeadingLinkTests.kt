package com.gladomat.linklet.ui.screens.note

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gladomat.linklet.data.model.LinkTarget
import com.gladomat.linklet.data.model.Note
import com.gladomat.linklet.data.model.NoteId
import com.gladomat.linklet.data.model.NoteLink
import com.gladomat.linklet.data.parser.org.parseOrgDocument
import com.gladomat.linklet.testing.Aarch64RobolectricTestRunner
import com.gladomat.linklet.viewmodel.note.NoteViewUiState
import com.gladomat.linklet.viewmodel.note.NoteViewViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(Aarch64RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteViewScreenHeadingLinkTests {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `tap link inside heading opens note instead of raw markup`() {
        val note = Note(
            id = NoteId("notes/heading-link.org"),
            title = "Heading Link",
            content = """
                * Heading with [[id:target-note][alias]] link
                Body text.
            """.trimIndent(),
            links = listOf(
                NoteLink(
                    fromId = NoteId("notes/heading-link.org"),
                    target = LinkTarget.Id("target-note"),
                    label = "alias",
                    resolvedPath = "notes/target-note.org",
                ),
            ),
        )

        val state = NoteViewUiState.Success(
            note = note,
            document = parseOrgDocument(note.content),
            backlinks = emptyList(),
            lastModified = null,
            isFavorite = false,
        )

        var openedLink: String? = null

        composeRule.setContent {
            MaterialTheme {
                NoteViewScreen(
                    state = state,
                    searchState = NoteViewViewModel.NoteSearchState(),
                    onOpenLink = { openedLink = it },
                    onOpenExternalLink = {},
                    resolveStorageUri = { Result.failure(IllegalStateException()) },
                    onEditNote = {},
                    onNavigateHome = {},
                    onBack = {},
                    onShare = {},
                    onFavorite = {},
                    onCreateNote = {},
                    onRetry = {},
                    onDownload = {},
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

        composeRule.onNodeWithText("Heading with alias link", substring = false).assertExists()
        composeRule.onNodeWithText("Heading with alias link").performClick()
        composeRule.waitForIdle()

        assertEquals("notes/target-note.org", openedLink)
    }
}
