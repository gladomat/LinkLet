package com.gladomat.linklet.data.graph

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Force-directed layout state at one point in the simulation.
 */
data class GraphLayoutState(
    val positions: Map<String, Vector2>,
    val iteration: Int = 0,
    val temperature: Float = ForceLayoutEngine.INITIAL_TEMPERATURE,
    val lastMaxDisplacement: Float = Float.MAX_VALUE,
)

/**
 * Fruchterman-Reingold force-directed layout with a Barnes-Hut approximation for repulsion
 * (Design decision 2, docs/plans/2026-07-06-note-graph-view.md). Pure Kotlin, no Android
 * dependency - unit-testable on the plain-JVM tier without Robolectric.
 *
 * Usage: [seed] once for a fresh/changed node set, then repeatedly call [step] until
 * [isConverged] - each call is one simulation tick. The caller (a ViewModel coroutine on
 * Dispatchers.Default) owns the tick loop, throttling, and publishing; this class only owns
 * the physics, which keeps it trivial to unit test step-by-step.
 */
class ForceLayoutEngine(
    private val repulsionStrength: Float = 2_000f,
    private val springLength: Float = 120f,
    private val springStrength: Float = 0.05f,
    private val centeringStrength: Float = 0.01f,
    private val theta: Float = 0.8f,
    private val coolingFactor: Float = 0.96f,
    private val minTemperature: Float = 0.05f,
    private val convergenceEpsilon: Float = 0.05f,
    private val maxIterations: Int = 300,
) {

    /**
     * Builds the initial layout state for [nodes]: nodes present in [cachedPositions] start
     * already-settled there (Design decision 4 - reopening the graph shouldn't re-scramble
     * unchanged nodes); any node with no cached entry (a new note) is seeded on a spiral so
     * distinct fresh nodes never start at the exact same point.
     */
    fun seed(nodes: List<String>, cachedPositions: Map<String, Vector2>): GraphLayoutState {
        var freshIndex = 0
        val positions = nodes.associateWith { id ->
            cachedPositions[id] ?: spiralSeedPosition(freshIndex++)
        }
        return GraphLayoutState(positions = positions)
    }

    /** Runs one simulation tick. Returns [state] unchanged once already converged. */
    fun step(state: GraphLayoutState, nodes: List<String>, edges: List<Pair<String, String>>): GraphLayoutState {
        if (nodes.isEmpty() || isConverged(state)) return state

        val tree = QuadTree.build(nodes.map { state.positions.getValue(it) })
        val forces = HashMap<String, Vector2>(nodes.size)
        nodes.forEach { id ->
            val position = state.positions.getValue(id)
            forces[id] = tree.repulsionForce(position, theta, repulsionStrength)
        }

        edges.forEach { (sourceId, targetId) ->
            val sourcePos = state.positions[sourceId] ?: return@forEach
            val targetPos = state.positions[targetId] ?: return@forEach
            val delta = targetPos - sourcePos
            val distance = delta.length().coerceAtLeast(MIN_DISTANCE)
            // Hooke's law toward springLength: pulls together if farther, pushes apart if closer.
            val magnitude = springStrength * (distance - springLength)
            val direction = Vector2(delta.x / distance, delta.y / distance)
            val force = direction * magnitude
            forces[sourceId] = (forces[sourceId] ?: Vector2.ZERO) + force
            forces[targetId] = (forces[targetId] ?: Vector2.ZERO) - force
        }

        var maxDisplacement = 0f
        val newPositions = HashMap<String, Vector2>(nodes.size)
        nodes.forEach { id ->
            val position = state.positions.getValue(id)
            val centeringForce = position * -centeringStrength
            val netForce = (forces[id] ?: Vector2.ZERO) + centeringForce
            val forceMagnitude = netForce.length()
            val displacement = if (forceMagnitude < MIN_DISTANCE) {
                Vector2.ZERO
            } else {
                // Classic Fruchterman-Reingold: move along the force's direction, but cap the
                // step by the current "temperature" so displacement shrinks as it cools instead
                // of jittering forever.
                val cappedMagnitude = forceMagnitude.coerceAtMost(state.temperature)
                Vector2(netForce.x / forceMagnitude, netForce.y / forceMagnitude) * cappedMagnitude
            }
            maxDisplacement = maxDisplacement.coerceAtLeast(displacement.length())
            newPositions[id] = position + displacement
        }

        return GraphLayoutState(
            positions = newPositions,
            iteration = state.iteration + 1,
            temperature = (state.temperature * coolingFactor).coerceAtLeast(minTemperature),
            lastMaxDisplacement = maxDisplacement,
        )
    }

    fun isConverged(state: GraphLayoutState): Boolean =
        state.iteration >= maxIterations || state.lastMaxDisplacement < convergenceEpsilon

    private fun spiralSeedPosition(index: Int): Vector2 {
        // Golden-angle spiral: every fresh node gets a distinct starting point (no two indices
        // coincide, which the Barnes-Hut quadtree needs - see QuadTree's MAX_DEPTH comment)
        // while still starting roughly centered.
        val angle = index * GOLDEN_ANGLE_RADIANS
        val radius = SEED_RADIUS_STEP * sqrt(index + 1f)
        return Vector2(radius * cos(angle), radius * sin(angle))
    }

    companion object {
        const val INITIAL_TEMPERATURE = 30f
        private const val MIN_DISTANCE = 0.01f
        private const val SEED_RADIUS_STEP = 8f
        private val GOLDEN_ANGLE_RADIANS = (PI * (3 - sqrt(5.0))).toFloat()
    }
}
