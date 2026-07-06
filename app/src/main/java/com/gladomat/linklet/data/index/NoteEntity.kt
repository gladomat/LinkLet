package com.gladomat.linklet.data.index

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class NoteAvailability {
    STUB,
    AVAILABLE,
    PINNED_OFFLINE,
    ERROR,
}

enum class NoteSource {
    LOCAL,
    REMOTE_STUB,
}

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val path: String,
    val title: String,
    val orgId: String? = null,
    val fileTags: List<String> = emptyList(),
    val deletedAt: Long? = null,
    val fingerprintMtime: Long? = null,
    val fingerprintSize: Long? = null,
    val linksReady: Boolean = false,
    val availability: NoteAvailability = NoteAvailability.AVAILABLE,
    val source: NoteSource = NoteSource.LOCAL,
)

@Entity(tableName = "links", primaryKeys = ["source", "target"])
data class LinkEntity(
    val source: String,
    val target: String,
    val alias: String?,
    val sourceOrgId: String? = null,
    val targetOrgId: String? = null,
)

/**
 * Cached last-settled position for a note's node in the graph view (see docs/plans/2026-07-06-note-graph-view.md).
 * Reopening the graph loads these instead of re-running the force layout from a random seed;
 * only nodes with no cached row (new notes) get seeded and nudged into place.
 */
@Entity(tableName = "graph_positions")
data class GraphPositionEntity(
    @PrimaryKey val path: String,
    val x: Float,
    val y: Float,
    val updatedAtEpochMillis: Long,
)
