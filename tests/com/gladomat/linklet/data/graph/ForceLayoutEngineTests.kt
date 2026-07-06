package com.gladomat.linklet.data.graph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForceLayoutEngineTests {

    private fun runToConvergence(
        engine: ForceLayoutEngine,
        nodes: List<String>,
        edges: List<Pair<String, String>>,
        cachedPositions: Map<String, Vector2> = emptyMap(),
    ): GraphLayoutState {
        var state = engine.seed(nodes, cachedPositions)
        var guard = 0
        while (!engine.isConverged(state) && guard < 1000) {
            state = engine.step(state, nodes, edges)
            guard++
        }
        return state
    }

    @Test
    fun `converges within maxIterations on a small connected graph`() {
        val engine = ForceLayoutEngine(maxIterations = 200)
        val nodes = listOf("a", "b", "c", "d")
        val edges = listOf("a" to "b", "b" to "c", "c" to "d", "d" to "a")

        val finalState = runToConvergence(engine, nodes, edges)

        assertTrue(engine.isConverged(finalState))
        assertTrue(finalState.iteration <= 200)
    }

    @Test
    fun `connected nodes end up closer than disconnected ones`() {
        val engine = ForceLayoutEngine(maxIterations = 300)
        // a-b are linked; c and d are isolated (no edges at all).
        val nodes = listOf("a", "b", "c", "d")
        val edges = listOf("a" to "b")

        val finalState = runToConvergence(engine, nodes, edges)
        val positions = finalState.positions

        val abDistance = (positions.getValue("a") - positions.getValue("b")).length()
        val acDistance = (positions.getValue("a") - positions.getValue("c")).length()
        val adDistance = (positions.getValue("a") - positions.getValue("d")).length()
        val cdDistance = (positions.getValue("c") - positions.getValue("d")).length()

        assertTrue("linked pair ($abDistance) should end up closer than to an unlinked node ($acDistance)", abDistance < acDistance)
        assertTrue("linked pair ($abDistance) should end up closer than to an unlinked node ($adDistance)", abDistance < adDistance)
        // c and d share no edge with anything - Barnes-Hut repulsion alone should still keep
        // them apart, not collapsed onto the same point.
        assertTrue(cdDistance > 1f)
    }

    @Test
    fun `deterministic given the same seed - no randomness involved`() {
        val engineA = ForceLayoutEngine(maxIterations = 50)
        val engineB = ForceLayoutEngine(maxIterations = 50)
        val nodes = listOf("a", "b", "c", "d", "e")
        val edges = listOf("a" to "b", "b" to "c", "c" to "d")

        val stateA = runToConvergence(engineA, nodes, edges)
        val stateB = runToConvergence(engineB, nodes, edges)

        nodes.forEach { id ->
            assertEquals(stateA.positions.getValue(id).x, stateB.positions.getValue(id).x, 1e-6f)
            assertEquals(stateA.positions.getValue(id).y, stateB.positions.getValue(id).y, 1e-6f)
        }
    }

    @Test
    fun `fresh nodes get distinct seed positions`() {
        val engine = ForceLayoutEngine()
        val nodes = listOf("a", "b", "c")

        val state = engine.seed(nodes, cachedPositions = emptyMap())

        val distinctPositions = state.positions.values.toSet()
        assertEquals(nodes.size, distinctPositions.size)
    }

    @Test
    fun `cached positions are honored as the starting point for unchanged nodes`() {
        val engine = ForceLayoutEngine()
        val cached = mapOf("a" to Vector2(500f, 500f))

        val state = engine.seed(listOf("a", "b"), cachedPositions = cached)

        assertEquals(500f, state.positions.getValue("a").x, 0f)
        assertEquals(500f, state.positions.getValue("a").y, 0f)
    }

    @Test
    fun `an already-settled graph reseeded from its own final positions converges immediately`() {
        val engine = ForceLayoutEngine(maxIterations = 300, convergenceEpsilon = 0.05f)
        val nodes = listOf("a", "b", "c")
        val edges = listOf("a" to "b", "b" to "c")
        val settled = runToConvergence(engine, nodes, edges)

        // Reopen: seed from the previous session's cached (settled) positions.
        val reseeded = engine.seed(nodes, cachedPositions = settled.positions)
        val nextTick = engine.step(reseeded, nodes, edges)

        assertTrue("displacement from an already-settled layout should be tiny", nextTick.lastMaxDisplacement < 1f)
    }

    @Test
    fun `single isolated node settles near the origin instead of drifting`() {
        val engine = ForceLayoutEngine(maxIterations = 100)
        val nodes = listOf("solo")

        val finalState = runToConvergence(engine, nodes, edges = emptyList())

        val distanceFromOrigin = finalState.positions.getValue("solo").length()
        assertTrue(distanceFromOrigin < 50f)
    }

    @Test
    fun `empty node list is a no-op`() {
        val engine = ForceLayoutEngine()
        val state = engine.seed(emptyList(), emptyMap())

        val stepped = engine.step(state, emptyList(), emptyList())

        assertTrue(stepped.positions.isEmpty())
    }
}
