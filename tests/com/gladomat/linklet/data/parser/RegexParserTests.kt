package com.gladomat.linklet.data.parser

import com.gladomat.linklet.data.model.NoteId
import com.gladomat.linklet.data.model.LinkTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegexParserTests {

    private val parser = RegexParser()

    @Test
    fun `parse extracts title from header`() {
        val content = """
            #+title: My Note
            Some content
        """.trimIndent()

        val note = parser.parse(content, path = "notes/my-note.org")

        assertEquals("My Note", note.title)
        assertEquals(NoteId("notes/my-note.org"), note.id)
    }

    @Test
    fun `parse falls back to path when title missing`() {
        val content = "No title here"

        val note = parser.parse(content, path = "notes/untitled.org")

        assertEquals("notes/untitled.org", note.title)
    }

    @Test
    fun `parse extracts file links and aliases`() {
        val content = """
            Some [[file:target.org][Alias]] link and [[file:other.org]]
        """.trimIndent()

        val note = parser.parse(content, path = "notes/source.org")

        assertEquals(2, note.links.size)
        val first = note.links.first()
        assertEquals(NoteId("notes/source.org"), first.fromId)
        val firstTarget = first.target as LinkTarget.Path
        assertEquals("target.org", firstTarget.value)
        assertEquals("Alias", first.label)

        val second = note.links.last()
        val secondTarget = second.target as LinkTarget.Path
        assertEquals("other.org", secondTarget.value)
        assertNull(second.label)
    }

    @Test
    fun `parse extracts org id property and id links`() {
        val content = """
            :PROPERTIES:
            :ID: abc-123
            :END:
            [[id:abc-123][Self]]
        """.trimIndent()

        val note = parser.parse(content, path = "notes/sample.org")

        assertEquals("abc-123", note.orgId)
        assertEquals(1, note.links.size)
        val link = note.links.first()
        val target = link.target as LinkTarget.Id
        assertEquals("abc-123", target.value)
        assertEquals("Self", link.label)
    }

    @Test
    fun `parse does not treat id links as note id`() {
        val content = """
            #+title: A
            [[id:abc-123][Some other note]]
        """.trimIndent()

        val note = parser.parse(content, path = "notes/sample.org")

        assertNull(note.orgId)
    }

    @Test
    fun `parse does not treat inline id as property`() {
        val content = """
            #+title: A
            This line mentions :ID: abc-123 but is not a property drawer.
        """.trimIndent()

        val note = parser.parse(content, path = "notes/sample.org")

        assertNull(note.orgId)
    }
}
