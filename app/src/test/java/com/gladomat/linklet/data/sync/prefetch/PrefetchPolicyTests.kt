package com.gladomat.linklet.data.sync.prefetch

import com.gladomat.linklet.data.index.NoteAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefetchPolicyTests {

    private fun candidate(
        path: String,
        availability: NoteAvailability = NoteAvailability.STUB,
        lastModified: Long? = null,
        size: Long? = null,
    ) = PrefetchCandidate(path, availability, lastModified, size)

    @Test
    fun `pinned offline items are prioritized over stubs`() {
        val policy = PrefetchPolicy()
        val candidates = listOf(
            candidate("notes/b.org", NoteAvailability.STUB, lastModified = 2000L),
            candidate("notes/a.org", NoteAvailability.PINNED_OFFLINE, lastModified = 1000L),
        )
        val result = policy.selectForPrefetch(candidates)
        assertEquals(2, result.size)
        assertEquals(NoteAvailability.PINNED_OFFLINE, result[0].availability)
        assertEquals("notes/a.org", result[0].path)
    }

    @Test
    fun `budget maxFiles is respected`() {
        val policy = PrefetchPolicy(PrefetchBudget(maxFiles = 2))
        val candidates = (1..10).map {
            candidate("notes/$it.org", NoteAvailability.STUB, lastModified = it.toLong())
        }
        val result = policy.selectForPrefetch(candidates)
        assertEquals(2, result.size)
    }

    @Test
    fun `budget maxTotalBytes is respected`() {
        val policy = PrefetchPolicy(PrefetchBudget(maxTotalBytes = 100))
        val candidates = listOf(
            candidate("notes/a.org", NoteAvailability.STUB, size = 60L, lastModified = 3000L),
            candidate("notes/b.org", NoteAvailability.STUB, size = 60L, lastModified = 2000L),
            candidate("notes/c.org", NoteAvailability.STUB, size = 30L, lastModified = 1000L),
        )
        val result = policy.selectForPrefetch(candidates)
        // a (60) fits, b (60) would exceed 100, skip b, c (30) fits => a + c = 90
        assertEquals(2, result.size)
        assertEquals("notes/a.org", result[0].path)
        assertEquals("notes/c.org", result[1].path)
    }

    @Test
    fun `files exceeding maxFileSizeBytes are skipped`() {
        val policy = PrefetchPolicy(PrefetchBudget(maxFileSizeBytes = 100))
        val candidates = listOf(
            candidate("notes/small.org", NoteAvailability.STUB, size = 50L),
            candidate("notes/big.org", NoteAvailability.STUB, size = 200L),
        )
        val result = policy.selectForPrefetch(candidates)
        assertEquals(1, result.size)
        assertEquals("notes/small.org", result[0].path)
    }

    @Test
    fun `only allowed extensions are included`() {
        val policy = PrefetchPolicy(PrefetchBudget(allowedExtensions = setOf("org", "md")))
        val candidates = listOf(
            candidate("notes/good.org", NoteAvailability.STUB),
            candidate("notes/good.md", NoteAvailability.STUB),
            candidate("notes/bad.pdf", NoteAvailability.STUB),
            candidate("notes/bad.png", NoteAvailability.STUB),
        )
        val result = policy.selectForPrefetch(candidates)
        assertEquals(2, result.size)
        assertTrue(result.all { it.path.endsWith(".org") || it.path.endsWith(".md") })
    }

    @Test
    fun `already AVAILABLE notes are excluded`() {
        val policy = PrefetchPolicy()
        val candidates = listOf(
            candidate("notes/available.org", NoteAvailability.AVAILABLE),
            candidate("notes/error.org", NoteAvailability.ERROR),
            candidate("notes/stub.org", NoteAvailability.STUB),
        )
        val result = policy.selectForPrefetch(candidates)
        assertEquals(1, result.size)
        assertEquals("notes/stub.org", result[0].path)
    }

    @Test
    fun `empty candidate list returns empty result`() {
        val policy = PrefetchPolicy()
        val result = policy.selectForPrefetch(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `candidates sorted by recency when no pinned items`() {
        val policy = PrefetchPolicy()
        val candidates = listOf(
            candidate("notes/old.org", NoteAvailability.STUB, lastModified = 1000L),
            candidate("notes/new.org", NoteAvailability.STUB, lastModified = 3000L),
            candidate("notes/mid.org", NoteAvailability.STUB, lastModified = 2000L),
        )
        val result = policy.selectForPrefetch(candidates)
        assertEquals(3, result.size)
        // Most recent first (highest lastModified = lowest sortKey)
        assertEquals("notes/new.org", result[0].path)
        assertEquals("notes/mid.org", result[1].path)
        assertEquals("notes/old.org", result[2].path)
    }
}
