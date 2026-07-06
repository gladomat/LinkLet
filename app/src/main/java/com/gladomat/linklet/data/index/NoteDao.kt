package com.gladomat.linklet.data.index

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Projection for batch org-id -> path lookups (avoids N+1 queries when resolving [[id:...]] links). */
data class OrgIdToPath(
    val orgId: String,
    val path: String,
)

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinks(links: List<LinkEntity>)

    @Query("DELETE FROM links WHERE source = :source")
    suspend fun deleteLinksBySource(source: String)

    @Query("DELETE FROM links WHERE target = :target")
    suspend fun deleteLinksByTarget(target: String)

    @Query("DELETE FROM links WHERE sourceOrgId = :orgId OR targetOrgId = :orgId")
    suspend fun deleteLinksByOrgId(orgId: String)

    @Query("DELETE FROM notes")
    suspend fun clearNotes()

    @Query("DELETE FROM notes WHERE source = 'LOCAL'")
    suspend fun clearLocalNotes()

    @Query("DELETE FROM links")
    suspend fun clearLinks()

    @Query("SELECT * FROM notes ORDER BY title COLLATE NOCASE")
    suspend fun getAllNotes(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND linksReady = 0 ORDER BY path")
    suspend fun listNotesNeedingLinks(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY title COLLATE NOCASE")
    fun observeActiveNotes(): Flow<List<NoteEntity>>

    @Query("UPDATE notes SET deletedAt = :deletedAt WHERE path = :path")
    suspend fun markDeleted(path: String, deletedAt: Long)

    @Query("UPDATE notes SET linksReady = :linksReady WHERE path = :path")
    suspend fun updateLinksReady(path: String, linksReady: Boolean)

    @Query("SELECT fileTags FROM notes WHERE deletedAt IS NULL")
    suspend fun getAllFileTagsSerialized(): List<String>

    @Query("SELECT path FROM notes WHERE deletedAt IS NULL AND orgId = :orgId LIMIT 1")
    suspend fun findPathByOrgId(orgId: String): String?

    // GROUP BY + MIN(path) makes the pick deterministic if the same orgId is ever
    // duplicated across two active notes (e.g. a copy-pasted :ID: drawer), matching
    // findPathByOrgId's single-row-per-id contract instead of an arbitrary SQLite row order.
    @Query("SELECT orgId, MIN(path) AS path FROM notes WHERE deletedAt IS NULL AND orgId IN (:orgIds) GROUP BY orgId")
    suspend fun findPathsByOrgIds(orgIds: List<String>): List<OrgIdToPath>

    @Query("SELECT orgId FROM notes WHERE path = :path LIMIT 1")
    suspend fun findOrgIdByPath(path: String): String?

    @Query("SELECT availability FROM notes WHERE path = :path LIMIT 1")
    suspend fun getNoteAvailability(path: String): NoteAvailability?

    @Query("SELECT * FROM notes WHERE availability IN (:availabilities) AND deletedAt IS NULL")
    suspend fun getNotesByAvailability(availabilities: List<NoteAvailability>): List<NoteEntity>

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
        WHERE links.target = :path AND notes.deletedAt IS NULL
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
