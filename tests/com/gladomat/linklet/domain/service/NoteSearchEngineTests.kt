package com.gladomat.linklet.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteSearchEngineTests {

    @Test
    fun `case insensitive search finds all matches`() {
        val blocks = listOf(
            SearchBlock(blockId = "a", text = "Hello hello HELLO"),
        )

        val matches = NoteSearchEngine.search(blocks, query = "hello", options = SearchOptions(caseSensitive = false))
            .getOrThrow()

        assertEquals(3, matches.size)
        assertEquals(listOf(0, 6, 12), matches.map { it.start })
    }

    @Test
    fun `case sensitive search finds exact casing only`() {
        val blocks = listOf(
            SearchBlock(blockId = "a", text = "Hello hello HELLO"),
        )

        val matches = NoteSearchEngine.search(blocks, query = "Hello", options = SearchOptions(caseSensitive = true))
            .getOrThrow()

        assertEquals(1, matches.size)
        assertEquals(0, matches.single().start)
    }

    @Test
    fun `whole word search ignores partials`() {
        val blocks = listOf(
            SearchBlock(blockId = "a", text = "word words sword word"),
        )

        val matches = NoteSearchEngine.search(
            blocks,
            query = "word",
            options = SearchOptions(wholeWord = true),
        ).getOrThrow()

        assertEquals(listOf(0, 17), matches.map { it.start })
    }

    @Test
    fun `regex search matches pattern`() {
        val blocks = listOf(
            SearchBlock(blockId = "a", text = "abc 123 abcd 999"),
        )

        val matches = NoteSearchEngine.search(
            blocks,
            query = "\\d+",
            options = SearchOptions(useRegex = true),
        ).getOrThrow()

        assertEquals(listOf(4, 13), matches.map { it.start })
        assertEquals(listOf(7, 16), matches.map { it.end })
    }

    @Test
    fun `invalid regex returns failure`() {
        val result = NoteSearchEngine.search(
            blocks = listOf(SearchBlock(blockId = "a", text = "abc")),
            query = "(",
            options = SearchOptions(useRegex = true),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `matches are block-local and preserve blockId`() {
        val blocks = listOf(
            SearchBlock(blockId = "one", text = "hello"),
            SearchBlock(blockId = "two", text = "hello"),
        )

        val matches = NoteSearchEngine.search(blocks, query = "hello", options = SearchOptions())
            .getOrThrow()

        assertEquals(listOf("one", "two"), matches.map { it.blockId })
        assertEquals(listOf(0, 0), matches.map { it.start })
    }
}

