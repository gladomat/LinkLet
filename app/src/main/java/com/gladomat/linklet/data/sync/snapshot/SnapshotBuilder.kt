package com.gladomat.linklet.data.sync.snapshot

import com.gladomat.linklet.data.index.NoteAvailability
import com.gladomat.linklet.data.index.NoteDao
import com.gladomat.linklet.data.index.NoteEntity
import com.gladomat.linklet.data.index.NoteSource
import com.gladomat.linklet.data.sync.SyncPathFilter
import com.gladomat.linklet.data.sync.db.ServerSnapshotDao
import com.gladomat.linklet.data.sync.db.ServerSnapshotEntity
import com.gladomat.linklet.data.sync.metrics.SyncMetricKeys
import com.gladomat.linklet.data.sync.metrics.SyncMetrics
import com.gladomat.linklet.data.sync.webdav.PropfindItem
import java.net.URI

data class SnapshotResult(
    val directoriesTraversed: Int,
    val filesFound: Int,
    val stubsCreated: Int,
    val directoriesSkipped: Int,
    val pendingDirectories: List<String>,
)

/**
 * Abstraction over PROPFIND calls so that [SnapshotBuilder] can be unit-tested
 * without a real HTTP client. [WebDavPropfindClient] is the production implementation.
 */
fun interface PropfindSource {
    fun propfind(url: String, depth: Int): Result<List<PropfindItem>>
}

