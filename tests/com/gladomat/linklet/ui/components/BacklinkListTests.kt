package com.gladomat.linklet.ui.components

import com.gladomat.linklet.domain.repository.LinkEntityDto
import org.junit.Assert.assertEquals
import org.junit.Test

class BacklinkListTests {

    @Test
    fun `dedupeBacklinks keeps first entry per source and fills missing title or alias`() {
        val backlinks = listOf(
            LinkEntityDto(source = "a", target = "note", alias = null, sourceTitle = null),
            LinkEntityDto(source = "b", target = "note", alias = "Alias B", sourceTitle = "Title B"),
            LinkEntityDto(source = "a", target = "note", alias = "Alias A", sourceTitle = "Title A"),
        )

        val deduped = dedupeBacklinks(backlinks)

        assertEquals(2, deduped.size)
        val entryA = deduped.first { it.source == "a" }
        assertEquals("Alias A", entryA.alias)
        assertEquals("Title A", entryA.sourceTitle)
        val entryB = deduped.first { it.source == "b" }
        assertEquals("Alias B", entryB.alias)
        assertEquals("Title B", entryB.sourceTitle)
    }
}
