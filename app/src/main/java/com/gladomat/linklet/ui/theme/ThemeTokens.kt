package com.gladomat.linklet.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The token contract. Every theme module must supply every token — the constructor has no default
 * values, so a theme that omits one fails the build.
 *
 * Components consume these through [LocalThemeTokens]; they must not reference palette constants or
 * color literals directly.
 */
data class ThemeTokens(
    /** Page ground. The background a note is read on. */
    val bgPrimary: Color,
    /** Recessed ground: app bars, drawers, anything sitting behind [bgPrimary]. */
    val bgSecondary: Color,
    /** Raised ground: cards, sheets, dialogs, code blocks. */
    val bgSurface: Color,
    /** Pressed/hovered row or list item. */
    val bgModifierHover: Color,
    /** Hairlines, dividers, outlines. */
    val border: Color,

    /** Body text. Must reach 4.5:1 on [bgPrimary]. */
    val textNormal: Color,
    /** Secondary text: timestamps, paths, captions. Must reach 4.5:1 on [bgPrimary]. */
    val textMuted: Color,
    /** Separators, disabled states, inactive icons. NEVER text a user reads — exempt from 4.5:1. */
    val textFaint: Color,

    /** Primary accent: active controls, selection, the theme's identity color. */
    val accent: Color,
    /** A link to another note in the vault. */
    val linkInternal: Color,
    /** A link leaving the vault (http, mailto, file). */
    val linkExternal: Color,
    /** A link whose target does not exist yet. */
    val linkUnresolved: Color,

    /** TODO keyword text. */
    val todoFg: Color,
    /** TODO keyword chip ground. */
    val todoBg: Color,
    /** NEXT keyword text. */
    val nextFg: Color,
    /** NEXT keyword chip ground. */
    val nextBg: Color,
    /** WAITING keyword text. */
    val waitFg: Color,
    /** WAITING keyword chip ground. */
    val waitBg: Color,
    /** DONE keyword text. */
    val doneFg: Color,
    /** DONE keyword chip ground. */
    val doneBg: Color,
    /** CANCELLED keyword text. */
    val cancelFg: Color,
    /** CANCELLED keyword chip ground. */
    val cancelBg: Color,

    /** Priority [#A]. */
    val priorityA: Color,
    /** Priority [#B]. */
    val priorityB: Color,
    /** Priority [#C]. */
    val priorityC: Color,
    /** Tag pill text. */
    val tagFg: Color,
    /** Tag pill ground. */
    val tagBg: Color,

    /** Graph node fill, default state. */
    val graphNode: Color,
    /** Graph node fill for a link target that has no note yet. */
    val graphNodeUnresolved: Color,
    /** Graph edge stroke. */
    val graphEdge: Color,
    /** Graph node fill under hover/selection. */
    val graphNodeHover: Color,

    /** Cluster identity ring 1. Drawn as a 2px stroke — never a fill. */
    val graphCluster1: Color,
    /** Cluster identity ring 2. Drawn as a 2px stroke — never a fill. */
    val graphCluster2: Color,
    /** Cluster identity ring 3. Drawn as a 2px stroke — never a fill. */
    val graphCluster3: Color,
    /** Cluster identity ring 4. Drawn as a 2px stroke — never a fill. */
    val graphCluster4: Color,
    /** Cluster identity ring 5. Drawn as a 2px stroke — never a fill. */
    val graphCluster5: Color,

    /** Org heading level 1. */
    val heading1: Color,
    /** Org heading level 2. */
    val heading2: Color,
    /** Org heading level 3 and deeper. */
    val heading3: Color,
    /** Inline code (`~code~`) text. */
    val codeFg: Color,
    /** Inline code ground. */
    val codeBg: Color,
    /** Verbatim (`=verbatim=`) text. */
    val verbatimFg: Color,
    /** Verbatim ground. */
    val verbatimBg: Color,
    /** In-note search match ground. Body text is drawn on it. */
    val searchHighlight: Color,
    /** The currently focused search match ground. Body text is drawn on it. */
    val searchHighlightActive: Color,
)

val LocalThemeTokens = staticCompositionLocalOf<ThemeTokens> {
    error("No ThemeTokens provided — wrap the content in LinkLetAppTheme")
}
