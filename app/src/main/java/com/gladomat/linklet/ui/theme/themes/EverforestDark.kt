package com.gladomat.linklet.ui.theme.themes

import androidx.compose.ui.graphics.Color
import com.gladomat.linklet.ui.theme.ThemeTokens
import com.gladomat.linklet.ui.theme.chipBackground

/**
 * Everforest, Dark / Medium.
 *
 * Source: https://github.com/sainnhe/everforest/blob/master/palette.md
 * SHA: d84d9ec896564730855c3f6705dc9a734c74a344
 *
 * Everforest ships tinted backgrounds (bg_red/bg_yellow/bg_green/bg_blue/bg_purple), but its accent
 * foregrounds do not reach 4.5:1 on them — todo 3.53:1, next 3.84:1, cancel 4.15:1, tag 4.08:1, and
 * the hard-contrast tints top out at 4.45:1. The chips therefore go through the shared
 * [chipBackground] mix, like Catppuccin and Tokyo Night. Every accent keeps its upstream hex; only
 * the chip grounds are derived. The search highlights keep their upstream tints, which do pass.
 *
 * `--text-muted` is grey2, not grey0 ("Foreground UI Elements") or grey1 (the comment grey): both
 * fail 4.5:1 on bg0. grey2 in turn fails on bg2 (3.86:1) and bg1 (4.44:1), so the grounds are the
 * darker steps: bg_dim for surfaces and the dark-hard bg0 for the recessed ground.
 */
private const val NAME = "Everforest"

private val Bg0 = Color(0xFF2D353B)
private val BgDim = Color(0xFF232A2E)

private val Red = Color(0xFFE67E80)
private val Blue = Color(0xFF7FBBB3)
private val Yellow = Color(0xFFDBBC7F)
private val Green = Color(0xFFA7C080)
private val Grey2 = Color(0xFF9DA9A0)
private val Purple = Color(0xFFD699B6)
val EverforestDark: ThemeTokens = ThemeTokens(
    bgPrimary = Bg0, // bg0
    bgSecondary = Color(0xFF272E33), // dark-hard bg0
    bgSurface = BgDim, // bg_dim
    bgModifierHover = Color(0xFF343F44), // bg1
    border = Color(0xFF4F585E), // bg4

    textNormal = Color(0xFFD3C6AA), // fg
    textMuted = Grey2, // grey2
    textFaint = Color(0xFF7A8478), // grey0

    accent = Color(0xFFA7C080), // green
    linkInternal = Color(0xFF7FBBB3), // blue
    linkExternal = Color(0xFF83C092), // aqua
    linkUnresolved = Color(0xFFE67E80), // red

    todoFg = Red,
    todoBg = chipBackground(Red, Bg0, BgDim, NAME, "todo"),
    nextFg = Blue,
    nextBg = chipBackground(Blue, Bg0, BgDim, NAME, "next"),
    waitFg = Yellow,
    waitBg = chipBackground(Yellow, Bg0, BgDim, NAME, "wait"),
    doneFg = Green,
    doneBg = chipBackground(Green, Bg0, BgDim, NAME, "done"),
    cancelFg = Grey2,
    cancelBg = chipBackground(Grey2, Bg0, BgDim, NAME, "cancel"),

    priorityA = Color(0xFFE67E80), // red
    priorityB = Color(0xFFDBBC7F), // yellow
    priorityC = Color(0xFF83C092), // aqua
    tagFg = Purple, // purple
    tagBg = chipBackground(Purple, Bg0, BgDim, NAME, "tag"),

    graphNode = Color(0xFFA7C080), // green
    graphNodeUnresolved = Color(0xFFE67E80), // red
    graphEdge = Color(0xFF4F585E), // bg4
    graphNodeHover = Color(0xFF83C092), // aqua

    graphCluster1 = Color(0xFFA7C080), // green
    graphCluster2 = Color(0xFF7FBBB3), // blue
    graphCluster3 = Color(0xFFDBBC7F), // yellow
    graphCluster4 = Color(0xFFD699B6), // purple
    graphCluster5 = Color(0xFFE69875), // orange

    heading1 = Color(0xFFA7C080), // green
    heading2 = Color(0xFF7FBBB3), // blue
    heading3 = Color(0xFFDBBC7F), // yellow
    codeFg = Color(0xFF83C092), // aqua — "Constants, Macros"
    codeBg = Color(0xFF343F44), // bg1
    verbatimFg = Color(0xFFD699B6), // purple
    verbatimBg = Color(0xFF343F44), // bg1
    searchHighlight = Color(0xFF3A515D), // bg_blue
    searchHighlightActive = Color(0xFF4D4C43), // bg_yellow
)
