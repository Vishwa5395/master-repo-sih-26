package nisargpatel.deadreckoning.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nisargpatel.deadreckoning.ui.components.*
import nisargpatel.deadreckoning.ui.theme.*
import nisargpatel.deadreckoning.ui.viewmodel.AnalyticsViewModel
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel
) {
    val state by viewModel.analyticsState.collectAsState()

    CommandScreen {

        // ── Header ────────────────────────────────────────────────────────────
        PageHeader(
            title = "Analytics",
            subtitle = "Trip history · outage recovery · model error",
            icon = Icons.Default.Analytics
        )

        // ── Session score ring + key stats ────────────────────────────────────
        CommandPanel {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Accuracy ring
                AccuracyRing(
                    percentage = state.mapMatchingAccuracyPercentage,
                    modifier = Modifier.size(96.dp)
                )

                Spacer(modifier = Modifier.width(20.dp))

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("Session score")
                    Text(
                        text = "${state.mapMatchingAccuracyPercentage}%  map match",
                        color = SuccessGreen,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp
                    )
                    DataRow(
                        label = "Total distance",
                        value = String.format("%.1f km", state.totalDistanceKm)
                    )
                    DataRow(
                        label = "GNSS outages",
                        value = "${state.outageCount}  (${state.totalOutageDurationSeconds}s)",
                        valueColor = if (state.outageCount == 0) SuccessGreen else WarningAmber
                    )
                }
            }
        }

        // ── Four metric cards (2 × 2 grid) ───────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "Avg DR Error",
                value = String.format("%.1f m", state.averageDriftMeters),
                subtitle = "Mean drift",
                icon = Icons.Default.CompareArrows,
                valueColor = SuccessGreen,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Max DR Error",
                value = String.format("%.1f m", state.maxDriftMeters),
                subtitle = "Peak drift",
                icon = Icons.Default.ErrorOutline,
                valueColor = ErrorRed,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "AI Speed RMSE",
                value = String.format("%.1f km/h", state.aiSpeedRmseKmh),
                subtitle = "Root mean error",
                icon = Icons.Default.Speed,
                valueColor = PurpleAI,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Recovery time",
                value = "${state.gnssRecoveryTimeSeconds} s",
                subtitle = "GNSS reconcile",
                icon = Icons.Default.Sync,
                valueColor = PrimaryBlue,
                modifier = Modifier.weight(1f)
            )
        }

        // ── DR drift bar chart ────────────────────────────────────────────────
        CommandPanel {
            SectionLabel("Drift over session")
            Spacer(modifier = Modifier.height(4.dp))
            DriftBarChart(
                avgDrift = state.averageDriftMeters.toFloat(),
                maxDrift = state.maxDriftMeters.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(color = SuccessGreen, label = "Avg ${String.format("%.1f", state.averageDriftMeters)} m")
                LegendDot(color = ErrorRed, label = "Max ${String.format("%.1f", state.maxDriftMeters)} m")
            }
        }

        // ── Recovery & heading panel ──────────────────────────────────────────
        CommandPanel(color = PanelRaised, borderColor = DividerSoft) {
            SectionLabel("Recovery analysis")
            Spacer(modifier = Modifier.height(4.dp))

            // GNSS recovery time progress bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("GNSS reconciliation", color = TextSecondary, fontSize = 12.sp)
                    Text(
                        "${state.gnssRecoveryTimeSeconds} sec",
                        color = SuccessGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                val recoveryProgress = (state.gnssRecoveryTimeSeconds.toFloat() / 30f).coerceIn(0f, 1f)
                AnimatedLinearBar(
                    progress = recoveryProgress,
                    color = SuccessGreen,
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                )
            }

            DividerLine()

            DataRow("Heading error bias", "${state.headingErrorDegrees} °")
            DataRow(
                "Map matching confidence",
                "${state.mapMatchingAccuracyPercentage}%",
                SuccessGreen
            )
        }

        // ── Status pills summary ──────────────────────────────────────────────
        CommandPanel {
            SectionLabel("System readiness")
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val matchOk = state.mapMatchingAccuracyPercentage >= 80
                val driftOk  = state.averageDriftMeters <= 10.0
                val outageOk = state.outageCount == 0

                StatusPill(
                    text = if (matchOk) "Map Match OK" else "Map Match Weak",
                    color = if (matchOk) SuccessGreen else WarningAmber
                )
                StatusPill(
                    text = if (driftOk) "DR Stable" else "DR High",
                    color = if (driftOk) SuccessGreen else ErrorRed
                )
                StatusPill(
                    text = if (outageOk) "No Outages" else "${state.outageCount} Outage(s)",
                    color = if (outageOk) SuccessGreen else WarningAmber
                )
            }
        }
    }
}

// ── Accuracy Ring (arc gauge) ────────────────────────────────────────────────
@Composable
private fun AccuracyRing(
    percentage: Int,
    modifier: Modifier = Modifier
) {
    val animPercent by animateFloatAsState(
        targetValue = percentage / 100f,
        animationSpec = tween(1000, easing = EaseOutCubic),
        label = "ringAnim"
    )
    val ringColor = when {
        percentage >= 80 -> SuccessGreen
        percentage >= 60 -> WarningAmber
        else             -> ErrorRed
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val inset  = stroke / 2f
            val arcRect = Size(size.width - stroke, size.height - stroke)

            // Track
            drawArc(
                color = UberCardBorder,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcRect,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            // Value
            drawArc(
                brush = Brush.sweepGradient(listOf(ringColor.copy(alpha = 0.6f), ringColor)),
                startAngle = 135f,
                sweepAngle = 270f * animPercent,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcRect,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$percentage",
                color = TextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                lineHeight = 22.sp
            )
            Text(
                text = "%",
                color = TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

// ── Drift bar chart ──────────────────────────────────────────────────────────
@Composable
private fun DriftBarChart(
    avgDrift: Float,
    maxDrift: Float,
    modifier: Modifier = Modifier
) {
    val animAvg by animateFloatAsState(
        targetValue = if (maxDrift > 0) avgDrift / maxDrift else 0f,
        animationSpec = tween(900, easing = EaseOutCubic),
        label = "avgAnim"
    )
    val animMax by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(900, easing = EaseOutCubic),
        label = "maxAnim"
    )
    val avgColor = SuccessGreen
    val maxColor = ErrorRed

    Canvas(modifier = modifier) {
        val barH = size.height * 0.34f
        val gap  = size.height * 0.18f
        val r    = 6.dp.toPx()

        fun drawBar(yTop: Float, widthFraction: Float, color: Color) {
            val w = size.width * widthFraction.coerceIn(0f, 1f)
            // background track
            drawRoundRect(
                color = UberCardBorder,
                topLeft = Offset(0f, yTop),
                size = Size(size.width, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
            )
            if (w > r * 2) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(color.copy(alpha = 0.55f), color),
                        endX = w
                    ),
                    topLeft = Offset(0f, yTop),
                    size = Size(w, barH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
                )
            }
        }

        drawBar(0f, animAvg, avgColor)
        drawBar(barH + gap, animMax, maxColor)
    }
}

// ── Animated linear bar ───────────────────────────────────────────────────────
@Composable
private fun AnimatedLinearBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val animProg by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(800, easing = EaseOutCubic),
        label = "barProg"
    )
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(UberCardBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animProg)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(listOf(color.copy(alpha = 0.6f), color))
                )
        )
    }
}

// ── Legend dot ───────────────────────────────────────────────────────────────
@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(text = label, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
