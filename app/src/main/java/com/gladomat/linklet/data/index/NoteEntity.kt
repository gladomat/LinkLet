package com.gladomat.linklet.data.index

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val path: String,
    val title: String,
)

@Entity(tableName = "links", primaryKeys = ["source", "target"])
data class LinkEntity(
    val source: String,
    val target: String,
    val alias: String?,
)
