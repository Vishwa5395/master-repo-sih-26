package nisargpatel.deadreckoning.domain.model

import org.osmdroid.util.GeoPoint

data class RouteInfo(
    val sourceName: String = "Current Location",
    val destinationName: String = "Select a destination",
    val sourcePoint: GeoPoint = GeoPoint(0.0, 0.0),
    val destinationPoint: GeoPoint = GeoPoint(0.0, 0.0),
    val routePoints: List<GeoPoint> = emptyList(),
    val totalDistanceKm: Double = 0.0,
    val estimatedTimeMinutes: Int = 0,
    val nextManeuver: String = "Choose a destination after location is available",
    val distanceToNextManeuverMeters: Int = 0,
    val maneuverIconType: ManeuverIconType = ManeuverIconType.STRAIGHT
)

enum class ManeuverIconType {
    STRAIGHT,
    LEFT,
    RIGHT,
    SLIGHT_LEFT,
    SLIGHT_RIGHT,
    UTURN,
    ARRIVED
}
