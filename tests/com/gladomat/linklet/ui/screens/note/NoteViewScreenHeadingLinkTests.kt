package com.gladomat.linklet.ui.screens.note

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
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

        val fullText = "Heading with alias link"
        val headingNode = composeRule.onNodeWithText(fullText, substring = false)
        headingNode.assertExists()

        // ClickableText has no clickable-semantics action - it resolves taps to a character
        // offset itself via detectTapGestures, so hitting the "alias" annotation requires
        // tapping the actual pixel position of that substring, not a guessed fraction of the
        // node's width (font metrics/padding make that unreliable - a prior attempt at a fixed
        // 65% guess still missed). Query the real TextLayoutResult via the semantics action
        // Compose Text exposes for exactly this, and click the precise character position.
        val layoutResults = mutableListOf<TextLayoutResult>()
        val getLayoutAction = headingNode.fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)
        checkNotNull(getLayoutAction) { "Heading ClickableText did not expose a TextLayoutResult" }
        check(getLayoutAction.action?.invoke(layoutResults) == true) { "Failed to fetch TextLayoutResult" }
        val layoutResult = layoutResults.first()

        val aliasStart = fullText.indexOf("alias")
        check(aliasStart >= 0) { "\"alias\" not found in rendered heading text" }
        val aliasMiddleCharIndex = aliasStart + "alias".length / 2
        val targetRect = layoutResult.getBoundingBox(aliasMiddleCharIndex)

        headingNode.performTouchInput {
            click(targetRect.center)
        }
        composeRule.waitForIdle()

        assertEquals("notes/target-note.org", openedLink)
    }
}
