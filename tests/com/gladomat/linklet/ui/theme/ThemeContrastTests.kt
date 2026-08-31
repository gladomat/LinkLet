package com.gladomat.linklet.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.fail
import org.junit.Test

/**
 * The contrast check. Plain JUnit — the token contract, the themes and the color math are all free
 * of the Compose runtime, so this runs on every machine including arm64.
 *
 * Run it with:
 *   ./gradlew :app:testDebugUnitTest --tests '*ThemeContrastTests'
 *
 * It fails (non-zero exit) on any content foreground/background pair below WCAG AA, printing the
 * theme, the token pair and the measured ratio. `--text-faint` is exempt by contract: it is for
 * separators, disabled states and inactive icons, never for text.
 */
class ThemeContrastTests {

    private data class Pair(val fg: String, val bg: String, val fgColor: Color, val bgColor: Color)

    private fun pairsFor(t: ThemeTokens): List<Pair> = buildList {
        // Body and secondary text on every ground they can be drawn on.
        listOf(
            "--bg-primary" to t.bgPrimary,
            "--bg-secondary" to t.bgSecondary,
            "--bg-surface" to t.bgSurface,
        ).forEach { (bgName, bg) ->
            add(Pair("--text-normal", bgName, t.textNormal, bg))
            add(Pair("--text-muted", bgName, t.textMuted, bg))
        }

        // Links and other content text on the page ground.
        listOf(
            "--link-internal" to t.linkInternal,
            "--link-external" to t.linkExternal,
            "--link-unresolved" to t.linkUnresolved,
            "--heading-1" to t.heading1,
            "--heading-2" to t.heading2,
            "--heading-3" to t.heading3,
            "--priority-a" to t.priorityA,
            "--priority-b" to t.priorityB,
            "--priority-c" to t.priorityC,
        ).forEach { (fgName, fg) -> add(Pair(fgName, "--bg-primary", fg, t.bgPrimary)) }

        // Keyword chips, tag pills and prose spans against their own ground.
        add(Pair("--todo-fg", "--todo-bg", t.todoFg, t.todoBg))
        add(Pair("--next-fg", "--next-bg", t.nextFg, t.nextBg))
        add(Pair("--wait-fg", "--wait-bg", t.waitFg, t.waitBg))
        add(Pair("--done-fg", "--done-bg", t.doneFg, t.doneBg))
        add(Pair("--cancel-fg", "--cancel-bg", t.cancelFg, t.cancelBg))
        add(Pair("--tag-fg", "--tag-bg", t.tagFg, t.tagBg))
        add(Pair("--code-fg", "--code-bg", t.codeFg, t.codeBg))
        add(Pair("--verbatim-fg", "--verbatim-bg", t.verbatimFg, t.verbatimBg))

        // Body text is drawn on top of search highlights.
        add(Pair("--text-normal", "--search-highlight", t.textNormal, t.searchHighlight))
        add(Pair("--text-normal", "--search-highlight-active", t.textNormal, t.searchHighlightActive))
    }

    @Test
    fun `every content pair reaches WCAG AA in every theme`() {
        val failures = mutableListOf<String>()

        ThemeRegistry.themes.forEach { theme ->
            pairsFor(theme.tokens).forEach { pair ->
                val ratio = contrastRatio(pair.fgColor, pair.bgColor)
                if (ratio < AA_CONTRAST) {
                    failures += "%-18s %-22s on %-24s %.2f:1 (needs %.1f:1)"
                        .format(theme.label, pair.fg, pair.bg, ratio, AA_CONTRAST)
                }
            }
        }

        ChipTintLog.all().forEach { fallback ->
            println(
                "chip tint fell back to --bg-surface: ${fallback.theme} ${fallback.label} " +
                    "(accent on --bg-primary is only %.2f:1)".format(fallback.ratioAtZeroMix),
            )
        }

        if (failures.isNotEmpty()) {
            fail("WCAG AA violations:\n" + failures.joinToString("\n"))
        }
    }

    @Test
    fun `no theme leaves a token fully transparent`() {
        ThemeRegistry.themes.forEach { theme ->
            val t = theme.tokens
            val tokens = listOf(
                "--bg-primary" to t.bgPrimary, "--bg-secondary" to t.bgSecondary,
                "--bg-surface" to t.bgSurface, "--bg-modifier-hover" to t.bgModifierHover,
                "--border" to t.border, "--text-normal" to t.textNormal,
                "--text-muted" to t.textMuted, "--text-faint" to t.textFaint,
                "--accent" to t.accent, "--link-internal" to t.linkInternal,
                "--link-external" to t.linkExternal, "--link-unresolved" to t.linkUnresolved,
                "--todo-fg" to t.todoFg, "--todo-bg" to t.todoBg,
                "--next-fg" to t.nextFg, "--next-bg" to t.nextBg,
                "--wait-fg" to t.waitFg, "--wait-bg" to t.waitBg,
                "--done-fg" to t.doneFg, "--done-bg" to t.doneBg,
                "--cancel-fg" to t.cancelFg, "--cancel-bg" to t.cancelBg,
                "--priority-a" to t.priorityA, "--priority-b" to t.priorityB,
                "--priority-c" to t.priorityC, "--tag-fg" to t.tagFg, "--tag-bg" to t.tagBg,
                "--graph-node" to t.graphNode, "--graph-node-unresolved" to t.graphNodeUnresolved,
                "--graph-edge" to t.graphEdge, "--graph-node-hover" to t.graphNodeHover,
                "--graph-cluster-1" to t.graphCluster1, "--graph-cluster-2" to t.graphCluster2,
                "--graph-cluster-3" to t.graphCluster3, "--graph-cluster-4" to t.graphCluster4,
                "--graph-cluster-5" to t.graphCluster5,
                "--heading-1" to t.heading1, "--heading-2" to t.heading2, "--heading-3" to t.heading3,
                "--code-fg" to t.codeFg, "--code-bg" to t.codeBg,
                "--verbatim-fg" to t.verbatimFg, "--verbatim-bg" to t.verbatimBg,
                "--search-highlight" to t.searchHighlight,
                "--search-highlight-active" to t.searchHighlightActive,
            )
            tokens.forEach { (name, color) ->
                if (color.alpha < 1f) {
                    fail("${theme.label} defines $name with alpha ${color.alpha}; tokens must be opaque")
                }
            }
        }
    }
}
