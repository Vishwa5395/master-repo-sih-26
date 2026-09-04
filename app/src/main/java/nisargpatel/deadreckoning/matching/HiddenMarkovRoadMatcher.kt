package nisargpatel.deadreckoning.matching

import nisargpatel.deadreckoning.data.RoadCandidate
import org.osmdroid.util.GeoPoint
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.exp

data class MatchedRoad(val candidate: RoadCandidate, val confidence: Int)

/** Fixed-lag route-network Viterbi trellis using proximity, heading, and transition continuity. */
class HiddenMarkovRoadMatcher(private val historyDepth: Int = 20) {
    private data class State(val candidate: RoadCandidate, val score: Double, val parentIndex: Int?)
    private data class Layer(val observation: GeoPoint, val states: List<State>)
    private val trellis = ArrayDeque<Layer>()

    fun update(observation: GeoPoint, candidates: List<RoadCandidate>): MatchedRoad? {
        if (candidates.isEmpty()) return null
        val prior = trellis.lastOrNull()
        val observedDistance = prior?.observation?.distanceToAsDouble(observation) ?: 0.0
        val observedBearing = prior?.observation?.bearingTo(observation)?.toDouble()
        val current = candidates.map { candidate ->
            val emission = -candidate.distanceMeters / 10.0 - headingPenalty(candidate, observedBearing)
            val parent = prior?.states?.mapIndexed { index, state ->
                index to (state.score + transitionScore(state.candidate, candidate, observedDistance))
            }?.maxByOrNull { it.second }
            State(candidate, emission + (parent?.second ?: 0.0), parent?.first)
        }
        trellis += Layer(observation, current)
        while (trellis.size > historyDepth) trellis.removeFirst()
        val best = current.maxBy { it.score }
        val normalizer = current.sumOf { exp((it.score - best.score).coerceAtLeast(-35.0)) }
        return MatchedRoad(best.candidate, (100.0 / normalizer).toInt().coerceIn(0, 100))
    }

    fun reset() = trellis.clear()

    private fun transitionScore(from: RoadCandidate, to: RoadCandidate, observedDistance: Double): Double {
        val graphDistance = from.point.distanceToAsDouble(to.point)
        val distanceError = abs(graphDistance - observedDistance)
        val sameWayBonus = if (from.wayId != 0L && from.wayId == to.wayId) 1.8 else if (from.roadName == to.roadName) 0.6 else 0.0
        return sameWayBonus - distanceError / 18.0
    }

    private fun headingPenalty(candidate: RoadCandidate, observedBearing: Double?): Double {
        observedBearing ?: return 0.0
        val forwardDifference = angularDifference(candidate.bearingDegrees, observedBearing)
        val difference = if (candidate.oneWay) forwardDifference else minOf(forwardDifference, angularDifference(candidate.bearingDegrees + 180.0, observedBearing))
        return difference / 45.0
    }

    private fun angularDifference(first: Double, second: Double): Double = abs(((first - second + 540.0) % 360.0) - 180.0)
}
