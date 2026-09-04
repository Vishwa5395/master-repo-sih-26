package nisargpatel.deadreckoning.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nisargpatel.deadreckoning.ui.components.CommandPanel
import nisargpatel.deadreckoning.ui.components.CommandScreen
import nisargpatel.deadreckoning.ui.components.DataRow
import nisargpatel.deadreckoning.ui.components.DividerLine
import nisargpatel.deadreckoning.ui.components.PageHeader
import nisargpatel.deadreckoning.ui.components.SectionLabel
import nisargpatel.deadreckoning.ui.components.StatusPill
import nisargpatel.deadreckoning.ui.components.AIStatusCard
import nisargpatel.deadreckoning.ui.components.MetricCard
import nisargpatel.deadreckoning.ui.theme.*
import nisargpatel.deadreckoning.ui.viewmodel.IntelligenceViewModel

@Composable
fun IntelligenceScreen(
    viewModel: IntelligenceViewModel
) {
    val aiState by viewModel.aiState.collectAsState()
    val potholeAlert by viewModel.potholeAlert.collectAsState()

    CommandScreen {
        PageHeader(
            title = "Model Intelligence",
            subtitle = "Local V8 inference and road-impact monitoring",
            icon = Icons.Default.Psychology,
            tint = PurpleAI,
            trailing = {
                StatusPill(
                    text = if (aiState.isModelLoaded) "Loaded" else "Offline",
                    color = if (aiState.isModelLoaded) SuccessGreen else ErrorRed
                )
            }
        )

        AIStatusCard(
            predictedSpeedKmh = aiState.predictedSpeedKmh,
            motionClassification = aiState.motionClassification,
            inferenceTimeMs = aiState.inferenceTimeMs
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "AI Speed Prediction",
                value = String.format("%.1f km/h", aiState.predictedSpeedKmh),
                subtitle = "Confidence: ${aiState.speedConfidencePercentage}%",
                icon = Icons.Default.Memory,
                valueColor = PrimaryBlue,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Motion Classification",
                value = aiState.motionClassification,
                subtitle = "Confidence: ${aiState.motionConfidencePercentage}%",
                icon = Icons.Default.Psychology,
                valueColor = SuccessGreen,
                modifier = Modifier.weight(1f)
            )
        }

        val alertText = potholeAlert ?: aiState.anomalyDetected
        val isAlert = potholeAlert != null || aiState.anomalyDetected != "None"
        CommandPanel(borderColor = if (isAlert) WarningAmber else AutomotiveCardBorder) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Warning, contentDescription = "Anomaly", tint = if (isAlert) WarningAmber else TextMuted, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    SectionLabel("Road impact detector")
                    Text(text = alertText, color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                }
                StatusPill(text = if (isAlert) "Alert" else "Clear", color = if (isAlert) WarningAmber else SuccessGreen)
            }
        }

        CommandPanel(color = PanelRaised, borderColor = DividerSoft) {
            SectionLabel("Model artifact")
            DataRow("Architecture", aiState.modelVersion)
            DividerLine()
            DataRow("Inference engine", "ONNX Runtime", if (aiState.isModelLoaded) SuccessGreen else ErrorRed)
            DataRow("Execution target", if (aiState.isModelLoaded) "Device CPU / NNAPI fallback" else "Unavailable")
            DataRow("Model version", aiState.modelVersion, PurpleAI)
        }
    }
}
