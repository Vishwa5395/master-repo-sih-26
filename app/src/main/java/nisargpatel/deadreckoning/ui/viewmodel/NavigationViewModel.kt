package nisargpatel.deadreckoning.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nisargpatel.deadreckoning.domain.model.RouteInfo
import nisargpatel.deadreckoning.domain.repository.NavigationRepository
import nisargpatel.deadreckoning.domain.state.*
import nisargpatel.deadreckoning.util.OSRMRouteFetcher
import org.osmdroid.util.GeoPoint

class NavigationViewModel(
    private val repository: NavigationRepository
) : ViewModel() {

    val navigationState: StateFlow<NavigationState> = repository.navigationState
    val gnssState: StateFlow<GNSSState> = repository.gnssState
    val aiState: StateFlow<AIState> = repository.aiState
    val sensorState: StateFlow<SensorState> = repository.sensorState
    val mapState: StateFlow<MapState> = repository.mapState
    val mapMatchingState: StateFlow<MapMatchingState> = repository.mapMatchingState
    val events: SharedFlow<NavigationEvent> = repository.navigationEvents

    private val _selectedRoute = MutableStateFlow(RouteInfo())
    val selectedRoute: StateFlow<RouteInfo> = _selectedRoute.asStateFlow()

    fun startNavigation() = repository.startNavigation()
    fun stopNavigation() = repository.stopNavigation()
    fun startGnssMonitoring() = repository.startGnssMonitoring()

    fun selectDestination(name: String, destinationPoint: GeoPoint) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentNav = navigationState.value
            if (currentNav.latitude == 0.0 && currentNav.longitude == 0.0) {
                val pendingRoute = RouteInfo(
                    destinationName = name,
                    destinationPoint = destinationPoint,
                    nextManeuver = "Start navigation to acquire your location"
                )
                _selectedRoute.value = pendingRoute
                repository.setActiveRoute(pendingRoute)
                return@launch
            }
            val sourcePoint = GeoPoint(currentNav.latitude, currentNav.longitude)
            val onlineRoute = OSRMRouteFetcher.fetchRoute(sourcePoint, destinationPoint, name)
            val route = if (onlineRoute.routePoints.size > 1) onlineRoute else
                repository.findOfflineRoute(sourcePoint, destinationPoint, name) ?: onlineRoute
            _selectedRoute.value = route
            repository.setActiveRoute(route)
        }
    }
}
