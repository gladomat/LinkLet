package com.gladomat.linklet.data.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrgPathResolverTests {

    @Test
    fun `orgAttachIdToRelativeDir shards by first two characters`() {
        val id = "6fa9215e-db36-476e-a8f1-33cc0f397eb4"
        assertEquals(
            "data/6f/a9215e-db36-476e-a8f1-33cc0f397eb4",
            OrgPathResolver.orgAttachIdToRelativeDir(id),
        )
    }

    @Test
    fun `resolveAttachmentPath uses data sharded ID next to note`() {
        val notePath = "notes/sample.org"
        val id = "6fa9215e-db36-476e-a8f1-33cc0f397eb4"
        val resolved = OrgPathResolver.resolveAttachmentPath(
            notePath = notePath,
            nodeId = id,
            dirProperty = null,
            attachmentName = "clipboard.png",
        )
        assertEquals(
            "notes/data/6f/a9215e-db36-476e-a8f1-33cc0f397eb4/clipboard.png",
            resolved,
        )
    }

    @Test
    fun `resolveAttachmentPath prefers DIR property relative to note dir`() {
        val notePath = "notes/sample.org"
        val resolved = OrgPathResolver.resolveAttachmentPath(
            notePath = notePath,
            nodeId = "ignored",
            dirProperty = "data/custom-dir",
            attachmentName = "img.png",
        )
        assertEquals("notes/data/custom-dir/img.png", resolved)
    }

    @Test
    fun `resolveAttachmentPath allows dot segments without traversal`() {
        val notePath = "notes/sample.org"
        val resolved = OrgPathResolver.resolveAttachmentPath(
            notePath = notePath,
            nodeId = "ignored",
            dirProperty = "./data/custom-dir",
            attachmentName = "./img.png",
        )
        assertEquals("notes/data/custom-dir/img.png", resolved)
    }

    @Test
    fun `resolveAttachmentPath rejects traversal in attachment name`() {
        val notePath = "notes/sample.org"
        val resolved = OrgPathResolver.resolveAttachmentPath(
            notePath = notePath,
            nodeId = "6fa9215e-db36-476e-a8f1-33cc0f397eb4",
            dirProperty = null,
            attachmentName = "../escape.png",
        )
        assertNull(resolved)
    }

    @Test
    fun `resolveFileTargetPath normalizes relative targets`() {
        val notePath = "notes/sub/sample.org"
        val resolved = OrgPathResolver.resolveFileTargetPath(notePath, "../img/cat.jpg")
        assertEquals("notes/img/cat.jpg", resolved)
    }
}
