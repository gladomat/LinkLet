package com.gladomat.linklet.data.sync.capabilities

import com.gladomat.linklet.data.sync.db.CapabilitiesCacheDao
import com.gladomat.linklet.data.sync.db.CapabilitiesCacheEntity
import com.gladomat.linklet.data.sync.metrics.SyncMetricKeys
import com.gladomat.linklet.data.sync.metrics.SyncMetrics
import com.gladomat.linklet.data.sync.webdav.PropfindResponseParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class ServerCapabilities(
    val supportsSearch: Boolean,
    val supportsTrashbin: Boolean,
    val supportsFileId: Boolean,
)

class CapabilitiesProbe(
    private val httpClient: OkHttpClient,
    private val capabilitiesCacheDao: CapabilitiesCacheDao,
    private val metrics: SyncMetrics,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    companion object {
        const val TTL_MS: Long = 24 * 60 * 60 * 1000L  // 24 hours
        const val NEGATIVE_TTL_MS: Long = 6 * 60 * 60 * 1000L  // 6 hours for negative results

        private val SEARCH_BODY = """
            <?xml version="1.0"?><d:searchrequest xmlns:d="DAV:"><d:basicsearch><d:select><d:prop><d:getlastmodified/></d:prop></d:select><d:from><d:scope><d:href>/</d:href><d:depth>0</d:depth></d:scope></d:from><d:where><d:gt><d:prop><d:getlastmodified/></d:prop><d:literal>Mon, 01 Jan 1990 00:00:00 GMT</d:literal></d:gt></d:where></d:basicsearch></d:searchrequest>
        """.trimIndent()

        private val FILEID_PROPFIND_BODY = """
            <?xml version="1.0"?>
            <d:propfind xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
              <d:prop>
                <oc:fileid/>
              </d:prop>
            </d:propfind>
        """.trimIndent()

        private val USERNAME_PATTERN = Regex("/files/([^/]+)")

        internal fun extractUsername(rootPath: String): String? {
            return USERNAME_PATTERN.find(rootPath)?.groupValues?.get(1)
        }
    }

    suspend fun probe(
        rootId: String,
        baseUrl: String,
        rootPath: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): ServerCapabilities {
        // 1. Check cache
        val cached = capabilitiesCacheDao.get(rootId)
        if (cached != null && cached.expiresAtEpochMillis > nowEpochMillis) {
            return ServerCapabilities(
                supportsSearch = cached.supportsSearch,
                supportsTrashbin = cached.supportsTrashbin,
                supportsFileId = cached.supportsFileId,
            )
        }

        // 2. Probe each capability in parallel (network calls on IO dispatcher)
        val (supportsSearch, supportsTrashbin, supportsFileId) = coroutineScope {
            val searchDeferred = async(dispatcher) { probeSearch(baseUrl, rootPath) }
            val trashbinDeferred = async(dispatcher) { probeTrashbin(baseUrl, rootPath) }
            val fileIdDeferred = async(dispatcher) { probeFileId(baseUrl, rootPath) }
            Triple(searchDeferred.await(), trashbinDeferred.await(), fileIdDeferred.await())
        }

        val capabilities = ServerCapabilities(
            supportsSearch = supportsSearch,
            supportsTrashbin = supportsTrashbin,
            supportsFileId = supportsFileId,
        )

        // 3. Cache with appropriate TTL
        val ttl = if (!supportsSearch && !supportsTrashbin && !supportsFileId) {
            NEGATIVE_TTL_MS
        } else {
            TTL_MS
        }

        capabilitiesCacheDao.upsert(
            CapabilitiesCacheEntity(
                rootId = rootId,
                supportsSearch = supportsSearch,
                supportsTrashbin = supportsTrashbin,
                supportsFileId = supportsFileId,
                checkedAtEpochMillis = nowEpochMillis,
                expiresAtEpochMillis = nowEpochMillis + ttl,
            )
        )

        return capabilities
    }

    private fun probeSearch(baseUrl: String, rootPath: String): Boolean {
        metrics.increment(SyncMetricKeys.HTTP_SEARCH)
        val url = baseUrl.trimEnd('/') + "/" + rootPath.trimStart('/')
        val request = Request.Builder()
            .url(url)
            .method("SEARCH", SEARCH_BODY.toRequestBody("text/xml; charset=utf-8".toMediaType()))
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                response.code in 200..299
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun probeTrashbin(baseUrl: String, rootPath: String): Boolean {
        val username = extractUsername(rootPath) ?: return false

        metrics.increment(SyncMetricKeys.HTTP_PROPFIND)
        val trashUrl = baseUrl.trimEnd('/') + "/remote.php/dav/trashbin/$username/trash/"
        val request = Request.Builder()
            .url(trashUrl)
            .header("Depth", "0")
            .method("PROPFIND", FILEID_PROPFIND_BODY.toRequestBody("text/xml; charset=utf-8".toMediaType()))
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                response.code in 200..299
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun probeFileId(baseUrl: String, rootPath: String): Boolean {
        metrics.increment(SyncMetricKeys.HTTP_PROPFIND)
        val url = baseUrl.trimEnd('/') + "/" + rootPath.trimStart('/')
        val request = Request.Builder()
            .url(url)
            .header("Depth", "0")
            .method("PROPFIND", FILEID_PROPFIND_BODY.toRequestBody("text/xml; charset=utf-8".toMediaType()))
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (response.code !in 200..299) return@use false
                val body = response.body ?: return@use false
                val items = PropfindResponseParser.parse(body.byteStream()).toList()
                items.any { it.fileId != null }
            }
        } catch (_: Exception) {
            false
        }
    }
}
