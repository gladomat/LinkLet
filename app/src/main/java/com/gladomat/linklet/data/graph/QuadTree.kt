package com.gladomat.linklet.data.graph

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Barnes-Hut quadtree over node positions, used to approximate pairwise repulsion in
 * O(n log n) instead of O(n^2) - see Design decision 2, docs/plans/2026-07-06-note-graph-view.md
 * (this app's real vault size, 500-2000 notes, makes naive O(n^2) repulsion risk visible
 * stutter on the JVM, not "trivially fast").
 */
internal class QuadTree private constructor(
    private val minX: Float,
    private val minY: Float,
    private val maxX: Float,
    private val maxY: Float,
    private val depth: Int,
) {
    private var massCount = 0
    private var centerOfMassX = 0f
    private var centerOfMassY = 0f
    private var children: Array<QuadTree?>? = null

    // Barnes-Hut theta criterion needs this region's full extent, not just its x-extent - a
    // region padded/subdivided from a non-square bounding box (e.g. a tall, narrow cluster of
    // nodes) would otherwise under-approximate its vertical spread and skew the resulting force.
    private val size get() = max(maxX - minX, maxY - minY)

    fun insert(point: Vector2) {
        if (massCount == 0) {
            centerOfMassX = point.x
            centerOfMassY = point.y
            massCount = 1
            return
        }

        // Cap subdivision depth: exactly-coincident (or extremely close) points would otherwise
        // recurse into ever-smaller quadrants forever. Past MAX_DEPTH, merge them into one
        // aggregate leaf instead - a safe approximation for points this close together.
        if (children == null && massCount == 1 && depth < MAX_DEPTH) {
            subdivide()
            insertIntoChild(Vector2(centerOfMassX, centerOfMassY))
        }

        children?.let { insertIntoChild(point) }

        centerOfMassX = (centerOfMassX * massCount + point.x) / (massCount + 1)
        centerOfMassY = (centerOfMassY * massCount + point.y) / (massCount + 1)
        massCount += 1
    }

    private fun subdivide() {
        val midX = (minX + maxX) / 2f
        val midY = (minY + maxY) / 2f
        children = arrayOf(
            QuadTree(minX, minY, midX, midY, depth + 1),
            QuadTree(midX, minY, maxX, midY, depth + 1),
            QuadTree(minX, midY, midX, maxY, depth + 1),
            QuadTree(midX, midY, maxX, maxY, depth + 1),
        )
    }

    private fun insertIntoChild(point: Vector2) {
        val midX = (minX + maxX) / 2f
        val midY = (minY + maxY) / 2f
        val index = (if (point.x >= midX) 1 else 0) + (if (point.y >= midY) 2 else 0)
        children!![index]!!.insert(point)
    }

    /** Approximate net repulsive force on [from] due to every point held in this (sub)tree. */
    fun repulsionForce(from: Vector2, theta: Float, strength: Float): Vector2 {
        if (massCount == 0) return Vector2.ZERO

        val dx = from.x - centerOfMassX
        val dy = from.y - centerOfMassY
        val distance = sqrt(dx * dx + dy * dy)

        val kids = children
        if (kids == null || (size / distance.coerceAtLeast(MIN_DISTANCE)) < theta) {
            // Treat this (sub)tree's mass as one aggregate body (the Barnes-Hut approximation),
            // either because it's a genuine leaf or because it's far enough away that the
            // internal spread of its points doesn't matter at this distance.
            if (distance < MIN_DISTANCE) return Vector2.ZERO // `from`'s own point, or a coincident one
            val magnitude = strength * massCount / distance
            return Vector2(dx / distance, dy / distance) * magnitude
        }

        var force = Vector2.ZERO
        kids.forEach { child -> force += child?.repulsionForce(from, theta, strength) ?: Vector2.ZERO }
        return force
    }

    companion object {
        private const val MIN_DISTANCE = 0.01f
        private const val MAX_DEPTH = 24

        fun build(points: List<Vector2>): QuadTree {
            if (points.isEmpty()) return QuadTree(0f, 0f, 1f, 1f, depth = 0)
            var minX = points[0].x
            var minY = points[0].y
            var maxX = points[0].x
            var maxY = points[0].y
            points.forEach { p ->
                minX = min(minX, p.x)
                minY = min(minY, p.y)
                maxX = max(maxX, p.x)
                maxY = max(maxY, p.y)
            }
            // Pad so the root always has a non-zero extent, even when every point starts at the
            // same spot (e.g. a brand-new graph before its first tick spreads nodes out).
            val padding = max(1f, max(maxX - minX, maxY - minY) * 0.1f)
            val tree = QuadTree(minX - padding, minY - padding, maxX + padding, maxY + padding, depth = 0)
            points.forEach(tree::insert)
            return tree
        }
    }
}
