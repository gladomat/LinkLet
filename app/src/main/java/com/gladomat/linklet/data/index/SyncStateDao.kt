package com.gladomat.linklet.data.index

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gladomat.linklet.data.sync.SyncPendingAction
import com.gladomat.linklet.data.sync.SyncStateEntity

@Dao
interface SyncStateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SyncStateEntity)

    @Query("SELECT * FROM sync_state WHERE path = :path LIMIT 1")
    suspend fun getState(path: String): SyncStateEntity?

    @Query(
        """
        SELECT * FROM sync_state
        WHERE pendingAction != :none
        """,
    )
    suspend fun getPendingStates(none: SyncPendingAction = SyncPendingAction.NONE): List<SyncStateEntity>

    @Query(
        """
        UPDATE sync_state
        SET pendingAction = :action,
            lastError = :lastError
        WHERE path = :path
        """,
    )
    suspend fun updatePendingAction(
        path: String,
        action: SyncPendingAction,
        lastError: String?,
    )

    @Query("DELETE FROM sync_state WHERE path = :path")
    suspend fun deleteState(path: String)

    @Query("SELECT * FROM sync_state")
    suspend fun getAllStates(): List<SyncStateEntity>

    @Query("SELECT COUNT(*) FROM sync_state")
    suspend fun count(): Int

    @Query("DELETE FROM sync_state")
    suspend fun clearAllStates()
}
