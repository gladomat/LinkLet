package com.gladomat.linklet.data.sync.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ServerSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ServerSnapshotEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ServerSnapshotEntity)

    @Query("SELECT * FROM server_snapshot WHERE rootId = :rootId AND path = :path LIMIT 1")
    suspend fun getByPath(rootId: String, path: String): ServerSnapshotEntity?

    @Query("DELETE FROM server_snapshot WHERE rootId = :rootId")
    suspend fun clearRoot(rootId: String)
}
