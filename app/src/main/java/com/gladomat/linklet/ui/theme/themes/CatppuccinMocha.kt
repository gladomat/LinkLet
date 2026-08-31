package com.gladomat.linklet.ui.theme.themes

import androidx.compose.ui.graphics.Color
import com.gladomat.linklet.ui.theme.ThemeTokens
import com.gladomat.linklet.ui.theme.chipBackground

/**
 * Catppuccin Mocha.
 *
 * Source: https://github.com/catppuccin/palette/blob/main/palette.json (flavor `mocha`;
 *   `latte` is the light counterpart)
 * SHA: 07d02aa110ef9eb7e7427afca5c73ba9cf7f8ebd
 *
 * Catppuccin ships no tinted keyword backgrounds, so every chip ground comes from the shared
 * [chipBackground] mix (15% accent into bg-primary in OKLab, stepped down until the chip's own
 * foreground clears 4.5:1).
 *
 * `--text-muted` is subtext0, not overlay0: overlay0 is the comment grey and fails 4.5:1 on base.
 */
private const val NAME = "Catppuccin Mocha"

private val Base = Color(0xFF1E1E2E)
private val Surface0 = Color(0xFF313244)

private val Red = Color(0xFFF38BA8)
private val Blue = Color(0xFF89B4FA)
private val Yellow = Color(0xFFF9E2AF)
private val Green = Color(0xFFA6E3A1)
private val Overlay2 = Color(0xFF9399B2)
private val Pink = Color(0xFFF5C2E7)
private val Peach = Color(0xFFFAB387)
private val Teal = Color(0xFF94E2D5)
private val Mauve = Color(0xFFCBA6F7)

val CatppuccinMocha: ThemeTokens = ThemeTokens(
    bgPrimary = Base, // base
    bgSecondary = Color(0xFF181825), // mantle
    bgSurface = Surface0, // surface0
    bgModifierHover = Color(0xFF45475A), // surface1
    border = Color(0xFF585B70), // surface2

    textNormal = Color(0xFFCDD6F4), // text
    textMuted = Color(0xFFA6ADC8), // subtext0
    textFaint = Color(0xFF6C7086), // overlay0

    accent = Mauve, // mauve
    linkInternal = Blue, // blue
    linkExternal = Color(0xFF74C7EC), // sapphire
    linkUnresolved = Red, // red

    todoFg = Red,
    todoBg = chipBackground(Red, Base, Surface0, NAME, "todo"),
    nextFg = Blue,
    nextBg = chipBackground(Blue, Base, Surface0, NAME, "next"),
    waitFg = Yellow,
    waitBg = chipBackground(Yellow, Base, Surface0, NAME, "wait"),
    doneFg = Green,
    doneBg = chipBackground(Green, Base, Surface0, NAME, "done"),
    cancelFg = Overlay2,
    cancelBg = chipBackground(Overlay2, Base, Surface0, NAME, "cancel"),

    priorityA = Red, // red
    priorityB = Peach, // peach
    priorityC = Teal, // teal
    tagFg = Pink, // pink
    tagBg = chipBackground(Pink, Base, Surface0, NAME, "tag"),

    graphNode = Mauve, // mauve
    graphNodeUnresolved = Red, // red
    graphEdge = Color(0xFF585B70), // surface2
    graphNodeHover = Color(0xFFB4BEFE), // lavender

    graphCluster1 = Mauve, // mauve
    graphCluster2 = Blue, // blue
    graphCluster3 = Green, // green
    graphCluster4 = Peach, // peach
    graphCluster5 = Teal, // teal

    heading1 = Mauve, // mauve
    heading2 = Blue, // blue
    heading3 = Peach, // peach
    codeFg = Green, // green
    codeBg = Surface0, // surface0
    verbatimFg = Pink, // pink
    verbatimBg = Surface0, // surface0
    searchHighlight = chipBackground(Yellow, Base, Surface0, NAME, "search"),
    searchHighlightActive = chipBackground(Peach, Base, Surface0, NAME, "search-active"),
)
