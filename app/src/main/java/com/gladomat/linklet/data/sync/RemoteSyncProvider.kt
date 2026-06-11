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
    /** Remote file size in bytes (WebDAV getcontentlength), if reported. */
    val sizeBytes: Long? = null,
    /**
     * Server-advertised content checksums, keyed by uppercase algorithm name (e.g. "SHA-256",
     * "SHA-1", "MD5") with a lowercase hex value. Populated best-effort from PROPFIND when the
     * server exposes them (e.g. ownCloud/Nextcloud). Used on directory-change adoption to decide
     * whether a local file already matches the remote without downloading it.
     */
    val checksums: Map<String, String>? = null,
)

data class RemoteUploadResult(
    val remoteId: String,
    val fingerprint: String?,
)
