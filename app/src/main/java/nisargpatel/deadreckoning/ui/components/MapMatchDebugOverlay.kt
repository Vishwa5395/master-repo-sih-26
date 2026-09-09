package nisargpatel.deadreckoning.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.Color as AndroidColor
import nisargpatel.deadreckoning.data.RoadCandidate
import nisargpatel.deadreckoning.domain.state.MapMatchDebugState
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Renders live visual debug overlays for HMM map-matching on an OSMDroid [MapView].
 *
 * Visual cues:
 * - Matched candidate road: Solid vibrant green Polyline (14 dp) along road tangent + green dot Marker
 * - Rejected candidates: Dimmer translucent orange Polylines (6 dp) + amber dot Markers
 * - Projection offset: Cyan connector line from raw GPS fix to snapped road position
 *
 * All overlays carry IDs prefixed with [OVERLAY_PREFIX] and are cleanly wiped each update
 * or when the debug toggle is switched off.
 */
object MapMatchDebugOverlay {

    private const val OVERLAY_PREFIX = "hmm_debug_"
    private const val MATCHED_POLYLINE_ID = "${OVERLAY_PREFIX}matched_road"
    private const val MATCHED_MARKER_ID = "${OVERLAY_PREFIX}matched_marker"
    private const val PROJECTION_LINE_ID = "${OVERLAY_PREFIX}projection_line"

    private var matchedMarkerDrawableCache: Drawable? = null
    private var candidateMarkerDrawableCache: Drawable? = null

    fun updateOverlay(
        mapView: MapView,
        debugState: MapMatchDebugState?,
        isVisible: Boolean
    ) {
        // Unconditionally remove any stale HMM debug overlays
        clearOverlays(mapView)

        if (!isVisible || debugState == null) {
            mapView.invalidate()
            return
        }

        val context = mapView.context
        val matched = debugState.matchedCandidate
        val allCandidates = debugState.allCandidates
        val rawPos = debugState.rawPosition

        // 1. Render rejected candidates (thinner, translucent orange)
        val rejectedCandidates = if (matched != null) {
            allCandidates.filterNot { it.wayId == matched.wayId && it.point == matched.point }
        } else {
            allCandidates
        }

        rejectedCandidates.forEachIndexed { index, candidate ->
            // Road tangent polyline
            val tangentPoints = computeRoadTangent(candidate.point, candidate.bearingDegrees, 25.0)
            val candidatePolyline = Polyline().apply {
                id = "${OVERLAY_PREFIX}candidate_road_$index"
                outlinePaint.color = AndroidColor.parseColor("#90FF9800") // Translucent orange
                outlinePaint.strokeWidth = 6.0f
                setPoints(tangentPoints)
            }
            mapView.overlays.add(candidatePolyline)

            // Candidate position marker
            val candidateMarker = Marker(mapView).apply {
                id = "${OVERLAY_PREFIX}candidate_marker_$index"
                position = candidate.point
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = getCandidateMarkerDrawable(context)
                title = candidate.roadName
                snippet = "Candidate (dist: ${"%.1f".format(candidate.distanceMeters)}m, wayId: ${candidate.wayId})"
            }
            mapView.overlays.add(candidateMarker)
        }

        // 2. Render matched candidate (thick vibrant green)
        if (matched != null) {
            val matchedTangent = computeRoadTangent(matched.point, matched.bearingDegrees, 35.0)
            val matchedPolyline = Polyline().apply {
                id = MATCHED_POLYLINE_ID
                outlinePaint.color = AndroidColor.parseColor("#00E676") // Vibrant green
                outlinePaint.strokeWidth = 14.0f
                setPoints(matchedTangent)
            }
            mapView.overlays.add(matchedPolyline)

            val matchedMarker = Marker(mapView).apply {
                id = MATCHED_MARKER_ID
                position = matched.point
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = getMatchedMarkerDrawable(context)
                title = "${matched.roadName} (MATCHED)"
                val conf = debugState.confidence ?: 0
                snippet = "HMM Match: ${"%.1f".format(matched.distanceMeters)}m offset | Conf: $conf% | wayId: ${matched.wayId}"
            }
            mapView.overlays.add(matchedMarker)

            // 3. Render projection line from raw GPS position to matched candidate
            if (rawPos != null && (rawPos.latitude != 0.0 || rawPos.longitude != 0.0)) {
                val projLine = Polyline().apply {
                    id = PROJECTION_LINE_ID
                    outlinePaint.color = AndroidColor.parseColor("#00E5FF") // Cyan
                    outlinePaint.strokeWidth = 4.0f
                    setPoints(listOf(rawPos, matched.point))
                }
                mapView.overlays.add(projLine)
            }
        }

        mapView.invalidate()
    }

