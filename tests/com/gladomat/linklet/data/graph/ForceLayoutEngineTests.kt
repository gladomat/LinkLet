package com.gladomat.linklet.data.graph

import kotlin.math.sqrt
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
        while (!engine.isConverged(state) && guard < 2000) {
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

    /** Deterministic preferential-attachment graph - a realistic hub/leaf mix, no randomness. */
    private fun scaleFreeGraph(nodeCount: Int): Pair<List<String>, List<Pair<String, String>>> {
        val nodes = (0 until nodeCount).map { "n$it" }
        val edges = mutableListOf<Pair<String, String>>()
        val attachmentPool = mutableListOf("n0")
        for (i in 1 until nodeCount) {
            repeat(1 + (i % 3)) { k ->
                val target = attachmentPool[(i * 7 + k * 13) % attachmentPool.size]
                if (target != "n$i") {
                    edges += "n$i" to target
                    attachmentPool += target
                }
            }
            attachmentPool += "n$i"
        }
        return nodes to edges
    }

    @Test
    fun `linked notes settle close together relative to the graph's overall size`() {
        // The failure this guards against: with a long-range (inverse-linear) repulsion law the
        // whole node set's combined push outweighs the springs, and edges stretch in proportion to
        // the graph's size - measured at ~16x the typical node spacing here, i.e. edges spanning
        // the entire canvas, which is what makes a selected note's links unreadable.
        val (nodes, edges) = scaleFreeGraph(300)
        val engine = ForceLayoutEngine()

        val settled = runToConvergence(engine, nodes, edges)
        val positions = settled.positions

        val meanEdgeLength = edges
            .map { (source, target) -> (positions.getValue(source) - positions.getValue(target)).length() }
            .average()
        // Typical node-to-node spacing: the radius the layout occupies, divided by the number of
        // nodes across it. Comparing against this rather than an absolute pixel count keeps the
        // assertion about the layout's *shape*, not about whatever the force constants are tuned to.
        val graphRadius = nodes.maxOf { positions.getValue(it).length() }
        val typicalSpacing = graphRadius / sqrt(nodes.size.toFloat())

        assertTrue(
            "edges average ${meanEdgeLength.toInt()}px against ${typicalSpacing.toInt()}px typical spacing " +
                "(${"%.1f".format(meanEdgeLength / typicalSpacing)}x) - linked notes have drifted apart",
            meanEdgeLength < typicalSpacing * 9f,
        )
    }

    @Test
    fun `a leaf stays near its hub instead of drifting out to the rim`() {
        // A degree-1 note is held by one spring but pushed outward by every other node in the
        // vault, and that repulsion grows with the vault while the single spring does not. Without
        // the leaf spring boost, degree-1 notes settled at ~46% of the graph radius on this graph -
        // visually, a note that is clearly linked to a central hub sitting way out near the edge.
        val (nodes, edges) = scaleFreeGraph(400)
        val engine = ForceLayoutEngine()

        val positions = runToConvergence(engine, nodes, edges).positions

        val degree = HashMap<String, Int>()
        edges.forEach { (source, target) ->
            degree[source] = (degree[source] ?: 0) + 1
            degree[target] = (degree[target] ?: 0) + 1
        }
        val lengths = edges.map { (source, target) ->
            Triple(source, target, (positions.getValue(source) - positions.getValue(target)).length())
        }
        val medianLength = lengths.map { it.third }.sorted()[lengths.size / 2]
        val leafLengths = lengths.filter { (source, target, _) -> degree[source] == 1 || degree[target] == 1 }

        assertTrue("expected some degree-1 notes in the fixture", leafLengths.size > 20)
        val meanLeafLength = leafLengths.map { it.third }.average()
        // Measured against the median edge rather than the graph radius: the radius depends on how
        // many orphans the vault happens to contain, whereas "is a leaf's edge an outlier next to a
        // normal edge" is exactly what you see when you select a note and read its links.
        assertTrue(
            "a leaf's edge averages ${meanLeafLength.toInt()}px against a ${medianLength.toInt()}px " +
                "median edge (${"%.1f".format(meanLeafLength / medianLength)}x) - leaves are drifting out",
            meanLeafLength < medianLength * 1.6f,
        )
    }

    @Test
    fun `unlinked notes still settle outside the connected core`() {
        // The complement of the test above: pulling leaves in must not also drag orphans inward.
        // A note with no links has nothing holding it among the connected ones, and showing that
        // by leaving it on the rim is the intended reading of the graph, not a defect.
        val (linked, edges) = scaleFreeGraph(300)
        val orphans = (0 until 60).map { "orphan$it" }
        val engine = ForceLayoutEngine()

        val positions = runToConvergence(engine, linked + orphans, edges).positions

        val meanLinkedRadius = linked.map { positions.getValue(it).length() }.average()
        val meanOrphanRadius = orphans.map { positions.getValue(it).length() }.average()

        assertTrue(
            "orphans ($meanOrphanRadius) should sit well outside the connected core ($meanLinkedRadius)",
            meanOrphanRadius > meanLinkedRadius * 2f,
        )
    }

    @Test
    fun `reopening a fully cached layout does not reheat it`() {
        val engine = ForceLayoutEngine()
        val nodes = listOf("a", "b", "c", "d")
        val edges = listOf("a" to "b", "b" to "c", "c" to "d")
        val settled = runToConvergence(engine, nodes, edges)

        val reseeded = engine.seed(nodes, cachedPositions = settled.positions)

        assertTrue("a layout with no fresh nodes must start cold", engine.isConverged(engine.step(reseeded, nodes, edges)))
    }

    @Test
    fun `adding fresh nodes reheats the layout so it can rearrange`() {
        val engine = ForceLayoutEngine()
        val nodes = listOf("a", "b", "c", "d")
        val settled = runToConvergence(engine, nodes, listOf("a" to "b", "b" to "c", "c" to "d"))
        val grown = nodes + listOf("e", "f", "g", "h")

        val reseeded = engine.seed(grown, cachedPositions = settled.positions)

        assertTrue("half the node set is new - the layout must be free to move", reseeded.temperature > 1f)
    }

    @Test
    fun `empty node list is a no-op`() {
        val engine = ForceLayoutEngine()
        val state = engine.seed(emptyList(), emptyMap())

        val stepped = engine.step(state, emptyList(), emptyList())

        assertTrue(stepped.positions.isEmpty())
    }
}
