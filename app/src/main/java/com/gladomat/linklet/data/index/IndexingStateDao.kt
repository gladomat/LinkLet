package com.gladomat.linklet.data.index

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Persists indexing checkpoint state for resumability.
 */
@Dao
interface IndexingStateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: IndexingStateEntity)

    @Query("SELECT * FROM indexing_state WHERE id = 1")
    suspend fun get(): IndexingStateEntity?
}