    private fun clearOverlays(mapView: MapView) {
        mapView.overlays.removeAll(
            mapView.overlays.filter { overlay ->
                (overlay is Polyline && overlay.id?.startsWith(OVERLAY_PREFIX) == true) ||
                    (overlay is Marker && overlay.id?.startsWith(OVERLAY_PREFIX) == true)
            }
        )
    }

    /**
     * Compute a road tangent line extending [halfLengthMeters] in both directions
     * along [bearingDegrees] through [center].
     */
    private fun computeRoadTangent(
        center: GeoPoint,
        bearingDegrees: Double,
        halfLengthMeters: Double
    ): List<GeoPoint> {
        val forward = offsetPoint(center, halfLengthMeters, bearingDegrees)
        val backward = offsetPoint(center, halfLengthMeters, (bearingDegrees + 180.0) % 360.0)
        return listOf(backward, center, forward)
    }

    private fun offsetPoint(point: GeoPoint, distanceMeters: Double, bearingDegrees: Double): GeoPoint {
        val radius = 6378137.0 // Earth radius in meters
        val distRatio = distanceMeters / radius
        val radBearing = Math.toRadians(bearingDegrees)
        val radLat = Math.toRadians(point.latitude)
        val radLon = Math.toRadians(point.longitude)
        val newLat = Math.asin(
            Math.sin(radLat) * Math.cos(distRatio) +
                Math.cos(radLat) * Math.sin(distRatio) * Math.cos(radBearing)
        )
        val newLon = radLon + Math.atan2(
            Math.sin(radBearing) * Math.sin(distRatio) * Math.cos(radLat),
            Math.cos(distRatio) - Math.sin(radLat) * Math.sin(newLat)
        )
        return GeoPoint(Math.toDegrees(newLat), Math.toDegrees(newLon))
    }

    private fun getMatchedMarkerDrawable(context: Context): Drawable {
        matchedMarkerDrawableCache?.let { return it }
        val size = 44
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Drop shadow
        paint.color = AndroidColor.parseColor("#50000000")
        canvas.drawCircle(size / 2f, size / 2f + 2f, 18f, paint)

        // Outer vibrant green ring
        paint.color = AndroidColor.parseColor("#00E676")
        canvas.drawCircle(size / 2f, size / 2f, 16f, paint)

        // White inner core
        paint.color = AndroidColor.WHITE
        canvas.drawCircle(size / 2f, size / 2f, 7f, paint)

        return BitmapDrawable(context.resources, bitmap).also { matchedMarkerDrawableCache = it }
    }

    private fun getCandidateMarkerDrawable(context: Context): Drawable {
        candidateMarkerDrawableCache?.let { return it }
        val size = 32
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Drop shadow
        paint.color = AndroidColor.parseColor("#30000000")
        canvas.drawCircle(size / 2f, size / 2f + 1f, 12f, paint)

        // Outer translucent amber ring
        paint.color = AndroidColor.parseColor("#D0FFA000")
        canvas.drawCircle(size / 2f, size / 2f, 11f, paint)

        // Darker amber center
        paint.color = AndroidColor.parseColor("#E65100")
        canvas.drawCircle(size / 2f, size / 2f, 5f, paint)

        return BitmapDrawable(context.resources, bitmap).also { candidateMarkerDrawableCache = it }
    }
}
