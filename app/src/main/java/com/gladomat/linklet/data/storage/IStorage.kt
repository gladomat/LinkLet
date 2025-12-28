package com.gladomat.linklet.data.storage

import android.net.Uri

/**
 * Abstraction over note persistence.
 */
interface IStorage {
    /**
     * Lists all note paths relative to the storage root.
     */
    suspend fun listNotes(): Result<List<String>>

    /**
     * Lists all file paths relative to the storage root.
     */
    suspend fun listFiles(): Result<List<String>>

    /**
     * Reads full note content for the given path.
     */
    suspend fun readNote(path: String): Result<String>

    /**
     * Reads raw bytes for the given path.
     */
    suspend fun readFileBytes(path: String): Result<ByteArray>

    /**
     * Reads lightweight file metadata for the given path.
     */
    suspend fun statNote(path: String): Result<StorageFileStat>

    /**
     * Writes note content at the given path, creating intermediary directories if needed.
     */
    suspend fun writeNote(path: String, content: String): Result<Unit>

    /**
     * Writes raw bytes at the given path, creating intermediary directories if needed.
     */
    suspend fun writeFileBytes(path: String, content: ByteArray): Result<Unit>

    /**
     * Deletes the note at the given path.
     */
    suspend fun deleteNote(path: String): Result<Unit>

    /**
     * Renames/moves a note from oldPath to newPath.
     */
    suspend fun renameNote(oldPath: String, newPath: String): Result<Unit>

    /**
     * Resolves a storage-relative path (note or attachment) to a readable [Uri].
     */
    suspend fun resolveUri(path: String): Result<Uri>

    /**
     * Invalidates any cached directory listings or file metadata.
     * This should be called after external operations (like sync) that modify
     * the storage without going through this interface.
     */
    suspend fun invalidateCache()
}

data class StorageFileStat(
    val lastModifiedEpochMillis: Long?,
    val sizeBytes: Long?,
)
