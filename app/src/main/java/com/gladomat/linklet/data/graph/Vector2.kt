package com.gladomat.linklet.data.graph

import kotlin.math.sqrt

/**
 * Plain 2D vector for the force-layout math. Deliberately not androidx.compose.ui.geometry.Offset -
 * this whole package stays pure Kotlin / pure-JVM testable (no Robolectric needed), matching this
 * app's pure-JVM unit test tier. Convert to Offset only at the Composable boundary.
 */
data class Vector2(val x: Float, val y: Float) {
    operator fun plus(other: Vector2) = Vector2(x + other.x, y + other.y)
    operator fun minus(other: Vector2) = Vector2(x - other.x, y - other.y)
    operator fun times(scalar: Float) = Vector2(x * scalar, y * scalar)
    fun length(): Float = sqrt(x * x + y * y)

    companion object {
        val ZERO = Vector2(0f, 0f)
    }
}
