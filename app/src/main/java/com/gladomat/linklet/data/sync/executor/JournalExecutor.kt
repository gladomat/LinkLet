package com.gladomat.linklet.data.sync.executor

import com.gladomat.linklet.data.sync.db.OperationJournalDao
import com.gladomat.linklet.data.sync.db.OperationJournalEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class JournalExecutor(
    private val operationJournalDao: OperationJournalDao,
    private val workerId: String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun execute(
        rootId: String,
        limit: Int,
        concurrency: Int,
        nowEpochMillis: Long,
        executeOperation: suspend (OperationJournalEntity) -> Result<Unit>,
    ) = coroutineScope {
        val claimed = claim(rootId, limit, nowEpochMillis)
        if (claimed.isEmpty()) {
            return@coroutineScope
        }

        val writer = DbWriter(operationJournalDao)
        val results = Channel<ExecutionResult>(capacity = claimed.size.coerceAtLeast(1))
        val writerJob = launch(dispatcher) {
            for (result in results) {
                writer.apply(result, updatedAtEpochMillis = nowEpochMillis)
            }
        }

        val semaphore = Semaphore(concurrency.coerceAtLeast(1))
        claimed.map { operation ->
            async(dispatcher) {
                semaphore.withPermit {
                    val outcome = executeOperation(operation)
                    if (outcome.isSuccess) {
                        results.send(ExecutionResult(operationId = operation.operationId, success = true))
                    } else {
                        results.send(
                            ExecutionResult(
                                operationId = operation.operationId,
                                success = false,
                                errorMessage = outcome.exceptionOrNull()?.message ?: "Operation failed",
                            ),
                        )
                    }
                }
            }
        }.awaitAll()

        results.close()
        writerJob.join()
    }

    suspend fun claim(rootId: String, limit: Int, nowEpochMillis: Long): List<OperationJournalEntity> {
        val ready = operationJournalDao.findReady(
            rootId = rootId,
            status = "PLANNED",
            nowEpochMillis = nowEpochMillis,
            limit = limit,
        )
        ready.forEach { operation ->
            operationJournalDao.claim(
                operationId = operation.operationId,
                newStatus = "RUNNING",
                claimedByWorker = workerId,
                claimedAtEpochMillis = nowEpochMillis,
                updatedAtEpochMillis = nowEpochMillis,
            )
        }
        return ready
    }
}
