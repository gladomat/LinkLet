package com.gladomat.linklet.data.index

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Position cache for the graph view (docs/plans/2026-07-06-note-graph-view.md). Keeps the force
 * layout's last-settled coordinates per note so reopening the graph doesn't re-run the
 * simulation from a random seed every time.
 */
@Dao
interface GraphPositionDao {
    @Query("SELECT * FROM graph_positions")
    fun observeAll(): Flow<List<GraphPositionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(positions: List<GraphPositionEntity>)

    // Follows NoteDao.renameNotePath so a cached position isn't orphaned under the note's old path.
    @Query("UPDATE graph_positions SET path = :newPath WHERE path = :oldPath")
    suspend fun renamePath(oldPath: String, newPath: String)

    @Query("DELETE FROM graph_positions WHERE path = :path")
    suspend fun delete(path: String)
}
