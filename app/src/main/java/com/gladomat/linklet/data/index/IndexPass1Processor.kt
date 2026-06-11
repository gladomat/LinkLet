package com.gladomat.linklet.data.index

import android.util.Log
import androidx.room.withTransaction
import com.gladomat.linklet.data.parser.NoteMetadataParser
import com.gladomat.linklet.data.storage.IStorage
import com.gladomat.linklet.data.storage.NoteNotFoundException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
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
            val now = System.currentTimeMillis()
            val recovered = indexQueueDao.failStaleRunning(
                pass = PASS_1,
                staleBefore = now - STALE_RUNNING_RECOVERY_MILLIS,
                now = now,
                reason = STALE_RUNNING_ERROR,
            )
            if (recovered > 0) {
                Log.w(TAG, "Pass 1 recovered stale running entries count=$recovered")
            }
            val pendingAtStart = indexQueueDao.countByStatus(PASS_1, IndexQueueStatus.PENDING)
            if (pendingAtStart > 0) {
                Log.d(TAG, "Pass 1 skipping scan because pending=$pendingAtStart already exists")
                processQueue(
                    runStartedAt = runStartedAt,
                    claimNow = now,
                    timeBudgetMillis = timeBudgetMillis,
                )
                return@runCatching
            }

            Log.d(TAG, "Pass 1 scan starting storage.listNotes()")
            storage.invalidateCache()
            val allPaths = withTimeoutOrNull(STORAGE_SCAN_TIMEOUT_MILLIS) {
                storage.listNotes().getOrThrow()
            } ?: throw IOException("Pass 1 scan timed out after ${STORAGE_SCAN_TIMEOUT_MILLIS}ms")
            val activePaths = allPaths.filterNot(::isTrashPath)
            val activeSet = activePaths.toSet()
            val existingNotes = noteDao.getAllNotes()
            val existingByPath = existingNotes.associateBy { it.path }
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

            // Existing queue rows for this pass, so we can preserve attempt counts (so the retry
            // cap is reachable) and skip paths that have exhausted their retry budget.
            val existingQueueByPath = indexQueueDao.listAllByPass(PASS_1).associateBy { it.path }
            val enqueueEntries = mutableListOf<IndexQueueEntity>()
            activePaths.forEach { path ->
                val existing = existingByPath[path]
                val queued = existingQueueByPath[path]
                if (queued != null &&
                    queued.status == IndexQueueStatus.FAILED &&
                    queued.attempts >= MAX_ATTEMPTS
                ) {
                    // Terminally failed (e.g. unreadable file); don't loop on it.
                    return@forEach
                }
                val stat = if (existing != null && existing.deletedAt == null) {
                    withTimeoutOrNull(STORAGE_STAT_TIMEOUT_MILLIS) {
                        storage.statNote(path).getOrNull()
                    }
                } else {
                    null
                }
                val unchanged = existing != null &&
                    existing.deletedAt == null &&
                    stat != null &&
                    existing.fingerprintMtime == stat.lastModifiedEpochMillis &&
                    existing.fingerprintSize == stat.sizeBytes
                if (!unchanged) {
                    enqueueEntries += IndexQueueEntity(
                        path = path,
                        pass = PASS_1,
                        status = IndexQueueStatus.PENDING,
                        // Carry forward attempts for an entry that keeps failing; reset for one
                        // that previously succeeded (a genuine content change).
                        attempts = if (queued?.status == IndexQueueStatus.FAILED) queued.attempts else 0,
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

            processQueue(
                runStartedAt = runStartedAt,
                claimNow = now,
                timeBudgetMillis = timeBudgetMillis,
            )
        }.onFailure { if (it is CancellationException) throw it }
    }

    private suspend fun processQueue(
        runStartedAt: Long,
        claimNow: Long,
        timeBudgetMillis: Long?,
    ) {
        val processingStartedAt = System.currentTimeMillis()
        var entry = indexQueueDao.claimNext(pass = PASS_1, now = claimNow, leaseTimeoutMillis = LEASE_TIMEOUT_MILLIS)
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
            val shouldStat = current.expectedMtime != null || current.expectedSize != null
            val stat = if (shouldStat) storage.statNote(current.path).getOrNull() else null
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
                entry = indexQueueDao.claimNext(pass = PASS_1, now = claimNow, leaseTimeoutMillis = LEASE_TIMEOUT_MILLIS)
                continue
            }

            try {
                val content = withTimeoutOrNull(STORAGE_READ_TIMEOUT_MILLIS) {
                    storage.readNote(current.path).getOrThrow()
                } ?: throw IOException("Timed out reading ${current.path} after ${STORAGE_READ_TIMEOUT_MILLIS}ms")
                val metadata = NoteMetadataParser.parse(content, current.path)
                val fingerprintMtime = stat?.lastModifiedEpochMillis ?: current.expectedMtime
                val fingerprintSize = stat?.sizeBytes
                    ?: current.expectedSize
                    ?: content.toByteArray(Charsets.UTF_8).size.toLong()
                database.withTransaction {
                    noteDao.insertNotes(
                        listOf(
                            NoteEntity(
                                path = current.path,
                                title = metadata.title,
                                orgId = metadata.orgId,
                                fileTags = metadata.fileTags,
                                deletedAt = null,
                                fingerprintMtime = fingerprintMtime,
                                fingerprintSize = fingerprintSize,
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
                            expectedMtime = fingerprintMtime,
                            expectedSize = fingerprintSize,
                        ),
                    )
                }
                processed += 1
                Log.d(TAG, "Pass 1 indexed path=${current.path} processed=$processed")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (e is NoteNotFoundException) {
                    // File vanished between the scan and the read — reconcile as a deletion and
                    // enqueue a pass 2 link cleanup, rather than retrying a doomed read.
                    Log.d(TAG, "Pass 1 file missing, tombstoning path=${current.path}")
                    database.withTransaction {
                        noteDao.markDeleted(path = current.path, deletedAt = updatedAt)
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
                                operation = IndexQueueOperation.DELETE,
                                status = IndexQueueStatus.PENDING,
                                attempts = 0,
                                lastError = null,
                                updatedAtEpochMillis = updatedAt,
                            ),
                        )
                    }
                    entry = indexQueueDao.claimNext(pass = PASS_1, now = claimNow, leaseTimeoutMillis = LEASE_TIMEOUT_MILLIS)
                    continue
                }
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
            entry = indexQueueDao.claimNext(pass = PASS_1, now = claimNow, leaseTimeoutMillis = LEASE_TIMEOUT_MILLIS)
        }
        Log.d(
            TAG,
            "Pass 1 run complete processed=$processed stale=$staleEntries failed=$failed pending=${indexQueueDao.countByStatus(PASS_1, IndexQueueStatus.PENDING)} done=${indexQueueDao.countByStatus(PASS_1, IndexQueueStatus.DONE)} totalElapsedMs=${System.currentTimeMillis() - runStartedAt}",
        )
    }

    private fun isTrashPath(path: String): Boolean =
        path.startsWith(PRIMARY_TRASH_PREFIX) || path.startsWith(LEGACY_TRASH_PREFIX)

    companion object {
        private const val TAG = "IndexPass1Processor"
        private const val PASS_1 = 1
        private const val PASS_2 = 2
        private const val MAX_ATTEMPTS = 5
        private const val PRIMARY_TRASH_PREFIX = "_trash_bin/"
        private const val LEGACY_TRASH_PREFIX = "_trash/"
        private const val LEASE_TIMEOUT_MILLIS = 10 * 60 * 1000L
        private const val STALE_RUNNING_RECOVERY_MILLIS = 60 * 60 * 1000L
        private const val STALE_RUNNING_ERROR = "Recovered stale RUNNING queue entry"
        private const val STORAGE_SCAN_TIMEOUT_MILLIS = 60_000L
        private const val STORAGE_STAT_TIMEOUT_MILLIS = 5_000L
        private const val STORAGE_READ_TIMEOUT_MILLIS = 10_000L
    }
}
