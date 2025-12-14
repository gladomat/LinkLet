package com.gladomat.linklet.domain.repository

import android.util.Log
import com.gladomat.linklet.data.index.LinkEntity
import com.gladomat.linklet.data.index.NoteDao
import com.gladomat.linklet.data.index.NoteEntity
import com.gladomat.linklet.data.model.Note
import com.gladomat.linklet.data.model.NoteLink
import com.gladomat.linklet.data.parser.IParser
import com.gladomat.linklet.data.storage.IStorage
import com.gladomat.linklet.data.model.LinkTarget
import com.gladomat.linklet.data.sync.SyncScheduler
import com.gladomat.linklet.domain.repository.LinkEntityDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Default repository implementation backed by [IStorage] and [IParser].
 */
class NoteRepositoryImpl(
    private val storage: IStorage,
    private val parser: IParser,
    private val noteDao: NoteDao,
    private val syncScheduler: SyncScheduler,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : INoteRepository {

    private val notesState = MutableStateFlow<List<Note>>(emptyList())
    private val noteIdIndex = ConcurrentHashMap<String, String>()

    override fun observeNotes(): Flow<List<Note>> = notesState.asStateFlow()

    override suspend fun listNotes(): Result<List<Note>> {
        return withContext(ioDispatcher) {
            runCatching {
                val entities = noteDao.getAllNotes()
                entities.map { entity ->
                    val content = storage.readNote(entity.path).getOrThrow()
                    val parsed = parser.parse(content = content, path = entity.path)
                    parsed.copy(links = resolveLinks(parsed.links))
                }
            }
        }
    }

    override suspend fun getNote(path: String): Result<Note> {
        return storage.readNote(path).mapCatching { content ->
            val parsed = parser.parse(content = content, path = path)
            parsed.copy(links = resolveLinks(parsed.links))
        }
    }

    override suspend fun reindex(): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            // Invalidate storage cache to ensure we see newly synced files
            storage.invalidateCache()
            
            val notePaths = storage.listNotes().getOrThrow()
            val parsedNotes = notePaths.map { path ->
                val content = storage.readNote(path).getOrThrow()
                parser.parse(content = content, path = path)
            }

            noteIdIndex.clear()
            parsedNotes.forEach { note ->
                note.orgId?.let { idValue -> noteIdIndex[idValue] = note.id.path }
            }

            val resolvedNotes = parsedNotes.map { note ->
                note.copy(links = resolveLinks(note.links))
            }

            noteDao.clearLinks()
            noteDao.clearNotes()
            noteDao.insertNotes(resolvedNotes.map { NoteEntity(path = it.id.path, title = it.title) })
            noteDao.insertLinks(
                resolvedNotes.flatMap { note ->
                    note.links.mapNotNull { link ->
                        val target = link.resolvedPath ?: return@mapNotNull null
                        LinkEntity(
                            source = note.id.path,
                            target = target,
                            alias = link.label,
                        )
                    }
                },
            )

            notesState.update { resolvedNotes }
        }
    }

    override suspend fun getBacklinks(path: String): Result<List<LinkEntityDto>> = withContext(ioDispatcher) {
        runCatching {
            noteDao.getBacklinks(path).map {
                LinkEntityDto(
                    source = it.source,
                    target = it.target,
                    alias = it.alias,
                    sourceTitle = it.sourceTitle,
                )
            }
        }
    }

    override suspend fun saveNote(path: String, content: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            Log.d(TAG, "saveNote() - Writing note to storage: path='$path'")
            storage.writeNote(path, content).getOrThrow()
            Log.d(TAG, "saveNote() - Note written successfully, scheduling reindex and sync")
            // Reindex and sync asynchronously to avoid blocking the save operation
            // The index will be updated in the background, and the note is already saved
            GlobalScope.launch(SupervisorJob() + ioDispatcher) {
                try {
                    reindex().getOrThrow()
                    Log.d(TAG, "saveNote() - Reindex complete, scheduling immediate sync")
                    syncScheduler.scheduleImmediate()
                    Log.d(TAG, "saveNote() - Immediate sync scheduled for path='$path'")
                } catch (e: Exception) {
                    Log.e(TAG, "saveNote() - Error during async reindex: ${e.message}", e)
                }
            }
            Unit  // Explicitly return Unit since Log.d returns Int
        }
    }

    override suspend fun deleteNote(path: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            storage.deleteNote(path).getOrThrow()
            reindex().getOrThrow()
            syncScheduler.scheduleImmediate()
        }
    }

    override suspend fun duplicateNote(path: String): Result<String> = withContext(ioDispatcher) {
        runCatching {
            Log.d(TAG, "duplicateNote() - Duplicating note: $path")
            val originalContent = storage.readNote(path).getOrThrow()

            val newPath = generateDuplicatePath(path)
            val newId = UUID.randomUUID().toString()
            val newContent = updateOrgId(originalContent, newId)

            storage.writeNote(newPath, newContent).getOrThrow()
            Log.d(TAG, "duplicateNote() - Created duplicate at: $newPath")

            val parsed = parser.parse(content = newContent, path = newPath)
            parsed.orgId?.let { noteIdIndex[it] = newPath }
            val resolved = parsed.copy(links = resolveLinks(parsed.links))

            noteDao.insertNotes(listOf(NoteEntity(path = resolved.id.path, title = resolved.title)))
            noteDao.insertLinks(
                resolved.links.mapNotNull { link ->
                    val target = link.resolvedPath ?: return@mapNotNull null
                    LinkEntity(
                        source = resolved.id.path,
                        target = target,
                        alias = link.label,
                    )
                },
            )

            notesState.update { existing ->
                (existing.filterNot { it.id.path == resolved.id.path } + resolved)
                    .sortedBy { it.title.lowercase() }
            }

            syncScheduler.scheduleImmediate()
            newPath
        }
    }

    override suspend fun renameNote(oldPath: String, newPath: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            Log.d(TAG, "renameNote() - Renaming from $oldPath to $newPath")
            validateFilename(newPath.substringAfterLast('/'))
            storage.renameNote(oldPath, newPath).getOrThrow()
            noteDao.renameNotePath(oldPath, newPath)

            val content = storage.readNote(newPath).getOrThrow()
            val parsed = parser.parse(content = content, path = newPath)
            parsed.orgId?.let { id ->
                val current = noteIdIndex[id]
                if (current == null || current == oldPath) {
                    noteIdIndex[id] = newPath
                }
            }

            val resolved = parsed.copy(links = resolveLinks(parsed.links))
            notesState.update { existing ->
                (existing.filterNot { it.id.path == oldPath } + resolved)
                    .sortedBy { it.title.lowercase() }
            }

            syncScheduler.scheduleImmediate()
            Log.d(TAG, "renameNote() - Rename complete and synced")
            Unit
        }
    }

    /**
     * Updates or inserts an :ID: property in the org-mode content.
     */
    private fun updateOrgId(content: String, newId: String): String {
        val newline = if (content.contains("\r\n")) "\r\n" else "\n"

        val idLineRegex = Regex("""(?im)^([ \t]*):ID:\s*\S+\s*$""")
        val idLineMatch = idLineRegex.find(content)
        if (idLineMatch != null) {
            val indent = idLineMatch.groupValues[1]
            return content.replaceFirst(idLineRegex, "${indent}:ID: $newId")
        }

        val propertiesRegex = Regex("""(?im)^([ \t]*):PROPERTIES:\s*$""")
        val propertiesMatch = propertiesRegex.find(content)
        if (propertiesMatch != null) {
            val indent = propertiesMatch.groupValues[1]
            val insertion = "${propertiesMatch.value}$newline${indent}:ID: $newId"
            return content.replaceRange(propertiesMatch.range, insertion)
        }

        val titleRegex = Regex("""(?im)^[ \t]*#\+title:.*$""")
        val titleMatch = titleRegex.find(content)
        val drawer = listOf(":PROPERTIES:", ":ID: $newId", ":END:").joinToString(newline)
        return if (titleMatch != null) {
            val lineBreakIndex = content.indexOf('\n', startIndex = titleMatch.range.last)
            val insertAt = if (lineBreakIndex >= 0) lineBreakIndex + 1 else content.length
            content.substring(0, insertAt) + drawer + newline + content.substring(insertAt)
        } else {
            drawer + newline + content
        }
    }

    private fun generateDuplicatePath(originalPath: String): String {
        val originalFilename = originalPath.substringAfterLast('/')
        val directory = originalPath.substringBeforeLast('/', "")
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        val suffix = UUID.randomUUID().toString().substring(0, 8)
        val newFilename = "$timestamp-$suffix-$originalFilename"
        return if (directory.isEmpty()) newFilename else "$directory/$newFilename"
    }

    private fun validateFilename(filename: String) {
        val trimmed = filename.trim()
        require(trimmed.isNotEmpty()) { "Filename cannot be empty" }
        require(!trimmed.startsWith(".")) { "Filename cannot start with '.'" }
        require(!trimmed.contains("/") && !trimmed.contains("\\")) { "Filename cannot contain path separators" }
        require(!trimmed.contains(":")) { "Filename cannot contain ':'" }
        require(trimmed.length <= 255) { "Filename too long" }
    }

    private fun resolveLinks(links: List<NoteLink>): List<NoteLink> =
        links.mapNotNull { link ->
            val resolvedPath = when (val target = link.target) {
                is LinkTarget.Path -> target.value
                is LinkTarget.Id -> noteIdIndex[target.value]
            } ?: return@mapNotNull null
            link.copy(resolvedPath = resolvedPath)
        }

    companion object {
        private const val TAG = "NoteRepositoryImpl"
    }
}
