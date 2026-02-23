package com.gladomat.linklet.data.sync.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_watermark")
data class SyncWatermarkEntity(
    @PrimaryKey val rootId: String,
    val lastFullSweepServerTimeEpochMillis: Long? = null,
    val lastDeltaServerTimeEpochMillis: Long? = null,
    val deltaValidityState: String? = null,
    val deltaValidityReason: String? = null,
    val updatedAtEpochMillis: Long,
)
