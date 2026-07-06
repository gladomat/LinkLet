package com.gladomat.linklet.data.parser.org

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrgDocumentParserTests {

    @Test
    fun `parseOrgDocument extracts metadata and sections`() {
        val content = """
            :PROPERTIES:
            :ID: abc
            :END:

            Intro paragraph.

            * Heading 1
            Body line
            ** Child
            Details
        """.trimIndent()

        val document = parseOrgDocument(content)

        assertEquals(3, document.metadata.size)
        assertTrue(document.preface.startsWith("Intro"))
        assertEquals(1, document.sections.size)
        val section = document.sections.first()
        assertEquals(1, section.level)
        assertEquals("Heading 1", section.title)
        assertEquals("Body line", section.body)
        assertEquals(1, section.children.size)
        assertEquals("Child", section.children.first().title)
    }

    @Test
    fun `parseContentToBlocks parses simple paragraphs`() {
        val lines = listOf(
            "This is line one.",
            "This is line two.",
            "",
            "Another paragraph.",
        )
        val blocks = parseContentToBlocks(lines)

        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is OrgBlock.Paragraph)
        assertEquals("This is line one.\nThis is line two.", (blocks[0] as OrgBlock.Paragraph).text)
        assertEquals("Another paragraph.", (blocks[1] as OrgBlock.Paragraph).text)
    }

    @Test
    fun `parseContentToBlocks parses source block`() {
        val lines = listOf(
            "#+BEGIN_SRC python",
            "def hello():",
            "    print('Hello')",
            "#+END_SRC",
        )
        val blocks = parseContentToBlocks(lines)

        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is OrgBlock.SourceBlock)
        val srcBlock = blocks[0] as OrgBlock.SourceBlock
        assertEquals("python", srcBlock.language)
        assertEquals("def hello():\n    print('Hello')", srcBlock.content)
    }

    @Test
    fun `parseContentToBlocks parses table with header`() {
        val lines = listOf(
            "| Name  | Age |",
            "|-------|-----|",
            "| Alice | 30  |",
            "| Bob   | 25  |",
        )
        val blocks = parseContentToBlocks(lines)

        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is OrgBlock.Table)
        val table = blocks[0] as OrgBlock.Table
        assertEquals(3, table.rows.size)
        assertEquals(1, table.headerRowCount)
        assertEquals(listOf("Name", "Age"), table.rows[0])
        assertEquals(listOf("Alice", "30"), table.rows[1])
    }

    @Test
    fun `parseContentToBlocks parses src block immediately after table`() {
        val lines = listOf(
            "| Col1 | Col2 |",
            "| A    | B    |",
            "#+BEGIN_SRC kotlin",
            "fun main() {}",
            "#+END_SRC",
        )

        val blocks = parseContentToBlocks(lines)

        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is OrgBlock.Table)
        assertTrue(blocks[1] is OrgBlock.SourceBlock)
        assertEquals("kotlin", (blocks[1] as OrgBlock.SourceBlock).language)
    }

    @Test
    fun `parseContentToBlocks tolerates trailing whitespace on end line`() {
        val lines = listOf(
            "#+BEGIN_SRC python",
            "print('hi')",
            "#+END_SRC   ",
        )

        val blocks = parseContentToBlocks(lines)

        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is OrgBlock.SourceBlock)
        assertEquals("print('hi')", (blocks[0] as OrgBlock.SourceBlock).content)
    }

    @Test
    fun `parseContentToBlocks handles mixed content`() {
        val lines = listOf(
            "Some intro text.",
            "",
            "| Col1 | Col2 |",
            "| A    | B    |",
            "",
            "#+BEGIN_SRC kotlin",
            "fun main() {}",
            "#+END_SRC",
            "",
            "Final paragraph.",
        )
        val blocks = parseContentToBlocks(lines)

        assertEquals(4, blocks.size)
        assertTrue(blocks[0] is OrgBlock.Paragraph)
        assertTrue(blocks[1] is OrgBlock.Table)
        assertTrue(blocks[2] is OrgBlock.SourceBlock)
        assertTrue(blocks[3] is OrgBlock.Paragraph)
    }

    @Test
    fun `parseContentToBlocks parses quote block`() {
        val lines = listOf(
            "#+BEGIN_QUOTE",
            "To be or not to be.",
            "That is the question.",
            "#+END_QUOTE",
        )
        val blocks = parseContentToBlocks(lines)

        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is OrgBlock.QuoteBlock)
        assertEquals("To be or not to be.\nThat is the question.", (blocks[0] as OrgBlock.QuoteBlock).content)
    }

    @Test
    fun `parseContentToBlocks handles unknown block type gracefully`() {
        val lines = listOf(
            "#+BEGIN_CUSTOM",
            "Custom content here.",
            "#+END_CUSTOM",
        )
        val blocks = parseContentToBlocks(lines)

        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is OrgBlock.UnknownBlock)
        val unknown = blocks[0] as OrgBlock.UnknownBlock
        assertEquals("CUSTOM", unknown.blockType)
        assertEquals("Custom content here.", unknown.content)
    }

    @Test
    fun `parseOrgDocument extracts leading PROPERTIES drawer for sections`() {
        val content = """
            * Heading 1
            :PROPERTIES:
            :ID: 6fa9215e-db36-476e-a8f1-33cc0f397eb4
            :DIR: data/custom
            :END:

            Text
        """.trimIndent()

        val document = parseOrgDocument(content)
        val section = document.sections.single()
        assertEquals("6fa9215e-db36-476e-a8f1-33cc0f397eb4", section.properties["ID"])
        assertEquals("data/custom", section.properties["DIR"])
    }

    // ========================================================================
    // Drawer block tests
    // ========================================================================

    @Test
    fun `leading PROPERTIES drawer populates section properties and is dropped from blocks`() {
        val content = """
            * Heading 1
            :PROPERTIES:
            :ID: abc-123
            :CUSTOM_ID: my-heading
            :END:

            Body text.
        """.trimIndent()

        val document = parseOrgDocument(content)
        val section = document.sections.single()
        assertEquals("abc-123", section.properties["ID"])
        assertEquals("my-heading", section.properties["CUSTOM_ID"])

        // Leading PROPERTIES drawer is node metadata, not a renderable block: it's
        // consumed into section.properties and dropped from blocks (no pill for it).
        assertFalse(
            "Leading PROPERTIES drawer should not also appear as a Drawer block",
            section.blocks.any { it is OrgBlock.Drawer && it.name == "PROPERTIES" },
        )

        assertFalse(
            "Drawer lines should not leak into paragraphs",
            section.blocks.any { it is OrgBlock.Paragraph && (it as OrgBlock.Paragraph).text.contains(":PROPERTIES:") },
        )
    }

    @Test
    fun `mid-body custom drawer emits in-place Drawer block between paragraphs`() {
        val lines = listOf(
            "Before drawer.",
            "",
            ":NOTES:",
            "Some note content",
            "More notes",
            ":END:",
            "",
            "After drawer.",
        )
        val blocks = parseContentToBlocks(lines)

        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is OrgBlock.Paragraph)
        assertEquals("Before drawer.", (blocks[0] as OrgBlock.Paragraph).text)
        assertTrue(blocks[1] is OrgBlock.Drawer)
        val drawer = blocks[1] as OrgBlock.Drawer
        assertEquals("NOTES", drawer.name)
        assertEquals(listOf("Some note content", "More notes"), drawer.lines)
        assertTrue(blocks[2] is OrgBlock.Paragraph)
        assertEquals("After drawer.", (blocks[2] as OrgBlock.Paragraph).text)
    }

    @Test
    fun `LOGBOOK drawer has raw lines and empty properties`() {
        val lines = listOf(
            ":LOGBOOK:",
            "CLOCK: [2024-01-01 Mon 10:00]--[2024-01-01 Mon 11:00] =>  1:00",
            "CLOCK: [2024-01-02 Tue 14:00]--[2024-01-02 Tue 15:30] =>  1:30",
            ":END:",
        )
        val blocks = parseContentToBlocks(lines)

        assertEquals(1, blocks.size)
        val drawer = blocks[0] as OrgBlock.Drawer
        assertEquals("LOGBOOK", drawer.name)
        assertEquals(2, drawer.lines.size)
        assertTrue(drawer.properties.isEmpty())
    }

    @Test
    fun `unclosed drawer falls back to paragraph text`() {
        val lines = listOf(
            ":NOTES:",
            "Some content",
            "No :END: here",
        )
        val blocks = parseContentToBlocks(lines)

        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is OrgBlock.Paragraph)
        val text = (blocks[0] as OrgBlock.Paragraph).text
        assertTrue(text.contains(":NOTES:"))
        assertTrue(text.contains("Some content"))
        assertTrue(text.contains("No :END: here"))
    }

    @Test
    fun `property line alone does not start a drawer`() {
        val lines = listOf(
            ":ID: some-value",
        )
        val blocks = parseContentToBlocks(lines)

        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is OrgBlock.Paragraph)
    }

    @Test
    fun `drawer-like lines inside source block stay in source block`() {
        val lines = listOf(
            "#+BEGIN_SRC org",
            ":PROPERTIES:",
            ":ID: inside-src",
            ":END:",
            "#+END_SRC",
        )
        val blocks = parseContentToBlocks(lines)

        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is OrgBlock.SourceBlock)
        assertTrue((blocks[0] as OrgBlock.SourceBlock).content.contains(":PROPERTIES:"))
    }

    @Test
    fun `drawer in preface is parsed as Drawer block`() {
        val content = """
            Intro text.

            :NOTES:
            A preface note.
            :END:

            * Heading
            Body
        """.trimIndent()

        val document = parseOrgDocument(content)
        assertTrue(document.prefaceBlocks.any { it is OrgBlock.Drawer })
        val drawer = document.prefaceBlocks.first { it is OrgBlock.Drawer } as OrgBlock.Drawer
        assertEquals("NOTES", drawer.name)
    }

    @Test
    fun `blank lines between heading and PROPERTIES drawer still work`() {
        val content = """
            * Heading

            :PROPERTIES:
            :ID: with-blanks
            :END:

            Body text.
        """.trimIndent()

        val document = parseOrgDocument(content)
        val section = document.sections.single()
        assertEquals("with-blanks", section.properties["ID"])
    }
}
