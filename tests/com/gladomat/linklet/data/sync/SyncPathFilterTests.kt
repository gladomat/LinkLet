package com.gladomat.linklet.data.sync

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncPathFilterTests {

    @Before
    @After
    fun resetIgnoreRules() {
        SyncPathFilter.ignoreRules = SyncIgnoreRules.EMPTY
    }

    @Test
    fun `should include org note path`() {
        assertTrue(SyncPathFilter.shouldInclude("note.org"))
    }

    @Test
    fun `should include any attachment extension by default`() {
        assertTrue(SyncPathFilter.shouldInclude("images/photo.png"))
        assertTrue(SyncPathFilter.shouldInclude("images/photo.jpg"))
        assertTrue(SyncPathFilter.shouldInclude("images/photo.jpeg"))
        assertTrue(SyncPathFilter.shouldInclude("docs/paper.pdf"))
        assertTrue(SyncPathFilter.shouldInclude("images/diagram.svg"))
        assertTrue(SyncPathFilter.shouldInclude("images/anim.gif"))
        assertTrue(SyncPathFilter.shouldInclude("images/modern.webp"))
        assertTrue(SyncPathFilter.shouldInclude("refs/library.bib"))
        // No extension allowlist: unrecognized extensions sync too, unless excluded via
        // .syncignore (see the ignoreRules tests below).
        assertTrue(SyncPathFilter.shouldInclude("videos/clip.mp4"))
        assertTrue(SyncPathFilter.shouldInclude("docs/paper.docx"))
    }

    @Test
    fun `should exclude dot and underscore paths`() {
        assertFalse(SyncPathFilter.shouldInclude(".git/config"))
        assertFalse(SyncPathFilter.shouldInclude("_trash_bin/note.org"))
        assertFalse(SyncPathFilter.shouldInclude("folder/_private/note.org"))
    }

    @Test
    fun `should exclude emacs latex preview directory`() {
        assertFalse(SyncPathFilter.shouldInclude("ltximg/eq1.png"))
        assertFalse(SyncPathFilter.shouldInclude("notes/ltximg/eq2.png"))
    }

    @Test
    fun `directory traversal allows extensionless attachment folders`() {
        assertTrue(SyncPathFilter.isDirectoryTraversable("images"))
        assertTrue(SyncPathFilter.isDirectoryTraversable("data"))
        assertTrue(SyncPathFilter.isDirectoryTraversable("data/3f"))
        assertTrue(SyncPathFilter.isDirectoryTraversable("data/3f/abc123def"))
    }

    @Test
    fun `directory traversal still excludes blocked and hidden names`() {
        assertFalse(SyncPathFilter.isDirectoryTraversable(".git"))
        assertFalse(SyncPathFilter.isDirectoryTraversable("_private"))
        assertFalse(SyncPathFilter.isDirectoryTraversable("ltximg"))
        assertFalse(SyncPathFilter.isDirectoryTraversable("notes/ltximg"))
    }

    @Test
    fun `syncignore rules exclude matching paths`() {
        SyncPathFilter.ignoreRules = SyncIgnoreRules.parse(
            """
            *.sqlite
            *.zip
            *~
            Backups/
            """.trimIndent(),
        )

        assertFalse(SyncPathFilter.shouldInclude("db/notes.sqlite"))
        assertFalse(SyncPathFilter.shouldInclude("archive.zip"))
        assertFalse(SyncPathFilter.shouldInclude("notes.org~"))
        assertFalse(SyncPathFilter.shouldInclude("PureWriter/Backups/backup.pwb"))
        assertFalse(SyncPathFilter.isDirectoryTraversable("PureWriter/Backups"))

        assertTrue(SyncPathFilter.shouldInclude("note.org"))
        assertTrue(SyncPathFilter.shouldInclude("images/photo.png"))
    }

    @Test
    fun `syncignore rules do not affect unmatched paths`() {
        SyncPathFilter.ignoreRules = SyncIgnoreRules.parse("*.sqlite")

        assertTrue(SyncPathFilter.shouldInclude("note.org"))
        assertTrue(SyncPathFilter.shouldInclude("images/photo.png"))
    }
}
