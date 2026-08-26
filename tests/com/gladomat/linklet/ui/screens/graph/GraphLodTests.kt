package com.gladomat.linklet.ui.screens.graph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphLodTests {

    private fun lodFor(degrees: Map<String, Int>): Map<String, NodeLod> =
        buildNodeLod(degrees.keys, degrees)

    @Test
    fun `ranking is deterministic when degrees tie`() {
        val degrees = mapOf("zeta" to 3, "alpha" to 3, "mid" to 3)

        val first = buildNodeLod(listOf("zeta", "alpha", "mid"), degrees)
        val second = buildNodeLod(listOf("mid", "alpha", "zeta"), degrees)

        assertEquals(first.mapValues { it.value.rank }, second.mapValues { it.value.rank })
    }

    @Test
    fun `node radius shrinks when zoomed out and is capped when zoomed in`() {
        val base = 10f

        val zoomedOut = lodNodeRadiusPx(base, scale = 0.25f, minRadiusPx = 1f)
        val atRest = lodNodeRadiusPx(base, scale = 1f, minRadiusPx = 1f)
        val zoomedIn = lodNodeRadiusPx(base, scale = 8f, minRadiusPx = 1f)

        assertTrue("zoomed out should be smaller than at rest", zoomedOut < atRest)
        assertEquals(base, atRest, 1e-4f)
        assertTrue("zoomed in should be larger but capped", zoomedIn > atRest && zoomedIn <= base * 3f)
    }

    @Test
    fun `node radius never drops below the floor`() {
        assertEquals(2f, lodNodeRadiusPx(baseRadiusPx = 10f, scale = 0.0001f, minRadiusPx = 2f), 1e-4f)
    }

    @Test
    fun `edges fade out towards the zoomed-out view and are solid at full detail`() {
        val out = lodEdgeAlphaMultiplier(LOD_MIN_SCALE)
        val mid = lodEdgeAlphaMultiplier((LOD_MIN_SCALE + LOD_FULL_DETAIL_SCALE) / 2f)

        assertTrue("edges should be faint but not invisible when zoomed out", out > 0f && out < mid)
        assertEquals(1f, lodEdgeAlphaMultiplier(LOD_FULL_DETAIL_SCALE), 1e-4f)
        assertEquals(1f, lodEdgeAlphaMultiplier(8f), 1e-4f)
    }

    @Test
    fun `viewport culling accepts points inside and just outside, rejects far ones`() {
        assertTrue(isOnScreen(x = 10f, y = 10f, width = 100, height = 200, marginPx = 20f))
        assertTrue(isOnScreen(x = -10f, y = 205f, width = 100, height = 200, marginPx = 20f))
        assertFalse(isOnScreen(x = -50f, y = 10f, width = 100, height = 200, marginPx = 20f))
        assertFalse(isOnScreen(x = 10f, y = 500f, width = 100, height = 200, marginPx = 20f))
    }

    @Test
    fun `an edge is drawn when either endpoint is on screen`() {
        val incoming = edgeIsWorthDrawing(
            startX = -500f, startY = 100f, endX = 50f, endY = 100f,
            width = 100, height = 200, marginPx = 0f,
        )
        val outgoing = edgeIsWorthDrawing(
            startX = 50f, startY = 100f, endX = 900f, endY = 100f,
            width = 100, height = 200, marginPx = 0f,
        )

        assertTrue(incoming)
        assertTrue(outgoing)
    }

    @Test
    fun `an edge merely crossing the viewport is dropped - it joins nothing you can see`() {
        // Both endpoints off screen: the line would cross the view without either end visible, so
        // it says nothing about what connects to what and only adds clutter.
        val crossing = edgeIsWorthDrawing(
            startX = -500f, startY = 100f, endX = 600f, endY = 100f,
            width = 100, height = 200, marginPx = 0f,
        )
        val elsewhere = edgeIsWorthDrawing(
            startX = -500f, startY = -500f, endX = -400f, endY = -400f,
            width = 100, height = 200, marginPx = 0f,
        )

        assertFalse(crossing)
        assertFalse(elsewhere)
    }

    @Test
    fun `label grid rejects a second label overlapping the first`() {
        val grid = LabelGrid(cellWidthPx = 4f, cellHeightPx = 4f)

        assertTrue(grid.tryClaim(left = 0f, top = 0f, widthPx = 40f, heightPx = 12f))
        assertFalse("an overlapping label must be dropped", grid.tryClaim(left = 20f, top = 4f, widthPx = 40f, heightPx = 12f))
        assertTrue("a clear patch of screen must still be claimable", grid.tryClaim(left = 200f, top = 200f, widthPx = 40f, heightPx = 12f))
    }

    @Test
    fun `a rejected label claims nothing - the patch stays free for the next candidate`() {
        val grid = LabelGrid(cellWidthPx = 4f, cellHeightPx = 4f)
        grid.tryClaim(left = 0f, top = 0f, widthPx = 40f, heightPx = 12f)

        // Overlaps the first claim on its left edge and would otherwise have reserved 40..100.
        assertFalse(grid.tryClaim(left = 30f, top = 0f, widthPx = 70f, heightPx = 12f))
        assertTrue("the rejected label must not have reserved anything", grid.tryClaim(left = 60f, top = 0f, widthPx = 30f, heightPx = 12f))
    }

    @Test
    fun `long titles are truncated with an ellipsis and short ones left alone`() {
        assertEquals("Short title", truncateLabel("Short title"))

        val long = "A very long note title that would otherwise hog an entire row of the canvas"
        val truncated = truncateLabel(long)

        assertTrue(truncated.length < long.length)
        assertTrue(truncated.endsWith("…"))
    }

    @Test
    fun `fit scale frames the whole graph inside the viewport`() {
        val bounds = GraphBounds(minX = -500f, minY = -250f, maxX = 500f, maxY = 250f)

        val scale = fitScale(bounds, viewportWidth = 400, viewportHeight = 800)!!

        // The wider axis is the binding one: 1000 graph units must fit 400px of viewport.
        assertTrue("scaled width must fit", bounds.width * scale <= 400f)
        assertTrue("scaled height must fit", bounds.height * scale <= 800f)
    }

    @Test
    fun `fit pan centres the graph in the viewport`() {
        val bounds = GraphBounds(minX = 100f, minY = 100f, maxX = 300f, maxY = 300f)

        val (panX, panY) = fitPan(bounds, viewportWidth = 400, viewportHeight = 600, scale = 1f)

        // The bounds' centre (200, 200) must land at the viewport's centre (200, 300).
        assertEquals(200f, bounds.centerX * 1f + panX, 1e-4f)
        assertEquals(300f, bounds.centerY * 1f + panY, 1e-4f)
    }

    @Test
    fun `a tiny graph is not blown up past life size`() {
        val bounds = GraphBounds(minX = -5f, minY = -5f, maxX = 5f, maxY = 5f)

        val scale = fitScale(bounds, viewportWidth = 1000, viewportHeight = 2000)!!

        assertTrue("a 10px graph must not be scaled to fill a 1000px viewport", scale <= 1f)
    }

    @Test
    fun `fit scale is undefined for an empty graph or an unmeasured viewport`() {
        val bounds = GraphBounds(minX = 0f, minY = 0f, maxX = 100f, maxY = 100f)

        assertEquals(null, fitScale(null, 100, 100))
        assertEquals(null, fitScale(bounds, 0, 0))
        // A single node has zero extent on both axes - there is no meaningful scale to fit it at.
        assertEquals(null, fitScale(GraphBounds(5f, 5f, 5f, 5f), 100, 100))
    }

    @Test
    fun `every node keeps a rank - none are dropped at any zoom`() {
        val lod = lodFor(mapOf("hub" to 20, "mid" to 5, "leaf" to 1, "orphan" to 0))

        assertEquals(4, lod.size)
        assertEquals(setOf(0, 1, 2, 3), lod.values.map { it.rank }.toSet())
        assertEquals(0, lod.getValue("hub").rank)
        assertEquals(3, lod.getValue("orphan").rank)
    }

    @Test
    fun `an empty graph produces no LOD entries`() {
        assertTrue(buildNodeLod(emptyList(), emptyMap()).isEmpty())
    }
}
