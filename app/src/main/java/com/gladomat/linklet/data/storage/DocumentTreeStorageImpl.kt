package com.gladomat.linklet.data.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.gladomat.linklet.data.settings.FolderSettingsRepository
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class DocumentTreeStorageImpl @Inject constructor(
    private val context: Context,
    private val folderSettingsRepository: FolderSettingsRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : IStorage {

    private val contentResolver: ContentResolver = context.contentResolver

    override suspend fun listNotes(): Result<List<String>> = withContext(dispatcher) {
        runCatching {
            val base = baseDocumentFile() ?: throw IllegalStateException("Folder not selected")
            val result = mutableListOf<String>()
            traverse(base, currentPath = "", into = result)
            result.sorted()
        }
    }

    override suspend fun readNote(path: String): Result<String> = withContext(dispatcher) {
        runCatching {
            val target = resolveFile(path) ?: throw IOException("Note not found: $path")
            readFromDocument(target)
        }
    }

    override suspend fun writeNote(path: String, content: String): Result<Unit> = withContext(dispatcher) {
        runCatching {
            val parent = ensureParentDirectories(path)
            val fileName = path.substringAfterLast('/')
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
        }
    }

    override suspend fun deleteNote(path: String): Result<Unit> = withContext(dispatcher) {
        runCatching {
            val target = resolveFile(path)
            target?.delete()
            Unit
        }
    }

    private suspend fun baseDocumentFile(): DocumentFile? {
        val uri: Uri = folderSettingsRepository.currentFolderUri() ?: return null
        return when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> DocumentFile.fromFile(File(uri.path!!))
            else -> DocumentFile.fromTreeUri(context, uri)
        }
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
                    existing.delete()
                    current.createDirectory(segment)
                        ?: throw IOException("Unable to create directory: $segment")
                }
            }
        }
        return current
    }

    private fun traverse(document: DocumentFile, currentPath: String, into: MutableList<String>) {
        if (!document.exists()) return
        if (document.isFile) {
            if (currentPath.endsWith(".org", ignoreCase = true)) {
                into += currentPath
            }
            return
        }
        val children = document.listFiles()
        for (child in children) {
            val childName = child.name ?: continue
            val nextPath = if (currentPath.isEmpty()) childName else "$currentPath/$childName"
            traverse(child, nextPath, into)
        }
    }

    private fun readFromDocument(document: DocumentFile): String {
        val input = contentResolver.openInputStream(document.uri)
            ?: if (document.uri.scheme == "file") {
                File(document.uri.path!!).inputStream()
            } else {
                null
            }
        input?.use { stream ->
            return stream.bufferedReader().use { it.readText() }
        }
        throw IOException("Unable to open input stream for ${document.uri}")
    }

    private fun writeToDocument(document: DocumentFile, content: String) {
        val output = contentResolver.openOutputStream(document.uri, "wt")
            ?: if (document.uri.scheme == "file") {
                FileOutputStream(File(document.uri.path!!))
            } else {
                null
            }
        output?.use { stream ->
            stream.bufferedWriter().use { it.write(content) }
            return
        }
        throw IOException("Unable to open output stream for ${document.uri}")
    }

    private fun validateSegments(segments: List<String>) {
        segments.forEach { segment ->
            if (segment == ".." || segment == ".") {
                throw IllegalArgumentException("Invalid path segment: $segment")
            }
        }
    }
}
