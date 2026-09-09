package nisargpatel.deadreckoning.domain.state

import nisargpatel.deadreckoning.data.RoadCandidate
import org.osmdroid.util.GeoPoint

/**
 * Snapshot of HMM map-matching state for live visual debugging.
 *
 * Holds the raw position observation, the winning candidate chosen by Viterbi,
 * and the full candidate set considered during that update.
 */
data class MapMatchDebugState(
    val rawPosition: GeoPoint? = null,
    val matchedCandidate: RoadCandidate? = null,
    val allCandidates: List<RoadCandidate> = emptyList(),
    val confidence: Int? = null,
    val distanceErrorMeters: Double? = null,
    val timestampMs: Long = System.currentTimeMillis()
)
