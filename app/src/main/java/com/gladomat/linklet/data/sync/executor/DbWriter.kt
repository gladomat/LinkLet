package com.gladomat.linklet.data.sync.executor

import com.gladomat.linklet.data.sync.db.OperationJournalDao

class DbWriter(
    private val operationJournalDao: OperationJournalDao,
) {
    suspend fun apply(result: ExecutionResult, updatedAtEpochMillis: Long) {
        operationJournalDao.updateStatus(
            operationId = result.operationId,
            newStatus = if (result.success) "DONE" else "FAILED",
            lastError = result.errorMessage,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
    }
}

data class ExecutionResult(
    val operationId: String,
    val success: Boolean,
    val errorMessage: String? = null,
)
