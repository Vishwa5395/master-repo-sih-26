package nisargpatel.deadreckoning.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import nisargpatel.deadreckoning.domain.repository.NavigationRepository
import nisargpatel.deadreckoning.domain.state.SensorState

class SensorsViewModel(
    private val repository: NavigationRepository
) : ViewModel() {

    val sensorState: StateFlow<SensorState> = repository.sensorState
}