class SnapshotBuilder(
    private val propfindClient: PropfindSource,
    private val snapshotDao: ServerSnapshotDao,
    private val noteDao: NoteDao,
    private val metrics: SyncMetrics,
    private val batchSize: Int = 500,
) {
    companion object {
        private const val ENCODED_SLASH_PLACEHOLDER = "\u0000SLASH\u0000"
    }

    /**
     * Build a snapshot of the remote WebDAV tree.
     *
     * When [etagBubbling] is enabled (Nextcloud optimization), each directory is first
     * probed with a Depth:0 PROPFIND. If the directory's ETag matches what we have in
     * [ServerSnapshotDao], the subtree is assumed unchanged and the full Depth:1 listing
     * is skipped entirely — dramatically reducing network traffic for no-change syncs.
     */
    suspend fun buildSnapshot(
        rootId: String,
        rootUrl: String,
        resumeFrom: List<String>? = null,
        maxDirectories: Int = Int.MAX_VALUE,
        etagBubbling: Boolean = false,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): SnapshotResult {
        val rootPrefix = extractRootPath(rootUrl)

        var directoriesTraversed = 0
        var directoriesSkipped = 0
        var filesFound = 0
        var stubsCreated = 0

        val snapshotBatch = mutableListOf<ServerSnapshotEntity>()
        val stubBatch = mutableListOf<NoteEntity>()

        // Bulk-load existing note paths to avoid N+1 queries per file
        val existingNotePaths = noteDao.getAllNotes().mapTo(HashSet()) { it.path }

        // Queue stores (decodedRelativePath, rawHrefSuffix) pairs.
        // We use raw hrefs for URL construction to preserve percent-encoding,
        // and decoded paths for DB storage and path filtering.
        val dirQueue = ArrayDeque<Pair<String, String>>()
        if (resumeFrom != null) {
            resumeFrom.forEach { dirQueue.add(it to it) }
        } else {
            dirQueue.add("" to "")
        }

        while (dirQueue.isNotEmpty() && directoriesTraversed < maxDirectories) {
            val (dirRelPath, dirRawSuffix) = dirQueue.removeFirst()
            val dirUrl = buildDirectoryUrl(rootUrl, dirRawSuffix)

            // ETag-bubbled traversal: probe directory ETag with Depth:0 first.
            // If the ETag matches our stored snapshot, skip the full listing.
            if (etagBubbling && dirRelPath.isNotEmpty()) {
                val unchanged = isDirEtagUnchanged(rootId, dirRelPath, dirUrl)
                if (unchanged) {
                    directoriesSkipped++
                    continue
                }
            }

            metrics.increment(SyncMetricKeys.HTTP_PROPFIND)
            val items = propfindClient.propfind(dirUrl, depth = 1).getOrElse { continue }

            directoriesTraversed++
            val now = nowEpochMillis

            for (item in items) {
                val relativePath = extractRelativePath(item.href, rootPrefix) ?: continue

                // Skip the directory itself (self-referencing entry in PROPFIND depth=1)
                if (relativePath == dirRelPath) continue

                val snapshotEntity = ServerSnapshotEntity(
                    rootId = rootId,
                    path = relativePath,
                    href = item.href,
                    etag = item.etag,
                    isDir = item.isDir,
                    fileId = item.fileId,
                    lastModifiedEpochMillis = item.lastModifiedEpochMillis,
                    sizeBytes = item.sizeBytes,
                    lastSeenAtEpochMillis = now,
                )
                snapshotBatch.add(snapshotEntity)

                if (item.isDir) {
                    // Use the raw href suffix for URL construction to preserve encoding
                    val rawSuffix = extractRawRelative(item.href, rootPrefix)
                    dirQueue.add(relativePath to (rawSuffix ?: relativePath))
                } else {
                    filesFound++
                    if (SyncPathFilter.shouldInclude(relativePath) && relativePath !in existingNotePaths) {
                        val title = extractTitle(relativePath)
                        stubBatch.add(
                            NoteEntity(
                                path = relativePath,
                                title = title,
                                source = NoteSource.REMOTE_STUB,
                                availability = NoteAvailability.STUB,
                            ),
                        )
                        existingNotePaths.add(relativePath)
                        stubsCreated++
                    }
                }

                if (snapshotBatch.size >= batchSize) {
                    snapshotDao.upsertAll(snapshotBatch.toList())
                    snapshotBatch.clear()
                }
                if (stubBatch.size >= batchSize) {
                    noteDao.insertNotes(stubBatch.toList())
                    stubBatch.clear()
                }
            }
        }

        // Flush remaining items
        if (snapshotBatch.isNotEmpty()) {
            snapshotDao.upsertAll(snapshotBatch.toList())
        }
        if (stubBatch.isNotEmpty()) {
            noteDao.insertNotes(stubBatch.toList())
        }

        return SnapshotResult(
            directoriesTraversed = directoriesTraversed,
            filesFound = filesFound,
            stubsCreated = stubsCreated,
            directoriesSkipped = directoriesSkipped,
            pendingDirectories = dirQueue.map { it.first },
        )
    }

    /**
     * Probes a directory with Depth:0 PROPFIND and checks if its ETag matches
     * what's stored in our snapshot table. Returns true if unchanged (safe to skip).
     */
    private suspend fun isDirEtagUnchanged(
        rootId: String,
        dirRelPath: String,
        dirUrl: String,
    ): Boolean {
        metrics.increment(SyncMetricKeys.HTTP_PROPFIND)
        val probeItems = propfindClient.propfind(dirUrl, depth = 0).getOrNull()
            ?: return false  // On failure, don't skip — do full listing

        val remoteEtag = probeItems.firstOrNull()?.etag
            ?: return false  // No ETag available, can't compare

        val stored = snapshotDao.getByPath(rootId, dirRelPath)
            ?: return false  // No stored snapshot, must traverse

        return stored.etag != null && stored.etag == remoteEtag
    }

    /**
     * Extracts the path portion from the root URL for use as a prefix.
     * e.g., "https://cloud.example.com/remote.php/dav/files/user/Notes"
     * -> "/remote.php/dav/files/user/Notes"
     */
    private fun extractRootPath(rootUrl: String): String {
        return URI(rootUrl).rawPath.trimEnd('/')
    }

    /**
     * Builds the full URL for a directory given its relative path from root.
     */
    private fun buildDirectoryUrl(rootUrl: String, relativePath: String): String {
        val base = rootUrl.trimEnd('/')
        return if (relativePath.isEmpty()) base else "$base/$relativePath"
    }

    /**
     * Extracts a relative path from a PROPFIND href by removing the root path prefix.
     * Handles percent-encoding: decodes %XX sequences but preserves %2F (encoded slashes)
     * to avoid corrupting path structure.
     */
    internal fun extractRelativePath(href: String, rootPrefix: String): String? {
        val hrefPath = if (href.startsWith("http://") || href.startsWith("https://")) {
            URI(href).rawPath
        } else {
            href
        }

        val normalizedHref = hrefPath.trimEnd('/')
        val normalizedPrefix = rootPrefix.trimEnd('/')

        if (!normalizedHref.startsWith(normalizedPrefix)) return null

        // Ensure we match at a path-segment boundary to avoid /Notes matching /NotesBackup
        val remainder = normalizedHref.removePrefix(normalizedPrefix)
        if (remainder.isNotEmpty() && !remainder.startsWith("/")) return null

        val rawRelative = remainder.trimStart('/')
        if (rawRelative.isEmpty()) return ""

        return decodePreservingEncodedSlashes(rawRelative)
    }

    /**
     * Extracts the raw (still percent-encoded) relative path from an href.
     * Used for URL construction where encoding must be preserved.
     */
    private fun extractRawRelative(href: String, rootPrefix: String): String? {
        val hrefPath = if (href.startsWith("http://") || href.startsWith("https://")) {
            URI(href).rawPath
        } else {
            href
        }
        val normalizedHref = hrefPath.trimEnd('/')
        val normalizedPrefix = rootPrefix.trimEnd('/')
        if (!normalizedHref.startsWith(normalizedPrefix)) return null
        val remainder = normalizedHref.removePrefix(normalizedPrefix)
        if (remainder.isNotEmpty() && !remainder.startsWith("/")) return null
        return remainder.trimStart('/')
    }

    /**
     * Decodes percent-encoded sequences in a path, but preserves %2F (encoded slashes)
     * so that literal slash characters in file/folder names are not confused with
     * path separators.
     */
    private fun decodePreservingEncodedSlashes(value: String): String {
        val protected = value.replace(Regex("(?i)%2f"), ENCODED_SLASH_PLACEHOLDER)
        val decoded = decodePercent(protected)
        return decoded.replace(ENCODED_SLASH_PLACEHOLDER, "%2F")
    }

    /**
     * Percent-decoding that correctly handles multi-byte UTF-8 sequences.
     * Works on both Android and JVM without depending on android.net.Uri.
     */
    private fun decodePercent(value: String): String {
        val sb = StringBuilder(value.length)
        val byteBuffer = mutableListOf<Byte>()
        var i = 0
        while (i < value.length) {
            if (value[i] == '%' && i + 2 < value.length) {
                val hi = value[i + 1]
                val lo = value[i + 2]
                if (isHexDigit(hi) && isHexDigit(lo)) {
                    byteBuffer.add(((Character.digit(hi, 16) shl 4) + Character.digit(lo, 16)).toByte())
                    i += 3
                    continue
                }
            }
            // Flush accumulated bytes as UTF-8 before appending a literal character
            if (byteBuffer.isNotEmpty()) {
                sb.append(byteBuffer.toByteArray().toString(Charsets.UTF_8))
                byteBuffer.clear()
            }
            sb.append(value[i])
            i++
        }
        // Flush any trailing bytes
        if (byteBuffer.isNotEmpty()) {
            sb.append(byteBuffer.toByteArray().toString(Charsets.UTF_8))
        }
        return sb.toString()
    }

    private fun isHexDigit(c: Char): Boolean =
        c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    /**
     * Extracts a human-readable title from a file path by taking the filename
     * and removing its extension.
     */
    private fun extractTitle(path: String): String {
        val filename = path.substringAfterLast('/')
        return filename.substringBeforeLast('.', filename)
    }
}
