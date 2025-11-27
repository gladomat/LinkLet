package com.gladomat.linklet.data.sync

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
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URLDecoder
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

@Singleton
class WebDavRemoteSyncProvider @Inject constructor(
    private val settingsRepository: WebDavSettingsRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RemoteSyncProvider {

    override val name: String = "webdav"

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
            val resources = sardine.list(url, 1)
            
            resources.mapNotNull { resource ->
                if (resource.isDirectory) return@mapNotNull null
                val relativePath = relativePathFromUrl(resource.href.toString(), settings)
                if (relativePath == null || !relativePath.endsWith(".org")) return@mapNotNull null
                
                RemoteNoteMetadata(
                    remoteId = relativePath,
                    path = relativePath,
                    fingerprint = normalizeEtag(resource.etag),
                    lastModifiedEpochMillis = resource.modified?.time
                )
            }
        }
    }

    override suspend fun download(path: String, remoteId: String): Result<InputStream> = withContext(dispatcher) {
        runCatching {
            nextRequestIfMatch.remove()
            val settings = ensureSettings()
            val sardine = createSardine(settings)
            val url = buildUrl(settings, remoteId)
            Log.d(TAG, "Downloading file from: $url")
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
                sardine.put(url, tempFile, "application/octet-stream")

                // 3. OPTIMIZATION: Try to get the ETag from the interceptor
                var finalEtag = normalizeEtag(capturedPutEtag.get())

                // 4. FALLBACK: If server didn't send ETag on PUT, use PROPFIND
                if (finalEtag.isNullOrBlank()) {
                    Log.w(TAG, "No ETag on PUT for $path, falling back to PROPFIND")
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
        val root = settings.normalizedRootPath.trim('/')
        val path = relativePath.trim('/')
        
        return buildString {
            append(baseUrl)
            if (root.isNotEmpty()) append("/").append(root)
            if (path.isNotEmpty()) append("/").append(path)
        }
    }
    
    private fun relativePathFromUrl(url: String, settings: WebDavSettings): String? {
        return runCatching {
            val decodedUrl = URLDecoder.decode(url, "UTF-8")
            val decodedPrefix = URLDecoder.decode(buildUrl(settings, ""), "UTF-8")
            val prefixUri = URI(decodedPrefix)

            val normalizedUri = when {
                decodedUrl.startsWith("http://") || decodedUrl.startsWith("https://") -> URI(decodedUrl)
                decodedUrl.startsWith("/") -> URI("${prefixUri.scheme}://${prefixUri.authority}$decodedUrl")
                else -> URI("${prefixUri.scheme}://${prefixUri.authority}/${decodedUrl.trimStart('/')}")
            }

            if (normalizedUri.authority != prefixUri.authority) return@runCatching null

            val prefixPath = prefixUri.path.trimEnd('/')
            val urlPath = normalizedUri.path
            if (!urlPath.startsWith(prefixPath)) return@runCatching null

            urlPath.removePrefix(prefixPath).trim('/').ifEmpty { null }
        }.getOrNull()
    }

    private fun normalizeEtag(raw: String?): String? {
        if (raw == null) return null
        // Removes "W/" prefix if present, then removes surrounding quotes
        return raw.replace("W/", "", ignoreCase = true).trim('"')
    }
}