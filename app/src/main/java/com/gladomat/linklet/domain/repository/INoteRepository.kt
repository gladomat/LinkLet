package com.gladomat.linklet.domain.repository

import android.net.Uri
import com.gladomat.linklet.data.index.NoteAvailability
import com.gladomat.linklet.data.model.IndexingProgress
import com.gladomat.linklet.data.model.Note
import com.gladomat.linklet.data.model.NoteId
import com.gladomat.linklet.data.model.NoteIndexEntry
import kotlinx.coroutines.flow.Flow

/**
 * Contract for retrieving and managing notes.
 */
interface INoteRepository {
    fun observeNotes(): Flow<List<NoteIndexEntry>>

    fun observeIndexingProgress(pass: Int): Flow<IndexingProgress>

    fun observeIndexingFailures(pass: Int): Flow<Int>
    suspend fun listNotes(): Result<List<Note>>
    suspend fun getNote(path: String): Result<Note>
    suspend fun getNoteAvailability(path: String): Result<NoteAvailability> = Result.success(NoteAvailability.AVAILABLE)
    suspend fun requestNoteDownload(path: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Download request is not supported"))
    suspend fun reindex(): Result<Unit>
    suspend fun getBacklinks(path: String): Result<List<LinkEntityDto>>
    suspend fun saveNote(path: String, content: String): Result<Unit>
    /**
     * Soft-deletes a note by moving it into the trash area while keeping its content on disk.
     * This mirrors modern note apps where deletion is reversible until the trash is emptied.
     */
    suspend fun deleteNoteSoft(path: String): Result<Unit>

    /**
     * Restores a note from the trash back to its original (or conflict-resolved) path.
     * Returns the restored path, which may differ from the original when name conflicts occur.
     */
    suspend fun restoreNoteFromTrash(trashPath: String): Result<String>

    /**
     * Permanently deletes a note from storage (used for "Delete Forever" semantics).
     */
    suspend fun deleteNotePermanent(path: String): Result<Unit>

    /**
     * Legacy delete API used by existing callers.
     * Now implemented as a soft delete for safety.
     */
    suspend fun deleteNote(path: String): Result<Unit>

    /**
     * Lists all active (non-trash) notes.
     */
    suspend fun listAllNotes(): List<Note>

    /**
     * Lists notes that live in the trash area only.
     */
    suspend fun listTrashNotes(): List<Note>

    /** Duplicates a note with a new timestamp-based filename. Returns the new path. */
    suspend fun duplicateNote(path: String): Result<String>
    /** Renames a note file. Backlinks remain intact because they use org-roam IDs. */
    suspend fun renameNote(oldPath: String, newPath: String): Result<Unit>
    /** Returns all unique tags from all notes in the repository. */
    suspend fun getAllTags(): Result<List<String>>
    /** Updates properties in a note's :PROPERTIES: drawer. */
    suspend fun updateNoteProperties(path: String, properties: Map<String, String>): Result<Unit>
    /** Updates the #+filetags: line in a note. */
    suspend fun updateNoteTags(path: String, tags: List<String>): Result<Unit>

    /** Resolves a storage-relative path (note or attachment) to a readable [Uri]. */
    suspend fun resolveStorageUri(path: String): Result<Uri>

    /**
     * Observes the note graph (see docs/plans/2026-07-06-note-graph-view.md): every active note
     * as a node, every link between two active notes as an edge, plus any cached layout
     * positions from a previous session.
     *
     * When [center] is null, this is the whole-vault (global) graph. When [center] is set, the
     * result is filtered to the [hopDepth]-hop neighborhood of that note, traversed in **both**
     * link directions - this is deliberately not the same filter as [getBacklinks], which is
     * incoming-links-only; the graph shows everything a note connects to, in either direction.
     */
    fun observeGraph(center: NoteId? = null, hopDepth: Int = 2): Flow<GraphSnapshot>

    /** Persists the graph view's settled node positions so the next open doesn't re-seed from scratch. */
    suspend fun saveGraphPositions(positions: Map<String, GraphPoint>): Result<Unit>
}

data class LinkEntityDto(
    val source: String,
    val target: String,
    val alias: String?,
    val sourceTitle: String?,
)

/** A plain 2D point - deliberately not androidx.compose.ui.geometry.Offset, so the graph data
 * layer and layout engine stay pure-Kotlin/pure-JVM-testable, matching this app's pure-JVM
 * unit test tier (see data/graph/AGENTS.md / ForceLayoutEngine). Converted to Offset only at
 * the Composable boundary. */
data class GraphPoint(val x: Float, val y: Float)

data class GraphSnapshot(
    val nodes: List<NoteIndexEntry>,
    val edges: List<LinkEntityDto>,
    val cachedPositions: Map<String, GraphPoint>,
)
