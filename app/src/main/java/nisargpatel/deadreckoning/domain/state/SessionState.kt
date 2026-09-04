package nisargpatel.deadreckoning.domain.state

data class NavigationSession(
    val id: String,
    val dateString: String,
    val durationString: String,
    val distanceKm: Double,
    val outageCount: Int,
    val drDurationSeconds: Long,
    val maxErrorMeters: Double,
    val avgErrorMeters: Double,
    val status: String
)

data class SessionState(
    val sessions: List<NavigationSession> = emptyList(),
    val selectedSession: NavigationSession? = null,
    val isLoading: Boolean = false
)
