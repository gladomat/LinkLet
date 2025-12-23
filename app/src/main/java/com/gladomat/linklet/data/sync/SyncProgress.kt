package com.gladomat.linklet.data.sync

enum class SyncPhase {
    DISCOVERY,
    RECONCILE,
    EXECUTE,
    REINDEX,
}

data class SyncProgress(
    val phase: SyncPhase,
    val completed: Int? = null,
    val total: Int? = null,
    val message: String? = null,
)
