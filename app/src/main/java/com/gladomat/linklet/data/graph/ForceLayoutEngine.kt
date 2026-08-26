package com.gladomat.linklet.data.graph

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
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
 * Force-directed layout with a Barnes-Hut approximation for repulsion (Design decision 2,
 * docs/plans/2026-07-06-note-graph-view.md). Pure Kotlin, no Android dependency - unit-testable on
 * the plain-JVM tier without Robolectric.
 *
 * The force model is inverse-square repulsion plus linear (Hooke) springs, not textbook
 * Fruchterman-Reingold. FR's inverse-*linear* repulsion decays so slowly that in a graph of this
 * size the combined push of every distant node outweighs the springs, and every edge stretches in
 * proportion to the graph's overall size - measured at ~16x the typical node spacing on a
 * 300-node vault, i.e. edges spanning the entire canvas. Inverse-square makes repulsion
 * effectively local, which is what lets linked notes actually sit next to each other (~6x
 * spacing on the same graph) while unlinked ones still keep their distance.
 *
 * Usage: [seed] once for a fresh/changed node set, then repeatedly call [step] until
 * [isConverged] - each call is one simulation tick. The caller (a ViewModel coroutine on
 * Dispatchers.Default) owns the tick loop, throttling, and publishing; this class only owns
 * the physics, which keeps it trivial to unit test step-by-step.
 */
