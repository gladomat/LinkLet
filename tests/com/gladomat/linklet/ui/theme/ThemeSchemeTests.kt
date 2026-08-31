package com.gladomat.linklet.ui.theme

import com.gladomat.linklet.data.settings.ThemePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Plain JUnit — `colorSchemeFor` is deliberately free of the Compose runtime. */
class ThemeSchemeTests {

    @Test
    fun `each palette drives a distinct primary in both brightnesses`() {
        listOf(false, true).forEach { dark ->
            val primaries = ThemePalette.entries.map { colorSchemeFor(it, dark).primary }
            assertEquals(
                "duplicate primaries for dark=$dark: $primaries",
                ThemePalette.entries.size,
                primaries.toSet().size,
            )
        }
    }

    @Test
    fun `the swatch matches the palette's own primary`() {
        ThemePalette.entries.forEach { palette ->
            assertEquals(colorSchemeFor(palette, false).primary, paletteSwatch(palette))
            assertEquals(colorSchemeFor(palette, true).primary, paletteSwatch(palette))
        }
    }

    @Test
    fun `dark schemes keep the shared dark background`() {
        ThemePalette.entries.forEach { palette ->
            val dark = colorSchemeFor(palette, true)
            assertEquals(Charcoal, dark.background)
            assertNotEquals(dark.background, colorSchemeFor(palette, false).background)
        }
    }
}
