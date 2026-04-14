package com.gladomat.linklet.data.sync.prefetch

import com.gladomat.linklet.data.index.NoteAvailability

data class PrefetchBudget(
    val maxFiles: Int = 50,
    val maxTotalBytes: Long = 5 * 1024 * 1024, // 5 MB
    val maxFileSizeBytes: Long = 512 * 1024, // 512 KB per file
    val allowedExtensions: Set<String> = setOf("org"),
)

data class PrefetchCandidate(
    val path: String,
    val availability: NoteAvailability,
    val lastModifiedEpochMillis: Long?,
    val sizeBytes: Long?,
) {
    /** Priority: PINNED_OFFLINE first (0), then by recency (lower = higher priority). */
    val sortKey: Long
        get() = when (availability) {
            NoteAvailability.PINNED_OFFLINE -> 0L
            else -> Long.MAX_VALUE - (lastModifiedEpochMillis ?: 0L)
        }
}

class PrefetchPolicy(private val budget: PrefetchBudget = PrefetchBudget()) {

    /**
     * Given a list of candidates, return the subset that should be prefetched,
     * respecting budget constraints.
     */
    fun selectForPrefetch(candidates: List<PrefetchCandidate>): List<PrefetchCandidate> {
        val eligible = candidates
            .filter { it.availability == NoteAvailability.STUB || it.availability == NoteAvailability.PINNED_OFFLINE }
            .filter { candidate ->
                val ext = candidate.path.substringAfterLast('.', "").lowercase()
                ext in budget.allowedExtensions
            }
            .filter { candidate ->
                val size = candidate.sizeBytes ?: return@filter true
                size <= budget.maxFileSizeBytes
            }
            .sortedBy { it.sortKey }

        val selected = mutableListOf<PrefetchCandidate>()
        var totalBytes = 0L
        for (candidate in eligible) {
            if (selected.size >= budget.maxFiles) break
            val size = candidate.sizeBytes ?: 0L
            // Greedy bin-packing: skip files that don't fit, keep trying smaller ones
            if (totalBytes + size > budget.maxTotalBytes) continue
            totalBytes += size
            selected.add(candidate)
        }
        return selected
    }
}
