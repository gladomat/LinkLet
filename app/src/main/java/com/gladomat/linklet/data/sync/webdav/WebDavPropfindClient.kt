package com.gladomat.linklet.data.sync.webdav

import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class WebDavPropfindClient(
    private val client: OkHttpClient,
) {
    fun propfind(url: String, depth: Int = 1): Result<List<PropfindItem>> = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("Depth", depth.toString())
            .method("PROPFIND", buildPropfindBody())
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("PROPFIND failed: HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("PROPFIND failed: empty body")
            PropfindResponseParser.parse(body.byteStream()).toList()
        }
    }

    private fun buildPropfindBody() =
        """
        <d:propfind xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
          <d:prop>
            <d:getetag/>
            <d:getlastmodified/>
            <d:getcontentlength/>
            <d:resourcetype/>
            <oc:fileid/>
          </d:prop>
        </d:propfind>
        """.trimIndent().toRequestBody("text/xml; charset=utf-8".toMediaType())
}
