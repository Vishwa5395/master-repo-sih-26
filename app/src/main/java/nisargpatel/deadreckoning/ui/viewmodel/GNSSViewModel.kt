package nisargpatel.deadreckoning.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import nisargpatel.deadreckoning.domain.repository.NavigationRepository
import nisargpatel.deadreckoning.domain.state.GNSSState

class GNSSViewModel(
    private val repository: NavigationRepository
) : ViewModel() {

    val gnssState: StateFlow<GNSSState> = repository.gnssState
}
