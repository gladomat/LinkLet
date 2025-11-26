package com.gladomat.linklet.data.storage

/**
 * Abstraction over note persistence.
 */
interface IStorage {
    /**
     * Lists all note paths relative to the storage root.
     */
    suspend fun listNotes(): Result<List<String>>

    /**
     * Reads full note content for the given path.
     */
    suspend fun readNote(path: String): Result<String>

    /**
     * Writes note content at the given path, creating intermediary directories if needed.
     */
    suspend fun writeNote(path: String, content: String): Result<Unit>

    /**
     * Deletes the note at the given path.
     */
    suspend fun deleteNote(path: String): Result<Unit>
}
