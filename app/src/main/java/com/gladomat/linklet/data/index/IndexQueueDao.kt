package com.gladomat.linklet.data.index

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
}
