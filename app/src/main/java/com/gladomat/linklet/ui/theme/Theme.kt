package com.gladomat.linklet.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.gladomat.linklet.data.settings.ThemeMode
import com.gladomat.linklet.data.settings.ThemePalette

/** Resolves the user's stored light/dark choice against the device setting. */
@Composable
fun ThemeMode.resolveDarkTheme(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Applies a theme from [ThemeRegistry].
 *
 * Tokens are published through [LocalThemeTokens]; the Material [ColorScheme] is derived from the
 * same tokens so stock Material components stay in step with token-driven ones. Switching theme is
 * a Compose recomposition, not a stylesheet swap — the platform has no CSS repaint to hook.
 */
@Composable
fun LinkLetAppTheme(
    theme: ThemeSpec = ThemeRegistry.default,
    content: @Composable () -> Unit,
) {
    val tokens = theme.tokens
    val colorScheme = colorSchemeFor(theme)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = tokens.bgSecondary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !theme.isDark
        }
    }

    CompositionLocalProvider(LocalThemeTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}

/**
 * Maps the token contract onto Material 3's slots. Kept free of the Compose runtime so the contrast
 * check can build every scheme on the plain-JVM test tier.
 */
fun colorSchemeFor(theme: ThemeSpec): ColorScheme {
    val t = theme.tokens
    val base = if (theme.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = t.accent,
        onPrimary = readableOn(t.accent, t),
        primaryContainer = t.bgSurface,
        onPrimaryContainer = t.accent,
        secondary = t.linkInternal,
        onSecondary = readableOn(t.linkInternal, t),
        secondaryContainer = t.bgSurface,
        onSecondaryContainer = t.linkInternal,
        tertiary = t.tagFg,
        onTertiary = readableOn(t.tagFg, t),
        tertiaryContainer = t.tagBg,
        onTertiaryContainer = t.tagFg,
        background = t.bgPrimary,
        onBackground = t.textNormal,
        surface = t.bgSurface,
        onSurface = t.textNormal,
        surfaceVariant = t.bgModifierHover,
        onSurfaceVariant = t.textMuted,
        surfaceContainer = t.bgSurface,
        surfaceContainerHigh = t.bgModifierHover,
        surfaceContainerHighest = t.bgModifierHover,
        surfaceContainerLow = t.bgSecondary,
        surfaceContainerLowest = t.bgSecondary,
        inverseSurface = t.textNormal,
        inverseOnSurface = t.bgPrimary,
        inversePrimary = t.accent,
        outline = t.border,
        outlineVariant = t.textFaint,
        error = t.linkUnresolved,
        onError = readableOn(t.linkUnresolved, t),
        errorContainer = t.bgSurface,
        onErrorContainer = t.linkUnresolved,
        scrim = t.bgSecondary,
    )
}

/** Picks whichever of the theme's own ground/ink colors reads better on [background]. */
private fun readableOn(background: Color, tokens: ThemeTokens): Color =
    if (contrastRatio(background, tokens.bgPrimary) >= contrastRatio(background, tokens.textNormal)) {
        tokens.bgPrimary
    } else {
        tokens.textNormal
    }

// ---------------------------------------------------------------------------------------------
// Legacy accent palettes. Superseded by the theme registry above and no longer reachable from the
// UI; kept on disk deliberately.
// ---------------------------------------------------------------------------------------------

/** The hand-tuned scheme for one legacy palette in one brightness. */
fun colorSchemeFor(palette: ThemePalette, darkTheme: Boolean): ColorScheme = when (palette) {
    ThemePalette.AMBER -> if (darkTheme) AmberDark else AmberLight
    ThemePalette.CERULEAN -> if (darkTheme) CeruleanDark else CeruleanLight
    ThemePalette.SEA_GREEN -> if (darkTheme) SeaGreenDark else SeaGreenLight
    ThemePalette.MAGENTA -> if (darkTheme) MagentaDark else MagentaLight
}

/** The accent a legacy palette shows. */
fun paletteSwatch(palette: ThemePalette): Color = when (palette) {
    ThemePalette.AMBER -> AmberGold
    ThemePalette.CERULEAN -> RichCerulean
    ThemePalette.SEA_GREEN -> SeaGreen
    ThemePalette.MAGENTA -> MagentaBloom
}

