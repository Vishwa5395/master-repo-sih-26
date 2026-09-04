package nisargpatel.deadreckoning.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nisargpatel.deadreckoning.ui.theme.*
import nisargpatel.deadreckoning.ui.components.*

@Composable
fun SettingsScreen(
    onNavigateToTechnicalScreen: (String) -> Unit
) {
    var voiceGuidance by remember { mutableStateOf(true) }
    var autoRecenter by remember { mutableStateOf(true) }
    var mapMatching by remember { mutableStateOf(true) }

    CommandScreen {
        PageHeader(
            title = "System Console",
            subtitle = "Navigation preferences and engineering panels",
            icon = Icons.Default.Tune
        )

        SectionLabel("Engineering panels", color = PurpleAI)
        TechnicalSubNavCard(title = "Sensors & IMU Dashboard", subtitle = "Live 3-axis accel, gyro, mag readings", icon = Icons.Default.Sensors, onClick = { onNavigateToTechnicalScreen("sensors") })
        TechnicalSubNavCard(title = "GNSS Monitoring", subtitle = "Satellites view, fix status & HDOP", icon = Icons.Default.SatelliteAlt, onClick = { onNavigateToTechnicalScreen("gnss") })
        TechnicalSubNavCard(title = "Dead Reckoning Engine", subtitle = "Vehicle INS coordinates & position drift", icon = Icons.Default.DirectionsCar, onClick = { onNavigateToTechnicalScreen("dead_reckoning") })
        TechnicalSubNavCard(title = "Map Matching Engine", subtitle = "Road candidate probabilities & snaps", icon = Icons.Default.AltRoute, onClick = { onNavigateToTechnicalScreen("map_matching") })
        TechnicalSubNavCard(title = "Trajectory Comparison", subtitle = "GNSS vs DR vs Map-Matched path overlay", icon = Icons.Default.Polyline, onClick = { onNavigateToTechnicalScreen("trajectory") })
        TechnicalSubNavCard(title = "Offline Vector Maps", subtitle = "Map region download management", icon = Icons.Default.Map, onClick = { onNavigateToTechnicalScreen("offline_maps") })
        TechnicalSubNavCard(title = "Developer Diagnostics", subtitle = "Latency, FPS, Memory & Frequency telemetry", icon = Icons.Default.BugReport, onClick = { onNavigateToTechnicalScreen("diagnostics") })

        SectionLabel("Navigation preferences")
        SettingSwitchRow(title = "Voice Guidance", subtitle = "Audible navigation turn instructions", checked = voiceGuidance, onCheckedChange = { voiceGuidance = it })
        SettingSwitchRow(title = "Auto Recenter Map", subtitle = "Keep vehicle centered during active navigation", checked = autoRecenter, onCheckedChange = { autoRecenter = it })

        SectionLabel("Dead reckoning")
        SettingSwitchRow(title = "Vector Map Matching", subtitle = "Constrain DR positions onto road network", checked = mapMatching, onCheckedChange = { mapMatching = it })
        CommandPanel(color = RoadInk, borderColor = DividerSoft) {
            DataRow("Outage model", "V8 on-device", PurpleAI)
            DataRow("Primary fusion", "EKF + road matching", SuccessGreen)
            DataRow("Offline mode", "Cached tiles + imported OSM graph", PrimaryBlue)
        }
    }
}

@Composable
private fun TechnicalSubNavCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
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
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(text = subtitle, color = TextSecondary, fontSize = 11.sp)
            }
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Navigate", tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = AutomotiveCardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, AutomotiveCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(text = subtitle, color = TextSecondary, fontSize = 11.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedThumbColor = PrimaryBlue)
            )
        }
    }
}
