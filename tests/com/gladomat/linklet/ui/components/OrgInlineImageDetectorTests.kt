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
    fun `detectInlineImageCandidate accepts file link with description for images`() {
        val candidate = detectInlineImageCandidate("[[file:img/cat.png][caption]]")
        assertEquals("file", candidate?.scheme)
        assertEquals("img/cat.png", candidate?.target)
        assertEquals("caption", candidate?.caption)
    }

    @Test
    fun `detectInlineImageCandidate accepts described image link and uses bracket caption when no CAPTION`() {
        val candidate = detectInlineImageCandidate("[[file:img/cat.png][A cat]]")
        assertEquals("file", candidate?.scheme)
        assertEquals("img/cat.png", candidate?.target)
        assertEquals("A cat", candidate?.caption)
    }

    @Test
    fun `detectInlineImageCandidate prefers CAPTION over bracket caption`() {
        val text = """
            #+CAPTION: Prefer me
            [[file:img/cat.png][Ignore me]]
        """.trimIndent()
        val candidate = detectInlineImageCandidate(text)
        assertEquals("Prefer me", candidate?.caption)
    }

    @Test
    fun `detectInlineImageCandidate does not treat described non-image link as inline image`() {
        val candidate = detectInlineImageCandidate("[[file:note.org][A note]]")
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
    fun `detectInlineImageCandidate preserves width attr in dp`() {
        val text = """
            #+ATTR_ORG: :width 200
            [[file:img/cat.png]]
        """.trimIndent()
        val candidate = detectInlineImageCandidate(text)
        assertEquals("200", candidate?.attrs?.get("width"))
    }

    @Test
    fun `detectInlineImageCandidate preserves width attr in percent`() {
        val text = """
            #+ATTR_ORG: :width 80%
            [[file:img/cat.png]]
        """.trimIndent()
        val candidate = detectInlineImageCandidate(text)
        assertEquals("80%", candidate?.attrs?.get("width"))
    }

    @Test
    fun `detectInlineImageCandidate accepts mixed-case extensions`() {
        val candidate = detectInlineImageCandidate("[[file:img/CAT.PNG]]")
        assertEquals("file", candidate?.scheme)
        assertEquals("img/CAT.PNG", candidate?.target)
    }

    @Test
    fun `detectInlineImageCandidate rejects image links with query fragments`() {
        val candidate = detectInlineImageCandidate("[[file:img/cat.jpg?raw=1]]")
        assertNull(candidate)
    }

    @Test
    fun `detectInlineImageCandidate supports CAPTION NAME and ATTR_HTML lines`() {
        val text = """
            #+CAPTION: A cat
            #+NAME: CatImage
            #+ATTR_HTML: :align center
            [[file:img/cat.jpg]]
        """.trimIndent()
        val candidate = detectInlineImageCandidate(text)
        assertEquals("A cat", candidate?.caption)
        assertEquals("CatImage", candidate?.name)
        assertEquals("center", candidate?.attrs?.get("align"))
        assertEquals("img/cat.jpg", candidate?.target)
    }

    @Test
    fun `detectInlineImageCandidate matches standalone path line`() {
        val candidate = detectInlineImageCandidate("./img/cat.webp")
        assertEquals("file", candidate?.scheme)
        assertEquals("./img/cat.webp", candidate?.target)
    }
}
