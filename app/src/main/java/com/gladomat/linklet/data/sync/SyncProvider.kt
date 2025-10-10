package com.gladomat.linklet.data.sync

interface SyncProvider {
    suspend fun pull(): Result<Unit>
    suspend fun push(): Result<Unit>
    fun isOnline(): Boolean
}
