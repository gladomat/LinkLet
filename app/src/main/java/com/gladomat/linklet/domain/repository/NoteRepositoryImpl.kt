package com.gladomat.linklet.domain.repository

import com.gladomat.linklet.data.index.LinkEntity
import com.gladomat.linklet.data.index.NoteDao
import com.gladomat.linklet.data.index.NoteEntity
import com.gladomat.linklet.data.model.Note
import com.gladomat.linklet.data.parser.IParser
import com.gladomat.linklet.data.storage.IStorage
import com.gladomat.linklet.domain.repository.LinkEntityDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

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

    override fun observeNotes(): Flow<List<Note>> = notesState.asStateFlow()

    override suspend fun listNotes(): Result<List<Note>> {
        return withContext(ioDispatcher) {
            runCatching {
                val entities = noteDao.getAllNotes()
                entities.map { entity ->
                    val content = storage.readNote(entity.path).getOrThrow()
                    parser.parse(content = content, path = entity.path)
                }
            }
        }
    }

    override suspend fun getNote(path: String): Result<Note> {
        return storage.readNote(path).mapCatching { content ->
            parser.parse(content = content, path = path)
        }
    }

    override suspend fun reindex(): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val notePaths = storage.listNotes().getOrThrow()
            val notes = notePaths.map { path ->
                val content = storage.readNote(path).getOrThrow()
                parser.parse(content = content, path = path)
            }

            noteDao.clearLinks()
            noteDao.clearNotes()
            noteDao.insertNotes(notes.map { NoteEntity(path = it.id.path, title = it.title) })
            noteDao.insertLinks(
                notes.flatMap { note ->
                    note.links.map { link ->
                        LinkEntity(
                            source = note.id.path,
                            target = link.toPath,
                            alias = link.label,
                        )
                    }
                },
            )

            notesState.update { notes }
        }
    }

    override suspend fun getBacklinks(path: String): Result<List<LinkEntityDto>> = withContext(ioDispatcher) {
        runCatching {
            noteDao.getBacklinks(path).map {
                LinkEntityDto(
                    source = it.source,
                    target = it.target,
                    alias = it.alias,
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
}
