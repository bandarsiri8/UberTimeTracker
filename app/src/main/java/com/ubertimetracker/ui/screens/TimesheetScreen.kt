package com.ubertimetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ubertimetracker.data.model.ExportFormat
import com.ubertimetracker.ui.viewmodel.TimesheetViewModel

import com.ubertimetracker.ui.viewmodel.TimesheetItem
import com.ubertimetracker.data.model.TimesheetEntry
import com.ubertimetracker.data.model.WorkSegment
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimesheetScreen(
    viewModel: TimesheetViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showExportMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Permission handling for Android < 10
    var pendingExportFormat by remember { mutableStateOf<ExportFormat?>(null) }
    
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && pendingExportFormat != null) {
            viewModel.shareExport(context, pendingExportFormat!!)
            pendingExportFormat = null
        } else {
            // Permission denied
            pendingExportFormat = null
        }
    }

    fun initiateExport(format: ExportFormat) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (hasPermission) {
                viewModel.shareExport(context, format)
            } else {
                pendingExportFormat = format
                permissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        } else {
            // Android 10+ does not need WRITE_EXTERNAL_STORAGE for MediaStore downloads
            viewModel.shareExport(context, format)
        }
    }

    // Handle export result messages
    LaunchedEffect(uiState.exportResult) {
        uiState.exportResult?.let { result ->
            when (result) {
                is com.ubertimetracker.ui.viewmodel.ExportResult.Success -> {
                    val success = result
                    val message = if (success.savedToDownloads) "Export gespeichert in Downloads" else "Datei bereit zum Öffnen"
                    
                    val snackbarResult = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = "ÖFFNEN",
                        duration = SnackbarDuration.Long
                    )
                    
                    if (snackbarResult == SnackbarResult.ActionPerformed) {
                        val uri = success.uri
                        if (uri != null) {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, viewModel.getExportFileName(context, success.format).let { name ->
                                    // Infer mimetype again or pass it
                                        if (name.endsWith(".pdf")) "application/pdf"
                                        else if (name.endsWith(".xlsx")) "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                        else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                    })
                                    flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Keine App zum Öffnen gefunden")
                                }
                            } else {
                                // Fallback if URI is null (legacy/share path)
                                snackbarHostState.showSnackbar("Datei gespeichert")
                            }
                    }
                    viewModel.clearExportResult()
                }
                is com.ubertimetracker.ui.viewmodel.ExportResult.Error -> {
                    snackbarHostState.showSnackbar(
                        message = result.message,
                        withDismissAction = true
                    )
                    viewModel.clearExportResult()
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("ARBEITSZEITLISTE") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            ExportSpeedDial(
                expanded = showExportMenu,
                onToggle = { showExportMenu = !showExportMenu },
                onExportSelected = { format ->
                    showExportMenu = false
                    initiateExport(format)
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Month navigation
            MonthNavigator(
                currentMonth = uiState.currentMonth,
                onPreviousMonth = { viewModel.previousMonth() },
                onNextMonth = { viewModel.nextMonth() }
            )

            // Timesheet table
            TimesheetTable(
                items = uiState.items, // Use the new flat list
                monthlyTotal = uiState.monthlyTotal
            )
        }
        
        // Overlay when menu is open to close it on tap outside
        if (showExportMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(padding) // Respect scaffold padding? No, overlay entire screen
                    .clickable { showExportMenu = false }
            )
        }
    }
}

@Composable
fun ExportSpeedDial(
    expanded: Boolean,
    onToggle: () -> Unit,
    onExportSelected: (ExportFormat) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        // Options
        AnimatedVisibility(
            visible = expanded,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SpeedDialOption(
                    text = "PDF Export",
                    icon = Icons.Default.PictureAsPdf,
                    color = Color(0xFFE53935), // Red
                    onClick = { onExportSelected(ExportFormat.PDF) }
                )
                
                SpeedDialOption(
                    text = "Excel Export",
                    icon = Icons.Default.TableChart,
                    color = Color(0xFF43A047), // Green
                    onClick = { onExportSelected(ExportFormat.EXCEL) }
                )
                
                SpeedDialOption(
                    text = "Word Export",
                    icon = Icons.Default.Description,
                    color = Color(0xFF1E88E5), // Blue
                    onClick = { onExportSelected(ExportFormat.WORD) }
                )
            }
        }

        // Main FAB
        val rotation by animateFloatAsState(
            targetValue = if (expanded) 45f else 0f,
            label = "fab_rotation"
        )

        FloatingActionButton(
            onClick = onToggle,
            containerColor = MaterialTheme.colorScheme.primary,
            shape = CircleShape
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.Close else Icons.Default.Download, // Access vector from Icons.Default or just rotate Plus/Download
                contentDescription = "Export"
                // Rotate the icon if it's the download icon and we want to turn it into an X? 
                // Or just swap icons. Swapping is cleaner.
            )
        }
    }
}

