package nisargpatel.deadreckoning

import nisargpatel.deadreckoning.data.RoadCandidate
import nisargpatel.deadreckoning.matching.HiddenMarkovRoadMatcher
import org.junit.Assert.assertEquals
import org.junit.Test
import org.osmdroid.util.GeoPoint

class HiddenMarkovRoadMatcherTest {
    @Test
    fun `prefers a continuous road over a similarly close alternate`() {
        val matcher = HiddenMarkovRoadMatcher()
        val first = matcher.update(
            GeoPoint(16.5, 80.6),
            listOf(
                RoadCandidate("Main Road", GeoPoint(16.5, 80.6), 2.0),
                RoadCandidate("Side Road", GeoPoint(16.50001, 80.60001), 2.1)
            )
        )
        val second = matcher.update(
            GeoPoint(16.5001, 80.6),
            listOf(
                RoadCandidate("Main Road", GeoPoint(16.5001, 80.6), 2.3),
                RoadCandidate("Side Road", GeoPoint(16.5001, 80.60001), 1.9)
            )
        )
        assertEquals("Main Road", first?.candidate?.roadName)
        assertEquals("Main Road", second?.candidate?.roadName)
    }

    @Test
    fun `prefers network-connected candidate over closer but unreachable alternate`() {
        // Fake network: candidates on wayId=1 are "connected" (15 m path),
        // candidates on wayId=2 return null (unreachable within the bound).
        val fakeNetwork: (GeoPoint, Long, GeoPoint, Long, Double) -> Double? =
            { _, fromWay, _, toWay, _ ->
                if (fromWay == 1L && toWay == 1L) 15.0 else null
            }
        val matcher = HiddenMarkovRoadMatcher(networkDistance = fakeNetwork)
        matcher.update(
            GeoPoint(16.5, 80.6),
            listOf(
                RoadCandidate("Connected Road", GeoPoint(16.5, 80.6), 3.0, wayId = 1),
                RoadCandidate("Isolated Road", GeoPoint(16.5, 80.60005), 1.5, wayId = 2)
            )
        )
        // Second observation: Isolated Road is closer (1.0 m vs 3.2 m) but
        // its transition is unreachable → penalised by UNREACHABLE_PENALTY_METERS.
        val result = matcher.update(
            GeoPoint(16.5001, 80.6),
            listOf(
                RoadCandidate("Connected Road", GeoPoint(16.5001, 80.6), 3.2, wayId = 1),
                RoadCandidate("Isolated Road", GeoPoint(16.5001, 80.60005), 1.0, wayId = 2)
            )
        )
        assertEquals("Connected Road", result?.candidate?.roadName)
    }
}
