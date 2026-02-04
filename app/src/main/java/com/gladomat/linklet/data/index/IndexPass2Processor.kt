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
                            operation = IndexQueueOperation.UPSERT,
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

            // This processor is single-threaded; keep the cache simple.
            val orgIdCache = mutableMapOf<String, String?>()

            var entry = indexQueueDao.claimNext(pass = PASS_2, now = now, leaseTimeoutMillis = LEASE_TIMEOUT_MILLIS)
            while (entry != null) {
                val current = entry
                val updatedAt = System.currentTimeMillis()
                when (current.operation) {
                    IndexQueueOperation.DELETE -> {
                        val orgId = noteDao.findOrgIdByPath(current.path)
                        database.withTransaction {
                            if (orgId != null) {
                                noteDao.deleteLinksByOrgId(orgId)
                            } else {
                                noteDao.deleteLinksBySource(current.path)
                                noteDao.deleteLinksByTarget(current.path)
                            }
                            indexQueueDao.upsert(
                                current.copy(
                                    status = IndexQueueStatus.DONE,
                                    attempts = current.attempts + 1,
                                    lastError = null,
                                    updatedAtEpochMillis = updatedAt,
                                    lockedAtEpochMillis = null,
                                ),
                            )
                        }
                    }
                    IndexQueueOperation.UPSERT -> {
                        try {
                            val content = storage.readNote(current.path).getOrThrow()
                            val parsed = parser.parse(content = content, path = current.path)
                            val sourceOrgId = noteDao.findOrgIdByPath(current.path)
                            var hasUnresolvedIdLinks = false
                            val resolvedLinks = parsed.links.mapNotNull { link ->
                                val target = link.target
                                val resolved = when (target) {
                                    is LinkTarget.Path -> target.value to noteDao.findOrgIdByPath(target.value)
                                    is LinkTarget.Id -> orgIdCache.getOrPut(target.value) {
                                        noteDao.findPathByOrgId(target.value)
                                    }?.let { it to target.value }
                                } ?: run {
                                    hasUnresolvedIdLinks = true
                                    return@mapNotNull null
                                }
                                LinkEntity(
                                    source = current.path,
                                    target = resolved.first,
                                    alias = link.label,
                                    sourceOrgId = sourceOrgId,
                                    targetOrgId = resolved.second,
                                )
                            }
                                .distinctBy { it.target }

                            database.withTransaction {
                                noteDao.deleteLinksBySource(current.path)
                                if (resolvedLinks.isNotEmpty()) {
                                    noteDao.insertLinks(resolvedLinks)
                                }
                                val linksReady = !hasUnresolvedIdLinks
                                noteDao.updateLinksReady(path = current.path, linksReady = linksReady)
                                indexQueueDao.upsert(
                                    current.copy(
                                        status = if (linksReady) IndexQueueStatus.DONE else IndexQueueStatus.PENDING,
                                        attempts = current.attempts + 1,
                                        lastError = null,
                                        updatedAtEpochMillis = updatedAt,
                                        lockedAtEpochMillis = null,
                                    ),
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Pass 2 indexing failed for ${current.path}", e)
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
                    }
                }
                entry = indexQueueDao.claimNext(pass = PASS_2, now = now, leaseTimeoutMillis = LEASE_TIMEOUT_MILLIS)
            }
        }
    }

    companion object {
        private const val TAG = "IndexPass2Processor"
        private const val PASS_2 = 2
        private const val LEASE_TIMEOUT_MILLIS = 10 * 60 * 1000L
    }
}
