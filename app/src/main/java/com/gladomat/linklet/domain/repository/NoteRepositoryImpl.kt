package com.gladomat.linklet.domain.repository

import com.gladomat.linklet.data.index.LinkEntity
import com.gladomat.linklet.data.index.NoteDao
import com.gladomat.linklet.data.index.NoteEntity
import com.gladomat.linklet.data.model.Note
import com.gladomat.linklet.data.model.NoteLink
import com.gladomat.linklet.data.parser.IParser
import com.gladomat.linklet.data.storage.IStorage
import com.gladomat.linklet.data.model.LinkTarget
import com.gladomat.linklet.domain.repository.LinkEntityDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Default repository implementation backed by [IStorage] and [IParser].
 */
class NoteRepositoryImpl(
    private val storage: IStorage,
    private val parser: IParser,
    private val noteDao: NoteDao,
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
            storage.writeNote(path, content).getOrThrow()
            reindex().getOrThrow()
        }
    }

    private fun resolveLinks(links: List<NoteLink>): List<NoteLink> =
        links.mapNotNull { link ->
            val resolvedPath = when (val target = link.target) {
                is LinkTarget.Path -> target.value
                is LinkTarget.Id -> noteIdIndex[target.value]
            } ?: return@mapNotNull null
            link.copy(resolvedPath = resolvedPath)
        }
}
