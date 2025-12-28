package com.gladomat.linklet.data.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.gladomat.linklet.data.settings.FolderSettingsRepository
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

@Singleton
class DocumentTreeStorageImpl @Inject constructor(
    private val context: Context,
    private val folderSettingsRepository: FolderSettingsRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : IStorage {

    private val contentResolver: ContentResolver = context.contentResolver
    
    // Cache for the base DocumentFile to avoid repeated lookups
    // This cache is invalidated when external operations modify storage
    @Volatile
    private var cachedBaseDocumentFile: DocumentFile? = null
    private var cachedFolderUri: Uri? = null

    /**
     * Cache mapping storage-relative paths to concrete document URIs.
     *
     * SAF lookups like `findFile()` can be extremely slow when repeated for many files.
     * Populating this cache during directory traversal allows subsequent reads to open
     * streams directly from the final file URI.
     */
    private val pathUriCache = AtomicReference(ConcurrentHashMap<String, Uri>())

    override suspend fun listNotes(): Result<List<String>> = withContext(dispatcher) {
        runCatching {
            val base = baseDocumentFile() ?: throw IllegalStateException("Folder not selected")
            val result = mutableListOf<String>()
            val newCache = ConcurrentHashMap<String, Uri>()
            traverse(
                document = base,
                currentPath = "",
                into = result,
                includeAllFiles = false,
                cache = newCache,
            )
            pathUriCache.set(newCache)
            result.sorted()
        }
    }

    override suspend fun listFiles(): Result<List<String>> = withContext(dispatcher) {
        runCatching {
            val base = baseDocumentFile() ?: throw IllegalStateException("Folder not selected")
            val result = mutableListOf<String>()
            val newCache = ConcurrentHashMap<String, Uri>()
            traverse(
                document = base,
                currentPath = "",
                into = result,
                includeAllFiles = true,
                cache = newCache,
            )
            pathUriCache.set(newCache)
            result.sorted()
        }
    }

    override suspend fun readNote(path: String): Result<String> = withContext(dispatcher) {
        runCatching {
            val cachedUri = pathUriCache.get()[path]
            if (cachedUri != null) {
                return@runCatching readFromUri(cachedUri)
            }

            val target = resolveFile(path) ?: throw IOException("Note not found: $path")
            pathUriCache.get()[path] = target.uri
            readFromUri(target.uri)
        }
    }

    override suspend fun readFileBytes(path: String): Result<ByteArray> = withContext(dispatcher) {
        runCatching {
            val cachedUri = pathUriCache.get()[path]
            if (cachedUri != null) {
                return@runCatching readBytesFromUri(cachedUri)
            }

            val target = resolveFile(path) ?: throw IOException("File not found: $path")
            pathUriCache.get()[path] = target.uri
            readBytesFromUri(target.uri)
        }
    }

    override suspend fun statNote(path: String): Result<StorageFileStat> = withContext(dispatcher) {
        runCatching {
            val target = resolveFile(path) ?: throw IOException("File not found: $path")
            if (!target.isFile) throw IOException("Not a file: $path")
            StorageFileStat(
                lastModifiedEpochMillis = target.lastModified().takeIf { it > 0 },
                sizeBytes = target.length().takeIf { it >= 0 },
            )
        }
    }

    override suspend fun writeNote(path: String, content: String): Result<Unit> = withContext(dispatcher) {
        runCatching {
            val parent = ensureParentDirectories(path)
            val fileName = path.substringAfterLast('/')
            if (fileName.isBlank()) {
                throw IllegalArgumentException("Invalid filename in path: $path")
            }
            val existing = parent.findFile(fileName)
            val target = if (existing != null && existing.isFile) {
                existing
            } else {
                existing?.takeIf { it.isFile }?.delete()
                // "text/plain" causes some SAF providers to append .txt if extension is not .txt
                // "text/org" or "application/octet-stream" is safer for preserving .org extension.
                parent.createFile("text/org", fileName)
                    ?: throw IOException("Unable to create file: $path")
            }
            writeToDocument(target, content)
            // Directory structure/content changed; clear cached snapshot.
            pathUriCache.set(ConcurrentHashMap())
        }
    }

    override suspend fun writeFileBytes(path: String, content: ByteArray): Result<Unit> = withContext(dispatcher) {
        runCatching {
            val parent = ensureParentDirectories(path)
            val fileName = path.substringAfterLast('/')
            if (fileName.isBlank()) {
                throw IllegalArgumentException("Invalid filename in path: $path")
            }
            val existing = parent.findFile(fileName)
            val target = if (existing != null && existing.isFile) {
                existing
            } else {
                existing?.takeIf { it.isFile }?.delete()
                parent.createFile("application/octet-stream", fileName)
                    ?: throw IOException("Unable to create file: $path")
            }
            writeBytesToDocument(target, content)
            // Directory structure/content changed; clear cached snapshot.
            pathUriCache.set(ConcurrentHashMap())
        }
    }

    override suspend fun deleteNote(path: String): Result<Unit> = withContext(dispatcher) {
        runCatching {
            val target = resolveFile(path)
            target?.delete()
            pathUriCache.get().remove(path)
            Unit
        }
    }

    override suspend fun renameNote(oldPath: String, newPath: String): Result<Unit> = withContext(dispatcher) {
        runCatching {
            val sourceFile = resolveFile(oldPath) ?: throw IOException("Source file not found: $oldPath")
            val newFileName = newPath.substringAfterLast('/')
            if (newFileName.isBlank()) {
                throw IllegalArgumentException("Invalid filename in path: $newPath")
            }

            val oldDir = oldPath.substringBeforeLast('/', "")
            val newDir = newPath.substringBeforeLast('/', "")
            if (oldDir == newDir) {
                val parent = resolveFile(oldDir)?.takeIf { it.isDirectory }
                    ?: baseDocumentFile()
                    ?: throw IllegalStateException("Folder not selected")
                if (parent.findFile(newFileName) != null) {
                    throw IOException("Target file already exists: $newPath")
                }
                if (!sourceFile.renameTo(newFileName)) {
                    throw IOException("Failed to rename file from $oldPath to $newPath")
                }
                pathUriCache.set(ConcurrentHashMap())
                return@runCatching Unit
            }

            val parent = ensureParentDirectories(newPath)
            if (parent.findFile(newFileName) != null) {
                throw IOException("Target file already exists: $newPath")
            }
            val newFile = parent.createFile("text/org", newFileName)
                ?: throw IOException("Unable to create file: $newPath")
            try {
                copyDocument(sourceFile, newFile)
            } catch (e: Exception) {
                newFile.delete()
                throw e
            }

            if (!sourceFile.delete()) {
                newFile.delete()
                throw IOException("Failed to delete source file after copy: $oldPath")
            }
            pathUriCache.set(ConcurrentHashMap())
        }
    }

    override suspend fun resolveUri(path: String): Result<Uri> = withContext(dispatcher) {
        runCatching {
            val cachedUri = pathUriCache.get()[path]
            if (cachedUri != null) return@runCatching cachedUri
            val target = resolveFile(path) ?: throw IOException("File not found: $path")
            pathUriCache.get()[path] = target.uri
            target.uri
        }
    }

    private suspend fun baseDocumentFile(): DocumentFile? {
        val uri: Uri = folderSettingsRepository.currentFolderUri() ?: return null
        
        // Return cached instance if URI hasn't changed
        if (cachedFolderUri == uri && cachedBaseDocumentFile != null) {
            return cachedBaseDocumentFile
        }
        
        // Create new DocumentFile and cache it
        val documentFile = when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> DocumentFile.fromFile(File(uri.path!!))
            else -> DocumentFile.fromTreeUri(context, uri)
        }
        
        cachedFolderUri = uri
        cachedBaseDocumentFile = documentFile
        return documentFile
    }

    private suspend fun resolveFile(path: String): DocumentFile? {
        val base = baseDocumentFile() ?: return null
        val segments = path.split('/').filter { it.isNotEmpty() }
        validateSegments(segments)
        var current: DocumentFile? = base
        for (segment in segments) {
            current = current?.findFile(segment) ?: return null
            if (current?.isFile == true && segment != segments.last()) {
                return null
            }
        }
        return current
    }

    private suspend fun ensureParentDirectories(path: String): DocumentFile {
        val base = baseDocumentFile() ?: throw IllegalStateException("Folder not selected")
        val segments = path.split('/').filter { it.isNotEmpty() }
        validateSegments(segments)
        val directorySegments = segments.dropLast(1)
        var current: DocumentFile = base
        for (segment in directorySegments) {
            val existing = current.findFile(segment)
            current = when {
                existing == null -> current.createDirectory(segment)
                    ?: throw IOException("Unable to create directory: $segment")
                existing.isDirectory -> existing
                else -> {
                    throw IOException("Path segment is a file, expected directory: $segment")
                }
            }
        }
        return current
    }

    private fun traverse(
        document: DocumentFile,
        currentPath: String,
        into: MutableList<String>,
        includeAllFiles: Boolean,
        cache: MutableMap<String, Uri>,
    ) {
        if (!document.exists()) return
        if (document.isFile) {
            if (includeAllFiles || currentPath.endsWith(".org", ignoreCase = true)) {
                into += currentPath
                cache[currentPath] = document.uri
            }
            return
        }
        val children = document.listFiles()
        for (child in children) {
            val childName = child.name ?: continue
            val nextPath = if (currentPath.isEmpty()) childName else "$currentPath/$childName"
            traverse(child, nextPath, into, includeAllFiles, cache)
        }
    }

    private fun openInputStream(uri: Uri): InputStream? {
        return if (uri.scheme == ContentResolver.SCHEME_FILE) {
            File(uri.path!!).inputStream()
        } else {
            contentResolver.openInputStream(uri)
        }
    }

    private fun readFromUri(uri: Uri): String {
        openInputStream(uri)?.use { stream ->
            return stream.bufferedReader().use { it.readText() }
        }
        throw IOException("Unable to open input stream for $uri")
    }

    private fun writeToDocument(document: DocumentFile, content: String) {
        val uri = document.uri
        val output = if (uri.scheme == ContentResolver.SCHEME_FILE) {
            FileOutputStream(File(uri.path!!))
        } else {
            contentResolver.openOutputStream(uri, "wt")
        }
        output?.use { stream ->
            stream.bufferedWriter().use { it.write(content) }
            return
        }
        throw IOException("Unable to open output stream for ${document.uri}")
    }

    private fun readBytesFromUri(uri: Uri): ByteArray {
        openInputStream(uri)?.use { stream ->
            return stream.readBytes()
        }
        throw IOException("Unable to open input stream for $uri")
    }

    private fun writeBytesToDocument(document: DocumentFile, content: ByteArray) {
        val uri = document.uri
        val output = if (uri.scheme == ContentResolver.SCHEME_FILE) {
            FileOutputStream(File(uri.path!!))
        } else {
            contentResolver.openOutputStream(uri, "wt")
        }
        output?.use { stream ->
            stream.write(content)
            return
        }
        throw IOException("Unable to open output stream for ${document.uri}")
    }

    private fun copyDocument(source: DocumentFile, target: DocumentFile) {
        val sourceUri = source.uri
        val targetUri = target.uri
        val input = if (sourceUri.scheme == ContentResolver.SCHEME_FILE) {
            File(sourceUri.path!!).inputStream()
        } else {
            contentResolver.openInputStream(sourceUri)
        }
        val output = if (targetUri.scheme == ContentResolver.SCHEME_FILE) {
            FileOutputStream(File(targetUri.path!!))
        } else {
            contentResolver.openOutputStream(targetUri, "wt")
        }
        if (input == null || output == null) {
            input?.close()
            output?.close()
            throw IOException("Unable to open streams for copy: ${source.uri} -> ${target.uri}")
        }
        input.use { inStream ->
            output.use { outStream ->
                inStream.copyTo(outStream)
            }
        }
    }

    private fun validateSegments(segments: List<String>) {
        segments.forEach { segment ->
            if (segment == ".." || segment == ".") {
                throw IllegalArgumentException("Invalid path segment: $segment")
            }
            if (segment.contains('\u0000')) {
                throw IllegalArgumentException("Invalid path segment: $segment")
            }
        }
    }

    override suspend fun invalidateCache() {
        // Clear the cached DocumentFile to force fresh directory listings
        cachedBaseDocumentFile = null
        cachedFolderUri = null
        pathUriCache.set(ConcurrentHashMap())
    }
}
