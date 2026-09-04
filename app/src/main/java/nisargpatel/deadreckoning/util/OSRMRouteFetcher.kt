package nisargpatel.deadreckoning.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nisargpatel.deadreckoning.domain.model.ManeuverIconType
import nisargpatel.deadreckoning.domain.model.RouteInfo
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "OSRMRouteFetcher"

object OSRMRouteFetcher {

    suspend fun fetchRoute(
        start: GeoPoint,
        end: GeoPoint,
        destinationName: String
    ): RouteInfo = withContext(Dispatchers.IO) {
        val urlString = "https://router.project-osrm.org/route/v1/driving/${start.longitude},${start.latitude};${end.longitude},${end.latitude}?overview=full&geometries=geojson&steps=true"
        try {
            Log.i(TAG, "Fetching real street route from OSRM: $urlString")
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "DeadReckoningPro/1.0 (Android)")
            }

            if (connection.responseCode == 200) {
                val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonText)
                val routes = root.getJSONArray("routes")
                if (routes.length() > 0) {
                    val firstRoute = routes.getJSONObject(0)
                    val distanceMeters = firstRoute.getDouble("distance")
                    val durationSeconds = firstRoute.getDouble("duration")

                    val distanceKm = Math.round((distanceMeters / 1000.0) * 10.0) / 10.0
                    val durationMins = Math.max(1, Math.round(durationSeconds / 60.0).toInt())

                    val geometry = firstRoute.getJSONObject("geometry")
                    val coords = geometry.getJSONArray("coordinates")
                    val points = mutableListOf<GeoPoint>()

                    for (i in 0 until coords.length()) {
                        val pt = coords.getJSONArray(i)
                        val lon = pt.getDouble(0)
                        val lat = pt.getDouble(1)
                        points.add(GeoPoint(lat, lon))
                    }

                    // Turn maneuver parsing
                    var maneuverText = "Head toward $destinationName"
                    var iconType = ManeuverIconType.STRAIGHT

                    val legs = firstRoute.getJSONArray("legs")
                    if (legs.length() > 0) {
                        val steps = legs.getJSONObject(0).getJSONArray("steps")
                        if (steps.length() > 1) {
                            val nextStep = steps.getJSONObject(1)
                            val stepName = nextStep.optString("name", destinationName)
                            val maneuver = nextStep.optJSONObject("maneuver")
                            val modifier = maneuver?.optString("modifier", "") ?: ""

                            iconType = when {
                                modifier.contains("right") -> ManeuverIconType.RIGHT
                                modifier.contains("left") -> ManeuverIconType.LEFT
                                modifier.contains("slight right") -> ManeuverIconType.SLIGHT_RIGHT
                                modifier.contains("slight left") -> ManeuverIconType.SLIGHT_LEFT
                                modifier.contains("uturn") -> ManeuverIconType.UTURN
                                else -> ManeuverIconType.STRAIGHT
                            }

                            val streetLabel = if (stepName.isNotEmpty()) stepName else destinationName
                            maneuverText = when (iconType) {
                                ManeuverIconType.RIGHT -> "Turn Right onto $streetLabel"
                                ManeuverIconType.LEFT -> "Turn Left onto $streetLabel"
                                ManeuverIconType.SLIGHT_RIGHT -> "Bear Right onto $streetLabel"
                                ManeuverIconType.SLIGHT_LEFT -> "Bear Left onto $streetLabel"
                                ManeuverIconType.UTURN -> "Make U-Turn onto $streetLabel"
                                else -> "Continue straight on $streetLabel"
                            }
                        }
                    }

                    Log.i(TAG, "Successfully loaded OSRM street route with ${points.size} waypoints ($distanceKm km, $durationMins min)")
                    return@withContext RouteInfo(
                        sourceName = "Current Location",
                        destinationName = destinationName,
                        sourcePoint = start,
                        destinationPoint = end,
                        routePoints = points,
                        totalDistanceKm = distanceKm,
                        estimatedTimeMinutes = durationMins,
                        nextManeuver = maneuverText,
                        maneuverIconType = iconType
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "OSRM routing failed", e)
        }

        return@withContext RouteInfo(
            sourceName = "Current Location",
            destinationName = destinationName,
            sourcePoint = start,
            destinationPoint = end,
            nextManeuver = "Route unavailable. Check your connection."
        )
    }
}
