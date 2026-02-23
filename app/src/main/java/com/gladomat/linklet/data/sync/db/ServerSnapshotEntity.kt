package com.gladomat.linklet.data.sync.db

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "server_snapshot",
    primaryKeys = ["rootId", "path"],
    indices = [
        Index(value = ["rootId", "isDir", "etag"]),
        Index(value = ["rootId", "fileId"]),
        Index(value = ["rootId", "deletedAtEpochMillis"]),
        Index(value = ["rootId", "lastSeenAtEpochMillis"]),
    ],
)
data class ServerSnapshotEntity(
    val rootId: String,
    val path: String,
    val href: String,
    val etag: String?,
    val isDir: Boolean,
    val fileId: String?,
    val lastModifiedEpochMillis: Long? = null,
    val sizeBytes: Long? = null,
    val deletedAtEpochMillis: Long? = null,
    val lastSeenAtEpochMillis: Long,
)
