package nisargpatel.deadreckoning.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import nisargpatel.deadreckoning.domain.state.NavigationSession
import nisargpatel.deadreckoning.domain.state.AnalyticsState

/** Persistent history for completed navigation sessions and their measured metrics. */
class NavigationHistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences("navigation_history", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val sessionsType = object : TypeToken<List<NavigationSession>>() {}.type

    fun load(): List<NavigationSession> = runCatching {
        gson.fromJson<List<NavigationSession>>(preferences.getString("sessions", "[]"), sessionsType) ?: emptyList()
    }.getOrDefault(emptyList())

    fun save(sessions: List<NavigationSession>) {
        preferences.edit().putString("sessions", gson.toJson(sessions.take(25))).apply()
    }

    fun aggregate(sessions: List<NavigationSession>): AnalyticsState {
        if (sessions.isEmpty()) return AnalyticsState()
        val totalDistance = sessions.sumOf { it.distanceKm }
        val totalOutage = sessions.sumOf { it.drDurationSeconds }
        return AnalyticsState(
            totalDistanceKm = totalDistance,
            totalDurationSeconds = sessions.sumOf { parseDurationSeconds(it.durationString) },
            outageCount = sessions.sumOf { it.outageCount },
            totalOutageDurationSeconds = totalOutage,
            averageDriftMeters = sessions.map { it.avgErrorMeters }.average(),
            maxDriftMeters = sessions.maxOf { it.maxErrorMeters },
            positionErrorMeters = sessions.map { it.avgErrorMeters }.average()
        )
    }

    private fun parseDurationSeconds(value: String): Long = value.split(":").let {
        (it.getOrNull(0)?.toLongOrNull() ?: 0L) * 60 + (it.getOrNull(1)?.toLongOrNull() ?: 0L)
    }
}
