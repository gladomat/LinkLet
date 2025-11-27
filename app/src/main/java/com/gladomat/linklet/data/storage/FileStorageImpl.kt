package com.gladomat.linklet.data.storage

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Simple filesystem-backed storage rooted at [baseDir].
 */
class FileStorageImpl(
    private val baseDir: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : IStorage {

    init {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
    }

    override suspend fun listNotes(): Result<List<String>> = withContext(dispatcher) {
        runCatching {
            ensureBaseDir()
            baseDir
                .walkTopDown()
                .filter { it.isFile && it.extension.equals("org", ignoreCase = true) }
                .map { it.relativeTo(baseDir).invariantSeparatorsPath }
                .sorted()
                .toList()
        }
    }

    override suspend fun readNote(path: String): Result<String> = withContext(dispatcher) {
        runCatching {
            val file = resolvePath(path)
            if (!file.exists()) throw IOException("Note not found: $path")
            file.readText()
        }
    }

    override suspend fun writeNote(path: String, content: String): Result<Unit> = withContext(dispatcher) {
        runCatching {
            val file = resolvePath(path)
            file.parentFile?.mkdirs()
            file.writeText(content)
        }
    }

    override suspend fun deleteNote(path: String): Result<Unit> = withContext(dispatcher) {
        runCatching {
            val file = resolvePath(path)
            if (file.exists()) {
                if (!file.delete()) throw IOException("Failed to delete file: $path")
            }
        }
    }

    override suspend fun invalidateCache() {
        // No-op: File-based storage doesn't cache directory listings
    }

    private fun ensureBaseDir() {
        if (!baseDir.exists() && !baseDir.mkdirs()) {
            throw IOException("Unable to create storage directory at ${baseDir.absolutePath}")
        }
    }

    private fun resolvePath(path: String): File {
        val target = File(baseDir, path).canonicalFile
        val root = baseDir.canonicalFile
        if (!target.path.startsWith(root.path)) {
            throw IllegalArgumentException("Path escapes storage root: $path")
        }
        return target
    }
}
