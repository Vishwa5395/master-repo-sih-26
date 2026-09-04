package nisargpatel.deadreckoning.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import nisargpatel.deadreckoning.domain.repository.NavigationRepository
import nisargpatel.deadreckoning.domain.state.SessionState

class SessionsViewModel(
    private val repository: NavigationRepository
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = repository.sessionState
}
