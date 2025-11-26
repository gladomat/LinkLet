package com.gladomat.linklet.data.sync

/**
 * Provides remote metadata and content for syncing notes.
 */
interface RemoteSyncProvider {
    val name: String

    suspend fun listRemoteNotes(): Result<List<RemoteNoteMetadata>>

    suspend fun download(path: String, remoteId: String): Result<java.io.InputStream>

    suspend fun upload(
        path: String,
        content: java.io.InputStream,
        remoteId: String?,
        expectedFingerprint: String?,
    ): Result<RemoteUploadResult>

    suspend fun delete(path: String, remoteId: String, fingerprint: String?): Result<Unit>
}

data class RemoteNoteMetadata(
    val remoteId: String,
    val path: String,
    val fingerprint: String?,
    val lastModifiedEpochMillis: Long?,
)

data class RemoteUploadResult(
    val remoteId: String,
    val fingerprint: String?,
)
