package com.gladomat.linklet.domain.repository

import android.util.Log
import android.os.SystemClock
import com.gladomat.linklet.BuildConfig
import com.gladomat.linklet.data.index.IndexQueueDao
import com.gladomat.linklet.data.index.IndexQueueStatus
import com.gladomat.linklet.data.index.IndexTypeConverters
import com.gladomat.linklet.data.index.LinkEntity
import com.gladomat.linklet.data.index.NoteAvailability
import com.gladomat.linklet.data.index.NoteDao
import com.gladomat.linklet.data.index.NoteEntity
import com.gladomat.linklet.data.index.NoteSource
import com.gladomat.linklet.data.index.IndexingScheduler
import com.gladomat.linklet.data.model.IndexingProgress
import com.gladomat.linklet.data.model.Note
import com.gladomat.linklet.data.model.NoteId
import com.gladomat.linklet.data.model.NoteIndexEntry
import com.gladomat.linklet.data.model.NoteLink
import com.gladomat.linklet.data.parser.IParser
import com.gladomat.linklet.data.storage.IStorage
import com.gladomat.linklet.data.model.LinkTarget
import com.gladomat.linklet.data.sync.SyncScheduler
import com.gladomat.linklet.data.utils.OrgFileUtils
import com.gladomat.linklet.domain.repository.LinkEntityDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Default repository implementation backed by [IStorage] and [IParser].
 */
