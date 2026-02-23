package com.gladomat.linklet.data.sync.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CapabilitiesCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CapabilitiesCacheEntity)

    @Query("SELECT * FROM capabilities_cache WHERE rootId = :rootId LIMIT 1")
    suspend fun get(rootId: String): CapabilitiesCacheEntity?

    @Query("DELETE FROM capabilities_cache WHERE rootId = :rootId")
    suspend fun delete(rootId: String)
}
