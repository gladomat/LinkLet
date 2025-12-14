package com.gladomat.linklet.ui.components

import org.junit.Assert.assertEquals
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
}
