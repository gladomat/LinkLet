package com.gladomat.linklet.data.sync

/**
 * Represents the last sync issue that should be surfaced to the user.
 */
enum class SyncStatusType {
    DIRECTORY_CHANGED,
    REQUIRES_CONFIRMATION,
}

/**
 * Snapshot of an actionable sync status for UI + notification surfaces.
 */
data class SyncStatus(
    val type: SyncStatusType,
    val title: String,
    val message: String,
    val oldPath: String?,
    val newPath: String?,
    val requiresAction: Boolean,
    val updatedAtEpochMillis: Long,
)
