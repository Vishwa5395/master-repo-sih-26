package nisargpatel.deadreckoning

import nisargpatel.deadreckoning.data.RoadGraph
import nisargpatel.deadreckoning.data.RoadSegment
import nisargpatel.deadreckoning.data.TurnRestriction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.osmdroid.util.GeoPoint

class RoadGraphTest {

    // ── Shared points ────────────────────────────────────────────────
    //
    // At latitude 16.5°:  0.001° lon ≈ 106.7 m,  0.001° lat ≈ 111.1 m
    //
    //   p1 ──way100──▶ p2 ──way200──▶ p3
    //          (16.5, 80.600)  (16.5, 80.601)  (16.5, 80.602)
    //                          │
    //                        way300
    //                          │
    //                          ▼
    //                         p4   (16.501, 80.601)
    //                          │
    //                        way400
    //                          │
    //                          ▼
    //                         p5   (16.501, 80.602)
    //                          │
    //                        way500 (detour connector)
    //                          │
    //                          ▼
    //                         p3   (16.5, 80.602)

    private val p1 = GeoPoint(16.5, 80.600)
    private val p2 = GeoPoint(16.5, 80.601)
    private val p3 = GeoPoint(16.5, 80.602)
    private val p4 = GeoPoint(16.501, 80.601)
    private val p5 = GeoPoint(16.501, 80.602)

    private val way100 = RoadSegment(
        wayId = 100, name = "East Road",
        points = listOf(p1, p2), nodeIds = listOf(1L, 2L),
        oneWay = false, highway = "secondary"
    )
    private val way200 = RoadSegment(
        wayId = 200, name = "East Road Extension",
        points = listOf(p2, p3), nodeIds = listOf(2L, 3L),
        oneWay = false, highway = "secondary"
    )
    private val way300 = RoadSegment(
        wayId = 300, name = "South Road",
        points = listOf(p2, p4), nodeIds = listOf(2L, 4L),
        oneWay = false, highway = "residential"
    )
    private val way400 = RoadSegment(
        wayId = 400, name = "South-East Road",
        points = listOf(p4, p5), nodeIds = listOf(4L, 5L),
        oneWay = false, highway = "residential"
    )

    // ── Tests ────────────────────────────────────────────────────────

    @Test
    fun `graph reflects segments added after rebuild`() {
        // First graph: way100 (p1 ↔ p2) and way400 (p4 ↔ p5) are disconnected components
        val graph1 = RoadGraph(listOf(way100, way400), emptyList())
        assertNotNull("p1→p2 should be routable", graph1.route(p1, p2))
        assertNotNull("p4→p5 should be routable", graph1.route(p4, p5))
        assertNull("p1→p5 has no path between disconnected components", graph1.route(p1, p5))

        // Second graph: add way300 (p2 ↔ p4) connecting the two components
        val graph2 = RoadGraph(listOf(way100, way400, way300), emptyList())
        assertNotNull("p1→p5 should now be routable via way300 connector", graph2.route(p1, p5))

        // The original graph1 instance is unaffected (immutability check)
        assertNull("graph1 must not see graph2's connector", graph1.route(p1, p5))
    }

    @Test
    fun `shortestPathMeters respects one-way direction`() {
        val oneWaySegment = way100.copy(oneWay = true)  // p1 → p2 only
        val graph = RoadGraph(listOf(oneWaySegment), emptyList())

        val forward = graph.shortestPathMeters(p1, 100, p2, 100, 500.0)
        assertNotNull("Forward direction on one-way road should succeed", forward)

        val reverse = graph.shortestPathMeters(p2, 100, p1, 100, 500.0)
        assertNull("Reverse direction on one-way road must return null", reverse)
    }

    @Test
    fun `shortestPathMeters returns null when bound is exceeded`() {
        // p1 → p2 → p3 is about 213 m (106.7 + 106.7)
        val graph = RoadGraph(listOf(way100, way200), emptyList())

        val withinBound = graph.shortestPathMeters(p1, 100, p3, 200, 300.0)
        assertNotNull("Should find path within 300 m bound", withinBound)
        assertTrue("Path should be roughly 213 m", withinBound!! in 200.0..230.0)

        val tooTight = graph.shortestPathMeters(p1, 100, p3, 200, 100.0)
        assertNull("Should return null when bound (100 m) is less than path (~213 m)", tooTight)
    }

