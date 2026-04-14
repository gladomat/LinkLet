package com.gladomat.linklet.data.sync.delta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeltaValidityTests {

    @Test
    fun `valid is usable`() {
        val validity = DeltaValidity.valid()
        assertTrue(validity.isUsable)
        assertEquals(DeltaValidityState.VALID, validity.state)
        assertEquals("ok", validity.reason)
    }

    @Test
    fun `softInvalid is not usable`() {
        val validity = DeltaValidity.softInvalid("stale data")
        assertFalse(validity.isUsable)
        assertEquals(DeltaValidityState.SOFT_INVALID, validity.state)
        assertEquals("stale data", validity.reason)
    }

    @Test
    fun `hardInvalid is not usable`() {
        val validity = DeltaValidity.hardInvalid("no watermark")
        assertFalse(validity.isUsable)
        assertEquals(DeltaValidityState.HARD_INVALID, validity.state)
        assertEquals("no watermark", validity.reason)
    }
}
