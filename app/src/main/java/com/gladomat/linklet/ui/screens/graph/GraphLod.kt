package com.gladomat.linklet.ui.screens.graph

import kotlin.math.floor
import kotlin.math.pow

/**
 * Level-of-detail (LOD) rules for the graph canvas.
 *
 * Zoomed all the way out you see the *whole* graph - every node, as a fine dot, with no labels
 * and faint edges, so the overall shape reads at a glance. Zooming in doesn't add nodes, it adds
 * detail: dots grow into targets and titles appear, densest-first, as there is room for them.
 *
 * Nodes are deliberately never culled by zoom. Hiding the low-degree periphery when zoomed out
 * removes exactly the parts that give the graph its silhouette, and the crowding it was meant to
 * fix is really a spacing problem, owned by the layout engine (see data/graph/AGENTS.md), not
 * something to paper over by dropping nodes.
 *
 * All of it is pure math with no Android dependency so it can be unit-tested on the plain-JVM
 * tier - the `Canvas` block only consumes the results.
 */

/**
 * Per-node LOD metadata, precomputed once per node/edge set (not per frame).
 *
 * @param degree edge count, the importance metric that drives node radius.
 * @param rank 0-based importance rank (0 = highest degree). Orders the label pass so hubs win the
 *   collision grid over leaf notes, and decides which titles survive at partial zoom.
 */
data class NodeLod(
    val degree: Int,
    val rank: Int,
)

/**
 * Ranks [paths] by degree, most-connected first.
 *
 * Ties break on path so the ranking is deterministic - labels must not swap places between frames
 * just because the map iteration order changed.
 */
fun buildNodeLod(paths: Collection<String>, degreeByPath: Map<String, Int>): Map<String, NodeLod> {
    if (paths.isEmpty()) return emptyMap()
    val ordered = paths.sortedWith(compareByDescending<String> { degreeByPath[it] ?: 0 }.thenBy { it })
    return ordered.withIndex().associate { (index, path) ->
        path to NodeLod(degree = degreeByPath[path] ?: 0, rank = index)
    }
}

/**
 * Node radius in screen pixels. [baseRadiusPx] is the degree-derived size at scale 1; it then
 * grows with zoom under a damping [NODE_RADIUS_ZOOM_EXPONENT] so nodes shrink into fine dots when
 * zoomed out (the structural view) without disappearing, and grow into readable targets when
 * zoomed in without swallowing the screen.
 */
fun lodNodeRadiusPx(baseRadiusPx: Float, scale: Float, minRadiusPx: Float): Float =
    (baseRadiusPx * scale.pow(NODE_RADIUS_ZOOM_EXPONENT))
        .coerceIn(minRadiusPx, baseRadiusPx * MAX_NODE_RADIUS_MULTIPLIER)

/**
 * Edge opacity multiplier. Edges carry most of the visual noise, so they thin out towards
 * [EDGE_MIN_ALPHA_MULTIPLIER] as the view zooms out and the remaining hub-to-hub links become the
 * readable skeleton.
 */
fun lodEdgeAlphaMultiplier(scale: Float): Float {
    if (scale >= LOD_FULL_DETAIL_SCALE) return 1f
    val t = ((scale - LOD_MIN_SCALE) / (LOD_FULL_DETAIL_SCALE - LOD_MIN_SCALE))
        .coerceIn(0f, 1f)
    return EDGE_MIN_ALPHA_MULTIPLIER + (1f - EDGE_MIN_ALPHA_MULTIPLIER) * t
}

/** Axis-aligned bounds of a set of graph-space points, or null when there are none. */
data class GraphBounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
    val centerX get() = (minX + maxX) / 2f
    val centerY get() = (minY + maxY) / 2f
    val width get() = maxX - minX
    val height get() = maxY - minY
}

/**
 * The scale at which [bounds] exactly fills a [viewportWidth] x [viewportHeight] canvas, minus a
 * [padding] fraction of breathing room. Returns null for a degenerate viewport or an empty graph.
 *
 * This is what makes "zoomed all the way out" mean "the whole graph is on screen" for a vault of
 * any size: a fixed minimum zoom can't, because the layout's extent grows with the node count.
 */
fun fitScale(
    bounds: GraphBounds?,
    viewportWidth: Int,
    viewportHeight: Int,
    padding: Float = FIT_PADDING_FRACTION,
): Float? {
    if (bounds == null || viewportWidth <= 0 || viewportHeight <= 0) return null
    val usableWidth = viewportWidth * (1f - padding)
    val usableHeight = viewportHeight * (1f - padding)
    // A single node, or a perfectly collinear graph, has zero extent on one axis - fall back to
    // that axis being unconstrained rather than dividing by zero into an infinite scale.
    val scaleX = if (bounds.width > 0f) usableWidth / bounds.width else Float.MAX_VALUE
    val scaleY = if (bounds.height > 0f) usableHeight / bounds.height else Float.MAX_VALUE
    if (scaleX == Float.MAX_VALUE && scaleY == Float.MAX_VALUE) return null
    return minOf(scaleX, scaleY).coerceAtMost(MAX_FIT_SCALE)
}

