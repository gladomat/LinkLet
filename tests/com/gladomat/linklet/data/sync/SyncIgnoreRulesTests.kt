package com.gladomat.linklet.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncIgnoreRulesTests {

    @Test
    fun `empty or comment-only text yields EMPTY`() {
        val rules = SyncIgnoreRules.parse(
            """
            # just a comment

            """.trimIndent(),
        )
        assertFalse(rules.matches("anything.txt"))
    }

    @Test
    fun `basename pattern matches at any depth`() {
        val rules = SyncIgnoreRules.parse("Thumbs.db")
        assertTrue(rules.matches("Thumbs.db"))
        assertTrue(rules.matches("images/Thumbs.db"))
        assertTrue(rules.matches("a/b/c/Thumbs.db"))
    }

    @Test
    fun `star matches within a segment only`() {
        val rules = SyncIgnoreRules.parse("*.sqlite")
        assertTrue(rules.matches("notes.sqlite"))
        assertTrue(rules.matches("db/notes.sqlite"))
        assertFalse(rules.matches("db/notes.sqlite.bak"))
    }

    @Test
    fun `trailing slash restricts pattern to directories and their contents`() {
        val rules = SyncIgnoreRules.parse("Backups/")
        assertTrue(rules.matches("Backups"))
        assertTrue(rules.matches("Backups/file.pwb"))
        assertTrue(rules.matches("PureWriter/Backups"))
        assertTrue(rules.matches("PureWriter/Backups/file.pwb"))
        assertFalse(rules.matches("BackupsOld/file.pwb"))
    }

    @Test
    fun `leading slash anchors pattern to vault root`() {
        val rules = SyncIgnoreRules.parse("/scratch.org")
        assertTrue(rules.matches("scratch.org"))
        assertFalse(rules.matches("notes/scratch.org"))
    }

    @Test
    fun `path containing internal slash is implicitly anchored`() {
        val rules = SyncIgnoreRules.parse("archive/old.org")
        assertTrue(rules.matches("archive/old.org"))
        assertFalse(rules.matches("notes/archive/old.org"))
    }

    @Test
    fun `double star matches across directories`() {
        val rules = SyncIgnoreRules.parse("**/cache/**")
        assertTrue(rules.matches("cache/file.tmp"))
        assertTrue(rules.matches("a/b/cache/deep/file.tmp"))
        assertFalse(rules.matches("a/cachefile.tmp"))
    }

    @Test
    fun `parseVerbose reports no dropped lines for comments, blanks, and valid rules`() {
        val result = SyncIgnoreRules.parseVerbose(
            """
            # a comment

            *.bak
            """.trimIndent(),
        )
        assertTrue(result.droppedLines.isEmpty())
        assertTrue(result.rules.matches("notes/x.bak"))
    }

    @Test
    fun `parseVerbose reports a dropped line with its 1-based line number and raw text`() {
        val result = SyncIgnoreRules.parseVerbose("*.bak\n/\n*.tmp\n")
        assertEquals(1, result.droppedLines.size)
        assertEquals(2, result.droppedLines.first().lineNumber)
        assertEquals("/", result.droppedLines.first().rawText)
        assertTrue(result.rules.matches("a.bak"))
        assertTrue(result.rules.matches("a.tmp"))
    }

    @Test
    fun `parse and parseVerbose agree on the compiled rules`() {
        val text = "*.bak\n/\n*.tmp\n"
        assertEquals(
            SyncIgnoreRules.parse(text).matches("a.bak"),
            SyncIgnoreRules.parseVerbose(text).rules.matches("a.bak"),
        )
    }
}
