package com.gladomat.linklet.data.sync.delta

data class DeltaChange(
    val href: String,
    val etag: String?,
    val fileId: String?,
    val lastModifiedEpochMillis: Long?,
    val sizeBytes: Long?,
    val isDir: Boolean,
    val isDeleted: Boolean, // true for trashbin-discovered deletions
) {
    /** Stable dedup key: prefer (fileId,etag), else (href,etag) */
    val dedupKey: String
        get() = if (!fileId.isNullOrBlank()) "fid:$fileId:$etag" else "href:$href:$etag"
}
