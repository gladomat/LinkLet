package com.gladomat.linklet.data.sync

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class NoteLifecycle {
    ACTIVE,
    DELETED_LOCALLY, // User deleted on phone, waiting to push to server
    DELETED_REMOTELY // Server file is gone, waiting for user decision (or auto-archive)
}

/**
 * Tracks sync metadata for a single note path.
 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val path: String,
    val lifecycle: NoteLifecycle = NoteLifecycle.ACTIVE,
    val remoteId: String? = null,
    val remoteFingerprint: String? = null, // This is the ETag
    val localContentHash: String? = null, // MD5 of local file
    val lastKnownHash: String? = null, // Previous MD5
    val remoteETag: String? = null,       // Last known ETag from server (redundant with remoteFingerprint, but explicit in reqs. We will assume remoteFingerprint IS the ETag)
    val lastSyncedAtEpochMillis: Long? = null,
    val deletedAt: Long? = null,
    val pendingAction: SyncPendingAction = SyncPendingAction.NONE,
    val lastError: String? = null,
)

enum class SyncPendingAction {
    NONE,
    UPLOAD,
    DOWNLOAD,
    DELETE,
    CONFLICT,
}

/**
 * Tracks the last known status from a sync run.
 */
data class SyncState(
    val path: String,
    val lifecycle: NoteLifecycle,
    val remoteId: String?,
    val remoteFingerprint: String?,
    val localContentHash: String?,
    val lastKnownHash: String?,
    val lastSyncedAtEpochMillis: Long?,
    val pendingAction: SyncPendingAction,
    val lastError: String?,
)

fun SyncStateEntity.toDomain(): SyncState = SyncState(
    path = path,
    lifecycle = lifecycle,
    remoteId = remoteId,
    remoteFingerprint = remoteFingerprint,
    localContentHash = localContentHash,
    lastKnownHash = lastKnownHash,
    lastSyncedAtEpochMillis = lastSyncedAtEpochMillis,
    pendingAction = pendingAction,
    lastError = lastError,
)

class SyncStateTypeConverters {
    @TypeConverter
    fun toAction(value: String?): SyncPendingAction =
        value?.let { runCatching { SyncPendingAction.valueOf(it) }.getOrDefault(SyncPendingAction.NONE) }
            ?: SyncPendingAction.NONE

    @TypeConverter
    fun fromAction(action: SyncPendingAction?): String? = action?.name

    @TypeConverter
    fun toLifecycle(value: String?): NoteLifecycle =
        value?.let { runCatching { NoteLifecycle.valueOf(it) }.getOrDefault(NoteLifecycle.ACTIVE) }
            ?: NoteLifecycle.ACTIVE

    @TypeConverter
    fun fromLifecycle(lifecycle: NoteLifecycle?): String? = lifecycle?.name
}
