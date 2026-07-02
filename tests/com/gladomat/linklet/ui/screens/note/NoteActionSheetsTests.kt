package com.gladomat.linklet.ui.screens.note

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gladomat.linklet.testing.Aarch64RobolectricTestRunner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Compose-UI coverage for the note action / export bottom sheets (features VIEW-05 share,
 * VIEW-14 expand/collapse). Verifies the button -> callback wiring, including that selecting an
 * action also dismisses the sheet. Runs under Robolectric (skipped on arm64 hosts).
 */
@RunWith(Aarch64RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteActionSheetsTests {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `export sheet share invokes onShare and dismisses`() {
        var shared = false
        var copied = false
        var dismissed = false

        composeRule.setContent {
            MaterialTheme {
                ExportBottomSheet(
                    onDismiss = { dismissed = true },
                    onShare = { shared = true },
                    onCopyToClipboard = { copied = true },
                )
            }
        }

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Share").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Share").performClick()
        composeRule.waitForIdle()

        assertTrue("onShare should fire", shared)
        assertTrue("sheet should dismiss after share", dismissed)
        assertFalse("copy must not fire", copied)
    }

    @Test
    fun `more actions expand all invokes onExpandAll and dismisses`() {
        var expanded = false
        var collapsed = false
        var dismissed = false

        composeRule.setContent {
            MaterialTheme {
                MoreActionsBottomSheet(
                    onDismiss = { dismissed = true },
                    onDuplicate = {},
                    onRename = {},
                    onExport = {},
                    onDelete = {},
                    onProperties = {},
                    onTags = {},
                    onExpandAll = { expanded = true },
                    onCollapseAll = { collapsed = true },
                    onCopyIdLink = {},
                    onCopyFileLink = {},
                )
            }
        }

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Expand All").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Expand All").performClick()
        composeRule.waitForIdle()

        assertTrue("onExpandAll should fire", expanded)
        assertTrue("sheet should dismiss after expand", dismissed)
        assertFalse("collapse must not fire", collapsed)
    }

    @Test
    fun `more actions collapse all invokes onCollapseAll and dismisses`() {
        var expanded = false
        var collapsed = false
        var dismissed = false

        composeRule.setContent {
            MaterialTheme {
                MoreActionsBottomSheet(
                    onDismiss = { dismissed = true },
                    onDuplicate = {},
                    onRename = {},
                    onExport = {},
                    onDelete = {},
                    onProperties = {},
                    onTags = {},
                    onExpandAll = { expanded = true },
                    onCollapseAll = { collapsed = true },
                    onCopyIdLink = {},
                    onCopyFileLink = {},
                )
            }
        }

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Collapse All").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Collapse All").performClick()
        composeRule.waitForIdle()

        assertTrue("onCollapseAll should fire", collapsed)
        assertTrue("sheet should dismiss after collapse", dismissed)
        assertFalse("expand must not fire", expanded)
    }
}
