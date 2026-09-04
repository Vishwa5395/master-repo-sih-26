package nisargpatel.deadreckoning.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nisargpatel.deadreckoning.ui.components.*
import nisargpatel.deadreckoning.ui.theme.*
import nisargpatel.deadreckoning.ui.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStartNavClicked: () -> Unit
) {
    val navState by viewModel.navigationState.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()
    val gnssState by viewModel.gnssState.collectAsState()
    val aiState by viewModel.aiState.collectAsState()
    val sensorState by viewModel.sensorState.collectAsState()
    val lastSession = sessionState.sessions.firstOrNull()

    CommandScreen {
        PageHeader(
            title = "SIH NavCore",
            subtitle = "GNSS, IMU, road graph, and outage recovery",
            icon = Icons.Default.Route,
            trailing = { ModeIndicator(mode = navState.mode) }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SpeedIndicator(speedKmh = navState.speedKmh, modifier = Modifier.weight(1f))
            HeadingIndicator(headingDegrees = navState.headingDegrees, modifier = Modifier.weight(1f))
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onStartNavClicked() }
                .shadow(2.dp, shape = UberCardShape),
            shape = UberCardShape,
            color = PanelRaised,
            border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.MyLocation, contentDescription = "Set destination", tint = PrimaryBlue, modifier = Modifier.size(26.dp))
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Plan route", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Text(
                        text = if (navState.latitude == 0.0 && navState.longitude == 0.0) "Tap to enter destination coordinates" else String.format("Current %.4f, %.4f", navState.latitude, navState.longitude),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Go", tint = SuccessGreen, modifier = Modifier.size(24.dp))
            }
        }

        CommandPanel {
            SectionLabel("Readiness")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(text = if (gnssState.isAvailable) "GNSS fix" else "GNSS waiting", color = if (gnssState.isAvailable) SuccessGreen else WarningAmber)
                StatusPill(text = if (sensorState.isAccelAvailable && sensorState.isGyroAvailable) "IMU live" else "IMU unavailable", color = if (sensorState.isAccelAvailable && sensorState.isGyroAvailable) SuccessGreen else ErrorRed)
                StatusPill(text = if (aiState.isModelLoaded) "V8 loaded" else "V8 offline", color = if (aiState.isModelLoaded) PurpleAI else ErrorRed)
            }
            DividerLine()
            DataRow(
                label = "Position accuracy",
                value = if (navState.accuracyMeters > 0.0) String.format("%.1f m", navState.accuracyMeters) else "Awaiting fix"
            )
            DataRow(label = "Model build", value = aiState.modelVersion, valueColor = if (aiState.isModelLoaded) PurpleAI else TextSecondary)
            DataRow(label = "Mount stability", value = "${sensorState.mountStabilityPercentage}%", valueColor = if (sensorState.mountStabilityPercentage >= 75) SuccessGreen else WarningAmber)
        }

        CommandPanel(color = RoadInk, borderColor = DividerSoft) {
            SectionLabel("Vehicle signal")
            RouteStrip {
                Icon(imageVector = Icons.Default.Timeline, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "AI speed", color = TextSecondary, fontSize = 11.sp)
                        Text(text = String.format("%.1f km/h", aiState.predictedSpeedKmh), color = PurpleAI, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "Motion", color = TextSecondary, fontSize = 11.sp)
                        Text(text = aiState.motionClassification, color = SuccessGreen, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                }
            }
        }

        Button(
            onClick = {
                viewModel.startNavigation()
                onStartNavClicked()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(10.dp, shape = CircleShape),
            colors = ButtonDefaults.buttonColors(containerColor = PurpleAI),
            shape = CircleShape
        ) {
            Icon(imageVector = Icons.Default.Navigation, contentDescription = "Start Nav", tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Start journey", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
        }

        if (lastSession != null) {
            CommandPanel {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.History, contentDescription = "History", tint = PrimaryBlue, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        SectionLabel("Last trip")
                        Text(text = "${lastSession.distanceKm} km  ${lastSession.durationString}", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    }
                    StatusPill(text = lastSession.status, color = SuccessGreen)
                }
            }
        }
    }
}