class NoteRepositoryImpl(
    private val storage: IStorage,
    private val parser: IParser,
    private val noteDao: NoteDao,
    private val indexQueueDao: IndexQueueDao,
    private val syncScheduler: SyncScheduler,
    private val indexingScheduler: IndexingScheduler,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : INoteRepository {

    private val indexConverters = IndexTypeConverters()
    private val noteIdIndex = ConcurrentHashMap<String, String>()
    private val backgroundScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private enum class IndexScope {
        ACTIVE_NOTES,
        TRASH_ONLY,
    }

    private val primaryTrashDirPrefix = "${TRASH_DIR}/"
    private val legacyTrashDirPrefix = "${LEGACY_TRASH_DIR}/"
    private val trashDirPrefixes = listOf(primaryTrashDirPrefix, legacyTrashDirPrefix)

    override fun observeNotes(): Flow<List<NoteIndexEntry>> =
        noteDao.observeActiveNotes()
            .map { entities -> entities.map { it.toIndexEntry() } }

    override fun observeIndexingProgress(pass: Int): Flow<IndexingProgress> =
        combine(
            indexQueueDao.observeCountByPass(pass),
            indexQueueDao.observeCountByStatus(pass, IndexQueueStatus.DONE),
            indexQueueDao.observeCountByStatus(pass, IndexQueueStatus.FAILED),
        ) { total, done, failed ->
            IndexingProgress(completed = done + failed, total = total)
        }

    override fun observeIndexingFailures(pass: Int): Flow<Int> =
        indexQueueDao.observeCountByStatus(pass, IndexQueueStatus.FAILED)

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

    override suspend fun getNoteAvailability(path: String): Result<NoteAvailability> = withContext(ioDispatcher) {
        runCatching {
            noteDao.getNoteAvailability(path) ?: NoteAvailability.AVAILABLE
        }
    }

    override suspend fun requestNoteDownload(path: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            Log.d(TAG, "requestNoteDownload() - Scheduling sync for stub note: $path")
            syncScheduler.scheduleImmediate()
        }
    }

    override suspend fun reindex(): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val startedAt = SystemClock.elapsedRealtime()
            // Invalidate storage cache to ensure we see newly synced files
            storage.invalidateCache()
            val parsedNotes = scanNotes(IndexScope.ACTIVE_NOTES, shouldResolveLinks = false)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "reindex() - Parsed ${parsedNotes.size} notes in ${SystemClock.elapsedRealtime() - startedAt}ms")
            }

            noteIdIndex.clear()
            parsedNotes.forEach { note ->
                note.orgId?.let { idValue -> noteIdIndex[idValue] = note.id.path }
            }

            val resolvedNotes = mutableListOf<Note>()
            parsedNotes.forEach { note ->
                resolvedNotes += note.copy(links = resolveLinks(note.links))
            }

            noteDao.clearLinks()
            noteDao.clearLocalNotes()
            noteDao.insertNotes(
                resolvedNotes.map {
                    NoteEntity(
                        path = it.id.path,
                        title = it.title,
                        availability = NoteAvailability.AVAILABLE,
                        source = NoteSource.LOCAL,
                    )
                },
            )
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

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "reindex() - Completed in ${SystemClock.elapsedRealtime() - startedAt}ms")
            }
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

    override suspend fun resolveStorageUri(path: String) = storage.resolveUri(path)

    override suspend fun saveNote(path: String, content: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            Log.d(TAG, "saveNote() - Writing note to storage: path='$path'")
            storage.writeNote(path, content).getOrThrow()
            Log.d(TAG, "saveNote() - Note written successfully, scheduling reindex and sync")
            // Schedule progressive indexing and sync asynchronously to avoid blocking the save operation
            backgroundScope.launch {
                try {
                    indexingScheduler.schedulePass1()
                    Log.d(TAG, "saveNote() - Indexing scheduled, scheduling immediate sync")
                    syncScheduler.scheduleImmediate()
                    Log.d(TAG, "saveNote() - Immediate sync scheduled for path='$path'")
                } catch (e: Exception) {
                    Log.e(TAG, "saveNote() - Error during async indexing: ${e.message}", e)
                }
            }
            Unit  // Explicitly return Unit since Log.d returns Int
        }
    }

    override suspend fun deleteNoteSoft(path: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val content = storage.readNote(path).getOrThrow()
            val deletedAt = LocalDateTime.now()
            val trashPath = buildTrashPath(deletedAt = deletedAt, content = content)

            // Prefer atomic move when supported by the storage implementation.
            storage.renameNote(path, trashPath).recoverCatching {
                // Fallback for storages that can't rename across trees/providers.
                storage.writeNote(trashPath, content).getOrThrow()
                storage.deleteNote(path).getOrThrow()
            }.getOrThrow()

            writeTrashInfo(trashPath = trashPath, originalPath = path, deletedAt = deletedAt)

            indexingScheduler.schedulePass1()
            syncScheduler.scheduleImmediate()
        }
    }

    override suspend fun restoreNoteFromTrash(trashPath: String): Result<String> = withContext(ioDispatcher) {
        runCatching {
            val prefix = trashDirPrefixes.firstOrNull { trashPath.startsWith(it) }
                ?: error("Path '$trashPath' is not inside a trash directory")
            val inferredOriginalPath = trashPath.removePrefix(prefix)
            val metaOriginalPath = readTrashInfo(trashPath)?.originalPath
            val originalPath = when {
                metaOriginalPath != null -> metaOriginalPath
                inferredOriginalPath.contains('/') -> inferredOriginalPath // legacy layout: _trash_bin/<originalPath>
                else -> {
                    // Compatibility: older trash entries may not have metadata.
                    // Fall back to restoring into a sensible default directory.
                    val defaultDir = defaultRestoreDirPrefix()
                    if (defaultDir.isEmpty()) inferredOriginalPath else "$defaultDir$inferredOriginalPath"
                }
            }
            val restoreTarget = resolveRestorePath(originalPath)

            storage.renameNote(trashPath, restoreTarget).getOrThrow()
            deleteTrashInfoIfPresent(trashPath)
            indexingScheduler.schedulePass1()
            syncScheduler.scheduleImmediate()
            restoreTarget
        }
    }

    override suspend fun deleteNotePermanent(path: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            storage.deleteNote(path).getOrThrow()
            if (isTrashPath(path)) {
                deleteTrashInfoIfPresent(path)
            }
            indexingScheduler.schedulePass1()
            syncScheduler.scheduleImmediate()
        }
    }

    override suspend fun deleteNote(path: String): Result<Unit> {
        // Preserve legacy API while switching semantics to soft-delete.
        return deleteNoteSoft(path)
    }

    override suspend fun listAllNotes(): List<Note> = withContext(ioDispatcher) {
        scanNotes(IndexScope.ACTIVE_NOTES, shouldResolveLinks = true)
    }

    override suspend fun listTrashNotes(): List<Note> = withContext(ioDispatcher) {
        val notes = scanNotes(IndexScope.TRASH_ONLY, shouldResolveLinks = true)
        notes
            .map { note ->
                val deletedAt = readTrashInfo(note.id.path)?.deletedAtEpochMillis
                    ?: parseDeletedAtFromFilename(note.id.path.substringAfterLast('/'))
                note to (deletedAt ?: 0L)
            }
            .sortedByDescending { (_, deletedAt) -> deletedAt }
            .map { (note, _) -> note }
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

            noteDao.insertNotes(
                listOf(
                    NoteEntity(
                        path = resolved.id.path,
                        title = resolved.title,
                        availability = NoteAvailability.AVAILABLE,
                        source = NoteSource.LOCAL,
                    ),
                ),
            )
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
        val timestamp =     LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
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

    private suspend fun resolveLinks(links: List<NoteLink>): List<NoteLink> =
        links.mapNotNull { link ->
            val resolvedPath = when (val target = link.target) {
                is LinkTarget.Path -> target.value
                is LinkTarget.Id -> noteIdIndex[target.value]
                    ?: noteDao.findPathByOrgId(target.value)?.also { resolved -> noteIdIndex[target.value] = resolved }
            } ?: return@mapNotNull null
            link.copy(resolvedPath = resolvedPath)
        }

    private fun NoteEntity.toIndexEntry(): NoteIndexEntry =
        NoteIndexEntry(
            id = NoteId(path),
            title = title,
            fileTags = fileTags,
            deletedAt = deletedAt,
            linksReady = linksReady,
        )

    override suspend fun getAllTags(): Result<List<String>> = withContext(ioDispatcher) {
        runCatching {
            noteDao.getAllFileTagsSerialized()
                .flatMap { raw -> indexConverters.toFileTags(raw) }
                .distinct()
                .sorted()
        }
    }

    override suspend fun updateNoteProperties(
        path: String,
        properties: Map<String, String>,
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val originalContent = storage.readNote(path).getOrThrow()
            val existingProperties = parser.parse(content = originalContent, path = path).properties
            val newContent = updatePropertiesInContent(
                content = originalContent,
                properties = properties,
                existingProperties = existingProperties,
            )
            storage.writeNote(path, newContent).getOrThrow()

            syncScheduler.scheduleImmediate()
            Log.d(TAG, "updateNoteProperties() - Properties updated for $path")
            Unit
        }
    }

    override suspend fun updateNoteTags(
        path: String,
        tags: List<String>,
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val originalContent = storage.readNote(path).getOrThrow()
            val normalizedTags = tags.asSequence()
                .map(::normalizeTag)
                .filter { it.isNotEmpty() }
                .distinct()
                .toList()
            val newContent = updateFileTagsInContent(originalContent, normalizedTags)
            storage.writeNote(path, newContent).getOrThrow()

            syncScheduler.scheduleImmediate()
            Log.d(TAG, "updateNoteTags() - Tags updated for $path: $normalizedTags")
            Unit
        }
    }

    /**
     * Updates or creates :PROPERTIES: drawer with given properties.
     * Preserves ID property if it exists and isn't in the new map.
     */
    private fun updatePropertiesInContent(
        content: String,
        properties: Map<String, String>,
        existingProperties: Map<String, String>,
    ): String {
        val newline = if (content.contains("\r\n")) "\r\n" else "\n"

        val propertiesDrawerRegex = Regex(
            pattern = """^[ \t]*:PROPERTIES:\s*$\r?\n[\s\S]*?\r?\n[ \t]*:END:\s*(?:\r?\n)?""",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
        )
        val existingMatch = propertiesDrawerRegex.find(content)
        val hadTrailingNewline = existingMatch?.value?.endsWith("\n") == true

        val cleaned = properties
            .mapNotNull { (key, value) ->
                val normalizedKey = normalizePropertyKey(key) ?: return@mapNotNull null
                val trimmedValue = value.trim()
                if (trimmedValue.isBlank()) return@mapNotNull null
                normalizedKey to trimmedValue
            }
            .toMap()

        val preservedId = existingProperties["ID"]?.trim()?.takeIf { it.isNotEmpty() }
        val finalProperties = if (cleaned["ID"].isNullOrEmpty() && !preservedId.isNullOrEmpty()) {
            cleaned + ("ID" to preservedId)
        } else {
            cleaned
        }

        val drawerLines = finalProperties.entries
            .sortedBy { it.key }
            .map { (key, value) -> ":$key: $value" }

        val newDrawer = if (drawerLines.isEmpty()) {
            ""
        } else {
            (listOf(":PROPERTIES:") + drawerLines + listOf(":END:")).joinToString(newline)
        }

        if (existingMatch != null) {
            if (newDrawer.isEmpty()) {
                return content.replaceRange(existingMatch.range, "")
            }
            val replacement = if (hadTrailingNewline) newDrawer + newline else newDrawer
            return content.replaceRange(existingMatch.range, replacement)
        }

        if (newDrawer.isEmpty()) return content

        val titleRegex = Regex("""(?im)^[ \t]*#\+title:.*$""")
        val titleMatch = titleRegex.find(content)
        if (titleMatch != null) {
            val lineBreakIndex = content.indexOf('\n', startIndex = titleMatch.range.last)
            val insertAt = if (lineBreakIndex >= 0) lineBreakIndex + 1 else content.length
            return content.substring(0, insertAt) + newDrawer + newline + content.substring(insertAt)
        }

        return newDrawer + newline + content
    }

    /**
     * Updates or creates #+filetags: line.
     */
    private fun updateFileTagsInContent(content: String, tags: List<String>): String {
        val newline = if (content.contains("\r\n")) "\r\n" else "\n"

        val filetagsRegex = Regex(
            pattern = """^#\+filetags:\s*.*(?:\r?\n)?""",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
        )
        val existingMatch = filetagsRegex.find(content)
        val hadTrailingNewline = existingMatch?.value?.endsWith("\n") == true

        val newLine = if (tags.isEmpty()) "" else "#+filetags: :${tags.joinToString(":")}:"

        if (existingMatch != null) {
            if (newLine.isEmpty()) {
                return content.replaceRange(existingMatch.range, "")
            }
            val replacement = if (hadTrailingNewline) newLine + newline else newLine
            return content.replaceRange(existingMatch.range, replacement)
        }

        if (newLine.isEmpty()) return content

        val titleRegex = Regex("""(?im)^[ \t]*#\+title:.*$""")
        val titleMatch = titleRegex.find(content)
        if (titleMatch != null) {
            val lineBreakIndex = content.indexOf('\n', startIndex = titleMatch.range.last)
            val insertAt = if (lineBreakIndex >= 0) lineBreakIndex + 1 else content.length
            return content.substring(0, insertAt) + newLine + newline + content.substring(insertAt)
        }

        return newLine + newline + content
    }

    private fun normalizeTag(tag: String): String =
        tag.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9_-]"), "")

    private fun normalizePropertyKey(key: String): String? {
        val cleaned = key.trim().uppercase().replace(":", "")
        return cleaned.takeIf { it.isNotEmpty() }
    }

    companion object {
        private const val TAG = "NoteRepositoryImpl"
        // Current on-disk trash folder used by the app.
        private const val TRASH_DIR = "_trash_bin"
        // Compatibility with older/newer conventions.
        private const val LEGACY_TRASH_DIR = "_trash"
    }

    private suspend fun scanNotes(scope: IndexScope, shouldResolveLinks: Boolean): List<Note> {
        val startedAt = SystemClock.elapsedRealtime()
        val allPaths = storage.listNotes().getOrThrow()
        val filteredPaths = when (scope) {
            IndexScope.ACTIVE_NOTES -> allPaths.filterNot { isTrashPath(it) }
            IndexScope.TRASH_ONLY -> allPaths.filter { isTrashPath(it) }
        }
        val notes = mutableListOf<Note>()
        filteredPaths.forEachIndexed { index, path ->
            if (BuildConfig.DEBUG && index > 0 && index % 100 == 0) {
                Log.d(
                    TAG,
                    "scanNotes() - Parsed $index/${filteredPaths.size} in ${SystemClock.elapsedRealtime() - startedAt}ms",
                )
            }
            val content = storage.readNote(path).getOrThrow()
            val parsed = parser.parse(content = content, path = path)
            val note = if (shouldResolveLinks) parsed.copy(links = resolveLinks(parsed.links)) else parsed
            notes += note
        }
        return notes
    }

    private suspend fun resolveRestorePath(originalPath: String): String {
        // If original path does not exist, restore directly.
        val originalExists = storage.readNote(originalPath).isSuccess
        if (!originalExists) return originalPath

        // On conflict, append " (n)" before the extension, similar to desktop note apps.
        val directory = originalPath.substringBeforeLast('/', "")
        val filename = originalPath.substringAfterLast('/')
        val baseName = filename.substringBeforeLast('.', filename)
        val extension = filename.substringAfterLast('.', "")

        var counter = 1
        while (true) {
            val candidateName = buildString {
                append(baseName)
                append(" (")
                append(counter)
                append(")")
                if (extension.isNotEmpty()) {
                    append('.')
                    append(extension)
                }
            }
            val candidatePath = if (directory.isEmpty()) candidateName else "$directory/$candidateName"
            val exists = storage.readNote(candidatePath).isSuccess
            if (!exists) {
                return candidatePath
            }
            counter++
        }
    }

    private fun buildTrashPath(originalPath: String): String {
        // Legacy trash layout: original path mirrored under trash.
        return "$TRASH_DIR/$originalPath"
    }

    private suspend fun buildTrashPath(deletedAt: LocalDateTime, content: String): String {
        val timestamp = deletedAt.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        val title = runCatching { OrgFileUtils.extractTitle(content) }.getOrDefault("untitled")
        val slug = OrgFileUtils.slugifyTitle(title)
        val baseFilename = "${timestamp}_${slug}.org"
        return resolveUniqueTrashPath("$TRASH_DIR/$baseFilename")
    }

    private suspend fun resolveUniqueTrashPath(initialPath: String): String {
        if (storage.readNote(initialPath).isFailure) return initialPath
        val directory = initialPath.substringBeforeLast('/', "")
        val filename = initialPath.substringAfterLast('/')
        val baseName = filename.substringBeforeLast('.', filename)
        val extension = filename.substringAfterLast('.', "")
        var counter = 1
        while (true) {
            val candidateName = buildString {
                append(baseName)
                append(" (")
                append(counter)
                append(")")
                if (extension.isNotEmpty()) {
                    append('.')
                    append(extension)
                }
            }
            val candidatePath = if (directory.isEmpty()) candidateName else "$directory/$candidateName"
            if (storage.readNote(candidatePath).isFailure) return candidatePath
            counter++
        }
    }

    private data class TrashInfo(
        val originalPath: String,
        val deletedAtEpochMillis: Long,
    )

    private fun trashInfoPath(trashPath: String): String {
        val prefix = trashDirPrefixes.firstOrNull { trashPath.startsWith(it) }
            ?: error("Path '$trashPath' is not inside a trash directory")
        val dir = prefix.removeSuffix("/")
        val filename = trashPath.substringAfterLast('/')
        return "$dir/.meta/$filename.trashinfo"
    }

    private suspend fun writeTrashInfo(trashPath: String, originalPath: String, deletedAt: LocalDateTime) {
        val metaPath = trashInfoPath(trashPath)
        val deletedAtMillis = deletedAt.toInstant(ZoneOffset.UTC).toEpochMilli()
        val safeOriginal = java.net.URLEncoder.encode(originalPath, Charsets.UTF_8.name())
        val content = "originalPath=$safeOriginal\ndeletedAtEpochMillis=$deletedAtMillis\n"
        storage.writeNote(metaPath, content).getOrThrow()
    }

    private suspend fun readTrashInfo(trashPath: String): TrashInfo? {
        val metaPath = runCatching { trashInfoPath(trashPath) }.getOrNull() ?: return null
        val raw = storage.readNote(metaPath).getOrNull() ?: return null
        val map = raw.lineSequence()
            .mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                line.substring(0, idx) to line.substring(idx + 1)
            }
            .toMap()
        val encodedPath = map["originalPath"] ?: return null
        val decodedPath = java.net.URLDecoder.decode(encodedPath, Charsets.UTF_8.name())
        val deletedAtMillis = map["deletedAtEpochMillis"]?.toLongOrNull() ?: return null
        return TrashInfo(originalPath = decodedPath, deletedAtEpochMillis = deletedAtMillis)
    }

    private suspend fun deleteTrashInfoIfPresent(trashPath: String) {
        val metaPath = runCatching { trashInfoPath(trashPath) }.getOrNull() ?: return
        storage.deleteNote(metaPath).getOrThrow()
    }

    private fun isTrashPath(path: String): Boolean = trashDirPrefixes.any { path.startsWith(it) }

    private suspend fun defaultRestoreDirPrefix(): String {
        // Prefer restoring into "notes/" when the repository appears to be using that convention.
        val nonTrashPaths = storage.listNotes().getOrNull()
            ?.filterNot(::isTrashPath)
            .orEmpty()
        return if (nonTrashPaths.any { it.startsWith("notes/") }) "notes/" else ""
    }

    private fun parseDeletedAtFromFilename(filename: String): Long? {
        // Best-effort: accept leading digits like yyyyMMddHHmmss or yyyyMMddHH.
        val digits = filename.takeWhile { it.isDigit() }
        return when (digits.length) {
            10 -> runCatching {
                val dt = LocalDateTime.parse(digits, DateTimeFormatter.ofPattern("yyyyMMddHH"))
                dt.toInstant(ZoneOffset.UTC).toEpochMilli()
            }.getOrNull()
            14 -> runCatching {
                val dt = LocalDateTime.parse(digits, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                dt.toInstant(ZoneOffset.UTC).toEpochMilli()
            }.getOrNull()
            else -> null
        }
    }
}
