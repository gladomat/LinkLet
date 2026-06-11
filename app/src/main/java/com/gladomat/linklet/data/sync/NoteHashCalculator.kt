package com.gladomat.linklet.data.sync

import java.security.MessageDigest
import java.util.Locale

/**
 * Provides deterministic SHA-256 hashes for note contents so sync logic can
 * detect local changes without reading timestamps.
 */
object NoteHashCalculator {

    fun compute(content: String): String {
        return computeBytes(content.toByteArray(Charsets.UTF_8))
    }

    fun computeBytes(content: ByteArray): String = computeBytes(content, "SHA-256")!!

    /**
     * Computes a lowercase hex digest of [content] using the given JCA [algorithm]
     * (e.g. "SHA-256", "SHA-1", "MD5"). Returns null if the algorithm is unavailable.
     */
    fun computeBytes(content: ByteArray, algorithm: String): String? {
        val digest = runCatching { MessageDigest.getInstance(algorithm) }.getOrNull() ?: return null
        val hashed = digest.digest(content)
        return hashed.joinToString(separator = "") { byte ->
            String.format(Locale.US, "%02x", byte)
        }
    }

    /** Algorithms we can verify a remote checksum against locally, most-preferred first. */
    val SUPPORTED_ALGORITHMS: List<String> = listOf("SHA-256", "SHA-512", "SHA-1", "MD5")

    /**
     * Parses an ownCloud/Nextcloud-style checksum string such as
     * "SHA1:0beec7... MD5:5d4140..." into a map keyed by normalized JCA algorithm name
     * ("SHA-1", "MD5", ...) with lowercase hex values. Returns null if nothing parseable.
     */
    fun parseChecksums(raw: String?): Map<String, String>? {
        if (raw.isNullOrBlank()) return null
        val out = mutableMapOf<String, String>()
        raw.trim().split(Regex("\\s+")).forEach { token ->
            val idx = token.indexOf(':')
            if (idx <= 0 || idx == token.length - 1) return@forEach
            val algo = normalizeAlgorithm(token.substring(0, idx)) ?: return@forEach
            val value = token.substring(idx + 1).lowercase(Locale.US)
            if (value.isNotEmpty()) out[algo] = value
        }
        return out.takeIf { it.isNotEmpty() }
    }

    private fun normalizeAlgorithm(name: String): String? = when (name.uppercase(Locale.US)) {
        "SHA256", "SHA-256" -> "SHA-256"
        "SHA512", "SHA-512" -> "SHA-512"
        "SHA1", "SHA-1" -> "SHA-1"
        "MD5" -> "MD5"
        else -> null
    }
}
