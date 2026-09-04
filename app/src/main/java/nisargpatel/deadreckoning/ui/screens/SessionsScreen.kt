package nisargpatel.deadreckoning.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nisargpatel.deadreckoning.domain.state.NavigationSession
import nisargpatel.deadreckoning.ui.components.CommandPanel
import nisargpatel.deadreckoning.ui.components.PageHeader
import nisargpatel.deadreckoning.ui.components.SectionLabel
import nisargpatel.deadreckoning.ui.components.StatusPill
import nisargpatel.deadreckoning.ui.theme.*
import nisargpatel.deadreckoning.ui.viewmodel.SessionsViewModel

@Composable
fun SessionsScreen(
    viewModel: SessionsViewModel,
    onSessionSelected: (NavigationSession) -> Unit
) {
    val sessionState by viewModel.sessionState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AutomotiveDarkBg)
            .padding(16.dp)
    ) {
        PageHeader(
            title = "Trip Archive",
            subtitle = "Recorded routes and dead reckoning telemetry",
            icon = Icons.Default.History
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (sessionState.sessions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CommandPanel {
                    SectionLabel("No trips yet")
                    Text(text = "Start navigation to save a trip session.", color = TextSecondary, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sessionState.sessions) { session ->
                    SessionCardItem(session = session, onClick = { onSessionSelected(session) })
                }
            }
        }
    }
}

@Composable
private fun SessionCardItem(
    session: NavigationSession,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = AutomotiveCardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, AutomotiveCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Default.DirectionsCar, contentDescription = "Trip", tint = PrimaryBlue, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = session.dateString, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(text = "${session.distanceKm} km • ${session.durationString} • Outages: ${session.outageCount}", color = TextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "Max Error: ${session.maxErrorMeters}m • Avg Error: ${session.avgErrorMeters}m", color = WarningAmber, fontSize = 11.sp)
            }
            StatusPill(text = session.status, color = SuccessGreen)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Details", tint = TextSecondary)
        }
    }
}
