package com.gladomat.linklet.data.index

import com.gladomat.linklet.data.storage.IStorage
import javax.inject.Inject

/**
 * Scans storage and enqueues pass 1 work based on fingerprint changes.
 */
class IndexScanService @Inject constructor(
    private val storage: IStorage,
    private val noteDao: NoteDao,
    private val indexQueueDao: IndexQueueDao,
    private val indexingStateDao: IndexingStateDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun scan(): Result<Unit> = runCatching {
        storage.invalidateCache()
        val now = clock()
        val activePaths = storage.listNotes().getOrThrow()
        val activeSet = activePaths.toSet()
        val existingNotes = noteDao.getAllNotes()
        val existingByPath = existingNotes.associateBy { it.path }

        existingNotes
            .filter { it.deletedAt == null && it.path !in activeSet }
            .forEach { noteDao.markDeleted(path = it.path, deletedAt = now) }

        val enqueueEntries = activePaths.mapNotNull { path ->
            val stat = storage.statNote(path).getOrNull()
            val existing = existingByPath[path]
            val unchanged = stat != null &&
                existing != null &&
                existing.deletedAt == null &&
                existing.fingerprintMtime == stat.lastModifiedEpochMillis &&
                existing.fingerprintSize == stat.sizeBytes
            if (unchanged) {
                null
            } else {
                IndexQueueEntity(
                    path = path,
                    pass = PASS_1,
                    operation = IndexQueueOperation.UPSERT,
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

        val existingState = indexingStateDao.get()
        indexingStateDao.upsert(
            IndexingStateEntity(
                lastScanAtEpochMillis = now,
                lastPass1EnqueueAtEpochMillis = if (enqueueEntries.isNotEmpty()) now else existingState?.lastPass1EnqueueAtEpochMillis,
                lastPass2RunAtEpochMillis = existingState?.lastPass2RunAtEpochMillis,
            ),
        )
    }

    companion object {
        private const val PASS_1 = 1
    }
}
