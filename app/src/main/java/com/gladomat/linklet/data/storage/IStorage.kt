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

    /**
     * Renames/moves a note from oldPath to newPath.
     */
    suspend fun renameNote(oldPath: String, newPath: String): Result<Unit>

    /**
     * Invalidates any cached directory listings or file metadata.
     * This should be called after external operations (like sync) that modify
     * the storage without going through this interface.
     */
    suspend fun invalidateCache()
}
