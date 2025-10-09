package com.gladomat.linklet.data.index

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinks(links: List<LinkEntity>)

    @Query("DELETE FROM notes")
    suspend fun clearNotes()

    @Query("DELETE FROM links")
    suspend fun clearLinks()

    @Query("SELECT * FROM notes ORDER BY title COLLATE NOCASE")
    suspend fun getAllNotes(): List<NoteEntity>

    @Query("SELECT source, target, alias FROM links WHERE target = :path")
    suspend fun getBacklinks(path: String): List<LinkEntity>
}
