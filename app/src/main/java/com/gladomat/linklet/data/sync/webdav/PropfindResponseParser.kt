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

            when (parser.name.lowercase()) {
                "href" -> href = parser.nextText().trim()
                "getetag" -> etag = normalizeEtag(parser.nextText())
                "getlastmodified" -> lastModifiedEpochMillis = parseHttpDate(parser.nextText())
                "getcontentlength" -> sizeBytes = parser.nextText().trim().toLongOrNull()
                "collection" -> isDir = true
                "fileid" -> fileId = parser.nextText().trim().ifBlank { null }
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

    private fun normalizeEtag(raw: String?): String? {
        if (raw == null) return null
        return raw.replace("W/", "", ignoreCase = true).trim().trim('"').ifBlank { null }
    }
}
