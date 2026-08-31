package com.gladomat.linklet.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

private val Context.appearanceSettingsDataStore by preferencesDataStore(name = "appearance_settings")

/** How the app picks between the light and dark color schemes. */
enum class ThemeMode {
    /** Follow the device's dark-theme setting. */
    SYSTEM,
    LIGHT,
    DARK,
}

/** Accent palette applied on top of the light/dark choice. */
enum class ThemePalette {
    AMBER,
    CERULEAN,
    SEA_GREEN,
    MAGENTA,
}

/** Identifies a theme in the registry. Stored by name; unknown names fall back to the default. */
enum class ThemeId {
    AMBERLINK,
    EVERFOREST,
    MODUS_OPERANDI,
    CATPPUCCIN_MOCHA,
    TOKYO_NIGHT,
}

@Singleton
class AppearanceSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(context.appearanceSettingsDataStore)

    private object Keys {
        val THEME_MODE: Preferences.Key<String> = stringPreferencesKey("theme_mode")
        val THEME_PALETTE: Preferences.Key<String> = stringPreferencesKey("theme_palette")
        val THEME_ID: Preferences.Key<String> = stringPreferencesKey("theme_id")
    }

    val themeModeFlow: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { stored ->
            ThemeMode.entries.firstOrNull { it.name == stored }
        } ?: ThemeMode.SYSTEM
    }

    val themePaletteFlow: Flow<ThemePalette> = dataStore.data.map { prefs ->
        prefs[Keys.THEME_PALETTE]?.let { stored ->
            ThemePalette.entries.firstOrNull { it.name == stored }
        } ?: ThemePalette.AMBER
    }

    val themeIdFlow: Flow<ThemeId> = dataStore.data.map { prefs ->
        prefs[Keys.THEME_ID]?.let { stored ->
            ThemeId.entries.firstOrNull { it.name == stored }
        } ?: ThemeId.AMBERLINK
    }

    suspend fun setThemeId(themeId: ThemeId) {
        dataStore.edit { settings ->
            settings[Keys.THEME_ID] = themeId.name
        }
    }

    suspend fun currentThemeId(): ThemeId = themeIdFlow.firstOrNull() ?: ThemeId.AMBERLINK

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { settings ->
            settings[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun setThemePalette(palette: ThemePalette) {
        dataStore.edit { settings ->
            settings[Keys.THEME_PALETTE] = palette.name
        }
    }

    suspend fun currentThemeMode(): ThemeMode = themeModeFlow.firstOrNull() ?: ThemeMode.SYSTEM

    suspend fun currentThemePalette(): ThemePalette = themePaletteFlow.firstOrNull() ?: ThemePalette.AMBER
}
