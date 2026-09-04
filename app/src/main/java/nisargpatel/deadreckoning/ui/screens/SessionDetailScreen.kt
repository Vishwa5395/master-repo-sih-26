package nisargpatel.deadreckoning.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nisargpatel.deadreckoning.domain.state.NavigationSession
import nisargpatel.deadreckoning.ui.components.CommandPanel
import nisargpatel.deadreckoning.ui.components.DataRow
import nisargpatel.deadreckoning.ui.components.DividerLine
import nisargpatel.deadreckoning.ui.components.MetricCard
import nisargpatel.deadreckoning.ui.components.SectionLabel
import nisargpatel.deadreckoning.ui.theme.*

@Composable
fun SessionDetailScreen(
    session: NavigationSession,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AutomotiveDarkBg)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = PrimaryBlue)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(text = "Trip Detail", color = PrimaryBlue, fontWeight = FontWeight.Black, fontSize = 21.sp)
                Text(text = session.dateString, color = TextSecondary, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Session Overview Metrics Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "Distance",
                value = "${session.distanceKm} km",
                valueColor = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Duration",
                value = session.durationString,
                valueColor = TextPrimary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "GNSS Outages",
                value = "${session.outageCount} Outages",
                valueColor = WarningAmber,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "DR Active Time",
                value = "${session.drDurationSeconds}s",
                valueColor = WarningAmber,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Session Trajectory Preview Placeholder Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            shape = RoundedCornerShape(8.dp),
            color = AutomotiveCardBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, AutomotiveCardBorder)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.Default.Map, contentDescription = "Session Map", tint = PrimaryBlue.copy(alpha = 0.3f), modifier = Modifier.size(56.dp))
                Text(text = "Trip trajectory preview", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        CommandPanel(color = RoadInk, borderColor = DividerSoft) {
            SectionLabel("Error summary")
            DataRow("Max error", "${session.maxErrorMeters} m", WarningAmber)
            DividerLine()
            DataRow("Average error", "${session.avgErrorMeters} m")
            DataRow("Status", session.status, SuccessGreen)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Export Session Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Download, contentDescription = "Export GPX", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Export GPX", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = { },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Download, contentDescription = "Export CSV", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Export CSV", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
