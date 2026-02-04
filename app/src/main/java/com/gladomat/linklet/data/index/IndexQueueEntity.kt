package com.gladomat.linklet.data.index

import androidx.room.Entity

enum class IndexQueueStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED,
}

enum class IndexQueueOperation {
    UPSERT,
    DELETE,
}

@Entity(tableName = "index_queue", primaryKeys = ["path", "pass"])
data class IndexQueueEntity(
    val path: String,
    val pass: Int,
    val operation: IndexQueueOperation = IndexQueueOperation.UPSERT,
    val status: IndexQueueStatus = IndexQueueStatus.PENDING,
    val attempts: Int = 0,
    val lastError: String? = null,
    val updatedAtEpochMillis: Long? = null,
    val expectedMtime: Long? = null,
    val expectedSize: Long? = null,
    val expectedHash: String? = null,
    val lockedAtEpochMillis: Long? = null,
    val nextAttemptAtEpochMillis: Long? = null,
)