/** Pan that puts [bounds]' centre at the middle of the viewport, at the given [scale]. */
fun fitPan(bounds: GraphBounds, viewportWidth: Int, viewportHeight: Int, scale: Float): Pair<Float, Float> =
    (viewportWidth / 2f - bounds.centerX * scale) to (viewportHeight / 2f - bounds.centerY * scale)

/** Screen-space rectangle a point must fall inside to be worth drawing, with slack for labels. */
fun isOnScreen(x: Float, y: Float, width: Int, height: Int, marginPx: Float): Boolean =
    x >= -marginPx && x <= width + marginPx && y >= -marginPx && y <= height + marginPx

/**
 * True when an edge is worth drawing: at least one of its endpoints is on screen.
 *
 * Deliberately *not* a segment-vs-viewport intersection test. An edge whose endpoints are both
 * off screen contributes only an anonymous line crossing the view - you can't see what it joins,
 * so it carries no information and, at a real vault's edge count, hundreds of them turn a
 * zoomed-in view into a mesh of diagonals over the notes you're actually trying to read.
 */
fun edgeIsWorthDrawing(
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
    width: Int,
    height: Int,
    marginPx: Float,
): Boolean =
    isOnScreen(startX, startY, width, height, marginPx) ||
        isOnScreen(endX, endY, width, height, marginPx)

/**
 * Occupancy grid that rejects overlapping labels.
 *
 * Labels are offered in importance order; the first one to claim a run of cells keeps them, and a
 * later label whose box overlaps any claimed cell is dropped entirely rather than drawn on top.
 * That is what separates a readable zoomed-in view from the pile-up you get when every node draws
 * its title unconditionally.
 */
class LabelGrid(private val cellWidthPx: Float, private val cellHeightPx: Float) {
    private val occupied = HashSet<Long>()

    /**
     * Attempts to reserve the box at ([left], [top]) of the given size. Returns true (and marks
     * the cells) if nothing was there, false if the label must be skipped.
     */
    fun tryClaim(left: Float, top: Float, widthPx: Float, heightPx: Float): Boolean {
        val col0 = floor(left / cellWidthPx).toInt()
        val col1 = floor((left + widthPx) / cellWidthPx).toInt()
        val row0 = floor(top / cellHeightPx).toInt()
        val row1 = floor((top + heightPx) / cellHeightPx).toInt()
        for (row in row0..row1) {
            for (col in col0..col1) {
                if (cellKey(col, row) in occupied) return false
            }
        }
        for (row in row0..row1) {
            for (col in col0..col1) {
                occupied += cellKey(col, row)
            }
        }
        return true
    }

    private fun cellKey(col: Int, row: Int): Long =
        (col.toLong() shl COLUMN_KEY_SHIFT) xor (row.toLong() and ROW_KEY_MASK)
}

/** Truncates a title to [MAX_LABEL_CHARS], ellipsised, so one long note can't hog a whole row. */
fun truncateLabel(title: String): String =
    if (title.length <= MAX_LABEL_CHARS) title else title.take(MAX_LABEL_CHARS - 1).trimEnd() + "…"

/** The canvas's minimum zoom, and the point at which edges are at their faintest. */
const val LOD_MIN_SCALE = 0.1f

/** Scale at and above which edges are fully opaque and every eligible label is in play. */
const val LOD_FULL_DETAIL_SCALE = 1.2f

/** Sub-linear so zooming out shrinks nodes noticeably without ever losing them entirely. */
private const val NODE_RADIUS_ZOOM_EXPONENT = 0.5f

/** Ceiling on zoom-driven growth, so a deep zoom doesn't turn hubs into screen-filling discs. */
private const val MAX_NODE_RADIUS_MULTIPLIER = 2.2f

/** Floor for the edge fade, at full zoom-out. Not 0 - the link structure is the point of that view. */
private const val EDGE_MIN_ALPHA_MULTIPLIER = 0.25f

/** Fraction of the viewport left as margin when framing the whole graph. */
private const val FIT_PADDING_FRACTION = 0.12f

/** A tiny graph shouldn't be blown up past life size just because it would fit. */
private const val MAX_FIT_SCALE = 1f

private const val MAX_LABEL_CHARS = 32

private const val COLUMN_KEY_SHIFT = 32
private const val ROW_KEY_MASK = 0xFFFFFFFFL
