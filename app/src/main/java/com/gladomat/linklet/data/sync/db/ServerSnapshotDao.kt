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

    @Query("SELECT * FROM server_snapshot WHERE rootId = :rootId AND isDir = 0")
    suspend fun getAllFiles(rootId: String): List<ServerSnapshotEntity>

    @Query(
        "DELETE FROM server_snapshot WHERE rootId = :rootId AND deletedAtEpochMillis IS NOT NULL AND deletedAtEpochMillis < :cutoffEpochMillis",
    )
    suspend fun purgeTombstones(rootId: String, cutoffEpochMillis: Long): Int

    @Query(
        "SELECT * FROM server_snapshot WHERE rootId = :rootId AND deletedAtEpochMillis IS NOT NULL AND deletedAtEpochMillis < :cutoffEpochMillis",
    )
    suspend fun findExpiredTombstones(rootId: String, cutoffEpochMillis: Long): List<ServerSnapshotEntity>
}
