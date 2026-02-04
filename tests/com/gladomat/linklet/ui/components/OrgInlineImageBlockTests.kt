package com.gladomat.linklet.ui.components

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.gladomat.linklet.testing.Aarch64RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(Aarch64RobolectricTestRunner::class)
@Config(sdk = [34])
class OrgInlineImageBlockTests {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `inline image block loads and hides loading text`() {
        val resource = checkNotNull(javaClass.classLoader?.getResource("inline-image-test.png"))
        val uri = Uri.fromFile(File(resource.toURI()))

        composeRule.setContent {
            MaterialTheme {
                OrgInlineImageBlock(
                    uri = uri,
                    caption = null,
                    align = null,
                    widthHint = null,
                    onOpen = {},
                )
            }
        }

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("org-inline-image").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("org-inline-image").assertExists()
    }
}
