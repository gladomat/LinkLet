package com.gladomat.linklet.data.sync.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncWatermarkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncWatermarkEntity)

    @Query("SELECT * FROM sync_watermark WHERE rootId = :rootId LIMIT 1")
    suspend fun get(rootId: String): SyncWatermarkEntity?

    @Query("DELETE FROM sync_watermark WHERE rootId = :rootId")
    suspend fun delete(rootId: String)
}
