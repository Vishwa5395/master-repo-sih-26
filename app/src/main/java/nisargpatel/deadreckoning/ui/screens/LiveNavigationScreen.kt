package nisargpatel.deadreckoning.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.Log
import androidx.compose.animation.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import nisargpatel.deadreckoning.domain.model.NavigationMode
import nisargpatel.deadreckoning.domain.state.NavigationEvent
import nisargpatel.deadreckoning.ui.components.*
import nisargpatel.deadreckoning.ui.theme.*
import nisargpatel.deadreckoning.ui.viewmodel.NavigationViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

private const val TAG = "LiveNavMap"
private const val APP_USER_AGENT = "DeadReckoningPro/1.0 (Android; nisargpatel.deadreckoning)"

@Composable
fun LiveNavigationScreen(
    viewModel: NavigationViewModel
) {
    val navState by viewModel.navigationState.collectAsState()
    val routeInfo by viewModel.selectedRoute.collectAsState()
    val hmmDebugState by viewModel.mapMatchDebugState.collectAsState()
    var showHmmDebugOverlay by remember { mutableStateOf(false) }
    var potholeAlert by remember { mutableStateOf<String?>(null) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            if (event is NavigationEvent.PotholeDetected) {
                potholeAlert = event.severity
                delay(4_000L)
                potholeAlert = null
            }
        }
    }

    // Bind OSMDroid lifecycle
    DisposableEffect(lifecycleOwner, mapViewRef) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME  -> mapViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE   -> mapViewRef?.onPause()
                Lifecycle.Event.ON_DESTROY -> mapViewRef?.onDetach()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapViewRef?.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewRef?.onPause()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AutomotiveDarkBg)
    ) {
        // ── Layer 0: Full-bleed OSMDroid map ─────────────────────────────────
        AndroidView(
            factory = { context ->
                val config = Configuration.getInstance()
                config.userAgentValue = APP_USER_AGENT

                MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    setDestroyMode(false)
                    controller.setZoom(17.5)
                    onResume()

                    val rotationOverlay = RotationGestureOverlay(context, this)
                    rotationOverlay.isEnabled = true
                    overlays.add(rotationOverlay)

                    try {
                        val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), this)
                        locationOverlay.enableMyLocation()
                        locationOverlay.enableFollowLocation()
                        val emptyBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                        locationOverlay.setPersonIcon(emptyBitmap)
                        locationOverlay.setDirectionIcon(emptyBitmap)
                        overlays.add(locationOverlay)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error initializing location overlay", e)
                    }

                    try {
                        nisargpatel.deadreckoning.util.IndiaBoundaryOverlayHelper
                            .applyOfficialBoundary(context, this)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error applying boundary overlay", e)
                    }

                    mapViewRef = this
                }
            },
            update = { mapView ->
                mapViewRef = mapView

                mapView.overlays.removeAll(
                    mapView.overlays.filterIsInstance<Polyline>()
                        .filter { it.id == "uber_actual_track" }
                )

                val existingLine = mapView.overlays.filterIsInstance<Polyline>()
                    .firstOrNull { it.id == "uber_target_route" }
                val targetPolyline = existingLine ?: Polyline().also { line ->
                    line.id = "uber_target_route"
                    line.outlinePaint.color = AndroidColor.parseColor("#7654E8")
                    line.outlinePaint.strokeWidth = 14.0f
                    mapView.overlays.add(line)
                }
                targetPolyline.setPoints(routeInfo.routePoints)

                if (navState.latitude != 0.0 || navState.longitude != 0.0) {
                    val currentPos = GeoPoint(navState.latitude, navState.longitude)
                    UberVehicleMarker.updateVehicleMarker(
                        mapView = mapView,
                        position = currentPos,
                        headingDegrees = navState.headingDegrees
                    )
                    mapView.controller.animateTo(currentPos)
                }

                MapMatchDebugOverlay.updateOverlay(
                    mapView = mapView,
                    debugState = hmmDebugState,
                    isVisible = showHmmDebugOverlay
                )

                mapView.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Layer 1: Top scrim + controls ────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            // Subtle gradient scrim so top chips are always readable
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.82f), Color.Transparent)
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 14.dp, end = 14.dp)
            ) {
                // Mode pill + confidence row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModeIndicator(mode = navState.mode)
                    ConfidenceIndicator(percentage = navState.confidencePercentage)
                }

                // HMM Debug active banner
                AnimatedVisibility(
                    visible = showHmmDebugOverlay,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = UberDarkCard.copy(alpha = 0.92f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.7f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(SuccessGreen, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val matched = hmmDebugState?.matchedCandidate
                            val count = hmmDebugState?.allCandidates?.size ?: 0
                            val text = if (matched != null) {
                                val distStr = String.format(java.util.Locale.US, "%.1f", matched.distanceMeters)
                                "HMM Debug: ${matched.roadName} (${distStr}m, ${hmmDebugState?.confidence ?: 0}%) | $count cands"
                            } else {
                                "HMM Debug: No match ($count candidates)"
                            }
                            Text(
                                text = text,
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }

                // GNSS outage banner
                AnimatedVisibility(
                    visible = navState.mode == NavigationMode.AI_DEAD_RECKONING
                        || navState.outageDurationSeconds > 0,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutageBanner(outageSeconds = navState.outageDurationSeconds)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Vela-style search bar
                UberSearchBar(
                    currentRoute = routeInfo,
                    onDestinationSelected = { name, point ->
                        viewModel.selectDestination(name, point)
                    }
                )

                // Pothole alert toast
                AnimatedVisibility(
                    visible = potholeAlert != null,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    potholeAlert?.let { alert ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = WarningAmber.copy(alpha = 0.95f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = alert,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Layer 2: Right-side quick-action FABs ────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // HMM Map-Matching Debug Overlay Toggle
            SmallMapFab(
                icon = Icons.Default.AltRoute,
                tint = if (showHmmDebugOverlay) SuccessGreen else TextSecondary,
                background = if (showHmmDebugOverlay) SuccessGreen.copy(alpha = 0.25f) else UberDarkCard,
                contentDescription = "HMM Map Match Debug Overlay"
            ) {
                showHmmDebugOverlay = !showHmmDebugOverlay
            }

            // Recenter
            SmallMapFab(
                icon = Icons.Default.MyLocation,
                tint = PrimaryBlue,
                background = UberDarkCard,
                contentDescription = "Recenter"
            ) {
                if (navState.latitude != 0.0 || navState.longitude != 0.0) {
                    mapViewRef?.controller?.animateTo(
                        GeoPoint(navState.latitude, navState.longitude)
                    )
                    mapViewRef?.controller?.setZoom(18.5)
                }
            }

            // Zoom in
            SmallMapFab(
                icon = Icons.Default.Add,
                tint = TextSecondary,
                background = UberDarkCard,
                contentDescription = "Zoom in"
            ) { mapViewRef?.controller?.zoomIn() }

            // Zoom out
            SmallMapFab(
                icon = Icons.Default.Remove,
                tint = TextSecondary,
                background = UberDarkCard,
                contentDescription = "Zoom out"
            ) { mapViewRef?.controller?.zoomOut() }
        }

        // ── Layer 3: Bottom scrim + HUD ──────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            // Gradient scrim under HUD card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.90f))
                        )
                    )
            )

            UberNavigationHUD(
                routeInfo = routeInfo,
                speedKmh = navState.speedKmh,
                headingDegrees = navState.headingDegrees,
                accuracyMeters = navState.accuracyMeters,
                isNavigating = navState.isNavigating,
                onToggleNavigation = {
                    if (navState.isNavigating) viewModel.stopNavigation()
                    else viewModel.startNavigation()
                },
                onRecenterMap = {
                    if (navState.latitude != 0.0 || navState.longitude != 0.0) {
                        mapViewRef?.let { map ->
                            map.controller.animateTo(GeoPoint(navState.latitude, navState.longitude))
                            map.controller.setZoom(18.5)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            )
        }
    }
}

// ── Small circular FAB helper ────────────────────────────────────────────────
@Composable
private fun SmallMapFab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    background: Color,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = background.copy(alpha = 0.90f),
        border = androidx.compose.foundation.BorderStroke(1.dp, UberCardBorder),
        modifier = Modifier
            .size(44.dp)
            .shadow(6.dp, CircleShape)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
