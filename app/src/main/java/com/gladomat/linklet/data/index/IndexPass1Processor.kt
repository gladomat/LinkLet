package com.gladomat.linklet.data.index

import android.util.Log
import androidx.room.withTransaction
import com.gladomat.linklet.data.parser.NoteMetadataParser
import com.gladomat.linklet.data.storage.IStorage
import javax.inject.Inject

class IndexPass1Processor @Inject constructor(
    private val storage: IStorage,
    private val noteDao: NoteDao,
    private val indexQueueDao: IndexQueueDao,
    private val database: NoteDatabase,
) {

    suspend fun run(): Result<Unit> {
        return runCatching {
            storage.invalidateCache()
            val allPaths = storage.listNotes().getOrThrow()
            val activePaths = allPaths.filterNot(::isTrashPath)
            val activeSet = activePaths.toSet()
            val existingNotes = noteDao.getAllNotes()
            val existingByPath = existingNotes.associateBy { it.path }
            val now = System.currentTimeMillis()

            existingNotes
                .filter { it.deletedAt == null && it.path !in activeSet }
                .forEach { noteDao.markDeleted(path = it.path, deletedAt = now) }

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

            val pending = indexQueueDao.listByStatus(pass = PASS_1, status = IndexQueueStatus.PENDING)
            pending.chunked(BATCH_SIZE).forEach { batch ->
                val noteUpdates = mutableListOf<NoteEntity>()
                val queueUpdates = mutableListOf<IndexQueueEntity>()
                batch.forEach { entry ->
                    val updatedAt = System.currentTimeMillis()
                    try {
                        val content = storage.readNote(entry.path).getOrThrow()
                        val metadata = NoteMetadataParser.parse(content, entry.path)
                        noteUpdates += NoteEntity(
                            path = entry.path,
                            title = metadata.title,
                            orgId = metadata.orgId,
                            fileTags = metadata.fileTags,
                            deletedAt = null,
                            fingerprintMtime = entry.expectedMtime,
                            fingerprintSize = entry.expectedSize,
                            linksReady = false,
                        )
                        queueUpdates += entry.copy(
                            status = IndexQueueStatus.DONE,
                            attempts = entry.attempts + 1,
                            lastError = null,
                            updatedAtEpochMillis = updatedAt,
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Pass 1 indexing failed for ${entry.path}", e)
                        queueUpdates += entry.copy(
                            status = IndexQueueStatus.FAILED,
                            attempts = entry.attempts + 1,
                            lastError = e.message ?: "Unknown error",
                            updatedAtEpochMillis = updatedAt,
                        )
                    }
                }
                database.withTransaction {
                    if (noteUpdates.isNotEmpty()) {
                        noteDao.insertNotes(noteUpdates)
                    }
                    if (queueUpdates.isNotEmpty()) {
                        indexQueueDao.upsertAll(queueUpdates)
                    }
                }
            }
        }
    }

    private fun isTrashPath(path: String): Boolean =
        path.startsWith(PRIMARY_TRASH_PREFIX) || path.startsWith(LEGACY_TRASH_PREFIX)

    companion object {
        private const val TAG = "IndexPass1Processor"
        private const val PASS_1 = 1
        private const val PRIMARY_TRASH_PREFIX = "_trash_bin/"
        private const val LEGACY_TRASH_PREFIX = "_trash/"
        private const val BATCH_SIZE = 50
    }
}
