package com.gladomat.linklet.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NoteHashCalculatorTests {

    @Test
    fun `compute returns same hash for same input`() {
        val first = NoteHashCalculator.compute("sample")
        val second = NoteHashCalculator.compute("sample")

        assertEquals(first, second)
    }

    @Test
    fun `compute returns different hash for different input`() {
        val first = NoteHashCalculator.compute("alpha")
        val second = NoteHashCalculator.compute("beta")

        assertNotEquals(first, second)
    }
}