class ForceLayoutEngine(
    private val repulsionStrength: Float = 50_000f,
    private val springLength: Float = 60f,
    private val springStrength: Float = 0.5f,
    private val centeringStrength: Float = 0.05f,
    private val theta: Float = 0.8f,
    private val initialTemperature: Float = INITIAL_TEMPERATURE,
    private val coolingFactor: Float = 0.985f,
    // Must stay below convergenceEpsilon: displacement is capped at the temperature, so a floor
    // at or above the epsilon makes isConverged() unable to ever trip on displacement and the
    // simulation burns every one of its maxIterations ticks jittering in place at equilibrium.
    private val minTemperature: Float = 0.01f,
    private val convergenceEpsilon: Float = 0.05f,
    // High enough that the cooling schedule brings displacement below convergenceEpsilon on its
    // own - a lower cap would stop the run while the layout is still visibly drifting.
    private val maxIterations: Int = 500,
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
        // Reheat in proportion to how much actually changed. Starting every seed at full
        // temperature would let the first tick fling an already-settled layout by up to
        // initialTemperature pixels per node, re-scrambling a graph the user has already read
        // (Design decision 4). With nothing fresh the layout starts effectively frozen and
        // converges on the first tick; a wholly new node set starts fully hot.
        val freshFraction = freshIndex.toFloat() / nodes.size.coerceAtLeast(1)
        val temperature = (initialTemperature * freshFraction).coerceAtLeast(minTemperature)
        return GraphLayoutState(positions = positions, temperature = temperature)
    }

    /** Runs one simulation tick. Returns [state] unchanged once already converged. */
    fun step(state: GraphLayoutState, nodes: List<String>, edges: List<Pair<String, String>>): GraphLayoutState {
        if (nodes.isEmpty() || isConverged(state)) return state

        val degrees = degreesOf(nodes, edges)
        // Repulsion is mass-weighted by connectivity: a well-connected note clears a wider halo
        // around itself than a leaf does, which is what breaks a uniformly-dense hairball into
        // legible, separated clusters. Damped by ln so a 200-link hub doesn't blow the layout out
        // 200x - the same log scaling the UI uses for node radius.
        val masses = nodes.map { repulsionMass(degrees[it] ?: 0) }
        val tree = QuadTree.build(nodes.map { state.positions.getValue(it) }, masses)
        val forces = HashMap<String, Vector2>(nodes.size)
        nodes.forEach { id ->
            val position = state.positions.getValue(id)
            // Deliberately not multiplied by this node's own mass: the tree already accounts for
            // how hard everyone else pushes, and scaling a hub's own acceleration on top of that
            // just makes hubs overshoot and jitter.
            forces[id] = tree.repulsionForce(position, theta, repulsionStrength)
        }

        edges.forEach { (sourceId, targetId) ->
            val sourcePos = state.positions[sourceId] ?: return@forEach
            val targetPos = state.positions[targetId] ?: return@forEach
            val delta = targetPos - sourcePos
            val distance = delta.length().coerceAtLeast(MIN_DISTANCE)
            // Hooke's law toward an ideal length that grows with how connected both ends are: a
            // leaf hugs its hub tightly (a tight starburst) while two hubs sit further apart, so
            // clusters read as distinct blobs with space between them rather than one even mesh.
            val minDegree = minOf(degrees[sourceId] ?: 0, degrees[targetId] ?: 0)
            val idealLength = springLength * springLengthFactor(minDegree)
            val magnitude = springStrength * leafSpringBoost(minDegree) * (distance - idealLength)
            val direction = Vector2(delta.x / distance, delta.y / distance)
            val force = direction * magnitude
            forces[sourceId] = (forces[sourceId] ?: Vector2.ZERO) + force
            forces[targetId] = (forces[targetId] ?: Vector2.ZERO) - force
        }

        var maxDisplacement = 0f
        val newPositions = HashMap<String, Vector2>(nodes.size)
        nodes.forEachIndexed { index, id ->
            val position = state.positions.getValue(id)
            // Gravity is mass-weighted too: hubs are drawn towards the middle while leaves drift
            // out to their own cluster, instead of every node reaching the same equilibrium
            // radius and lining the whole graph up on one dense outer ring.
            val centeringForce = position * (-centeringStrength * masses[index])
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

    private fun degreesOf(nodes: List<String>, edges: List<Pair<String, String>>): Map<String, Int> {
        val degrees = HashMap<String, Int>(nodes.size)
        edges.forEach { (sourceId, targetId) ->
            degrees[sourceId] = (degrees[sourceId] ?: 0) + 1
            degrees[targetId] = (degrees[targetId] ?: 0) + 1
        }
        return degrees
    }

    private fun repulsionMass(degree: Int): Float =
        1f + REPULSION_DEGREE_WEIGHT * ln(1f + degree)

    /**
     * Ideal edge length, as a multiple of [springLength]. The *less* connected end decides: an
     * edge is short whenever either side is a leaf, and only stretches when both ends are hubs in
     * their own right.
     */
    private fun springLengthFactor(minDegree: Int): Float =
        LEAF_SPRING_FACTOR + SPRING_DEGREE_SPREAD * ln(1f + minDegree)

    /**
     * Extra spring stiffness for edges whose weaker end has few links.
     *
     * A leaf is held in place by its single edge but pushed outward by every other node in the
     * vault, and that repulsion grows with the vault while one spring does not - so on a large
     * graph a leaf drifts out towards the rim even though it belongs beside its hub (measured at
     * ~46% of the graph radius for degree-1 notes on a 1200-note vault). A well-connected node
     * needs no such help: its many edges already share the load, so the boost decays to 1.
     */
    private fun leafSpringBoost(minDegree: Int): Float =
        1f + LEAF_SPRING_BOOST / (1f + minDegree)

    private fun spiralSeedPosition(index: Int): Vector2 {
        // Golden-angle spiral: every fresh node gets a distinct starting point (no two indices
        // coincide, which the Barnes-Hut quadtree needs - see QuadTree's MAX_DEPTH comment)
        // while still starting roughly centered.
        val angle = index * GOLDEN_ANGLE_RADIANS
        val radius = SEED_RADIUS_STEP * sqrt(index + 1f)
        return Vector2(radius * cos(angle), radius * sin(angle))
    }

    companion object {
        const val INITIAL_TEMPERATURE = 80f
        private const val MIN_DISTANCE = 0.01f
        private const val SEED_RADIUS_STEP = 8f

        /** How much a node's connectivity inflates its repulsion halo (log-damped). */
        private const val REPULSION_DEGREE_WEIGHT = 0.5f

        /** Ideal edge length, as a fraction of springLength, when either end is an orphan. */
        private const val LEAF_SPRING_FACTOR = 0.6f

        /** How fast the ideal edge length grows once both ends are themselves well connected. */
        private const val SPRING_DEGREE_SPREAD = 0.15f

        // Extra stiffness at degree 0, decaying towards none as the weaker end gains links.
        // 12 brings degree-1 notes in from 46% to 24% of the graph radius on a 1200-note vault
        // while degree-3 sit at 21% and hubs at 18% - past roughly 25 the gradient collapses and
        // every connected node lands at the same radius, which reads as one undifferentiated blob.
        private const val LEAF_SPRING_BOOST = 12f

        private val GOLDEN_ANGLE_RADIANS = (PI * (3 - sqrt(5.0))).toFloat()
    }
}
