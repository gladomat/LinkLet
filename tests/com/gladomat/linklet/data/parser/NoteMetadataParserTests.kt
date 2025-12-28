package com.gladomat.linklet.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteMetadataParserTests {

    @Test
    fun `parse extracts title id and filetags`() {
        val content = """
            #+title: Sample Note
            #+filetags: :kotlin:async:

            :PROPERTIES:
            :ID: abc-123
            :END:
        """.trimIndent()

        val metadata = NoteMetadataParser.parse(content, "note.org")

        assertEquals("Sample Note", metadata.title)
        assertEquals("abc-123", metadata.orgId)
        assertEquals(listOf("kotlin", "async"), metadata.fileTags)
    }

    @Test
    fun `parse falls back to path when title missing`() {
        val content = ":PROPERTIES:\n:ID: xyz\n:END:"
        val metadata = NoteMetadataParser.parse(content, "path.org")

        assertEquals("path.org", metadata.title)
        assertEquals("xyz", metadata.orgId)
    }

    @Test
    fun `parse returns null org id when missing`() {
        val metadata = NoteMetadataParser.parse("#+title: Title", "path.org")

        assertEquals("Title", metadata.title)
        assertNull(metadata.orgId)
    }
}
