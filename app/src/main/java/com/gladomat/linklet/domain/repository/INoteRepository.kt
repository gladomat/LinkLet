package com.gladomat.linklet.domain.repository

import com.gladomat.linklet.data.model.Note
import kotlinx.coroutines.flow.Flow

/**
 * Contract for retrieving and managing notes.
 */
interface INoteRepository {
    fun observeNotes(): Flow<List<Note>>
    suspend fun listNotes(): Result<List<Note>>
    suspend fun getNote(path: String): Result<Note>
    suspend fun reindex(): Result<Unit>
    suspend fun getBacklinks(path: String): Result<List<LinkEntityDto>>
    suspend fun saveNote(path: String, content: String): Result<Unit>
}

data class LinkEntityDto(
    val source: String,
    val target: String,
    val alias: String?,
)
