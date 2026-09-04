package nisargpatel.deadreckoning.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import nisargpatel.deadreckoning.ui.theme.*

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(2500L)
        onSplashFinished()
    }

    val transition = rememberInfiniteTransition(label = "TrajectoryAnimation")
    val animProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AutomotiveDarkBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "IDR",
                color = PrimaryBlue,
                fontSize = 52.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "INTELLIGENT DEAD RECKONING",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                text = "AI-Powered Seamless Vehicle Navigation",
                color = TextSecondary,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Development Team · MARK-V",
                color = PrimaryBlue.copy(alpha = 0.65f),
                fontSize = 11.sp,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Animated trajectory simulation line
            Canvas(modifier = Modifier.size(240.dp, 60.dp)) {
                val width = size.width
                val height = size.height
                val startX = 0f
                val endX = width * animProgress

                // Background dashed line
                drawLine(
                    color = AutomotiveCardBorder,
                    start = Offset(0f, height / 2),
                    end = Offset(width, height / 2),
                    strokeWidth = 4f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )

                // Animated trajectory
                drawLine(
                    color = PrimaryBlue,
                    start = Offset(startX, height / 2),
                    end = Offset(endX, height / 2),
                    strokeWidth = 6f
                )

                // Vehicle position marker
                drawCircle(
                    color = SuccessGreen,
                    radius = 8f,
                    center = Offset(endX, height / 2)
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
            Text(
                text = "GNSS + IMU + AI → Continuous Navigation",
                color = PurpleAI,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
