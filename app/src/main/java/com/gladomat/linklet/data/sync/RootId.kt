package com.gladomat.linklet.data.sync

import java.security.MessageDigest

fun buildRootId(baseUrl: String, normalizedRootPath: String, username: String): String {
    val canonicalBaseUrl = baseUrl.trim().trimEnd('/')
    val canonicalRootPath = canonicalizeRootPath(normalizedRootPath)
    val canonicalUsername = username.trim()
    val payload = "$canonicalBaseUrl\n$canonicalRootPath\n$canonicalUsername"

    return sha256Hex(payload)
}

private fun canonicalizeRootPath(path: String): String {
    val trimmed = path.trim().trim('/')
    return if (trimmed.isEmpty()) "/" else "/$trimmed"
}

private fun sha256Hex(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return buildString(digest.size * 2) {
        digest.forEach { byte ->
            append("%02x".format(byte))
        }
    }
}
