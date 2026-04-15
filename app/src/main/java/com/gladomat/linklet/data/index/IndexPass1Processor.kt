package com.gladomat.linklet.data.index

import android.util.Log
import androidx.room.withTransaction
import com.gladomat.linklet.data.parser.NoteMetadataParser
import com.gladomat.linklet.data.storage.IStorage
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class IndexPass1Processor @Inject constructor(
    private val storage: IStorage,
    private val noteDao: NoteDao,
    private val indexQueueDao: IndexQueueDao,
    private val database: NoteDatabase,
) {

    suspend fun run(timeBudgetMillis: Long? = null): Result<Unit> {
        return runCatching {
            val runStartedAt = System.currentTimeMillis()
            Log.d(TAG, "Pass 1 run starting timeBudgetMillis=$timeBudgetMillis")
            storage.invalidateCache()
            val allPaths = storage.listNotes().getOrThrow()
            val activePaths = allPaths.filterNot(::isTrashPath)
            val activeSet = activePaths.toSet()
            val existingNotes = noteDao.getAllNotes()
            val existingByPath = existingNotes.associateBy { it.path }
            val now = System.currentTimeMillis()
            Log.d(
                TAG,
                "Pass 1 scan listed all=${allPaths.size} active=${activePaths.size} existing=${existingNotes.size} elapsedMs=${now - runStartedAt}",
            )

            val tombstoned = existingNotes
                .filter { it.deletedAt == null && it.path !in activeSet }
            tombstoned.forEach { noteDao.markDeleted(path = it.path, deletedAt = now) }
            if (tombstoned.isNotEmpty()) {
                Log.d(TAG, "Pass 1 tombstoned missing notes count=${tombstoned.size}")
            }

            val enqueueEntries = mutableListOf<IndexQueueEntity>()
            activePaths.forEach { path ->
                val stat = storage.statNote(path).getOrNull()
                val existing = existingByPath[path]
                val unchanged = stat != null &&
                    existing != null &&
                    existing.deletedAt == null &&
                    existing.fingerprintMtime == stat.lastModifiedEpochMillis &&
                    existing.fingerprintSize == stat.sizeBytes
                if (!unchanged) {
                    enqueueEntries += IndexQueueEntity(
                        path = path,
                        pass = PASS_1,
                        status = IndexQueueStatus.PENDING,
                        attempts = 0,
                        lastError = null,
                        updatedAtEpochMillis = now,
                        expectedMtime = stat?.lastModifiedEpochMillis,
                        expectedSize = stat?.sizeBytes,
                    )
                }
            }
            if (enqueueEntries.isNotEmpty()) {
                indexQueueDao.upsertAll(enqueueEntries)
            }
            Log.d(
                TAG,
                "Pass 1 enqueue complete queued=${enqueueEntries.size} pending=${indexQueueDao.countByStatus(PASS_1, IndexQueueStatus.PENDING)} elapsedMs=${System.currentTimeMillis() - runStartedAt}",
            )

            val processingStartedAt = System.currentTimeMillis()
            var entry = indexQueueDao.claimNext(pass = PASS_1, now = now, leaseTimeoutMillis = LEASE_TIMEOUT_MILLIS)
            var processed = 0
            var staleEntries = 0
            var failed = 0
            while (entry != null) {
                currentCoroutineContext().ensureActive()
                val current = entry
                val updatedAt = System.currentTimeMillis()
                if (timeBudgetMillis != null && updatedAt - processingStartedAt >= timeBudgetMillis) {
                    Log.d(
                        TAG,
                        "Pass 1 budget exhausted processed=$processed stale=$staleEntries failed=$failed elapsedProcessingMs=${updatedAt - processingStartedAt}",
                    )
                    break
                }
                Log.d(TAG, "Pass 1 processing path=${current.path} attempt=${current.attempts + 1}")
                val stat = storage.statNote(current.path).getOrNull()
                val stale = stat != null &&
                    ((current.expectedMtime != null && current.expectedMtime != stat.lastModifiedEpochMillis) ||
                        (current.expectedSize != null && current.expectedSize != stat.sizeBytes))
                if (stale) {
                    staleEntries += 1
                    Log.d(TAG, "Pass 1 found stale fingerprint path=${current.path}; requeueing")
                    indexQueueDao.upsert(
                        current.copy(
                            status = IndexQueueStatus.PENDING,
                            lastError = null,
                            updatedAtEpochMillis = updatedAt,
                            expectedMtime = stat?.lastModifiedEpochMillis,
                            expectedSize = stat?.sizeBytes,
                            lockedAtEpochMillis = null,
                        ),
                    )
                    entry = indexQueueDao.claimNext(pass = PASS_1, now = now, leaseTimeoutMillis = LEASE_TIMEOUT_MILLIS)
                    continue
                }

                try {
                    val content = storage.readNote(current.path).getOrThrow()
                    val metadata = NoteMetadataParser.parse(content, current.path)
                    database.withTransaction {
                        noteDao.insertNotes(
                            listOf(
                                NoteEntity(
                                    path = current.path,
                                    title = metadata.title,
                                    orgId = metadata.orgId,
                                    fileTags = metadata.fileTags,
                                    deletedAt = null,
                                    fingerprintMtime = current.expectedMtime,
                                    fingerprintSize = current.expectedSize,
                                    linksReady = false,
                                ),
                            ),
                        )
                        indexQueueDao.upsert(
                            current.copy(
                                status = IndexQueueStatus.DONE,
                                attempts = current.attempts + 1,
                                lastError = null,
                                updatedAtEpochMillis = updatedAt,
                                lockedAtEpochMillis = null,
                            ),
                        )
                        indexQueueDao.upsert(
                            IndexQueueEntity(
                                path = current.path,
                                pass = PASS_2,
                                operation = IndexQueueOperation.UPSERT,
                                status = IndexQueueStatus.PENDING,
                                attempts = 0,
                                lastError = null,
                                updatedAtEpochMillis = updatedAt,
                                expectedMtime = current.expectedMtime,
                                expectedSize = current.expectedSize,
                            ),
                        )
                    }
                    processed += 1
                    Log.d(TAG, "Pass 1 indexed path=${current.path} processed=$processed")
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    failed += 1
                    Log.e(TAG, "Pass 1 indexing failed for ${current.path}", e)
                    indexQueueDao.upsert(
                        current.copy(
                            status = IndexQueueStatus.FAILED,
                            attempts = current.attempts + 1,
                            lastError = e.message ?: "Unknown error",
                            updatedAtEpochMillis = updatedAt,
                            lockedAtEpochMillis = null,
                        ),
                    )
                }
                entry = indexQueueDao.claimNext(pass = PASS_1, now = now, leaseTimeoutMillis = LEASE_TIMEOUT_MILLIS)
            }
            Log.d(
                TAG,
                "Pass 1 run complete processed=$processed stale=$staleEntries failed=$failed pending=${indexQueueDao.countByStatus(PASS_1, IndexQueueStatus.PENDING)} done=${indexQueueDao.countByStatus(PASS_1, IndexQueueStatus.DONE)} totalElapsedMs=${System.currentTimeMillis() - runStartedAt}",
            )
            Unit
        }.onFailure { if (it is CancellationException) throw it }
    }

    private fun isTrashPath(path: String): Boolean =
        path.startsWith(PRIMARY_TRASH_PREFIX) || path.startsWith(LEGACY_TRASH_PREFIX)

    companion object {
        private const val TAG = "IndexPass1Processor"
        private const val PASS_1 = 1
        private const val PASS_2 = 2
        private const val PRIMARY_TRASH_PREFIX = "_trash_bin/"
        private const val LEGACY_TRASH_PREFIX = "_trash/"
        private const val LEASE_TIMEOUT_MILLIS = 10 * 60 * 1000L
    }
}
