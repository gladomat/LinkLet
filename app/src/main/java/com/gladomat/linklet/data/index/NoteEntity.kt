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
