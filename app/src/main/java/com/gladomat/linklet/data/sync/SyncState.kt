package com.gladomat.linklet.data.sync

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

/**
 * Tracks sync metadata for a single note path.
 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val path: String,
    val remoteId: String? = null,
    val remoteFingerprint: String? = null,
    val lastKnownHash: String? = null,
    val lastSyncedAtEpochMillis: Long? = null,
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
    val remoteId: String?,
    val remoteFingerprint: String?,
    val lastKnownHash: String?,
    val lastSyncedAtEpochMillis: Long?,
    val pendingAction: SyncPendingAction,
    val lastError: String?,
)

fun SyncStateEntity.toDomain(): SyncState = SyncState(
    path = path,
    remoteId = remoteId,
    remoteFingerprint = remoteFingerprint,
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
}
