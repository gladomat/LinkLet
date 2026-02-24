package com.gladomat.linklet.data.sync.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OperationJournalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: OperationJournalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<OperationJournalEntity>)

    @Query("SELECT * FROM operation_journal WHERE operationId = :operationId LIMIT 1")
    suspend fun getById(operationId: String): OperationJournalEntity?

    @Query(
        """
        SELECT * FROM operation_journal
        WHERE rootId = :rootId
          AND status = :status
          AND (nextAttemptAtEpochMillis IS NULL OR nextAttemptAtEpochMillis <= :nowEpochMillis)
        ORDER BY priority DESC, createdAtEpochMillis ASC
        LIMIT :limit
        """,
    )
    suspend fun findReady(
        rootId: String,
        status: String,
        nowEpochMillis: Long,
        limit: Int,
    ): List<OperationJournalEntity>

    @Query(
        """
        UPDATE operation_journal
        SET status = :newStatus,
            lastError = :lastError,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE operationId = :operationId
        """,
    )
    suspend fun updateStatus(
        operationId: String,
        newStatus: String,
        lastError: String?,
        updatedAtEpochMillis: Long,
    )

    @Query(
        """
        UPDATE operation_journal
        SET status = :newStatus,
            claimedByWorker = :claimedByWorker,
            claimedAtEpochMillis = :claimedAtEpochMillis,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE operationId = :operationId
        """,
    )
    suspend fun claim(
        operationId: String,
        newStatus: String,
        claimedByWorker: String,
        claimedAtEpochMillis: Long,
        updatedAtEpochMillis: Long,
    )

    @Query("DELETE FROM operation_journal WHERE rootId = :rootId")
    suspend fun clearRoot(rootId: String)
}
