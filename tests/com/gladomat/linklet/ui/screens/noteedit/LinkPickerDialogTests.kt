package com.gladomat.linklet.ui.screens.noteedit

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.gladomat.linklet.data.model.NoteId
import com.gladomat.linklet.data.model.NoteIndexEntry
import com.gladomat.linklet.testing.Aarch64RobolectricTestRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(Aarch64RobolectricTestRunner::class)
@Config(sdk = [34])
class LinkPickerDialogTests {
    @get:Rule
    val composeRule = createComposeRule()

    private val alpha = NoteIndexEntry(
        id = NoteId("notes/alpha.org"),
        title = "Alpha Project",
        fileTags = emptyList(),
        deletedAt = null,
        linksReady = true,
        orgId = "ORG-1",
    )
    private val beta = NoteIndexEntry(
        id = NoteId("notes/beta.org"),
        title = "Beta Notes",
        fileTags = emptyList(),
        deletedAt = null,
        linksReady = true,
    )

    @Test
    fun `typing a query reports it via onQueryChange`() {
        var reportedQuery: String? = null

        composeRule.setContent {
            MaterialTheme {
                LinkPickerDialog(
                    query = "",
                    results = listOf(alpha, beta),
                    onQueryChange = { reportedQuery = it },
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Search notes").performTextInput("alpha")

        assertEquals("alpha", reportedQuery)
    }

    @Test
    fun `tapping a result selects that note`() {
        var selected: NoteIndexEntry? = null

        composeRule.setContent {
            MaterialTheme {
                LinkPickerDialog(
                    query = "",
                    results = listOf(alpha, beta),
                    onQueryChange = {},
                    onSelect = { selected = it },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Beta Notes").performClick()

        assertEquals(beta, selected)
    }

    @Test
    fun `tapping cancel dismisses without selecting`() {
        var dismissed = false
        var selected: NoteIndexEntry? = null

        composeRule.setContent {
            MaterialTheme {
                LinkPickerDialog(
                    query = "",
                    results = listOf(alpha),
                    onQueryChange = {},
                    onSelect = { selected = it },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeRule.onNodeWithText("Cancel").performClick()

        assertTrue(dismissed)
        assertEquals(null, selected)
    }

    @Test
    fun `empty query with no results shows a search hint`() {
        composeRule.setContent {
            MaterialTheme {
                LinkPickerDialog(
                    query = "",
                    results = emptyList(),
                    onQueryChange = {},
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Start typing to search notes").assertExists()
    }

    @Test
    fun `non-empty query with no results shows a no-match message`() {
        composeRule.setContent {
            MaterialTheme {
                LinkPickerDialog(
                    query = "zzz",
                    results = emptyList(),
                    onQueryChange = {},
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("No matching notes").assertExists()
    }
}
