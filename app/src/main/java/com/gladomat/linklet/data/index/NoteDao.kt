package com.gladomat.linklet.data.index

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

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

    @Query("UPDATE notes SET path = :newPath WHERE path = :oldPath")
    suspend fun updateNotePath(oldPath: String, newPath: String)

    @Query("UPDATE links SET source = :newPath WHERE source = :oldPath")
    suspend fun updateLinksSource(oldPath: String, newPath: String)

    @Query("UPDATE links SET target = :newPath WHERE target = :oldPath")
    suspend fun updateLinksTarget(oldPath: String, newPath: String)

    @Transaction
    suspend fun renameNotePath(oldPath: String, newPath: String) {
        updateNotePath(oldPath, newPath)
        updateLinksSource(oldPath, newPath)
        updateLinksTarget(oldPath, newPath)
    }

    @Query(
        """
        SELECT links.source AS source,
               links.target AS target,
               links.alias AS alias,
               notes.title AS sourceTitle
        FROM links
        INNER JOIN notes ON notes.path = links.source
        WHERE links.target = :path
        """,
    )
    suspend fun getBacklinks(path: String): List<LinkWithSourceTitle>
}

data class LinkWithSourceTitle(
    val source: String,
    val target: String,
    val alias: String?,
    val sourceTitle: String?,
)
