package com.gladomat.linklet.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
class OrgInlineImageBlockWidthTests {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `width hint percent constrains image width`() {
        val resource = checkNotNull(javaClass.classLoader?.getResource("inline-image-test.png"))
        val uri = Uri.fromFile(File(resource.toURI()))

        composeRule.setContent {
            Box(modifier = Modifier.width(200.dp)) {
                OrgInlineImageBlock(
                    uri = uri,
                    caption = "Caption",
                    align = null,
                    widthHint = "50%",
                    onOpen = {},
                )
            }
        }

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("org-inline-image").fetchSemanticsNodes().isNotEmpty()
        }

        val rect = composeRule.onNodeWithTag("org-inline-image").assertIsDisplayed().getUnclippedBoundsInRoot()
        val expectedWidthDp = 100f
        val actualWidthDp = (rect.right - rect.left).value
        assertTrue("Expected width ~${expectedWidthDp}dp, got ${actualWidthDp}dp", abs(actualWidthDp - expectedWidthDp) <= 1.0f)
    }

    @Test
    fun `width hint dp constrains image width`() {
        val resource = checkNotNull(javaClass.classLoader?.getResource("inline-image-test.png"))
        val uri = Uri.fromFile(File(resource.toURI()))

        composeRule.setContent {
            Box(modifier = Modifier.width(200.dp)) {
                OrgInlineImageBlock(
                    uri = uri,
                    caption = "Caption",
                    align = null,
                    widthHint = "120",
                    onOpen = {},
                )
            }
        }

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("org-inline-image").fetchSemanticsNodes().isNotEmpty()
        }

        val rect = composeRule.onNodeWithTag("org-inline-image").assertIsDisplayed().getUnclippedBoundsInRoot()
        val expectedWidthDp = 120f
        val actualWidthDp = (rect.right - rect.left).value
        assertTrue("Expected width ~${expectedWidthDp}dp, got ${actualWidthDp}dp", abs(actualWidthDp - expectedWidthDp) <= 1.0f)
    }

    @Test
    fun `invalid width hint falls back to full width`() {
        val resource = checkNotNull(javaClass.classLoader?.getResource("inline-image-test.png"))
        val uri = Uri.fromFile(File(resource.toURI()))

        composeRule.setContent {
            Box(modifier = Modifier.width(200.dp)) {
                OrgInlineImageBlock(
                    uri = uri,
                    caption = "Caption",
                    align = null,
                    widthHint = "0%",
                    onOpen = {},
                )
            }
        }

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("org-inline-image").fetchSemanticsNodes().isNotEmpty()
        }

        val rect = composeRule.onNodeWithTag("org-inline-image").assertIsDisplayed().getUnclippedBoundsInRoot()
        val expectedWidthDp = 200f
        val actualWidthDp = (rect.right - rect.left).value
        assertTrue("Expected width ~${expectedWidthDp}dp, got ${actualWidthDp}dp", abs(actualWidthDp - expectedWidthDp) <= 1.0f)
    }
}
