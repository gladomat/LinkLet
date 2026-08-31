package com.gladomat.linklet.ui.theme.themes

import androidx.compose.ui.graphics.Color
import com.gladomat.linklet.ui.theme.ThemeTokens

/**
 * Modus Operandi (light).
 *
 * Source: https://github.com/protesilaos/modus-themes/blob/main/modus-themes.el
 *   `modus-themes-operandi-palette`, modus-themes.el:713
 * SHA: b86cace43523b5809ac5d62ca5ab01e9eb2727bc
 *
 * Note: the palette is no longer inside `modus-operandi-theme.el` (now a 92-line loader,
 * SHA f71db69bad2d4bac680960273b663c73c305a413); it lives in `modus-themes.el`.
 *
 * Modus ships explicit `bg-*-nuanced` tints and named `fg-heading-*` / `fg-prose-*` faces, so the
 * chips, headings and prose colors are taken directly rather than derived. Modus targets WCAG AAA
 * upstream, so every pair clears AA with room to spare.
 */
val ModusOperandi: ThemeTokens = ThemeTokens(
    bgPrimary = Color(0xFFFFFFFF), // bg-main
    bgSecondary = Color(0xFFF2F2F2), // bg-dim
    bgSurface = Color(0xFFE0E0E0), // bg-inactive
    bgModifierHover = Color(0xFFC4C4C4), // bg-active
    border = Color(0xFF9F9F9F), // border

    textNormal = Color(0xFF000000), // fg-main
    textMuted = Color(0xFF595959), // fg-dim
    textFaint = Color(0xFF9F9F9F), // border — non-text UI only

    accent = Color(0xFF0031A9), // blue
    linkInternal = Color(0xFF3548CF), // blue-warmer
    linkExternal = Color(0xFF005F5F), // cyan-cooler
    linkUnresolved = Color(0xFFA60000), // red

    todoFg = Color(0xFFA60000), // red (prose-todo)
    todoBg = Color(0xFFFFE8E8), // bg-red-nuanced
    nextFg = Color(0xFF0031A9), // blue
    nextBg = Color(0xFFECEDFF), // bg-blue-nuanced
    waitFg = Color(0xFF6F5500), // yellow
    waitBg = Color(0xFFF8F0D0), // bg-yellow-nuanced
    doneFg = Color(0xFF006800), // green (prose-done)
    doneBg = Color(0xFFE0F6E0), // bg-green-nuanced
    cancelFg = Color(0xFF595959), // fg-dim
    cancelBg = Color(0xFFF2F2F2), // bg-dim

    priorityA = Color(0xFFA60000), // red
    priorityB = Color(0xFF6F5500), // yellow
    priorityC = Color(0xFF005E8B), // cyan
    tagFg = Color(0xFF7C318F), // magenta-faint (prose-tag)
    tagBg = Color(0xFFF8E6F5), // bg-magenta-nuanced

    graphNode = Color(0xFF0031A9), // blue
    graphNodeUnresolved = Color(0xFFA60000), // red
    graphEdge = Color(0xFF9F9F9F), // border
    graphNodeHover = Color(0xFF531AB6), // magenta-cooler

    graphCluster1 = Color(0xFF0031A9), // blue
    graphCluster2 = Color(0xFF006800), // green
    graphCluster3 = Color(0xFF721045), // magenta
    graphCluster4 = Color(0xFF005E8B), // cyan
    graphCluster5 = Color(0xFF6F5500), // yellow

    heading1 = Color(0xFF000000), // fg-heading-1 (fg-main)
    heading2 = Color(0xFF624416), // fg-heading-2 (yellow-faint)
    heading3 = Color(0xFF193668), // fg-heading-3 (fg-alt)
    codeFg = Color(0xFF005F5F), // fg-prose-code (cyan-cooler)
    codeBg = Color(0xFFF2F2F2), // bg-dim
    verbatimFg = Color(0xFF8F0075), // fg-prose-verbatim (magenta-warmer)
    verbatimBg = Color(0xFFF2F2F2), // bg-dim
    searchHighlight = Color(0xFFA4D5F9), // bg-search-lazy (bg-cyan-intense)
    searchHighlightActive = Color(0xFFF3D000), // bg-search-current (bg-yellow-intense)
)
