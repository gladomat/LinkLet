package com.gladomat.linklet.data.utils

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrgFileUtilsTests {

    // ================================
    // Title Extraction Tests
    // ================================

    @Test
    fun `extractTitle returns title from standard format`() {
        val content = "#+title: Hello World\n\nSome content"
        assertEquals("Hello World", OrgFileUtils.extractTitle(content))
    }

    @Test
    fun `extractTitle is case insensitive`() {
        val content = "#+TITLE: CaSe InSeNsItIvE\n\nContent"
        assertEquals("CaSe InSeNsItIvE", OrgFileUtils.extractTitle(content))
    }

    @Test
    fun `extractTitle handles mixed case keyword`() {
        val content = "#+Title: Mixed Case Keyword\n\n"
        assertEquals("Mixed Case Keyword", OrgFileUtils.extractTitle(content))
    }

    @Test
    fun `extractTitle returns untitled when no title present`() {
        val content = "Some content without title line"
        assertEquals("untitled", OrgFileUtils.extractTitle(content))
    }

    @Test
    fun `extractTitle returns untitled when title is empty`() {
        val content = "#+title: \n\nSome content"
        assertEquals("untitled", OrgFileUtils.extractTitle(content))
    }

    @Test
    fun `extractTitle returns untitled when title has only whitespace`() {
        val content = "#+title:    \n\n"
        assertEquals("untitled", OrgFileUtils.extractTitle(content))
    }

    @Test
    fun `extractTitle trims surrounding whitespace`() {
        val content = "#+title:   Padded Title   \n"
        assertEquals("Padded Title", OrgFileUtils.extractTitle(content))
    }

    @Test
    fun `extractTitle handles title after properties drawer`() {
        val content = """
            :PROPERTIES:
            :ID: abc123
            :END:
            #+title: After Drawer
        """.trimIndent()
        assertEquals("After Drawer", OrgFileUtils.extractTitle(content))
    }

    // ================================
    // Title Truncation Tests
    // ================================

    @Test
    fun `truncateForUi returns unchanged title under limit`() {
        val title = "Short Title"
        assertEquals("Short Title", OrgFileUtils.truncateForUi(title))
    }

    @Test
    fun `truncateForUi returns unchanged title at exactly 60 chars`() {
        val title = "A".repeat(60)
        assertEquals(title, OrgFileUtils.truncateForUi(title))
    }

    @Test
    fun `truncateForUi truncates with ellipsis over 60 chars`() {
        val title = "A".repeat(70)
        val result = OrgFileUtils.truncateForUi(title)
        assertEquals(61, result.length)
        assertEquals("A".repeat(60) + "…", result)
    }

    @Test
    fun `truncateForFilename returns unchanged title under limit`() {
        val title = "Short Title"
        assertEquals("Short Title", OrgFileUtils.truncateForFilename(title))
    }

    @Test
    fun `truncateForFilename truncates without ellipsis`() {
        val title = "A".repeat(70)
        val result = OrgFileUtils.truncateForFilename(title)
        assertEquals(60, result.length)
        assertEquals("A".repeat(60), result)
    }

    // ================================
    // Slug Generation Tests
    // ================================

    @Test
    fun `slugifyTitle converts to lowercase`() {
        assertEquals("hello_world", OrgFileUtils.slugifyTitle("Hello World"))
    }

    @Test
    fun `slugifyTitle replaces spaces with underscores`() {
        assertEquals("multiple_word_title", OrgFileUtils.slugifyTitle("Multiple Word Title"))
    }

    @Test
    fun `slugifyTitle replaces special chars with underscores`() {
        assertEquals("hello_world", OrgFileUtils.slugifyTitle("Hello @#$ World"))
    }

    @Test
    fun `slugifyTitle collapses repeated underscores`() {
        assertEquals("hello_world", OrgFileUtils.slugifyTitle("Hello---World"))
    }

    @Test
    fun `slugifyTitle trims leading and trailing underscores`() {
        assertEquals("title", OrgFileUtils.slugifyTitle("---Title---"))
    }

    @Test
    fun `slugifyTitle handles special characters in title`() {
        assertEquals("note_about_kotlin_java", OrgFileUtils.slugifyTitle("Note about Kotlin & Java!"))
    }

    @Test
    fun `slugifyTitle returns untitled for empty result`() {
        assertEquals("untitled", OrgFileUtils.slugifyTitle("!@#$%^&*()"))
    }

    @Test
    fun `slugifyTitle returns untitled for empty string`() {
        assertEquals("untitled", OrgFileUtils.slugifyTitle(""))
    }

    @Test
    fun `slugifyTitle truncates long titles before slugifying`() {
        val longTitle = "A Very Long Title " + "Word ".repeat(20)
        val result = OrgFileUtils.slugifyTitle(longTitle)
        assertTrue("Slug should not exceed original 60 char limit", result.length <= 60)
    }

    @Test
    fun `slugifyTitle handles unicode characters`() {
        // Unicode gets replaced with underscores, collapsed
        assertEquals("caf_latt", OrgFileUtils.slugifyTitle("Café Latté"))
    }

    // ================================
    // ID Generation Tests
    // ================================

    @Test
    fun `generateNoteId returns dashed UUID in uppercase format`() {
        val id = OrgFileUtils.generateNoteId("Test", LocalDateTime.of(2025, 3, 11, 21, 30, 0))
        assertTrue(
            "ID should match 8-4-4-4-12 uppercase UUID format",
            id.matches(Regex("^[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}$")),
        )
    }

    @Test
    fun `generateNoteId creates unique values across calls`() {
        val id1 = OrgFileUtils.generateNoteId("Same", LocalDateTime.of(2025, 3, 11, 21, 30, 0))
        val id2 = OrgFileUtils.generateNoteId("Same", LocalDateTime.of(2025, 3, 11, 21, 30, 0))
        assertTrue("IDs should differ between calls", id1 != id2)
    }

    // ================================
    // Filename Generation Tests
    // ================================

    @Test
    fun `generateFilename creates correct format`() {
        val timestamp = LocalDateTime.of(2025, 3, 11, 21, 30, 0)
        val result = OrgFileUtils.generateFilename("My Note Title", timestamp)
        assertEquals("20250311213000-my_note_title.org", result)
    }

    @Test
    fun `generateFilename uses untitled for empty title`() {
        val timestamp = LocalDateTime.of(2025, 3, 11, 21, 30, 0)
        val result = OrgFileUtils.generateFilename("", timestamp)
        assertEquals("20250311213000-untitled.org", result)
    }

    @Test
    fun `generateFilename handles special characters in title`() {
        val timestamp = LocalDateTime.of(2025, 3, 11, 21, 30, 0)
        val result = OrgFileUtils.generateFilename("Note: Important! @#$", timestamp)
        assertEquals("20250311213000-note_important.org", result)
    }

    @Test
    fun `generateFilename truncates long titles`() {
        val timestamp = LocalDateTime.of(2025, 3, 11, 21, 30, 0)
        val longTitle = "A".repeat(100)
        val result = OrgFileUtils.generateFilename(longTitle, timestamp)
        // Timestamp (14) + "-" (1) + slug (max 60) + ".org" (4)
        assertTrue("Filename should have reasonable length", result.length <= 79)
    }

    // ================================
    // Properties Drawer Tests
    // ================================

    @Test
    fun `ensureIdInProperties creates drawer when missing`() {
        val content = "#+title: Test\n\nContent here"
        val result = OrgFileUtils.ensureIdInProperties(content, "abc123def456")

        assertTrue("Should contain PROPERTIES drawer", result.contains(":PROPERTIES:"))
        assertTrue("Should contain ID", result.contains(":ID:       abc123def456"))
        assertTrue("Should contain END", result.contains(":END:"))
        assertTrue("Should contain original content", result.contains("#+title: Test"))
    }

    @Test
    fun `ensureIdInProperties inserts ID into existing drawer without ID`() {
        val content = """
:PROPERTIES:
:CUSTOM: value
:END:
#+title: Test
""".trimIndent()

        val result = OrgFileUtils.ensureIdInProperties(content, "abc123def456")

        assertTrue("Should contain ID", result.contains(":ID:       abc123def456"))
        assertTrue("Should preserve CUSTOM property", result.contains(":CUSTOM: value"))
    }

    @Test
    fun `ensureIdInProperties fills empty ID in existing drawer`() {
        val content = """
:PROPERTIES:
:ID:
:END:
#+title: Test
""".trimIndent()

        val result = OrgFileUtils.ensureIdInProperties(content, "abc123def456")

        assertTrue("Should contain filled ID", result.contains(":ID:       abc123def456"))
        assertFalse("Should not have empty ID line", result.contains(":ID:\n"))
    }

    @Test
    fun `ensureIdInProperties preserves existing non-empty ID`() {
        val content = """
:PROPERTIES:
:ID:       existing-id-123
:END:
#+title: Test
""".trimIndent()

        val result = OrgFileUtils.ensureIdInProperties(content, "new-id-456")

        assertTrue("Should preserve existing ID", result.contains("existing-id-123"))
        assertFalse("Should not contain new ID", result.contains("new-id-456"))
    }

    @Test
    fun `ensureIdInProperties preserves other properties in drawer`() {
        val content = """
:PROPERTIES:
:ID:
:CREATED: 2025-03-11
:AUTHOR: Test Author
:END:
#+title: Test
""".trimIndent()

        val result = OrgFileUtils.ensureIdInProperties(content, "abc123")

        assertTrue("Should preserve CREATED", result.contains(":CREATED: 2025-03-11"))
        assertTrue("Should preserve AUTHOR", result.contains(":AUTHOR: Test Author"))
    }

    @Test
    fun `ensureIdInProperties maintains org-mode compliant format`() {
        val content = "#+title: Test\n"
        val result = OrgFileUtils.ensureIdInProperties(content, "abc123")

        val lines = result.lines()
        assertEquals(":PROPERTIES:", lines[0])
        assertTrue("ID line should start with :ID:", lines[1].startsWith(":ID:"))
        assertEquals(":END:", lines[2])
    }

    // ================================
    // hasExistingId Tests
    // ================================

    @Test
    fun `hasExistingId returns false when no drawer exists`() {
        val content = "#+title: Test\n\nContent"
        assertFalse(OrgFileUtils.hasExistingId(content))
    }

    @Test
    fun `hasExistingId returns false when drawer has no ID property`() {
        val content = """
:PROPERTIES:
:CUSTOM: value
:END:
#+title: Test
""".trimIndent()
        assertFalse(OrgFileUtils.hasExistingId(content))
    }

    @Test
    fun `hasExistingId returns false when ID is empty`() {
        val content = """
:PROPERTIES:
:ID:
:END:
#+title: Test
""".trimIndent()
        assertFalse(OrgFileUtils.hasExistingId(content))
    }

    @Test
    fun `hasExistingId returns false when ID has only whitespace`() {
        val content = """
:PROPERTIES:
:ID:    
:END:
#+title: Test
""".trimIndent()
        assertFalse(OrgFileUtils.hasExistingId(content))
    }

    @Test
    fun `hasExistingId returns true when ID has value`() {
        val content = """
:PROPERTIES:
:ID:       abc123def456
:END:
#+title: Test
""".trimIndent()
        assertTrue(OrgFileUtils.hasExistingId(content))
    }

    // ================================
    // getDisplayName Tests
    // ================================

    @Test
    fun `getDisplayName returns truncated title when present`() {
        val content = "#+title: My Note Title\n\nContent"
        assertEquals("My Note Title", OrgFileUtils.getDisplayName(content))
    }

    @Test
    fun `getDisplayName returns default when no title`() {
        val content = "Some content without title"
        assertEquals("New note", OrgFileUtils.getDisplayName(content))
    }

    @Test
    fun `getDisplayName returns custom default when no title`() {
        val content = "Some content without title"
        assertEquals("Custom Default", OrgFileUtils.getDisplayName(content, "Custom Default"))
    }

    @Test
    fun `getDisplayName returns default for empty title`() {
        val content = "#+title: \n\nContent"
        assertEquals("New note", OrgFileUtils.getDisplayName(content))
    }

    @Test
    fun `getDisplayName truncates long titles with ellipsis`() {
        val longTitle = "A".repeat(70)
        val content = "#+title: $longTitle\n\nContent"
        val result = OrgFileUtils.getDisplayName(content)
        assertEquals(61, result.length)
        assertTrue("Should end with ellipsis", result.endsWith("…"))
    }

    // ================================
    // Round Trip Tests
    // ================================

    @Test
    fun `round trip - ID remains stable after multiple ensureIdInProperties calls`() {
        val originalContent = "#+title: Test Note\n\nContent"
        val firstId = "abc123def456"

        // First call inserts ID
        val afterFirst = OrgFileUtils.ensureIdInProperties(originalContent, firstId)
        assertTrue(afterFirst.contains(firstId))

        // Second call with different ID should NOT change it
        val afterSecond = OrgFileUtils.ensureIdInProperties(afterFirst, "different-id")
        assertTrue("Original ID should be preserved", afterSecond.contains(firstId))
        assertFalse("New ID should not be inserted", afterSecond.contains("different-id"))

        // Third call - same result
        val afterThird = OrgFileUtils.ensureIdInProperties(afterSecond, "yet-another-id")
        assertEquals("Content should be unchanged", afterSecond, afterThird)
    }

    @Test
    fun `round trip - title extraction works after drawer insertion`() {
        val originalContent = "#+title: Original Title\n\nContent"
        val withId = OrgFileUtils.ensureIdInProperties(originalContent, "abc123")

        val extractedTitle = OrgFileUtils.extractTitle(withId)
        assertEquals("Original Title", extractedTitle)
    }
}
