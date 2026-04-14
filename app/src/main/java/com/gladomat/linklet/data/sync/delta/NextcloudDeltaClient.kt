package com.gladomat.linklet.data.sync.delta

import com.gladomat.linklet.data.sync.db.SyncWatermarkDao
import com.gladomat.linklet.data.sync.db.SyncWatermarkEntity
import com.gladomat.linklet.data.sync.metrics.SyncMetricKeys
import com.gladomat.linklet.data.sync.metrics.SyncMetrics
import com.gladomat.linklet.data.sync.webdav.PropfindResponseParser
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class NextcloudDeltaClient(
    private val httpClient: OkHttpClient,
    private val watermarkDao: SyncWatermarkDao,
    private val metrics: SyncMetrics,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    companion object {
        private val XML_MEDIA_TYPE = "text/xml; charset=utf-8".toMediaType()
        private val USERNAME_REGEX = Regex("/remote\\.php/dav/files/([^/]+)/")
    }

    suspend fun fetchDelta(
        rootId: String,
        baseUrl: String,
        rootPath: String,
        supportsSearch: Boolean,
        supportsTrashbin: Boolean,
        nowEpochMillis: Long,
    ): DeltaResult {
        // 1. Load watermark
        val watermark = watermarkDao.get(rootId)

        // 2. No watermark → hard invalid (forces full sweep first time)
        if (watermark == null || watermark.lastDeltaServerTimeEpochMillis == null) {
            return DeltaResult(
                changes = emptyList(),
                validity = DeltaValidity.hardInvalid("no watermark"),
                serverTimeEpochMillis = null,
            )
        }

        // 3. Search not supported → hard invalid
        if (!supportsSearch) {
            return DeltaResult(
                changes = emptyList(),
                validity = DeltaValidity.hardInvalid("search not supported"),
                serverTimeEpochMillis = null,
            )
        }

        // 4. Compute query watermark
        val sinceMs = DeltaTime.queryWatermark(watermark.lastDeltaServerTimeEpochMillis)
        val rfc1123Date = formatRfc1123(sinceMs)

        // 5. Send SEARCH request
        val normalizedRootPath = "/" + rootPath.trim('/')
        val searchUrl = baseUrl.trimEnd('/') + normalizedRootPath
        val searchBody = buildSearchXml(rootPath, rfc1123Date)

        val searchRequest = Request.Builder()
            .url(searchUrl)
            .method("SEARCH", searchBody.toRequestBody(XML_MEDIA_TYPE))
            .build()

        metrics.increment(SyncMetricKeys.HTTP_SEARCH)

        return withContext(dispatcher) {
            val searchResponse = try {
                httpClient.newCall(searchRequest).execute()
            } catch (e: IOException) {
                return@withContext DeltaResult(
                    changes = emptyList(),
                    validity = DeltaValidity.hardInvalid("io error: ${e.message}"),
                    serverTimeEpochMillis = null,
                )
            }

            searchResponse.use { response ->
                // Capture server time from Date header
                val serverTimeEpochMillis = DeltaTime.parseHttpDate(response.header("Date"))

                // Check for error responses
                val code = response.code
                if (code >= 500 || code == 429) {
                    return@use DeltaResult(
                        changes = emptyList(),
                        validity = DeltaValidity.hardInvalid("server error: $code"),
                        serverTimeEpochMillis = serverTimeEpochMillis,
                    )
                }
                if (code !in 200..299) {
                    return@use DeltaResult(
                        changes = emptyList(),
                        validity = DeltaValidity.hardInvalid("http error: $code"),
                        serverTimeEpochMillis = serverTimeEpochMillis,
                    )
                }

                // 6. Parse SEARCH results into DeltaChange objects
                val searchChanges = response.body?.let { body ->
                    PropfindResponseParser.parse(body.byteStream()).map { item ->
                        DeltaChange(
                            href = item.href,
                            etag = item.etag,
                            fileId = item.fileId,
                            lastModifiedEpochMillis = item.lastModifiedEpochMillis,
                            sizeBytes = item.sizeBytes,
                            isDir = item.isDir,
                            isDeleted = false,
                        )
                    }.toList()
                } ?: emptyList()

                // 7. Optionally query trashbin for deletions
                val trashChanges = if (supportsTrashbin) {
                    fetchTrashbinChanges(baseUrl, rootPath, sinceMs)
                } else {
                    emptyList()
                }

                // 8. Deduplicate all changes by dedupKey
                val allChanges = (searchChanges + trashChanges)
                    .distinctBy { it.dedupKey }

                // Without trashbin, SEARCH cannot detect remote deletions.
                // Return SOFT_INVALID so the caller knows a periodic full sweep
                // is still required to catch deletions.
                val validity = if (!supportsTrashbin) {
                    DeltaValidity.softInvalid("deletions not observable without trashbin")
                } else {
                    DeltaValidity.valid()
                }

                DeltaResult(
                    changes = allChanges,
                    validity = validity,
                    serverTimeEpochMillis = serverTimeEpochMillis,
                )
            }
        }
    }

    suspend fun commitWatermark(
        rootId: String,
        serverTimeEpochMillis: Long,
        validity: DeltaValidity,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ) {
        val existing = watermarkDao.get(rootId)
        val entity = SyncWatermarkEntity(
            rootId = rootId,
            lastFullSweepServerTimeEpochMillis = existing?.lastFullSweepServerTimeEpochMillis,
            lastDeltaServerTimeEpochMillis = serverTimeEpochMillis,
            deltaValidityState = validity.state.name,
            deltaValidityReason = validity.reason,
            updatedAtEpochMillis = nowEpochMillis,
        )
        watermarkDao.upsert(entity)
    }

    private fun fetchTrashbinChanges(
        baseUrl: String,
        rootPath: String,
        sinceMs: Long,
    ): List<DeltaChange> {
        val username = USERNAME_REGEX.find(rootPath)?.groupValues?.get(1) ?: return emptyList()
        val trashUrl = "${baseUrl.trimEnd('/')}/remote.php/dav/trashbin/$username/trash/"

        val propfindBody = buildPropfindBody()
        val request = Request.Builder()
            .url(trashUrl)
            .header("Depth", "1")
            .method("PROPFIND", propfindBody.toRequestBody(XML_MEDIA_TYPE))
            .build()

        metrics.increment(SyncMetricKeys.HTTP_PROPFIND)

        val response = try {
            httpClient.newCall(request).execute()
        } catch (_: IOException) {
            return emptyList()
        }

        return response.use { resp ->
            if (!resp.isSuccessful) return@use emptyList()
            val body = resp.body ?: return@use emptyList()
            PropfindResponseParser.parse(body.byteStream())
                .filter { item ->
                    val modified = item.lastModifiedEpochMillis ?: return@filter false
                    modified > sinceMs
                }
                .map { item ->
                    DeltaChange(
                        href = item.href,
                        etag = item.etag,
                        fileId = item.fileId,
                        lastModifiedEpochMillis = item.lastModifiedEpochMillis,
                        sizeBytes = item.sizeBytes,
                        isDir = item.isDir,
                        isDeleted = true,
                    )
                }.toList()
        }
    }

    private fun buildSearchXml(rootPath: String, rfc1123Date: String): String {
        val escapedPath = escapeXml(rootPath)
        return """<?xml version="1.0" encoding="UTF-8"?>
<d:searchrequest xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
  <d:basicsearch>
    <d:select>
      <d:prop>
        <d:getetag/>
        <d:getlastmodified/>
        <d:getcontentlength/>
        <d:resourcetype/>
        <oc:fileid/>
      </d:prop>
    </d:select>
    <d:from>
      <d:scope>
        <d:href>$escapedPath</d:href>
        <d:depth>infinity</d:depth>
      </d:scope>
    </d:from>
    <d:where>
      <d:gt>
        <d:prop><d:getlastmodified/></d:prop>
        <d:literal>$rfc1123Date</d:literal>
      </d:gt>
    </d:where>
  </d:basicsearch>
</d:searchrequest>"""
    }

    private fun buildPropfindBody(): String =
        """<?xml version="1.0" encoding="UTF-8"?>
<d:propfind xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
  <d:prop>
    <d:getetag/>
    <d:getlastmodified/>
    <d:getcontentlength/>
    <d:resourcetype/>
    <oc:fileid/>
  </d:prop>
</d:propfind>"""

    private fun formatRfc1123(epochMillis: Long): String =
        DateTimeFormatter.RFC_1123_DATE_TIME.format(
            Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC),
        )

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;")
}
