package com.ubertimetracker.ui.screens

import androidx.compose.animation.core.*
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ubertimetracker.service.TimerService
import com.ubertimetracker.service.UberAccessibilityService
import com.ubertimetracker.ui.viewmodel.HomeViewModel
import com.ubertimetracker.util.UberEatsLanguageDetector.UberStatus
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isServiceEnabled by UberAccessibilityService.isServiceEnabled.collectAsState()
    var showStopDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Uber Time Tracker") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Timer Display
            TimerDisplay(
                elapsedTime = uiState.timerState.elapsedTime,
                isRunning = uiState.timerState.isRunning,
                isPaused = uiState.timerState.isPaused,
                pausedTimeMillis = uiState.timerState.pausedTimeMillis
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Timer Controls
            TimerControls(
                isRunning = uiState.timerState.isRunning,
                isPaused = uiState.timerState.isPaused,
                onStart = { viewModel.startTimer() },
                onPause = { viewModel.pauseTimer() },
                onResume = { viewModel.resumeTimer() },
                onStop = { showStopDialog = true }
            )

            if (showStopDialog) {
                AlertDialog(
                    onDismissRequest = { showStopDialog = false },
                    title = { Text("Sitzung beenden?") },
                    text = { Text("Möchten Sie die aktuelle Zeiterfassung wirklich beenden?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.stopTimer()
                                showStopDialog = false
                            }
                        ) {
                            Text("Beenden")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showStopDialog = false }) {
                            Text("Abbrechen")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Auto-Sync Toggle
            AutoSyncToggle(
                enabled = uiState.autoSyncEnabled,
                onToggle = { viewModel.toggleAutoSync(!uiState.autoSyncEnabled) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Status Indicators
            StatusIndicators(
                offlineCacheEnabled = uiState.offlineCacheReady,
                cloudSyncEnabled = uiState.cloudSyncActive
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Live Debug Toggle
            var showDebug by remember { mutableStateOf(false) } // Local state for now
            
            OutlinedButton(
                onClick = { showDebug = !showDebug },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (showDebug) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = if (showDebug) "Debug-Modus ausblenden" else "Debug-Modus anzeigen")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Live Debug Inspector
            if (showDebug) {
                LiveDebugInspector(
                    uberStatus = uiState.uberStatus,
                    debugEntries = uiState.debugLogs,
                    isServiceEnabled = isServiceEnabled,
                    modifier = Modifier.fillMaxWidth().height(300.dp)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun TimerDisplay(
    elapsedTime: Long,
    isRunning: Boolean,
    isPaused: Boolean,
    pausedTimeMillis: Long
) {
    val hours = TimeUnit.MILLISECONDS.toHours(elapsedTime)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedTime) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedTime) % 60
    val timeText = String.format("%02d:%02d:%02d", hours, minutes, seconds)

    // Pulse animation when running
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRunning && !isPaused) 0.6f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha)
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = timeText,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Status text
            var currentPauseDuration by remember { mutableLongStateOf(0L) }

            LaunchedEffect(isPaused, pausedTimeMillis) {
                if (isPaused && pausedTimeMillis > 0) {
                    while (true) {
                        currentPauseDuration = System.currentTimeMillis() - pausedTimeMillis
                        delay(1000)
                    }
                } else {
                    currentPauseDuration = 0L
                }
            }

            val statusText = when {
                !isRunning -> "Bereit"
                isPaused -> {
                    val pHours = TimeUnit.MILLISECONDS.toHours(currentPauseDuration)
                    val pMinutes = TimeUnit.MILLISECONDS.toMinutes(currentPauseDuration) % 60
                    val pSeconds = TimeUnit.MILLISECONDS.toSeconds(currentPauseDuration) % 60
                    "Pausiert (${String.format("%02d:%02d:%02d", pHours, pMinutes, pSeconds)})"
                }
                else -> "Läuft"
            }
            Text(
                text = statusText,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun TimerControls(
    isRunning: Boolean,
    isPaused: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when {
            !isRunning -> {
                // Start button
                FilledTonalButton(
                    onClick = onStart,
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            isPaused -> {
                // Resume button
                FilledTonalButton(
                    onClick = onResume,
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Resume",
                        modifier = Modifier.size(32.dp)
                    )
                }
                // Stop button
                FilledTonalButton(
                    onClick = onStop,
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            else -> {
                // Pause button
                FilledTonalButton(
                    onClick = onPause,
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = "Pause",
                        modifier = Modifier.size(32.dp)
                    )
                }
                // Stop button
                FilledTonalButton(
                    onClick = onStop,
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AutoSyncToggle(
    enabled: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Auto-Sync",
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Mit Uber Eats App synchronisieren",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

@Composable
fun StatusIndicators(
    offlineCacheEnabled: Boolean,
    cloudSyncEnabled: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Offline Cache
        StatusChip(
            text = "Offline Cache",
            enabled = offlineCacheEnabled,
            icon = Icons.Default.Storage
        )
        // Cloud Sync
        StatusChip(
            text = "Cloud Sync",
            enabled = cloudSyncEnabled,
            icon = Icons.Default.Cloud
        )
    }
}

@Composable
fun StatusChip(
    text: String,
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (enabled) 
            MaterialTheme.colorScheme.primaryContainer 
        else 
            MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (enabled)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                fontSize = 12.sp,
                color = if (enabled)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Live Debug Inspector - Only shows Uber Eats status (Online/Offline)
 */
@Composable
fun LiveDebugInspector(
    uberStatus: com.ubertimetracker.data.model.UberAppStatus, // Updated to use Model
    debugEntries: List<com.ubertimetracker.data.model.DebugLogEntry>, // Updated to use Model
    isServiceEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Cursor blink animation
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1E1E1E) // Terminal dark background
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Debug Inspector",
                    color = Color(0xFF00FF00), // Green terminal color
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                
                // Current status indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when (uberStatus) {
                                    com.ubertimetracker.data.model.UberAppStatus.ONLINE -> Color(0xFF00FF00)
                                    com.ubertimetracker.data.model.UberAppStatus.OFFLINE -> Color(0xFFFF0000)
                                    com.ubertimetracker.data.model.UberAppStatus.BUSY -> Color(0xFFFFA500)
                                    com.ubertimetracker.data.model.UberAppStatus.UNKNOWN -> Color(0xFF888888)
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (uberStatus) {
                            com.ubertimetracker.data.model.UberAppStatus.ONLINE -> "Online"
                            com.ubertimetracker.data.model.UberAppStatus.OFFLINE -> "Offline"
                            com.ubertimetracker.data.model.UberAppStatus.BUSY -> "Busy"
                            com.ubertimetracker.data.model.UberAppStatus.UNKNOWN -> "Unknown"
                        },
                        color = when (uberStatus) {
                            com.ubertimetracker.data.model.UberAppStatus.ONLINE -> Color(0xFF00FF00)
                            com.ubertimetracker.data.model.UberAppStatus.OFFLINE -> Color(0xFFFF0000)
                            com.ubertimetracker.data.model.UberAppStatus.BUSY -> Color(0xFFFFA500)
                            com.ubertimetracker.data.model.UberAppStatus.UNKNOWN -> Color(0xFF888888)
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Divider(color = Color(0xFF333333))

            Spacer(modifier = Modifier.height(8.dp))

            // Service status
            if (!isServiceEnabled) {
                Text(
                    text = "> Accessibility Service not enabled",
                    color = Color(0xFFFF6600),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }

            // Debug log - Only shows status changes
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f)
            ) {
                items(debugEntries) { entry ->
                    // Convert LocalDateTime to Date for SimpleDateFormat interaction if needed, or use DateTimeFormatter
                    // debugEntries.timestamp is LocalDateTime
                    val timeStr = entry.timestamp.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                    
                    val statusColor = when (entry.type) {
                        com.ubertimetracker.data.model.LogType.SYSTEM -> Color(0xFF888888)
                        com.ubertimetracker.data.model.LogType.SYNC -> Color(0xFF00FFFF)
                        com.ubertimetracker.data.model.LogType.OCR -> Color.Yellow
                        com.ubertimetracker.data.model.LogType.LANGUAGE -> Color.Magenta
                        com.ubertimetracker.data.model.LogType.ANALYSIS -> Color(0xFF00FF00)
                        com.ubertimetracker.data.model.LogType.ACTION -> Color.White
                        else -> Color(0xFF888888)
                    }
                    
                    Row {
                        Text(
                            text = "[$timeStr] ",
                            color = Color(0xFF888888),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                        Text(
                            text = entry.message,
                            color = statusColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Cursor line
            Row {
                Text(
                    text = "> ",
                    color = Color(0xFF00FF00),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
                Text(
                    text = "█",
                    color = Color(0xFF00FF00).copy(alpha = cursorAlpha),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }
    }
}