@Composable
fun SpeedDialOption(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
            modifier = Modifier.padding(end = 12.dp)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge
            )
        }
        
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = color,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun MonthNavigator(
    currentMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    // German month names
    val germanMonths = mapOf(
        1 to "Januar",
        2 to "Februar",
        3 to "März",
        4 to "April",
        5 to "Mai",
        6 to "Juni",
        7 to "Juli",
        8 to "August",
        9 to "September",
        10 to "Oktober",
        11 to "November",
        12 to "Dezember"
    )

    val monthName = germanMonths[currentMonth.monthValue] ?: currentMonth.month.name
    val displayText = "$monthName ${currentMonth.year}"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPreviousMonth) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Vorheriger Monat"
            )
        }

        Text(
            text = displayText,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        IconButton(onClick = onNextMonth) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Nächster Monat"
            )
        }
    }
}

@Composable
fun TimesheetTable(
    items: List<TimesheetItem>, // Changed from entries + weeklyTotals map
    monthlyTotal: String
) {
    val scrollState = rememberScrollState()
    
    // German day abbreviations
    val germanDays = mapOf(
        DayOfWeek.MONDAY to "Mo",
        DayOfWeek.TUESDAY to "Di",
        DayOfWeek.WEDNESDAY to "Mi",
        DayOfWeek.THURSDAY to "Do",
        DayOfWeek.FRIDAY to "Fr",
        DayOfWeek.SATURDAY to "Sa",
        DayOfWeek.SUNDAY to "So"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(scrollState)
    ) {
        // Table header
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary)
                .padding(vertical = 8.dp)
        ) {
            TableHeaderCell("Datum", 70.dp)
            TableHeaderCell("Tag", 40.dp)
            TableHeaderCell("Aktivitäten (Start - Ende ... Pause ...)", 400.dp) // Wide dynamic area
            TableHeaderCell("Gesamt", 70.dp)
        }

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(items) { item ->
                when (item) {
                    is TimesheetItem.DayEntry -> {
                        val entry = item.entry
                        val date = entry.date
                        val dayName = germanDays[date.dayOfWeek] ?: date.dayOfWeek.name.take(2)
                        val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || 
                                       date.dayOfWeek == DayOfWeek.SUNDAY

                        TimesheetRow(
                            entry = entry,
                            dayName = dayName,
                            isWeekend = isWeekend,
                            hasConflict = entry.hasConflict
                        )
                    }
                    is TimesheetItem.WeekTotal -> {
                        WeeklyTotalRow(
                            weekNumber = item.weekNumber,
                            total = item.total
                        )
                    }
                }
            }
        }

        // Monthly total at bottom right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                .padding(16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MONATSGESAMT:",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(end = 16.dp)
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = monthlyTotal,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun TableHeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(
        text = text,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .width(width)
            .padding(4.dp)
    )
}

@Composable
fun TimesheetRow(
    entry: TimesheetEntry,
    dayName: String,
    isWeekend: Boolean,
    hasConflict: Boolean
) {
    val backgroundColor = when {
        isWeekend -> Color(0xFFFFA726).copy(alpha = 0.3f) // Orange for weekends
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .background(backgroundColor)
            .padding(vertical = 4.dp)
            .height(IntrinsicSize.Min)
    ) {
        // Date (dd.MM)
        TableCell(
            text = entry.date.format(DateTimeFormatter.ofPattern("dd.MM")),
            width = 70.dp
        )
        // Day name
        TableCell(text = dayName, width = 40.dp)
        
        // Dynamic Segments Area
        Row(
            modifier = Modifier
                .width(400.dp) // Matches header
                .horizontalScroll(rememberScrollState()), // Allow internal scroll if too long? No, outer scroll handles it.
            verticalAlignment = Alignment.CenterVertically
        ) {
            entry.segments.forEachIndexed { index, segment ->
                when (segment) {
                    is WorkSegment.Work -> {
                        // Work Block
                        Text(
                            text = "${segment.startTime} - ${segment.endTime ?: "..."}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    is WorkSegment.Pause -> {
                        // Pause Block (Gap)
                        Text(
                            text = " ⏸ ${segment.startTime}-${segment.endTime ?: "?"} (${segment.duration}) ",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
                
                // Add spacer arrow between work segments if explicit pause isn't there?
                // Logic handles pauses, so just spacing
                if (index < entry.segments.size - 1) {
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
            
            if (entry.segments.isEmpty()) {
                Text(text = "-", fontSize = 12.sp, color = Color.Gray)
            }
        }

        // Total with conflict indicator
        TableCell(
            text = if (hasConflict) "⚠️ ${entry.totalDailyHours ?: "-"}" else entry.totalDailyHours ?: "-",
            width = 70.dp
        )
    }
}

@Composable
fun TableCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(
        text = text,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .width(width)
            .padding(4.dp)
    )
}

@Composable
fun WeeklyTotalRow(weekNumber: Int, total: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Text(
            text = "Woche $weekNumber Gesamt: $total",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

private fun getWeekOfYear(date: LocalDate): Int {
    return date.get(java.time.temporal.WeekFields.of(Locale.GERMANY).weekOfYear())
}


