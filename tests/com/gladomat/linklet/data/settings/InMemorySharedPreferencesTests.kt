package com.gladomat.linklet.data.settings

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM coverage for the in-memory SharedPreferences fallback used when encrypted prefs cannot
 * be opened (the AndroidKeyStore-unavailable path). Runs on any host (no Robolectric).
 */
class InMemorySharedPreferencesTests {

    @Test
    fun `put and get round-trip for each type`() {
        val prefs = InMemorySharedPreferences()
        prefs.edit()
            .putString("s", "hello")
            .putBoolean("b", true)
            .putInt("i", 7)
            .putLong("l", 9L)
            .apply()

        assertEquals("hello", prefs.getString("s", null))
        assertTrue(prefs.getBoolean("b", false))
        assertEquals(7, prefs.getInt("i", 0))
        assertEquals(9L, prefs.getLong("l", 0L))
        assertTrue(prefs.contains("s"))
        assertEquals("default", prefs.getString("missing", "default"))
    }

    @Test
    fun `remove and clear work`() {
        val prefs = InMemorySharedPreferences()
        prefs.edit().putString("a", "1").putString("b", "2").apply()

        prefs.edit().remove("a").apply()
        assertNull(prefs.getString("a", null))
        assertEquals("2", prefs.getString("b", null))

        prefs.edit().clear().apply()
        assertFalse(prefs.contains("b"))
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun `change listener fires for changed keys and stops after unregister`() {
        val prefs = InMemorySharedPreferences()
        val changed = mutableListOf<String>()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> key?.let(changed::add) }

        prefs.registerOnSharedPreferenceChangeListener(listener)
        prefs.edit().putString("x", "1").apply()
        assertEquals(listOf("x"), changed)

        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        prefs.edit().putString("y", "2").apply()
        assertEquals(listOf("x"), changed) // no further notifications
    }
}
