package com.gladomat.linklet.data.index

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores persistent indexing checkpoints for resumability and gating.
 */
@Entity(tableName = "indexing_state")
data class IndexingStateEntity(
    @PrimaryKey val id: Int = 1,
    val lastScanAtEpochMillis: Long? = null,
    val lastPass1EnqueueAtEpochMillis: Long? = null,
    val lastPass2RunAtEpochMillis: Long? = null,
)
