package com.gladomat.linklet.data.sync.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "operation_journal",
    indices = [
        Index(value = ["rootId", "status", "nextAttemptAtEpochMillis"]),
        Index(value = ["rootId", "path", "status"]),
    ],
)
data class OperationJournalEntity(
    @PrimaryKey val operationId: String,
    val rootId: String,
    val path: String,
    val operationType: String,
    val status: String,
    val priority: Int = 0,
    val attempts: Int = 0,
    val nextAttemptAtEpochMillis: Long? = null,
    val expectedRemoteEtag: String? = null,
    val expectedLocalHash: String? = null,
    val remoteId: String? = null,
    val remoteFingerprint: String? = null,
    val lastError: String? = null,
    val claimedByWorker: String? = null,
    val claimedAtEpochMillis: Long? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
