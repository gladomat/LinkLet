package com.gladomat.linklet.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gladomat.linklet.testing.Aarch64RobolectricTestRunner
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(Aarch64RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AppearanceSettingsRepositoryTests {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = AppearanceSettingsRepository(context)

    @After
    fun tearDown() = runTest {
        repository.setThemeMode(ThemeMode.SYSTEM)
        repository.setThemePalette(ThemePalette.AMBER)
        repository.setThemeId(ThemeId.AMBERLINK)
    }

    @Test
    fun `defaults to the Amberlink theme`() = runTest {
        assertEquals(ThemeId.AMBERLINK, repository.currentThemeId())
    }

    @Test
    fun `stored theme id round-trips`() = runTest {
        repository.setThemeId(ThemeId.TOKYO_NIGHT)
        assertEquals(ThemeId.TOKYO_NIGHT, repository.currentThemeId())
    }

    @Test
    fun `defaults to following the system theme`() = runTest {
        assertEquals(ThemeMode.SYSTEM, repository.currentThemeMode())
    }

    @Test
    fun `defaults to the amber palette`() = runTest {
        assertEquals(ThemePalette.AMBER, repository.currentThemePalette())
    }

    @Test
    fun `stored palette round-trips independently of the mode`() = runTest {
        repository.setThemePalette(ThemePalette.SEA_GREEN)
        repository.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemePalette.SEA_GREEN, repository.currentThemePalette())
        assertEquals(ThemeMode.DARK, repository.currentThemeMode())
    }

    @Test
    fun `stored theme mode round-trips`() = runTest {
        repository.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repository.currentThemeMode())

        repository.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, repository.currentThemeMode())
    }
}
