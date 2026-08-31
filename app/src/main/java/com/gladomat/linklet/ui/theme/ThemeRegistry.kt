package com.gladomat.linklet.ui.theme

import com.gladomat.linklet.data.settings.ThemeId
import com.gladomat.linklet.ui.theme.themes.Amberlink
import com.gladomat.linklet.ui.theme.themes.CatppuccinMocha
import com.gladomat.linklet.ui.theme.themes.EverforestDark
import com.gladomat.linklet.ui.theme.themes.ModusOperandi
import com.gladomat.linklet.ui.theme.themes.TokyoNightStorm

/**
 * One available theme: the token set plus the two facts the platform needs that are not colors
 * (its display label and whether it is a dark theme, which drives status-bar icon polarity).
 */
data class ThemeSpec(
    val id: ThemeId,
    val label: String,
    val isDark: Boolean,
    val tokens: ThemeTokens,
)

/** Enumerates the available themes and resolves the active one. */
object ThemeRegistry {

    val themes: List<ThemeSpec> = listOf(
        ThemeSpec(ThemeId.AMBERLINK, "Amberlink", isDark = true, tokens = Amberlink),
        ThemeSpec(ThemeId.EVERFOREST, "Everforest", isDark = true, tokens = EverforestDark),
        ThemeSpec(ThemeId.MODUS_OPERANDI, "Modus Operandi", isDark = false, tokens = ModusOperandi),
        ThemeSpec(ThemeId.CATPPUCCIN_MOCHA, "Catppuccin Mocha", isDark = true, tokens = CatppuccinMocha),
        ThemeSpec(ThemeId.TOKYO_NIGHT, "Tokyo Night", isDark = true, tokens = TokyoNightStorm),
    )

    val default: ThemeSpec = themes.first { it.id == ThemeId.AMBERLINK }

    fun resolve(id: ThemeId): ThemeSpec = themes.first { it.id == id }
}
