package com.gladomat.linklet.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Hand-written color math: WCAG contrast plus an OKLab mix. No dependency is added for this.
 *
 * Deliberately free of the Compose runtime and of Android APIs so themes and the contrast check run
 * on the plain-JVM test tier.
 */

private fun channelToLinear(c: Float): Double {
    val v = c.toDouble()
    return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
}

/** WCAG 2.1 relative luminance. */
fun Color.relativeLuminance(): Double =
    0.2126 * channelToLinear(red) + 0.7152 * channelToLinear(green) + 0.0722 * channelToLinear(blue)

/** WCAG 2.1 contrast ratio, always >= 1.0 and order-independent. */
fun contrastRatio(a: Color, b: Color): Double {
    val la = a.relativeLuminance()
    val lb = b.relativeLuminance()
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}

private data class Oklab(val l: Double, val a: Double, val b: Double)

private fun Color.toOklab(): Oklab {
    val r = channelToLinear(red)
    val g = channelToLinear(green)
    val bl = channelToLinear(blue)

    val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * bl)
    val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * bl)
    val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * bl)

    return Oklab(
        l = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
        a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
        b = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
    )
}

private fun linearToChannel(v: Double): Float {
    val c = if (v <= 0.0031308) v * 12.92 else 1.055 * v.pow(1.0 / 2.4) - 0.055
    return c.coerceIn(0.0, 1.0).toFloat()
}

private fun Oklab.toColor(): Color {
    val l_ = l + 0.3963377774 * a + 0.2158037573 * b
    val m_ = l - 0.1055613458 * a - 0.0638541728 * b
    val s_ = l - 0.0894841775 * a - 1.2914855480 * b

    val l3 = l_ * l_ * l_
    val m3 = m_ * m_ * m_
    val s3 = s_ * s_ * s_

    return Color(
        red = linearToChannel(4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3),
        green = linearToChannel(-1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3),
        blue = linearToChannel(-0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3),
    )
}

/** Mixes [amount] of [color] into [into], interpolating in OKLab. */
fun mixOklab(color: Color, into: Color, amount: Double): Color {
    val a = color.toOklab()
    val b = into.toOklab()
    return Oklab(
        l = b.l + (a.l - b.l) * amount,
        a = b.a + (a.a - b.a) * amount,
        b = b.b + (a.b - b.b) * amount,
    ).toColor()
}

/** A chip background that had to fall back because no mix reached AA. */
data class ChipTintFallback(val theme: String, val label: String, val ratioAtZeroMix: Double)

/**
 * Records chips that could not reach AA at any mix and fell back to `bg-surface`. Read by the
 * contrast check so a silent fallback still gets reported.
 */
object ChipTintLog {
    private val entries = mutableListOf<ChipTintFallback>()

    fun record(fallback: ChipTintFallback) {
        if (entries.none { it.theme == fallback.theme && it.label == fallback.label }) {
            entries += fallback
        }
    }

    fun all(): List<ChipTintFallback> = entries.toList()
}

const val AA_CONTRAST = 4.5

/**
 * The keyword-chip ground for palettes that ship no tinted backgrounds of their own.
 *
 * Mixes [accent] into [bgPrimary] at 15% in OKLab, then steps the mix down in 2% increments until
 * `accent` reaches 4.5:1 against the result. If no mix passes, falls back to [bgSurface] and logs it.
 */
fun chipBackground(
    accent: Color,
    bgPrimary: Color,
    bgSurface: Color,
    theme: String,
    label: String,
): Color {
    var percent = 15
    while (percent > 0) {
        val candidate = mixOklab(accent, bgPrimary, percent / 100.0)
        if (contrastRatio(accent, candidate) >= AA_CONTRAST) return candidate
        percent -= 2
    }
    // The last step is the untinted ground itself: a 0% mix is bg-primary.
    if (contrastRatio(accent, bgPrimary) >= AA_CONTRAST) return bgPrimary
    ChipTintLog.record(
        ChipTintFallback(theme, label, contrastRatio(accent, bgPrimary)),
    )
    return bgSurface
}
