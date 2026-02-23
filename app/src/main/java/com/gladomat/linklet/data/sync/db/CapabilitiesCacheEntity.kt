package com.gladomat.linklet.data.sync.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "capabilities_cache")
data class CapabilitiesCacheEntity(
    @PrimaryKey val rootId: String,
    val supportsSearch: Boolean,
    val supportsTrashbin: Boolean,
    val supportsFileId: Boolean,
    val checkedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
)
