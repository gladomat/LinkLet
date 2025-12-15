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
    /**
     * Soft-deletes a note by moving it into the trash area while keeping its content on disk.
     * This mirrors modern note apps where deletion is reversible until the trash is emptied.
     */
    suspend fun deleteNoteSoft(path: String): Result<Unit>

    /**
     * Restores a note from the trash back to its original (or conflict-resolved) path.
     * Returns the restored path, which may differ from the original when name conflicts occur.
     */
    suspend fun restoreNoteFromTrash(trashPath: String): Result<String>

    /**
     * Permanently deletes a note from storage (used for "Delete Forever" semantics).
     */
    suspend fun deleteNotePermanent(path: String): Result<Unit>

    /**
     * Legacy delete API used by existing callers.
     * Now implemented as a soft delete for safety.
     */
    suspend fun deleteNote(path: String): Result<Unit>

    /**
     * Lists all active (non-trash) notes.
     */
    suspend fun listAllNotes(): List<Note>

    /**
     * Lists notes that live in the trash area only.
     */
    suspend fun listTrashNotes(): List<Note>

    /** Duplicates a note with a new timestamp-based filename. Returns the new path. */
    suspend fun duplicateNote(path: String): Result<String>
    /** Renames a note file. Backlinks remain intact because they use org-roam IDs. */
    suspend fun renameNote(oldPath: String, newPath: String): Result<Unit>
    /** Returns all unique tags from all notes in the repository. */
    suspend fun getAllTags(): Result<List<String>>
    /** Updates properties in a note's :PROPERTIES: drawer. */
    suspend fun updateNoteProperties(path: String, properties: Map<String, String>): Result<Unit>
    /** Updates the #+filetags: line in a note. */
    suspend fun updateNoteTags(path: String, tags: List<String>): Result<Unit>
}

data class LinkEntityDto(
    val source: String,
    val target: String,
    val alias: String?,
    val sourceTitle: String?,
)
