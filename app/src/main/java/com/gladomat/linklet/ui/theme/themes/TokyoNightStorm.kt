package com.gladomat.linklet.ui.theme.themes

import androidx.compose.ui.graphics.Color
import com.gladomat.linklet.ui.theme.ThemeTokens
import com.gladomat.linklet.ui.theme.chipBackground

/**
 * Tokyo Night, "storm" variant (chosen over "night" and "moon" — storm's lifted background reads
 * better next to a light theme in the same picker).
 *
 * Source: https://github.com/folke/tokyonight.nvim/blob/main/lua/tokyonight/colors/storm.lua
 * SHA: 9578c6fe68af1db513914a2a01bea5585f3027db
 *
 * Tokyo Night ships no tinted keyword backgrounds, so every chip ground comes from the shared
 * [chipBackground] mix.
 *
 * `--text-muted` is fg_dark, not `comment`: the comment grey fails 4.5:1 on the storm background.
 */
private const val NAME = "Tokyo Night"

private val Bg = Color(0xFF24283B)
private val BgHighlight = Color(0xFF292E42)

private val Red = Color(0xFFF7768E)
private val Blue = Color(0xFF7AA2F7)
private val Yellow = Color(0xFFE0AF68)
private val Green = Color(0xFF9ECE6A)
private val Orange = Color(0xFFFF9E64)
private val Magenta = Color(0xFFBB9AF7)
private val Cyan = Color(0xFF7DCFFF)
private val FgDark = Color(0xFFA9B1D6)

val TokyoNightStorm: ThemeTokens = ThemeTokens(
    bgPrimary = Bg, // bg
    bgSecondary = Color(0xFF1F2335), // bg_dark
    bgSurface = BgHighlight, // bg_highlight
    bgModifierHover = Color(0xFF3B4261), // fg_gutter
    border = Color(0xFF414868), // terminal_black

    textNormal = Color(0xFFC0CAF5), // fg
    textMuted = FgDark, // fg_dark
    textFaint = Color(0xFF565F89), // comment

    accent = Blue, // blue
    linkInternal = Cyan, // cyan
    linkExternal = Color(0xFF2AC3DE), // blue1
    linkUnresolved = Red, // red

    todoFg = Red,
    todoBg = chipBackground(Red, Bg, BgHighlight, NAME, "todo"),
    nextFg = Blue,
    nextBg = chipBackground(Blue, Bg, BgHighlight, NAME, "next"),
    waitFg = Yellow,
    waitBg = chipBackground(Yellow, Bg, BgHighlight, NAME, "wait"),
    doneFg = Green,
    doneBg = chipBackground(Green, Bg, BgHighlight, NAME, "done"),
    cancelFg = FgDark,
    cancelBg = chipBackground(FgDark, Bg, BgHighlight, NAME, "cancel"),

    priorityA = Red, // red
    priorityB = Orange, // orange
    priorityC = Color(0xFF73DACA), // green1
    tagFg = Magenta, // magenta
    tagBg = chipBackground(Magenta, Bg, BgHighlight, NAME, "tag"),

    graphNode = Blue, // blue
    graphNodeUnresolved = Red, // red
    graphEdge = Color(0xFF414868), // terminal_black
    graphNodeHover = Cyan, // cyan

    graphCluster1 = Blue, // blue
    graphCluster2 = Magenta, // magenta
    graphCluster3 = Green, // green
    graphCluster4 = Orange, // orange
    graphCluster5 = Cyan, // cyan

    heading1 = Blue, // blue
    heading2 = Magenta, // magenta
    heading3 = Orange, // orange
    codeFg = Green, // green
    codeBg = BgHighlight, // bg_highlight
    verbatimFg = Magenta, // magenta
    verbatimBg = BgHighlight, // bg_highlight
    searchHighlight = chipBackground(Yellow, Bg, BgHighlight, NAME, "search"),
    searchHighlightActive = chipBackground(Orange, Bg, BgHighlight, NAME, "search-active"),
)