private val AmberDark = darkColorScheme(
    primary = AmberGold,
    onPrimary = Color.Black,
    secondary = MagentaBloom,
    onSecondary = Color.White,
    tertiary = RichCerulean,
    onTertiary = Color.White,
    background = Charcoal,
    onBackground = PaperSurface,
    surface = MidnightSurface,
    onSurface = PaperSurface,
    surfaceVariant = SeaGreen.copy(alpha = 0.45f),
    onSurfaceVariant = SeaGreen,
)

private val AmberLight = lightColorScheme(
    primary = AmberGold,
    onPrimary = Color.Black,
    secondary = MagentaBloom,
    onSecondary = Color.White,
    tertiary = RichCerulean,
    onTertiary = Color.White,
    background = SnowDrift,
    onBackground = AmberInk,
    surface = Color.White,
    onSurface = AmberInk,
    surfaceVariant = SeaGreen.copy(alpha = 0.18f),
    onSurfaceVariant = SeaGreen,
)

private val CeruleanDark = darkColorScheme(
    primary = RichCerulean,
    onPrimary = Color.White,
    secondary = SeaGreen,
    onSecondary = Color.Black,
    tertiary = AmberGold,
    onTertiary = Color.Black,
    background = Charcoal,
    onBackground = CeruleanPaper,
    surface = MidnightSurface,
    onSurface = CeruleanPaper,
    surfaceVariant = RichCerulean.copy(alpha = 0.45f),
    onSurfaceVariant = CeruleanPaper,
)

private val CeruleanLight = lightColorScheme(
    primary = RichCerulean,
    onPrimary = Color.White,
    secondary = SeaGreen,
    onSecondary = Color.White,
    tertiary = AmberGold,
    onTertiary = Color.Black,
    background = CeruleanMist,
    onBackground = CeruleanInk,
    surface = Color.White,
    onSurface = CeruleanInk,
    surfaceVariant = RichCerulean.copy(alpha = 0.18f),
    onSurfaceVariant = RichCerulean,
)

private val SeaGreenDark = darkColorScheme(
    primary = SeaGreen,
    onPrimary = Color.Black,
    secondary = AmberGold,
    onSecondary = Color.Black,
    tertiary = MagentaBloom,
    onTertiary = Color.White,
    background = Charcoal,
    onBackground = SeaGreenPaper,
    surface = MidnightSurface,
    onSurface = SeaGreenPaper,
    surfaceVariant = SeaGreen.copy(alpha = 0.45f),
    onSurfaceVariant = SeaGreenPaper,
)

private val SeaGreenLight = lightColorScheme(
    primary = SeaGreen,
    onPrimary = Color.White,
    secondary = AmberGold,
    onSecondary = Color.Black,
    tertiary = MagentaBloom,
    onTertiary = Color.White,
    background = SeaGreenMist,
    onBackground = SeaGreenInk,
    surface = Color.White,
    onSurface = SeaGreenInk,
    surfaceVariant = SeaGreen.copy(alpha = 0.18f),
    onSurfaceVariant = SeaGreen,
)

private val MagentaDark = darkColorScheme(
    primary = MagentaBloom,
    onPrimary = Color.White,
    secondary = RichCerulean,
    onSecondary = Color.White,
    tertiary = SeaGreen,
    onTertiary = Color.Black,
    background = Charcoal,
    onBackground = MagentaPaper,
    surface = MidnightSurface,
    onSurface = MagentaPaper,
    surfaceVariant = MagentaBloom.copy(alpha = 0.45f),
    onSurfaceVariant = MagentaPaper,
)

private val MagentaLight = lightColorScheme(
    primary = MagentaBloom,
    onPrimary = Color.White,
    secondary = RichCerulean,
    onSecondary = Color.White,
    tertiary = SeaGreen,
    onTertiary = Color.White,
    background = MagentaMist,
    onBackground = MagentaInk,
    surface = Color.White,
    onSurface = MagentaInk,
    surfaceVariant = MagentaBloom.copy(alpha = 0.18f),
    onSurfaceVariant = MagentaBloom,
)
