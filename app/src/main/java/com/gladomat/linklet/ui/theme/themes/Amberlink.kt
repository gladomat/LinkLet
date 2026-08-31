package com.gladomat.linklet.ui.theme.themes

import androidx.compose.ui.graphics.Color
import com.gladomat.linklet.ui.theme.ThemeTokens
import com.gladomat.linklet.ui.theme.chipBackground
import com.gladomat.linklet.ui.theme.mixOklab

/**
 * Amberlink — LinkLet's own palette, the four hues specified for this app before the theme registry
 * existed.
 *
 * Source: this repository, `ui/theme/Color.kt` as introduced in commit
 * 5147dfd ("feat(editor): add toolbar above keyboard with buttons"), from the palette given in the
 * original request: FFBF00 Amber Gold, E83F6F Magenta Bloom, 2274A5 Rich Cerulean, 32936F Sea Green,
 * on Charcoal 111111 / Midnight 1C1C1C with Paper F7F4E9 text.
 *
 * Dark variant: Amber Gold is the identity color and it only carries on a dark ground (1.60:1 on the
 * original SnowDrift, 11.42:1 on Charcoal).
 *
 * Two derivations, both from the palette's own colors rather than new hues:
 *  - Rich Cerulean is 3.70:1 on Charcoal, below AA for text. A lighter step is mixed from it and the
 *    palette's paper tone, keeping the hue for links, NEXT and heading 3.
 *  - the palette ships no greys, so the muted/faint/surface steps are paper mixed into Charcoal.
 *
 * Midnight Surface is left to the app bars: Sea Green and Magenta Bloom sit at 4.49:1 and 4.36:1 on
 * it, so the code and verbatim grounds go through [chipBackground] instead.
 */
private const val NAME = "Amberlink"

private val AmberGold = Color(0xFFFFBF00)
private val MagentaBloom = Color(0xFFE83F6F)
private val RichCerulean = Color(0xFF2274A5)
private val SeaGreen = Color(0xFF32936F)
private val Charcoal = Color(0xFF111111)
private val MidnightSurface = Color(0xFF1C1C1C)
private val PaperSurface = Color(0xFFF7F4E9)

/** Rich Cerulean lifted towards the paper tone until it carries as text on Charcoal. */
private val CeruleanLight = mixOklab(PaperSurface, RichCerulean, 0.45)

private val Surface = mixOklab(PaperSurface, Charcoal, 0.08)
private val Hover = mixOklab(PaperSurface, Charcoal, 0.16)
private val Muted = mixOklab(PaperSurface, Charcoal, 0.72)
private val Faint = mixOklab(PaperSurface, Charcoal, 0.42)
private val Border = mixOklab(RichCerulean, Charcoal, 0.45)

val Amberlink: ThemeTokens = ThemeTokens(
    bgPrimary = Charcoal, // Charcoal
    bgSecondary = MidnightSurface, // Midnight Surface
    bgSurface = Surface, // paper mixed 8% into Charcoal
    bgModifierHover = Hover, // paper mixed 16% into Charcoal
    border = Border, // cerulean mixed 45% into Charcoal

    textNormal = PaperSurface, // Paper Surface
    textMuted = Muted,
    textFaint = Faint,

    accent = AmberGold, // Amber Gold
    linkInternal = CeruleanLight,
    linkExternal = SeaGreen, // Sea Green
    linkUnresolved = MagentaBloom, // Magenta Bloom

    todoFg = MagentaBloom,
    todoBg = chipBackground(MagentaBloom, Charcoal, Surface, NAME, "todo"),
    nextFg = CeruleanLight,
    nextBg = chipBackground(CeruleanLight, Charcoal, Surface, NAME, "next"),
    waitFg = AmberGold,
    waitBg = chipBackground(AmberGold, Charcoal, Surface, NAME, "wait"),
    doneFg = SeaGreen,
    doneBg = chipBackground(SeaGreen, Charcoal, Surface, NAME, "done"),
    cancelFg = Muted,
    cancelBg = chipBackground(Muted, Charcoal, Surface, NAME, "cancel"),

    priorityA = MagentaBloom, // Magenta Bloom
    priorityB = AmberGold, // Amber Gold
    priorityC = SeaGreen, // Sea Green
    tagFg = MagentaBloom, // Magenta Bloom
    tagBg = chipBackground(MagentaBloom, Charcoal, Surface, NAME, "tag"),

    graphNode = AmberGold, // Amber Gold
    graphNodeUnresolved = MagentaBloom, // Magenta Bloom
    graphEdge = Border,
    graphNodeHover = CeruleanLight,

    graphCluster1 = AmberGold, // Amber Gold
    graphCluster2 = RichCerulean, // Rich Cerulean — ring stroke, not text
    graphCluster3 = SeaGreen, // Sea Green
    graphCluster4 = MagentaBloom, // Magenta Bloom
    graphCluster5 = CeruleanLight,

    // The original scheme's heading order: primary, secondary, tertiary.
    heading1 = AmberGold, // Amber Gold
    heading2 = MagentaBloom, // Magenta Bloom
    heading3 = CeruleanLight,
    codeFg = SeaGreen, // Sea Green
    // Midnight Surface would leave these at 4.49:1 and 4.36:1, so the prose grounds are derived the
    // same way the chips are.
    codeBg = chipBackground(SeaGreen, Charcoal, Surface, NAME, "code"),
    verbatimFg = MagentaBloom, // Magenta Bloom
    verbatimBg = chipBackground(MagentaBloom, Charcoal, Surface, NAME, "verbatim"),
    searchHighlight = chipBackground(AmberGold, Charcoal, Surface, NAME, "search"),
    searchHighlightActive = chipBackground(MagentaBloom, Charcoal, Surface, NAME, "search-active"),
)
