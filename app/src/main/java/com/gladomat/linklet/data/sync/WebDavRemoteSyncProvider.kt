package com.gladomat.linklet.data.sync

import android.net.Uri
import android.util.Log
import com.burgstaller.okhttp.AuthenticationCacheInterceptor
import com.burgstaller.okhttp.CachingAuthenticatorDecorator
import com.burgstaller.okhttp.DispatchingAuthenticator
import com.burgstaller.okhttp.basic.BasicAuthenticator
import com.burgstaller.okhttp.digest.CachingAuthenticator
import com.burgstaller.okhttp.digest.Credentials
import com.burgstaller.okhttp.digest.DigestAuthenticator
import com.gladomat.linklet.data.settings.WebDavSettings
import com.gladomat.linklet.data.settings.WebDavSettingsRepository
import com.gladomat.linklet.data.sync.metrics.SyncMetricKeys
import com.gladomat.linklet.data.sync.metrics.SyncMetrics
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient

private const val TAG = "WebDavSync"
private const val ENCODED_SLASH_PLACEHOLDER = "__ENC_SLASH__"

@Singleton
class WebDavRemoteSyncProvider @Inject constructor(
    private val settingsRepository: WebDavSettingsRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val metrics: SyncMetrics,
) : RemoteSyncProvider {

    override val name: String = "webdav"

    companion object {
        internal fun isSyncableRemotePath(path: String): Boolean = SyncPathFilter.shouldInclude(path)
    }

    // 1. The container for the captured ETag.
    private val capturedPutEtag = ThreadLocal<String>()
    
    // Container for the optional "If-Match" header for the next request (optimistic locking)
    private val nextRequestIfMatch = ThreadLocal<String>()

    // 2. The Interceptor logic.
    private val etagInterceptor = Interceptor { chain ->
        var request = chain.request()
        
        // Inject "If-Match" if requested
        val ifMatch = nextRequestIfMatch.get()
        if (ifMatch != null) {
            Log.d(TAG, "Injecting If-Match: $ifMatch for ${request.method} ${request.url}")
            request = request.newBuilder()
                .header("If-Match", ifMatch)
                .build()
        }

        val response = chain.proceed(request)

        // Only capture if it is a PUT and it succeeded (2xx)
        if (request.method == "PUT" && response.isSuccessful) {
            response.header("ETag")?.let { rawEtag ->
                Log.d(TAG, "Captured ETag from PUT response: $rawEtag")
                capturedPutEtag.set(rawEtag)
            }
        }
        response
    }

    override suspend fun listRemoteNotes(): Result<List<RemoteNoteMetadata>> = withContext(dispatcher) {
        runCatching {
            // Ensure we don't leak headers into unrelated requests
            nextRequestIfMatch.remove()
            
            val settings = ensureSettings()
            val sardine = createSardine(settings)
            val url = buildUrl(settings, "")
            Log.d(TAG, "Listing remote notes from: $url")
            Log.d(TAG, "DEBUG: Settings - baseUrl=${settings.baseUrl}, rootPath=${settings.rootPath}, normalizedRootPath=${settings.normalizedRootPath}")
            
            val resources = listAllRemoteResources(sardine, settings)
            Log.d(TAG, "DEBUG: PROPFIND returned ${resources.size} total resources")
            
            val result = resources.mapNotNull { resource ->
                Log.d(TAG, "DEBUG: Resource href='${resource.href}', isDirectory=${resource.isDirectory}, contentType=${resource.contentType}, etag=${resource.etag}")
                
                val relativePath = relativePathFromUrl(resource.href.toString(), settings)
                Log.d(TAG, "DEBUG: Calculated relativePath='$relativePath' from href='${resource.href}'")
                
                if (relativePath == null) {
                    Log.w(TAG, "DEBUG: relativePath is null for href='${resource.href}'")
                    return@mapNotNull null
                }
                if (!isSyncableRemotePath(relativePath)) {
                    Log.d(TAG, "DEBUG: Skipping ignored path: $relativePath")
                    return@mapNotNull null
                }
                
                RemoteNoteMetadata(
                    remoteId = relativePath,
                    path = relativePath,
                    fingerprint = normalizeEtag(resource.etag),
                    lastModifiedEpochMillis = resource.modified?.time,
                    sizeBytes = resource.contentLength?.takeIf { it >= 0 },
                    checksums = extractRemoteChecksums(resource),
                ).also {
                    Log.d(TAG, "DEBUG: Added remote note: path='${it.path}', fingerprint='${it.fingerprint}'")
                }
            }
            
            Log.i(TAG, "DEBUG: listRemoteNotes returning ${result.size} notes")
            result
        }
    }

    override suspend fun download(path: String, remoteId: String): Result<InputStream> = withContext(dispatcher) {
        runCatching {
            nextRequestIfMatch.remove()
            val settings = ensureSettings()
            val sardine = createSardine(settings)
            val url = buildUrl(settings, remoteId)
            Log.d(TAG, "Downloading file from: $url")
            metrics.increment(SyncMetricKeys.HTTP_GET)
            sardine.get(url)
        }
    }

    override suspend fun upload(
        path: String,
        content: InputStream,
        remoteId: String?,
        expectedFingerprint: String?,
    ): Result<RemoteUploadResult> = withContext(dispatcher) {
        runCatching {
            val settings = ensureSettings()
            val sardine = createSardine(settings)
            ensureRemoteDirectoriesExist(sardine, settings, path)
            val url = buildUrl(settings, path)
            
            Log.d(TAG, "Uploading file to: $url")
            val tempFile = java.io.File.createTempFile("upload", ".tmp")
            try {
                tempFile.outputStream().use { output ->
                    content.copyTo(output)
                }

                capturedPutEtag.remove()
                nextRequestIfMatch.remove()
                
                // We could optionally use If-Match here too for safe overwrites, 
                // but the requirements focused on Safe-Delete.
                // If expectedFingerprint is provided, we could set it.
                if (expectedFingerprint != null) {
                     nextRequestIfMatch.set(expectedFingerprint) // Or weak ETag format?
                     // WebDAV If-Match usually requires strong ETags (quoted).
                     // We'll stick to the basics for upload unless requested.
                     // Actually, for safety, let's NOT enable it for upload yet to avoid blocking valid edits 
                     // if logic is slightly off, unless explicitly asked.
                     // The user requirement specifically said "Safe-Delete ... use WebDAV's If-Match headers".
                     // It didn't explicitly demand safe-overwrite, but it's good practice. 
                     // However, sticking to the explicit plan: Delete only.
                     nextRequestIfMatch.remove() 
                }

                // 2. EXECUTE: Perform the upload
                try {
                    metrics.increment(SyncMetricKeys.HTTP_PUT)
                    sardine.put(url, tempFile, "application/octet-stream")
                } catch (e: com.thegrizzlylabs.sardineandroid.impl.SardineException) {
                    // Nextcloud/WebDAV often returns 404 on PUT when intermediate collections don't exist.
                    // We already try to create them, but retry once to cover servers that require
                    // collection URLs with trailing slashes / redirects, or have eventual consistency.
                    if (e.statusCode == 404) {
                        ensureRemoteDirectoriesExist(sardine, settings, path)
                        metrics.increment(SyncMetricKeys.HTTP_PUT)
                        sardine.put(url, tempFile, "application/octet-stream")
                    } else {
                        throw e
                    }
                }

                // 3. OPTIMIZATION: Try to get the ETag from the interceptor
                var finalEtag = normalizeEtag(capturedPutEtag.get())

                // 4. FALLBACK: If server didn't send ETag on PUT, use PROPFIND
                if (finalEtag.isNullOrBlank()) {
                    Log.w(TAG, "No ETag on PUT for $path, falling back to PROPFIND")
                    metrics.increment(SyncMetricKeys.HTTP_PROPFIND)
                    val resources = sardine.list(url, 0)
                    val resource = resources.firstOrNull()
                        ?: throw IOException("Upload verification failed: File not found on server after PUT")

                    finalEtag = normalizeEtag(resource.etag)
                }

                if (finalEtag.isNullOrBlank()) {
                    throw IOException("Upload verification failed: Server returned no ETag via PUT or PROPFIND")
                }

                RemoteUploadResult(
                    remoteId = path,
                    fingerprint = finalEtag
                )
            } finally {
                tempFile.delete()
                nextRequestIfMatch.remove()
            }
        }
    }

    private fun ensureRemoteDirectoriesExist(
        sardine: Sardine,
        settings: WebDavSettings,
        relativeFilePath: String,
    ) {
        parentDirectoriesFor(relativeFilePath).forEach { relativeDir ->
            // Many WebDAV servers expect collection URLs with a trailing slash.
            val dirUrl = buildUrl(settings, relativeDir).trimEnd('/') + "/"
            runCatching {
                Log.d(TAG, "Ensuring remote directory exists: $dirUrl")
                sardine.createDirectory(dirUrl)
            }.onFailure { error ->
                // Many WebDAV servers respond with 405 when directory already exists.
                val sardineError = error as? com.thegrizzlylabs.sardineandroid.impl.SardineException
                if (sardineError?.statusCode == 405) {
                    return@onFailure
                }
                throw error
            }
        }
    }

    override suspend fun delete(path: String, remoteId: String, fingerprint: String?): Result<Unit> = withContext(dispatcher) {
        runCatching {
            val settings = ensureSettings()
            val sardine = createSardine(settings)
            val url = buildUrl(settings, remoteId)
            
            // Set the If-Match header if we have a fingerprint (ETag)
            if (!fingerprint.isNullOrBlank()) {
                // WebDAV requires the ETag to be quoted usually.
                // normalizeEtag removes quotes. We might need to add them back.
                // However, some servers are picky.
                // Standard HTTP If-Match: "etag_value"
                val headerValue = if (fingerprint.startsWith("\"")) fingerprint else "\"$fingerprint\""
                nextRequestIfMatch.set(headerValue)
            } else {
                nextRequestIfMatch.remove()
            }
            
            try {
                Log.d(TAG, "Deleting $path with If-Match: ${nextRequestIfMatch.get()}")
                metrics.increment(SyncMetricKeys.HTTP_DELETE)
                sardine.delete(url)
            } catch (e: Exception) {
                // Check for 412 Precondition Failed
                // Sardine usually throws SardineException which has statusCode
                val message = e.message ?: ""
                if (message.contains("412") || (e is com.thegrizzlylabs.sardineandroid.impl.SardineException && e.statusCode == 412)) {
                     throw RemoteChangedException("Safe-Delete failed: Remote file has changed (HTTP 412). Resync required.")
                }
                throw e
            } finally {
                nextRequestIfMatch.remove()
            }
        }
    }

    suspend fun testConnection(overrideSettings: WebDavSettings? = null): Result<Unit> = withContext(dispatcher) {
        runCatching {
            val settings = overrideSettings?.takeIf { it.isConfigured() } ?: ensureSettings()
            if (settings.baseUrl.startsWith("http://")) {
                // In a real app, we might want to warn the user here via a specific exception or result type
            }
            val sardine = createSardine(settings)
            val url = buildUrl(settings, "")
            metrics.increment(SyncMetricKeys.HTTP_PROPFIND)
            sardine.list(url, 0)
            Unit
        }
    }

    suspend fun isReadyForSync(): Boolean {
        val settings = settingsRepository.currentSettings()
        return settings?.enabled == true && settings.isConfigured()
    }

    private suspend fun ensureSettings(): WebDavSettings =
        settingsRepository.currentSettings()
            ?.takeIf { it.enabled && it.isConfigured() }
            ?: throw IllegalStateException("WebDAV provider is not configured")

    private fun createSardine(settings: WebDavSettings): Sardine {
        val builder = OkHttpClient.Builder()
        
        val credentials = Credentials(settings.username, settings.password)
        val basicAuthenticator = BasicAuthenticator(credentials)
        val digestAuthenticator = DigestAuthenticator(credentials)
        
        val authenticator = DispatchingAuthenticator.Builder()
            .with("digest", digestAuthenticator)
            .with("basic", basicAuthenticator)
            .build()
            
        val authCache: Map<String, CachingAuthenticator> = ConcurrentHashMap()
        builder.authenticator(CachingAuthenticatorDecorator(authenticator, authCache))
        builder.addInterceptor(AuthenticationCacheInterceptor(authCache))
        
        // Add the ETag interceptor
        builder.addInterceptor(etagInterceptor)
        
        builder.connectTimeout(30, TimeUnit.SECONDS)
        builder.readTimeout(30, TimeUnit.SECONDS)
        builder.writeTimeout(30, TimeUnit.SECONDS)
        
        return OkHttpSardine(builder.build())
    }

    private fun buildUrl(settings: WebDavSettings, relativePath: String): String {
        val baseUrl = settings.baseUrl.trimEnd('/')
        val root = encodePathSegments(settings.normalizedRootPath)
        val path = encodePathSegments(relativePath)
        
        return buildString {
            append(baseUrl)
            if (root.isNotEmpty()) append("/").append(root)
            if (path.isNotEmpty()) append("/").append(path)
        }
    }
    
    /**
     * Returns a decoded path relative to the WebDAV root.
     * Percent-escapes are decoded except for "%2F", which remains encoded to avoid
     * changing path structure.
     */
    private fun relativePathFromUrl(url: String, settings: WebDavSettings): String? {
        return runCatching {
            val prefixUri = URI(buildUrl(settings, ""))
            Log.d(TAG, "DEBUG relativePathFromUrl: url='$url', prefix='${prefixUri}'")

            val normalizedUri = if (url.startsWith("http://") || url.startsWith("https://")) {
                URI(url)
            } else {
                null
            }
            val normalizedAuthority = normalizedUri?.authority ?: prefixUri.authority
            if (normalizedAuthority != prefixUri.authority) {
                Log.w(
                    TAG,
                    "DEBUG relativePathFromUrl: Authority mismatch - $normalizedAuthority vs ${prefixUri.authority}",
                )
                return@runCatching null
            }

            val prefixPath = (prefixUri.rawPath ?: "").trimEnd('/')
            val urlPath = when {
                normalizedUri != null -> normalizedUri.rawPath
                url.startsWith("/") -> url
                else -> "$prefixPath/${url.trimStart('/')}"
            }
            Log.d(TAG, "DEBUG relativePathFromUrl: normalizedUri='$normalizedUri', urlPath='$urlPath'")
            Log.d(TAG, "DEBUG relativePathFromUrl: prefixPath='$prefixPath', urlPath='$urlPath'")
            
            if (urlPath == null || !urlPath.startsWith(prefixPath)) {
                Log.w(TAG, "DEBUG relativePathFromUrl: Path doesn't start with prefix - '$urlPath' doesn't start with '$prefixPath'")
                return@runCatching null
            }

            val relativeRaw = urlPath.removePrefix(prefixPath).trim('/')
            val result = relativeRaw.takeIf { it.isNotEmpty() }?.let(::decodePreservingEncodedSlashes)
            Log.d(TAG, "DEBUG relativePathFromUrl: result='$result'")
            result
        }.onFailure { e ->
            Log.e(TAG, "DEBUG relativePathFromUrl: Exception while processing url='$url'", e)
        }.getOrNull()
    }

    private fun decodePreservingEncodedSlashes(value: String): String {
        val protected = value.replace(Regex("(?i)%2f"), ENCODED_SLASH_PLACEHOLDER)
        return Uri.decode(protected).replace(ENCODED_SLASH_PLACEHOLDER, "%2F")
    }

    private fun encodePathSegments(path: String): String {
        val trimmed = path.trim('/')
        if (trimmed.isEmpty()) return ""
        return trimmed.split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") { encodePathSegment(it) }
    }

    private fun encodePathSegment(segment: String): String {
        val protected = segment.replace(Regex("(?i)%2f"), ENCODED_SLASH_PLACEHOLDER)
        val encoded = Uri.encode(protected)
        return encoded.replace(ENCODED_SLASH_PLACEHOLDER, "%2F")
    }

    private fun listAllRemoteResources(
        sardine: Sardine,
        settings: WebDavSettings,
    ): List<com.thegrizzlylabs.sardineandroid.DavResource> {
        val resources = mutableListOf<com.thegrizzlylabs.sardineandroid.DavResource>()
        val visitedDirs = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add("")

        while (queue.isNotEmpty()) {
            val relativeDir = queue.removeFirst()
            val url = buildUrl(settings, relativeDir)
            metrics.increment(SyncMetricKeys.HTTP_PROPFIND)
            val entries = sardine.list(url, 1)
            entries.forEach { resource ->
                if (resource.isDirectory) {
                    val dirPath = relativePathFromUrl(resource.href.toString(), settings)
                    if (!dirPath.isNullOrBlank() && visitedDirs.add(dirPath)) {
                        if (isSyncableRemotePath(dirPath)) {
                            queue.add(dirPath)
                        }
                    }
                    return@forEach
                }
                resources.add(resource)
            }
        }

        return resources
    }

    private fun normalizeEtag(raw: String?): String? {
        if (raw == null) return null
        // We intentionally collapse weak and strong validators into one fingerprint string.
        // This keeps remote-change detection stable across servers that switch weak prefixes,
        // but it also means strength metadata is not preserved here.
        return raw.replace("W/", "", ignoreCase = true).trim('"')
    }

    /**
     * Best-effort extraction of server-reported content checksums from a PROPFIND response.
     * ownCloud/Nextcloud expose a `{http://owncloud.org/ns}checksums` property whose text is like
     * "SHA1:.. MD5:..". We don't fail or change behaviour if absent — adoption falls back to size.
     */
    private fun extractRemoteChecksums(
        resource: com.thegrizzlylabs.sardineandroid.DavResource,
    ): Map<String, String>? {
        val custom = runCatching { resource.customProps }.getOrNull() ?: return null
        val raw = custom["checksums"] ?: custom["checksum"] ?: return null
        return NoteHashCalculator.parseChecksums(raw)
    }
}

internal fun parentDirectoriesFor(relativeFilePath: String): List<String> {
    val normalized = relativeFilePath.trim('/')
    if (normalized.isEmpty()) return emptyList()
    val segments = normalized.split('/').filter { it.isNotEmpty() }
    if (segments.size <= 1) return emptyList()
    return (1 until segments.size).map { endExclusive ->
        segments.take(endExclusive).joinToString("/")
    }
}
