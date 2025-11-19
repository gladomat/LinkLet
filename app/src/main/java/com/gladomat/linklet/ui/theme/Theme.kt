package com.gladomat.linklet.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun LinkLetAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}

private val DarkColorScheme = androidx.compose.material3.darkColorScheme(
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

private val LightColorScheme = androidx.compose.material3.lightColorScheme(
    primary = AmberGold,
    onPrimary = Color.Black,
    secondary = MagentaBloom,
    onSecondary = Color.White,
    tertiary = RichCerulean,
    onTertiary = Color.White,
    background = SnowDrift,
    onBackground = Color(0xFF1A1200),
    surface = Color.White,
    onSurface = Color(0xFF1A1200),
    surfaceVariant = SeaGreen.copy(alpha = 0.18f),
    onSurfaceVariant = SeaGreen,
)
