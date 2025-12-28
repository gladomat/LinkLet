package com.gladomat.linklet.data.index

import android.util.Log
import androidx.room.withTransaction
import com.gladomat.linklet.data.model.LinkTarget
import com.gladomat.linklet.data.parser.IParser
import com.gladomat.linklet.data.storage.IStorage
import javax.inject.Inject

class IndexPass2Processor @Inject constructor(
    private val storage: IStorage,
    private val parser: IParser,
    private val noteDao: NoteDao,
    private val indexQueueDao: IndexQueueDao,
    private val database: NoteDatabase,
) {

    suspend fun run(): Result<Unit> {
        return runCatching {
            storage.invalidateCache()
            val now = System.currentTimeMillis()

            val notesNeedingLinks = noteDao.listNotesNeedingLinks()
            if (notesNeedingLinks.isNotEmpty()) {
                indexQueueDao.upsertAll(
                    notesNeedingLinks.map { note ->
                        IndexQueueEntity(
                            path = note.path,
                            pass = PASS_2,
                            status = IndexQueueStatus.PENDING,
                            attempts = 0,
                            lastError = null,
                            updatedAtEpochMillis = now,
                            expectedMtime = note.fingerprintMtime,
                            expectedSize = note.fingerprintSize,
                        )
                    },
                )
            }

            val pending = indexQueueDao.listByStatus(pass = PASS_2, status = IndexQueueStatus.PENDING)
            val failed = indexQueueDao.listByStatus(pass = PASS_2, status = IndexQueueStatus.FAILED)
            val work = (pending + failed).distinctBy { it.path }
            // This processor is single-threaded; keep the cache simple.
            val orgIdCache = mutableMapOf<String, String?>()

            work.chunked(BATCH_SIZE).forEach { batch ->
                batch.forEach { entry ->
                    val updatedAt = System.currentTimeMillis()
                    try {
                        val content = storage.readNote(entry.path).getOrThrow()
                        val parsed = parser.parse(content = content, path = entry.path)
                        var hasUnresolvedIdLinks = false
                        val resolvedLinks = parsed.links.mapNotNull { link ->
                            val targetPath = when (val target = link.target) {
                                is LinkTarget.Path -> target.value
                                is LinkTarget.Id -> orgIdCache.getOrPut(target.value) {
                                    noteDao.findPathByOrgId(target.value)
                                }
                            } ?: run {
                                hasUnresolvedIdLinks = true
                                return@mapNotNull null
                            }
                            LinkEntity(
                                source = entry.path,
                                target = targetPath,
                                alias = link.label,
                            )
                        }
                            .distinctBy { it.target }

                        database.withTransaction {
                            noteDao.deleteLinksBySource(entry.path)
                            if (resolvedLinks.isNotEmpty()) {
                                noteDao.insertLinks(resolvedLinks)
                            }
                            val linksReady = !hasUnresolvedIdLinks
                            noteDao.updateLinksReady(path = entry.path, linksReady = linksReady)
                            indexQueueDao.upsert(
                                entry.copy(
                                    status = if (linksReady) IndexQueueStatus.DONE else IndexQueueStatus.PENDING,
                                    attempts = entry.attempts + 1,
                                    lastError = null,
                                    updatedAtEpochMillis = updatedAt,
                                ),
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Pass 2 indexing failed for ${entry.path}", e)
                        indexQueueDao.upsert(
                            entry.copy(
                                status = IndexQueueStatus.FAILED,
                                attempts = entry.attempts + 1,
                                lastError = e.message ?: "Unknown error",
                                updatedAtEpochMillis = updatedAt,
                            ),
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "IndexPass2Processor"
        private const val PASS_2 = 2
        private const val BATCH_SIZE = 50
    }
}
