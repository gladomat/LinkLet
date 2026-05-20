package com.gladomat.linklet.data.index

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface IndexQueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<IndexQueueEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: IndexQueueEntity)

    @Query(
        """
        SELECT COUNT(*) FROM index_queue
        WHERE pass = :pass AND status = :status
        """,
    )
    suspend fun countByStatus(pass: Int, status: IndexQueueStatus): Int

    @Query(
        """
        SELECT COUNT(*) FROM index_queue
        WHERE pass = :pass AND status = :status
        """,
    )
    fun observeCountByStatus(pass: Int, status: IndexQueueStatus): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM index_queue
        WHERE pass = :pass
        """,
    )
    suspend fun countByPass(pass: Int): Int

    @Query(
        """
        SELECT COUNT(*) FROM index_queue
        WHERE pass = :pass
        """,
    )
    fun observeCountByPass(pass: Int): Flow<Int>

    @Query(
        """
        SELECT * FROM index_queue
        WHERE pass = :pass AND status = :status
        ORDER BY path
        """,
    )
    suspend fun listByStatus(pass: Int, status: IndexQueueStatus = IndexQueueStatus.PENDING): List<IndexQueueEntity>

    @Query(
        """
        UPDATE index_queue
        SET status = 'FAILED',
            lastError = :reason,
            lockedAtEpochMillis = NULL,
            nextAttemptAtEpochMillis = NULL,
            updatedAtEpochMillis = :now
        WHERE pass = :pass
          AND status = 'RUNNING'
          AND lockedAtEpochMillis IS NOT NULL
          AND lockedAtEpochMillis <= :staleBefore
        """,
    )
    suspend fun failStaleRunning(pass: Int, staleBefore: Long, now: Long, reason: String): Int

    @Query(
        """
        SELECT * FROM index_queue
        WHERE pass = :pass AND (
            status = 'PENDING' OR
            (status = 'RUNNING' AND lockedAtEpochMillis IS NOT NULL AND lockedAtEpochMillis <= :leaseExpiry) OR
            (status = 'FAILED' AND nextAttemptAtEpochMillis IS NOT NULL AND nextAttemptAtEpochMillis <= :now)
        )
        ORDER BY
            CASE
                WHEN status = 'PENDING' THEN 0
                WHEN status = 'RUNNING' THEN 1
                WHEN status = 'FAILED' THEN 2
                ELSE 3
            END,
            updatedAtEpochMillis DESC
        LIMIT 1
        """,
    )
    suspend fun findClaimCandidate(pass: Int, now: Long, leaseExpiry: Long): IndexQueueEntity?

    @Query(
        """
        UPDATE index_queue
        SET status = 'RUNNING',
            lockedAtEpochMillis = :now,
            attempts = attempts + 1,
            updatedAtEpochMillis = :now
        WHERE path = :path AND pass = :pass AND (
            status = 'PENDING' OR
            (status = 'RUNNING' AND lockedAtEpochMillis IS NOT NULL AND lockedAtEpochMillis <= :leaseExpiry) OR
            (status = 'FAILED' AND nextAttemptAtEpochMillis IS NOT NULL AND nextAttemptAtEpochMillis <= :now)
        )
        """,
    )
    suspend fun claim(path: String, pass: Int, now: Long, leaseExpiry: Long): Int

    @Transaction
    suspend fun claimNext(pass: Int, now: Long, leaseTimeoutMillis: Long): IndexQueueEntity? {
        val leaseExpiry = now - leaseTimeoutMillis
        val candidate = findClaimCandidate(pass = pass, now = now, leaseExpiry = leaseExpiry) ?: return null
        val claimed = claim(path = candidate.path, pass = pass, now = now, leaseExpiry = leaseExpiry)
        return if (claimed == 1) candidate else null
    }
}
