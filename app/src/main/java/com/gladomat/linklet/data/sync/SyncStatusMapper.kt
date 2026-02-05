package com.gladomat.linklet.data.sync

/**
 * Maps sync exceptions to user-visible sync status entries.
 */
object SyncStatusMapper {

    /**
     * Builds a status entry for directory change errors.
     */
    fun fromDirectoryChanged(
        exception: SyncDirectoryChangedException,
        nowEpochMillis: Long,
    ): SyncStatus = SyncStatus(
        type = SyncStatusType.DIRECTORY_CHANGED,
        title = "Sync blocked",
        message = exception.message ?: "Directory changed",
        oldPath = exception.oldPath,
        newPath = exception.newPath,
        requiresAction = true,
        updatedAtEpochMillis = nowEpochMillis,
    )
}
