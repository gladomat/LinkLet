package com.gladomat.linklet.ui.screens.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.gladomat.linklet.data.settings.ThemeId
import com.gladomat.linklet.testing.Aarch64RobolectricTestRunner
import com.gladomat.linklet.viewmodel.settings.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(Aarch64RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsScreenTests {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setScreen(
        state: SettingsUiState,
        onSelectTheme: (ThemeId) -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    state = state,
                    onNavigateBack = {},
                    onOpenWebDavSettings = {},
                    onPickFolder = {},
                    onManualSync = {},
                    onTogglePeriodicSync = {},
                    onUpdateSyncInterval = {},
                    onSelectTheme = onSelectTheme,
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }
    }

    @Test
    fun `theme select shows the stored theme`() {
        setScreen(SettingsUiState(themeId = ThemeId.TOKYO_NIGHT))

        composeRule.onNodeWithText("Tokyo Night").performScrollTo().assertExists()
    }

    @Test
    fun `picking a theme reports the selected id`() {
        val selected = mutableListOf<ThemeId>()
        setScreen(SettingsUiState(), onSelectTheme = selected::add)

        composeRule.onNodeWithText("Amberlink").performScrollTo().performClick()
        composeRule.onNodeWithText("Catppuccin Mocha").performClick()

        assertEquals(listOf(ThemeId.CATPPUCCIN_MOCHA), selected)
    }
}
