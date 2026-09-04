package nisargpatel.deadreckoning.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nisargpatel.deadreckoning.ui.theme.*
import nisargpatel.deadreckoning.ui.components.*

@Composable
fun PermissionScreen(
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    var locationGranted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> locationGranted = granted }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AutomotiveDarkBg)
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(modifier = Modifier.height(10.dp))
            PageHeader(
                title = "Access Setup",
                subtitle = "Permissions needed for continuous vehicle navigation",
                icon = Icons.Default.LocationOn
            )

            PermissionCard(
                title = "Location",
                subtitle = "Required for GNSS positioning and calibration",
                icon = Icons.Default.LocationOn,
                isGranted = locationGranted
            )

            PermissionCard(
                title = "Sensors",
                subtitle = "High-sampling rate Accelerometer, Gyroscope & Magnetometer",
                icon = Icons.Default.Sensors,
                isGranted = true
            )

            PermissionCard(
                title = "Notifications",
                subtitle = "Foreground service status & GNSS outage alert banners",
                icon = Icons.Default.Notifications,
                isGranted = true
            )
        }

        Button(
            onClick = {
                if (locationGranted) onContinue() else locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .shadow(8.dp, shape = CircleShape),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            shape = CircleShape
        ) {
            Text(text = if (locationGranted) "Continue to dashboard" else "Grant location access", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 15.sp)
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isGranted: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, shape = RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        color = AutomotiveCardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, AutomotiveCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = PrimaryBlue, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text(text = subtitle, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            if (isGranted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Granted", tint = SuccessGreen, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Granted", color = SuccessGreen, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            }
        }
    }
}
