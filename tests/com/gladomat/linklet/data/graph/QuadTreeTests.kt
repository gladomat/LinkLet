package com.gladomat.linklet.data.graph

import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuadTreeTests {

    /** Exact O(n^2) reference repulsion, mirroring QuadTree's per-pair force formula. */
    private fun naiveRepulsion(points: List<Vector2>, from: Vector2, strength: Float): Vector2 {
        var total = Vector2.ZERO
        points.forEach { other ->
            val dx = from.x - other.x
            val dy = from.y - other.y
            val distance = sqrt(dx * dx + dy * dy)
            if (distance >= 0.01f) {
                val magnitude = strength / distance
                total += Vector2(dx / distance, dy / distance) * magnitude
            }
        }
        return total
    }

    @Test
    fun `Barnes-Hut approximation is close to the exact naive sum for a small cluster`() {
        val points = listOf(
            Vector2(0f, 0f),
            Vector2(10f, 0f),
            Vector2(0f, 10f),
            Vector2(-8f, -4f),
            Vector2(15f, 12f),
            Vector2(-20f, 5f),
        )
        val tree = QuadTree.build(points)
        val strength = 100f
        val theta = 0.5f

        points.forEach { from ->
            val approx = tree.repulsionForce(from, theta, strength)
            val exact = naiveRepulsion(points, from, strength)
            val error = (approx - exact).length()
            val scale = exact.length().coerceAtLeast(1f)
            assertTrue(
                "approx=$approx exact=$exact error=$error should be within 25% of exact force magnitude ($scale)",
                error / scale < 0.25f,
            )
        }
    }

    @Test
    fun `a node exerts no repulsion on itself`() {
        val points = listOf(Vector2(0f, 0f), Vector2(5f, 5f), Vector2(-5f, 5f))
        val tree = QuadTree.build(points)

        val force = tree.repulsionForce(Vector2(0f, 0f), theta = 0.5f, strength = 1000f)

        // Force should point away from the other two points, not blow up from self-interaction.
        assertTrue(force.length().isFinite())
        assertTrue(force.length() < 1000f)
    }

    @Test
    fun `many exactly coincident points do not overflow the stack`() {
        val points = List(200) { Vector2(3f, 3f) }
        val tree = QuadTree.build(points)

        val force = tree.repulsionForce(Vector2(3f, 3f), theta = 0.5f, strength = 100f)

        assertTrue(force.length().isFinite())
    }

    @Test
    fun `empty point list yields zero force`() {
        val tree = QuadTree.build(emptyList())

        val force = tree.repulsionForce(Vector2(0f, 0f), theta = 0.5f, strength = 100f)

        assertEquals(Vector2.ZERO, force)
    }
}
