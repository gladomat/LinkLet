package com.gladomat.linklet.data.sync.webdav

import java.io.InputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class PropfindItem(
    val href: String,
    val etag: String?,
    val lastModifiedEpochMillis: Long?,
    val sizeBytes: Long?,
    val isDir: Boolean,
    val fileId: String?,
)

object PropfindResponseParser {
    private const val DAV_NAMESPACE = "DAV:"
    private const val OWNCLOUD_NAMESPACE = "http://owncloud.org/ns"
    private const val NEXTCLOUD_NAMESPACE = "http://nextcloud.org/ns"

    fun parse(input: InputStream): Sequence<PropfindItem> = sequence {
        input.bufferedReader().use { reader ->
            val parser = XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = true
            }.newPullParser()
            parser.setInput(reader)

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name.equals("response", ignoreCase = true)) {
                    readResponse(parser)?.let { yield(it) }
                }
                eventType = parser.next()
            }
        }
    }

    private fun readResponse(parser: XmlPullParser): PropfindItem? {
        val responseDepth = parser.depth
        var href: String? = null
        var etag: String? = null
        var lastModifiedEpochMillis: Long? = null
        var sizeBytes: Long? = null
        var isDir = false
        var fileId: String? = null

        while (!(parser.eventType == XmlPullParser.END_TAG && parser.depth == responseDepth && parser.name.equals("response", ignoreCase = true))) {
            parser.next()
            if (parser.eventType != XmlPullParser.START_TAG) continue

            // We only parse known DAV and ownCloud/Nextcloud properties. Namespace filtering
            // avoids accidental matches from custom extension tags with the same local names.
            when {
                isTag(parser, "href", DAV_NAMESPACE) -> href = parser.nextText().trim()
                isTag(parser, "getetag", DAV_NAMESPACE) -> etag = normalizeEtag(parser.nextText())
                isTag(parser, "getlastmodified", DAV_NAMESPACE) -> lastModifiedEpochMillis = parseHttpDate(parser.nextText())
                isTag(parser, "getcontentlength", DAV_NAMESPACE) -> sizeBytes = parser.nextText().trim().toLongOrNull()
                isTag(parser, "collection", DAV_NAMESPACE) -> isDir = true
                isTag(parser, "fileid", OWNCLOUD_NAMESPACE, NEXTCLOUD_NAMESPACE) -> fileId = parser.nextText().trim().ifBlank { null }
            }
        }

        val resolvedHref = href?.takeIf { it.isNotBlank() } ?: return null
        return PropfindItem(
            href = resolvedHref,
            etag = etag,
            lastModifiedEpochMillis = lastModifiedEpochMillis,
            sizeBytes = sizeBytes,
            isDir = isDir,
            fileId = fileId,
        )
    }

    private fun parseHttpDate(value: String): Long? = runCatching {
        ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    }.getOrNull()

    private fun isTag(
        parser: XmlPullParser,
        localName: String,
        vararg namespaces: String,
    ): Boolean {
        if (!parser.name.equals(localName, ignoreCase = true)) return false
        val namespace = parser.namespace ?: return true
        if (namespace.isBlank()) return true
        return namespaces.any { namespace.equals(it, ignoreCase = true) }
    }

    private fun normalizeEtag(raw: String?): String? {
        if (raw == null) return null
        // See WebDavRemoteSyncProvider.normalizeEtag: we intentionally collapse weak and
        // strong validators into a single fingerprint representation.
        return raw.replace("W/", "", ignoreCase = true).trim().trim('"').ifBlank { null }
    }
}
