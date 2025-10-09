package com.gladomat.linklet.data.parser

import com.gladomat.linklet.data.model.NoteId
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
        assertEquals("target.org", first.toPath)
        assertEquals("Alias", first.label)

        val second = note.links.last()
        assertEquals("other.org", second.toPath)
        assertNull(second.label)
    }
}
