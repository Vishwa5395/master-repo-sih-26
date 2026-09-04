package nisargpatel.deadreckoning.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.RequiresApi
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import nisargpatel.deadreckoning.core.gnss.GnssFix
import nisargpatel.deadreckoning.domain.state.GNSSState

/**
 * Bridges the platform location APIs into a [GnssFix] the portable quality monitor can
 * assess.
 *
 * This adapter deliberately makes no availability decision of its own. Previously it set
 * `isAvailable = true` for any delivered location and never set it false, so a coarse
 * network fix looked identical to a tight satellite fix. Judgement now belongs to
 * [nisargpatel.deadreckoning.core.gnss.GnssQualityMonitor]; this class only reports what
 * the platform actually told us, including what it declined to tell us.
 */
class LocationAdapter(context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _gnssState = MutableStateFlow(GNSSState())
    val gnssState: StateFlow<GNSSState> = _gnssState.asStateFlow()

    /** Raw observations for the quality monitor. Replay 1 so a late collector still sees the latest. */
    private val _fixes = MutableSharedFlow<GnssFix>(replay = 1, extraBufferCapacity = 8)
    val fixes: SharedFlow<GnssFix> = _fixes.asSharedFlow()

    /** Satellite totals from the GNSS engine, which the fused provider does not expose. */
    @Volatile
    private var satellitesVisible: Int? = null

    @Volatile
    private var satellitesUsedInFix: Int? = null

    private val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
        .setMinUpdateIntervalMillis(500L)
        .build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            handleLocation(location)
        }
    }

    /**
     * Created lazily because [GnssStatus.Callback] only exists from API 24 and this app
     * supports API 23. The explicit type matters: an anonymous object initialiser infers
     * its own type and will not satisfy the platform's parameter.
     */
    private var gnssStatusCallback: GnssStatus.Callback? = null

    @RequiresApi(Build.VERSION_CODES.N)
    private fun createGnssStatusCallback(): GnssStatus.Callback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            satellitesVisible = status.satelliteCount
            satellitesUsedInFix = (0 until status.satelliteCount).count { status.usedInFix(it) }
        }

        override fun onStopped() {
            satellitesVisible = null
            satellitesUsedInFix = null
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val callback = gnssStatusCallback ?: createGnssStatusCallback().also { gnssStatusCallback = it }
                locationManager?.registerGnssStatusCallback(callback, null)
            }
        } catch (e: Exception) {
            _gnssState.value = _gnssState.value.copy(isAvailable = false, fixStatus = "NO PERMISSION")
        }
    }

    fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            gnssStatusCallback?.let { callback ->
                runCatching { locationManager?.unregisterGnssStatusCallback(callback) }
            }
        }
        satellitesVisible = null
        satellitesUsedInFix = null
    }

    private fun handleLocation(location: Location) {
        val fix = toGnssFix(location)

        // Reported verbatim. The quality monitor decides what any of it means.
        _gnssState.value = _gnssState.value.copy(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = fix.horizontalAccuracyMeters,
            speedKmh = fix.speedMps * 3.6,
            bearingDegrees = fix.bearingDegrees,
            satelliteCount = fix.satelliteCount ?: 0,
            fixAgeMillis = fix.ageMillis,
            provider = location.provider ?: "unknown",
            isFromMockProvider = fix.isFromMockProvider
        )

        _fixes.tryEmit(fix)
    }

    private fun toGnssFix(location: Location): GnssFix {
        // elapsedRealtimeNanos is monotonic and immune to wall-clock changes, which makes
        // it the only trustworthy basis for fix age.
        val ageMillis = ((SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000L)
            .coerceAtLeast(0L)

        val speedAccuracy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasSpeedAccuracy()) {
            location.speedAccuracyMetersPerSecond.toDouble()
        } else {
            null
        }
        val bearingAccuracy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasBearingAccuracy()) {
            location.bearingAccuracyDegrees.toDouble()
        } else {
            null
        }

        return GnssFix(
            latitude = location.latitude,
            longitude = location.longitude,
            horizontalAccuracyMeters = if (location.hasAccuracy()) location.accuracy.toDouble() else Double.NaN,
            speedMps = if (location.hasSpeed()) location.speed.toDouble() else 0.0,
            bearingDegrees = if (location.hasBearing()) location.bearing.toDouble() else 0.0,
            ageMillis = ageMillis,
            speedAccuracyMps = speedAccuracy,
            bearingAccuracyDegrees = bearingAccuracy,
            satelliteCount = satellitesVisible,
            usedInFixSatelliteCount = satellitesUsedInFix,
            provider = location.provider,
            isFromMockProvider = location.isFromMockProvider
        )
    }
}
