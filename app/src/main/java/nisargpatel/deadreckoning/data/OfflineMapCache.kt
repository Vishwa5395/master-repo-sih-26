package nisargpatel.deadreckoning.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import kotlin.math.cos

data class OfflineMapCacheState(
    val cachedBytes: Long = 0L,
    val isDownloading: Boolean = false,
    val downloadedTiles: Int = 0,
    val totalTiles: Int = 0,
    val message: String = "No area cached in this session"
)

/** Downloads standard OSM tiles into osmdroid's persistent cache for offline reuse. */
class OfflineMapCache(context: Context) {
    private val appContext = context.applicationContext
    private val mapView = MapView(appContext).apply { setTileSource(TileSourceFactory.MAPNIK) }
    private val tileWriter = SqlTileWriter()
    private val cacheManager = CacheManager(mapView, tileWriter)
    private val _state = MutableStateFlow(OfflineMapCacheState(cachedBytes = cacheManager.currentCacheUsage()))
    val state: StateFlow<OfflineMapCacheState> = _state.asStateFlow()

    fun cacheAround(center: GeoPoint, radiusMeters: Double = 1_000.0) {
        if (_state.value.isDownloading) return
        val latitudeDelta = radiusMeters / 111_111.0
        val longitudeDelta = radiusMeters / (111_111.0 * cos(Math.toRadians(center.latitude)))
        val area = BoundingBox(
            center.latitude + latitudeDelta,
            center.longitude + longitudeDelta,
            center.latitude - latitudeDelta,
            center.longitude - longitudeDelta
        )
        _state.value = _state.value.copy(isDownloading = true, downloadedTiles = 0, totalTiles = 0, message = "Preparing offline tiles")
        cacheManager.downloadAreaAsyncNoUI(appContext, area, 14, 16, object : CacheManager.CacheManagerCallback {
            override fun downloadStarted() = Unit

            override fun setPossibleTilesInArea(total: Int) {
                _state.value = _state.value.copy(totalTiles = total)
            }

            override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
                _state.value = _state.value.copy(downloadedTiles = progress, message = "Caching zoom $currentZoomLevel")
            }

            override fun onTaskComplete() {
                _state.value = _state.value.copy(
                    cachedBytes = cacheManager.currentCacheUsage(),
                    isDownloading = false,
                    message = "Area available offline"
                )
            }

            override fun onTaskFailed(errors: Int) {
                _state.value = _state.value.copy(isDownloading = false, message = "Tile download failed ($errors errors)")
            }
        })
    }

    fun clearCache() {
        if (_state.value.isDownloading) return
        tileWriter.purgeCache()
        _state.value = OfflineMapCacheState(cachedBytes = cacheManager.currentCacheUsage(), message = "Offline tile cache cleared")
    }
}
