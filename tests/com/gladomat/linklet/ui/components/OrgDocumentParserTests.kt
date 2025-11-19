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
}
