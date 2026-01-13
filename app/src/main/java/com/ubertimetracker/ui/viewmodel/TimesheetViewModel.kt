package com.ubertimetracker.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ubertimetracker.data.model.*
import com.ubertimetracker.data.repository.SessionRepository
import com.ubertimetracker.util.ExportManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi



@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class TimesheetViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    private val _isLoading = MutableStateFlow(false)
    private val _syncStatus = MutableStateFlow(SyncStatus.SYNCED)
    private val _showExportMenu = MutableStateFlow(false)
    private val _exportResult = MutableStateFlow<ExportResult?>(null)

    // Data Flows
    private val timesheetRows: Flow<List<TimesheetRow>> = _selectedMonth
        .flatMapLatest { month ->
            sessionRepository.getTimesheetRows(month.year, month.monthValue)
        }

    private val weeklySummaries: Flow<List<WeeklySummary>> = _selectedMonth
        .flatMapLatest { month ->
            sessionRepository.getWeeklySummaries(month.year, month.monthValue)
        }

    private val monthlyTotal: Flow<Double> = timesheetRows
        .map { rows -> 
            rows.sumOf { row -> 
                row.sessions.sumOf { it.totalHours } 
            } 
        }

    // UI State
    val uiState: StateFlow<TimesheetUiState> = combine(
        _selectedMonth,
        timesheetRows,
        weeklySummaries,
        monthlyTotal,
        _isLoading,
        _syncStatus,
        _showExportMenu,
        _exportResult
    ) { args: Array<Any?> ->
        val month = args[0] as YearMonth
        @Suppress("UNCHECKED_CAST")
        val rows = args[1] as List<TimesheetRow>
        @Suppress("UNCHECKED_CAST")
        val summaries = args[2] as List<WeeklySummary>
        val total = args[3] as Double
        val isLoading = args[4] as Boolean
        val syncStatus = args[5] as SyncStatus
        val showMenu = args[6] as Boolean
        val exportResult = args[7] as ExportResult?

        // Build flat list of items (Daily Entries + Weekly Totals)
        val items = mutableListOf<TimesheetItem>()
        val weeklyMap = summaries.associateBy { it.weekNumber }
        
        // Group rows by week
        val rowsByWeek = rows.groupBy { it.weekNumber }
        
        // Sort weeks to match calendar order
        val sortedWeeks = rowsByWeek.keys.sorted()
        
        sortedWeeks.forEach { weekNum ->
            // Add all days for this week
            val weekRows = rowsByWeek[weekNum] ?: emptyList()
            weekRows.forEach { row ->
                items.add(TimesheetItem.DayEntry(mapRowToEntry(row)))
            }
            
            // Add weekly total at the end of the week
            val summary = weeklyMap[weekNum]
            if (summary != null) {
                items.add(TimesheetItem.WeekTotal(
                    weekNumber = weekNum,
                    total = SessionRepository.formatHours(summary.totalHours)
                ))
            }
        }

        TimesheetUiState(
            currentMonth = month,
            items = items, // New flat list
            monthlyTotal = SessionRepository.formatHours(total),
            isLoading = isLoading,
            syncStatus = syncStatus,
            showExportMenu = showMenu,
            exportResult = exportResult
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = TimesheetUiState()
    )

    // Helper to map TimesheetRow to TimesheetEntry
    private fun mapRowToEntry(row: TimesheetRow): TimesheetEntry {
        // Sort sessions by time
        val sortedSessions = row.sessions.sortedBy { it.startTime1 }
        val segments = mutableListOf<WorkSegment>()
        var dailyTotalHours = 0.0

        // Map sessions to pauses
        val pausesBySession = row.pauses.groupBy { it.sessionId }

        sortedSessions.forEachIndexed { index, session ->
            dailyTotalHours += session.totalHours
            
            val sessionPauses = pausesBySession[session.id]?.sortedBy { it.startTime } ?: emptyList()

            // If we have explicit pauses inside a session, we need to split the work segment
            if (session.startTime1 != null) {
                var currentWorkStart: LocalTime? = session.startTime1
                val sessionEnd = session.stopTime1

                if (sessionPauses.isNotEmpty()) {
                    sessionPauses.forEach { pause ->
                        // 1. Work segment before pause
                        val start = currentWorkStart
                        if (start != null && start.isBefore(pause.startTime)) {
                             // If work segment is valid (start < pause start)
                             segments.add(WorkSegment.Work(
                                 startTime = SessionRepository.formatTime(start),
                                 endTime = SessionRepository.formatTime(pause.startTime)
                             ))
                        }

                        // 2. The Pause segment itself
                        segments.add(WorkSegment.Pause(
                            startTime = SessionRepository.formatTime(pause.startTime),
                            endTime = SessionRepository.formatTime(pause.endTime),
                            duration = SessionRepository.formatPause(pause.durationMinutes)
                        ))

                        // Next work start is pause end
                         if (pause.endTime != null) {
                            currentWorkStart = pause.endTime
                        } else {
                            // If pause is ongoing (no end time), we can't really start next work segment?
                             currentWorkStart = pause.startTime // Fallback
                        }
                    }
                    
                    // 3. Final work segment after last pause (if any time remaining)
                    val start = currentWorkStart
                    if (start != null && (sessionEnd == null || start.isBefore(sessionEnd))) {
                         segments.add(WorkSegment.Work(
                             startTime = SessionRepository.formatTime(start),
                             endTime = SessionRepository.formatTime(sessionEnd)
                         ))
                    }

                } else {
                    // No internal pauses, standard full segment
                     segments.add(WorkSegment.Work(
                        startTime = SessionRepository.formatTime(session.startTime1),
                        endTime = SessionRepository.formatTime(session.stopTime1)
                    ))
                }
            }

            // Check for gap (Pause) BETWEEN sessions (legacy/multi-shift logic)
            // But if user requested specifically "Start 1 | Stop 1 | Pause | Start 2...", this implies
            // they might interpret the gap between shifts as the "Pause".
            // However, the app supports explicit pauses now.
            // If we have distinct sessions, we still show the gap as a pause?
            if (index < sortedSessions.size - 1) {
                val nextSession = sortedSessions[index + 1]
                if (session.stopTime1 != null && nextSession.startTime1 != null) {
                    val stopMinutes = session.stopTime1!!.hour * 60 + session.stopTime1!!.minute
                    val startMinutes = nextSession.startTime1!!.hour * 60 + nextSession.startTime1!!.minute
                    val gapMinutes = (startMinutes - stopMinutes).toLong()
                    
                    if (gapMinutes > 0) {
                        segments.add(WorkSegment.Pause(
                            SessionRepository.formatTime(session.stopTime1),
                            SessionRepository.formatTime(nextSession.startTime1),
                            SessionRepository.formatPause(gapMinutes)
                        ))
                    }
                }
            }
        }

        return TimesheetEntry(
            date = row.date,
            segments = segments,
            totalDailyHours = SessionRepository.formatHours(dailyTotalHours),
            hasConflict = sortedSessions.any { it.hasConflict },
            weekNumber = row.weekNumber
        )
    }

    fun selectMonth(yearMonth: YearMonth) {
        _selectedMonth.value = yearMonth
    }

    fun previousMonth() {
        _selectedMonth.value = _selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        _selectedMonth.value = _selectedMonth.value.plusMonths(1)
    }
    
    fun toggleExportMenu() {
        _showExportMenu.value = !_showExportMenu.value
    }

    fun hideExportMenu() {
        _showExportMenu.value = false
    }

    fun getExportFileName(context: Context, format: ExportFormat): String {
        val month = _selectedMonth.value
        val prefix = context.getString(com.ubertimetracker.R.string.export_filename_prefix)
        val ext = when (format) {
            ExportFormat.EXCEL -> "xlsx"
            ExportFormat.WORD -> "docx"
            ExportFormat.PDF -> "pdf"
        }
        return "${prefix}_${month.year}_${month.monthValue.toString().padStart(2, '0')}.$ext"
    }

    fun shareExport(context: Context, format: ExportFormat) {
        viewModelScope.launch {
            _isLoading.value = true
            _showExportMenu.value = false
            
            try {
                // Collect latest data for export
                val rows = timesheetRows.first()
                val summaries = weeklySummaries.first()
                val total = monthlyTotal.first()
                val month = _selectedMonth.value

                // Map to Entries for ExportManager
                val entries = rows.map { mapRowToEntry(it) }
                
                // Map weekly totals
                val weeklyMap = summaries.associate { it.weekNumber to SessionRepository.formatHours(it.totalHours) }
                val totalStr = SessionRepository.formatHours(total)

                val exportManager = ExportManager(context)
                val file = when (format) {
                    ExportFormat.EXCEL -> exportManager.exportToExcel(entries, weeklyMap, totalStr, month)
                    ExportFormat.WORD -> exportManager.exportToDocx(entries, weeklyMap, totalStr, month)
                    ExportFormat.PDF -> exportManager.exportToPdf(entries, weeklyMap, totalStr, month)
                }
                
                // Try to save to public Downloads, fallback to internal FileProvider if fails
                val (finalUri, isSavedToDownloads) = try {
                    android.util.Log.d("TimesheetViewModel", "Calling saveToDownloads...")
                    val uri = exportManager.saveToDownloads(file)
                    android.util.Log.d("TimesheetViewModel", "Export success. Uri: $uri")
                    uri to true
                } catch (e: Exception) {
                    android.util.Log.e("TimesheetViewModel", "saveToDownloads failed, falling back to FileProvider", e)
                    // Fallback to FileProvider
                    exportManager.getUriForFile(file) to false
                }
                
                if (finalUri != null) {
                     _exportResult.value = ExportResult.Success(file, format, finalUri, isSavedToDownloads)
                } else {
                     throw Exception("Could not generate URI for file")
                }

            } catch (e: Exception) {
                android.util.Log.e("TimesheetViewModel", "Export failed", e)
                _exportResult.value = ExportResult.Error("Fehler: ${e.localizedMessage}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveExport(context: Context, uri: android.net.Uri, format: ExportFormat) {
         viewModelScope.launch {
            _isLoading.value = true
            try {
                // Collect data
                val rows = timesheetRows.first()
                val summaries = weeklySummaries.first()
                val total = monthlyTotal.first()
                val month = _selectedMonth.value

                val entries = rows.map { mapRowToEntry(it) }
                val weeklyMap = summaries.associate { it.weekNumber to SessionRepository.formatHours(it.totalHours) }
                val totalStr = SessionRepository.formatHours(total)
                
                val exportManager = ExportManager(context)
                val file = when (format) {
                    ExportFormat.EXCEL -> exportManager.exportToExcel(entries, weeklyMap, totalStr, month)
                    ExportFormat.WORD -> exportManager.exportToDocx(entries, weeklyMap, totalStr, month)
                    ExportFormat.PDF -> exportManager.exportToPdf(entries, weeklyMap, totalStr, month)
                }
                
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    file.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                _exportResult.value = ExportResult.Success(file, format, uri, true)
            } catch (e: Exception) {
                _exportResult.value = ExportResult.Error(e.message ?: "Save failed")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearExportResult() {
        _exportResult.value = null
    }
}

data class TimesheetUiState(
    val currentMonth: YearMonth = YearMonth.now(),
    val items: List<TimesheetItem> = emptyList(),
    val monthlyTotal: String = "0.00h",
    val isLoading: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val showExportMenu: Boolean = false,
    val exportResult: ExportResult? = null
)

sealed interface TimesheetItem {
    data class DayEntry(val entry: TimesheetEntry) : TimesheetItem
    data class WeekTotal(val weekNumber: Int, val total: String) : TimesheetItem
}

enum class TimeField {
    START_1, STOP_1, START_2, STOP_2
}

sealed class ExportResult {
    data class Success(
        val file: File, 
        val format: ExportFormat, 
        val uri: android.net.Uri?,
        val savedToDownloads: Boolean
    ) : ExportResult()
    data class Error(val message: String) : ExportResult()
}
