package com.gladomat.linklet.data.sync.delta

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object DeltaTime {
    /** Safety margin to account for clock skew between client and server. */
    const val SKEW_MARGIN_MS: Long = 5_000 // 5 seconds

    /** Parse the HTTP Date header (RFC 1123) into epoch millis. Returns null on failure. */
    fun parseHttpDate(dateHeader: String?): Long? {
        if (dateHeader.isNullOrBlank()) return null
        return runCatching {
            ZonedDateTime.parse(dateHeader.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant().toEpochMilli()
        }.getOrNull()
    }

    /** Compute the query watermark: stored server time minus skew margin. */
    fun queryWatermark(lastServerTimeEpochMillis: Long): Long =
        lastServerTimeEpochMillis - SKEW_MARGIN_MS
}
