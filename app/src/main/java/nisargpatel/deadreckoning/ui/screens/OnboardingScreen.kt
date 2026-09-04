package nisargpatel.deadreckoning.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import nisargpatel.deadreckoning.ui.theme.*

data class OnboardingPageData(
    val title: String,
    val description: String,
    val icon: ImageVector
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onOnboardingFinished: () -> Unit
) {
    val pages = listOf(
        OnboardingPageData(
            title = "Navigate Beyond GPS",
            description = "The Intelligent Dead Reckoning system automatically continues vehicle navigation when GNSS signal is lost in tunnels, urban canyons, or dense cover.",
            icon = Icons.Default.GpsOff
        ),
        OnboardingPageData(
            title = "AI-Powered Motion Intelligence",
            description = "On-device AI algorithms analyze accelerometer and gyroscope IMU sensor data in real-time to estimate vehicle speed and detect road anomalies.",
            icon = Icons.Default.Psychology
        ),
        OnboardingPageData(
            title = "Seamless GNSS Recovery",
            description = "When GNSS returns, the system smoothly reconciles dead-reckoning trajectory with GPS coordinates, eliminating position drift.",
            icon = Icons.Default.Sync
        ),
        OnboardingPageData(
            title = "Offline-Ready Map Matching",
            description = "Integrated vector map matching aligns estimated positions directly onto road networks without requiring cloud connectivity.",
            icon = Icons.Default.Map
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AutomotiveDarkBg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Skip Button Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onOnboardingFinished) {
                Text(text = "SKIP", color = TextSecondary, fontWeight = FontWeight.Black)
            }
        }

        // Pager Content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            val data = pages[page]
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = AutomotiveCardBg,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, PrimaryBlue.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .size(130.dp)
                        .shadow(8.dp, shape = RoundedCornerShape(32.dp))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = data.icon,
                            contentDescription = data.title,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(60.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = data.title,
                    color = TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = data.description,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Page Indicator
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pages.size) { index ->
                val active = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .size(if (active) 28.dp else 8.dp, 8.dp)
                        .background(
                            color = if (active) PrimaryBlue else AutomotiveCardBorder,
                            shape = CircleShape
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Navigation Buttons - iOS Pill Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (pagerState.currentPage > 0) {
                OutlinedButton(
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    },
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AutomotiveCardBorder)
                ) {
                    Text(text = "BACK", color = TextPrimary, fontWeight = FontWeight.Black)
                }
            } else {
                Spacer(modifier = Modifier.width(80.dp))
            }

            Button(
                onClick = {
                    if (pagerState.currentPage < pages.size - 1) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        onOnboardingFinished()
                    }
                },
                modifier = Modifier.shadow(6.dp, shape = CircleShape),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = CircleShape
            ) {
                Text(
                    text = if (pagerState.currentPage == pages.size - 1) "GET STARTED" else "NEXT",
                    color = Color.Black,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}
