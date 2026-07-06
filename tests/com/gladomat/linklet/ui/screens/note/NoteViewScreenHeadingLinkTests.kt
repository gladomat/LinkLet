package com.gladomat.linklet.ui.screens.note

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
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
                    onOpenGraph = {},
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

        val headingNode = composeRule.onNodeWithText("Heading with alias link", substring = false)
        headingNode.assertExists()

        // ClickableText has no clickable-semantics action - it resolves taps to a character
        // offset via detectTapGestures, so a plain performClick() (which falls back to tapping
        // the node's geometric center) lands wherever the *middle of the whole heading string*
        // happens to be, not necessarily inside the "alias" annotation. In "Heading with alias
        // link", "alias" sits at roughly 57-78% of the string, past the midpoint - tap there
        // explicitly instead of relying on the center to coincide with it.
        val size = headingNode.fetchSemanticsNode().size
        headingNode.performTouchInput {
            click(Offset(x = size.width * 0.65f, y = size.height / 2f))
        }
        composeRule.waitForIdle()

        assertEquals("notes/target-note.org", openedLink)
    }
}
