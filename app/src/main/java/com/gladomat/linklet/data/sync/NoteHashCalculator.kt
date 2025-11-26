package com.gladomat.linklet.data.sync

import java.security.MessageDigest
import java.util.Locale

/**
 * Provides deterministic SHA-256 hashes for note contents so sync logic can
 * detect local changes without reading timestamps.
 */
object NoteHashCalculator {

    fun compute(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = content.toByteArray(Charsets.UTF_8)
        val hashed = digest.digest(bytes)
        return hashed.joinToString(separator = "") { byte ->
            String.format(Locale.US, "%02x", byte)
        }
    }
}