    @Test
    fun `shortestPathMeters includes partial-edge distance`() {
        // way100 is p1(16.5, 80.600) ↔ p2(16.5, 80.601), about 106.7 m
        val graph = RoadGraph(listOf(way100), emptyList())

        // Query point placed at ~32% along the edge from p1 to p2.
        // Node 1 (p1) is at 80.600, node 2 (p2) is at 80.601.
        // Distance to p1 is ~34.1 m; distance to p2 is ~72.6 m.
        // Nearest-node resolution is unambiguous: p1 (node 1) is strictly closer.
        val pointOnEdge = GeoPoint(16.5, 80.60032)
        val expectedPartial = pointOnEdge.distanceToAsDouble(p1) // ~34.15 m

        // Query pointOnEdge -> p1:
        // Both pointOnEdge and p1 snap to node 1 on way100 (startId == endId == 1L).
        // Early-return fires: partialFrom (pointOnEdge->node 1) + partialTo (p1->node 1 = 0m) = expectedPartial.
        val result = graph.shortestPathMeters(pointOnEdge, 100, p1, 100, 200.0)
        assertNotNull("pointOnEdge to p1 should be reachable", result)
        assertEquals(expectedPartial, result!!, 0.01)
        assertTrue("Result ($result m) should be ~34.1 m, not near zero", result in 32.0..36.0)

        // Full edge distance p1 -> p2 is ~106.7 m
        val fullEdge = graph.shortestPathMeters(p1, 100, p2, 100, 200.0)
        assertNotNull(fullEdge)
        assertTrue("Full edge ($fullEdge m) should be ~106.7 m", fullEdge!! in 95.0..120.0)
        assertTrue(
            "Partial distance ($result m) must be strictly less than full edge ($fullEdge m)",
            result < fullEdge
        )
    }

    @Test
    fun `shortestPathMeters respects turn restrictions`() {
        // Network:  p1 ──way100──▶ p2 ──way200──▶ p3
        //                          │              ▲
        //                        way300         way500
        //                          │              │
        //                          ▼              │
        //                         p4 ──way400──▶ p5
        //
        // p1=(16.5, 80.600), p2=(16.5, 80.601), p3=(16.5, 80.602)
        // p4=(16.501, 80.601), p5=(16.501, 80.602)
        val allWays = listOf(way100, way200, way300, way400)

        // No restriction: direct path p1 → p2 (way100) + p2 → p3 (way200)
        val graphUnrestricted = RoadGraph(allWays, emptyList())
        val directPath = graphUnrestricted.shortestPathMeters(p1, 100, p3, 200, 600.0)
        assertNotNull("Without restriction, direct path p1→p3 should succeed", directPath)
        val expectedDirect = p1.distanceToAsDouble(p2) + p2.distanceToAsDouble(p3)
        assertEquals(expectedDirect, directPath!!, 0.01)
        assertTrue("Direct path should be ~213 m", directPath in 200.0..230.0)

        // Turn restriction: no turn from way100 to way200 via node 2
        val restriction = TurnRestriction(
            fromWay = 100, toWay = 200, viaNode = 2L, only = false
        )

        // Detour topology: way500 connects p5 (node 5) to p3 (node 3)
        // Expected detour path: way100 (1→2) + way300 (2→4) + way400 (4→5) + way500 (5→3)
        val way500 = RoadSegment(
            wayId = 500, name = "Connector",
            points = listOf(p5, p3), nodeIds = listOf(5L, 3L),
            oneWay = false, highway = "residential"
        )
        val graphWithDetour = RoadGraph(allWays + way500, listOf(restriction))
        val detourPath = graphWithDetour.shortestPathMeters(p1, 100, p3, 200, 600.0)
        assertNotNull("With detour available, p3 must be reachable", detourPath)

        val expectedDetour = p1.distanceToAsDouble(p2) +
            p2.distanceToAsDouble(p4) +
            p4.distanceToAsDouble(p5) +
            p5.distanceToAsDouble(p3)
        assertEquals(expectedDetour, detourPath!!, 0.01)
        assertTrue("Detour path ($detourPath m) should be ~436 m", detourPath in 430.0..445.0)
        assertTrue(
            "Detour ($detourPath m) must be strictly longer than direct path ($directPath m)",
            detourPath > directPath
        )
    }
}
