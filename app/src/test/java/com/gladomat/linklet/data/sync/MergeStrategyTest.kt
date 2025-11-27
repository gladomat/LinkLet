package com.gladomat.linklet.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeStrategyTest {

    private val mergeStrategy = MergeStrategy()

    @Test
    fun `merge clean non-overlapping edits returns success`() {
        val base = "Line 1\nLine 2\nLine 3"
        val local = "Line 1\nLine 2 (Local)\nLine 3"
        val remote = "Line 1 (Remote)\nLine 2\nLine 3"

        val result = mergeStrategy.merge(local, remote, base)

        assertTrue(result is MergeResult.Success)
        val merged = (result as MergeResult.Success).mergedContent
        val expected = "Line 1 (Remote)\nLine 2 (Local)\nLine 3"
        assertEquals(expected, merged)
    }

    @Test
    fun `merge clean identical changes returns success`() {
        val base = "Line 1\nLine 2\nLine 3"
        val local = "Line 1\nLine 2 (Changed)\nLine 3"
        val remote = "Line 1\nLine 2 (Changed)\nLine 3"

        val result = mergeStrategy.merge(local, remote, base)

        assertTrue(result is MergeResult.Success)
        val merged = (result as MergeResult.Success).mergedContent
        val expected = "Line 1\nLine 2 (Changed)\nLine 3"
        assertEquals(expected, merged)
    }

    @Test
    fun `merge conflict overlapping edits returns conflict`() {
        val base = "Line 1\nLine 2\nLine 3"
        val local = "Line 1\nLine 2 (Local)\nLine 3"
        val remote = "Line 1\nLine 2 (Remote)\nLine 3"

        val result = mergeStrategy.merge(local, remote, base)

        assertTrue(result is MergeResult.Conflict)
    }

    @Test
    fun `merge remote addition local unchanged returns success`() {
        val base = "Line 1\nLine 2"
        val local = "Line 1\nLine 2"
        val remote = "Line 1\nLine 2\nLine 3"

        val result = mergeStrategy.merge(local, remote, base)

        assertTrue(result is MergeResult.Success)
        assertEquals("Line 1\nLine 2\nLine 3", (result as MergeResult.Success).mergedContent)
    }

    @Test
    fun `merge local addition remote unchanged returns success`() {
        val base = "Line 1\nLine 2"
        val local = "Line 1\nLine 2\nLine 3"
        val remote = "Line 1\nLine 2"

        val result = mergeStrategy.merge(local, remote, base)

        assertTrue(result is MergeResult.Success)
        assertEquals("Line 1\nLine 2\nLine 3", (result as MergeResult.Success).mergedContent)
    }
}
