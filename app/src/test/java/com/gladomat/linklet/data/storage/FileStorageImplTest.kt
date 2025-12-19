package com.gladomat.linklet.data.storage

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileStorageImplTest {
    @Test
    fun `listFiles includes non-org files`() {
        val tempDir = Files.createTempDirectory("storage-test").toFile()
        File(tempDir, "a.org").writeText("hi")
        File(tempDir, "img.png").writeBytes(byteArrayOf(1, 2, 3))
        val storage = FileStorageImpl(tempDir)

        runBlocking {
            val files = storage.listFiles().getOrThrow()
            assertTrue(files.contains("a.org"))
            assertTrue(files.contains("img.png"))
        }
    }

    @Test
    fun `binary write and read round trip`() {
        val tempDir = Files.createTempDirectory("storage-test").toFile()
        val storage = FileStorageImpl(tempDir)
        val bytes = byteArrayOf(5, 6, 7, 8)

        runBlocking {
            storage.writeFileBytes("bin/data.bin", bytes).getOrThrow()
            val result = storage.readFileBytes("bin/data.bin").getOrThrow()
            assertEquals(bytes.toList(), result.toList())
        }
    }
}
