package com.gladomat.linklet.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrgInlineImageDetectorTests {

    @Test
    fun `detectInlineImageCandidate matches file link without description`() {
        val candidate = detectInlineImageCandidate("[[file:img/cat.png]]")
        assertEquals("file", candidate?.scheme)
        assertEquals("img/cat.png", candidate?.target)
    }

    @Test
    fun `detectInlineImageCandidate ignores file link with description`() {
        val candidate = detectInlineImageCandidate("[[file:img/cat.png][caption]]")
        assertNull(candidate)
    }

    @Test
    fun `detectInlineImageCandidate matches attachment link`() {
        val candidate = detectInlineImageCandidate("[[attachment:clipboard-20251128T115556.png]]")
        assertEquals("attachment", candidate?.scheme)
        assertEquals("clipboard-20251128T115556.png", candidate?.target)
    }

    @Test
    fun `detectInlineImageCandidate supports CAPTION and ATTR lines`() {
        val text = """
            #+CAPTION: A cat
            #+ATTR_ORG: :align center
            [[./img/cat.jpg]]
        """.trimIndent()
        val candidate = detectInlineImageCandidate(text)
        assertEquals("A cat", candidate?.caption)
        assertEquals("center", candidate?.attrs?.get("align"))
        assertEquals("./img/cat.jpg", candidate?.target)
    }

    @Test
    fun `detectInlineImageCandidate matches standalone path line`() {
        val candidate = detectInlineImageCandidate("./img/cat.webp")
        assertEquals("file", candidate?.scheme)
        assertEquals("./img/cat.webp", candidate?.target)
    }
}

