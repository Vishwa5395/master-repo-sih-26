package nisargpatel.deadreckoning.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Top-Down Vehicle Car Marker helper for OSMDroid MapView.
 * Renders a sleek 3D top-down car icon that rotates according to heading degrees
 * and animates along GPS / GNSS / AI Dead Reckoning coordinates.
 */
object UberVehicleMarker {

    private var carDrawableCache: Drawable? = null

    fun updateVehicleMarker(
        mapView: MapView,
        position: GeoPoint,
        headingDegrees: Double
    ) {
        val context = mapView.context
        if (carDrawableCache == null) {
            carDrawableCache = createVehicleCarBitmap(context)
        }

        val existingMarker = mapView.overlays.filterIsInstance<Marker>().firstOrNull { it.id == "uber_vehicle_car_marker" }
        val carMarker = existingMarker ?: Marker(mapView).also { marker ->
            marker.id = "uber_vehicle_car_marker"
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            marker.icon = carDrawableCache
            marker.title = "Vehicle Position"
            mapView.overlays.add(marker)
        }

        carMarker.position = position
        carMarker.rotation = headingDegrees.toFloat()
        mapView.invalidate()
    }

    private fun createVehicleCarBitmap(context: Context): Drawable {
        val width = 64
        val height = 110
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Shadow under car
        paint.color = Color.parseColor("#40000000")
        canvas.drawRoundRect(8f, 12f, width - 8f, height - 4f, 16f, 16f, paint)

        // Car Body - Dark Metallic Charcoal / Uber Blue Accent
        paint.color = Color.parseColor("#121212")
        canvas.drawRoundRect(10f, 10f, width - 10f, height - 10f, 18f, 18f, paint)

        // Car Roof / Top
        paint.color = Color.parseColor("#276EF1")
        canvas.drawRoundRect(16f, 28f, width - 16f, height - 28f, 12f, 12f, paint)

        // Front Windshield
        paint.color = Color.parseColor("#80E0F7")
        val frontGlass = Path().apply {
            moveTo(18f, 32f)
            lineTo(width - 18f, 32f)
            lineTo(width - 20f, 44f)
            lineTo(20f, 44f)
            close()
        }
        canvas.drawPath(frontGlass, paint)

        // Headlights - Glowing Mint Green
        paint.color = Color.parseColor("#10B981")
        canvas.drawCircle(18f, 14f, 5f, paint)
        canvas.drawCircle(width - 18f, 14f, 5f, paint)

        // Rear Taillights - Bright Red
        paint.color = Color.parseColor("#EF4444")
        canvas.drawRect(16f, height - 14f, 26f, height - 10f, paint)
        canvas.drawRect(width - 26f, height - 14f, width - 16f, height - 10f, paint)

        return BitmapDrawable(context.resources, bitmap)
    }
}
