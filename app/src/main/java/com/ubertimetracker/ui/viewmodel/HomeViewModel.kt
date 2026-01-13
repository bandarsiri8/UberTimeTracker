package com.ubertimetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ubertimetracker.data.model.*
import com.ubertimetracker.data.repository.SessionRepository
import com.ubertimetracker.data.repository.SettingsRepository
import com.ubertimetracker.service.UberAccessibilityService
import com.ubertimetracker.util.UberEatsLanguageDetector.UberStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.DayOfWeek
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository,
    private val exportManager: com.ubertimetracker.util.ExportManager,
    private val googleDriveManager: com.ubertimetracker.util.GoogleDriveManager
) : ViewModel() {

    private val _timerState = MutableStateFlow(TimerState())
    private val _debugLogs = MutableStateFlow<List<DebugLogEntry>>(emptyList())
    private val _detectedLanguage = MutableStateFlow(DetectedLanguage("EN", "English"))
    private val _uberStatus = MutableStateFlow(UberAppStatus.UNKNOWN)
    private val _autoSyncEnabled = MutableStateFlow(true)
    private val _offlineCacheReady = MutableStateFlow(true)
    private val _cloudSyncActive = MutableStateFlow(true)
    private val _startTime = MutableStateFlow<LocalTime?>(null)

    private var timerJob: Job? = null

    val settings = settingsRepository.getSettings()
        .stateIn(viewModelScope, SharingStarted.Lazily, AppSettings())

    val uiState: StateFlow<HomeUiState> = combine(
        listOf(
            _timerState,
            _debugLogs,
            _detectedLanguage,
            _uberStatus,
            _autoSyncEnabled,
            _offlineCacheReady,
            _cloudSyncActive,
            _startTime
        )
    ) { args ->
        val timer = args[0] as TimerState
        @Suppress("UNCHECKED_CAST")
        val logs = args[1] as List<DebugLogEntry>
        val lang = args[2] as DetectedLanguage
        val status = args[3] as UberAppStatus
        val auto = args[4] as Boolean
        val offline = args[5] as Boolean
        val cloud = args[6] as Boolean
        val start = args[7] as LocalTime?

        HomeUiState(
            timerState = timer,
            debugLogs = logs,
            detectedLanguage = lang,
            uberStatus = status,
            autoSyncEnabled = auto,
            offlineCacheReady = offline,
            cloudSyncActive = cloud,
            startTime = start
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = HomeUiState()
    )

    init {
        loadSettings()
        addInitialLogs()
        observeServiceData()
    }

    private fun observeServiceData() {
        viewModelScope.launch {
            launch {
                UberAccessibilityService.uberStatus.collect { status ->
                    // Map Service Status to App Status Model if needed, but they seem to be the same enum or compatible
                    // Actually Service uses UberEatsLanguageDetector.UberStatus, VM uses UberAppStatus.
                    // Need mapping.
                    _uberStatus.value = when (status) {
                        UberStatus.ONLINE -> UberAppStatus.ONLINE
                        UberStatus.OFFLINE -> UberAppStatus.OFFLINE
                        UberStatus.UNKNOWN -> UberAppStatus.UNKNOWN
                    }
                }
            }
            
            launch {
                UberAccessibilityService.debugLog.collect { entries ->
                    _debugLogs.value = entries.map { serviceEntry ->
                        DebugLogEntry(
                            timestamp = java.time.Instant.ofEpochMilli(serviceEntry.timestamp)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDateTime(),
                            type = LogType.SYSTEM, // Using SYSTEM for service logs
                            message = serviceEntry.message,
                            details = null
                        )
                    }
                }
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            settingsRepository.getSettings().collect { settings ->
                _autoSyncEnabled.value = settings.autoSyncEnabled
                _offlineCacheReady.value = settings.offlineCacheEnabled
                _cloudSyncActive.value = settings.cloudSyncEnabled
            }
        }
    }

    private fun addInitialLogs() {
        addLog(LogType.SYSTEM, "App initialized.")
    }

    fun startTimer() {
        viewModelScope.launch {
            val now = LocalTime.now()
            _startTime.value = now
            
            val sessionId = sessionRepository.createSession(
                date = LocalDate.now(),
                startTime = now
            )
            
            _timerState.value = TimerState(
                isRunning = true,
                isPaused = false,
                startTimeMillis = System.currentTimeMillis(),
                currentSessionId = sessionId
            )
            
            addLog(LogType.ACTION, "Auto-Starting Timer...")
            addLog(LogType.SYNC, "Timer running.")
            
            startTimerTick()
        }
    }

    fun stopTimer() {
        viewModelScope.launch {
            timerJob?.cancel()
            
            _timerState.value.currentSessionId?.let { sessionId ->
                sessionRepository.stopSession(sessionId)
            }
            
            _timerState.value = TimerState(
                isRunning = false,
                isPaused = false
            )
            _startTime.value = null
            
            addLog(LogType.ACTION, "Auto-Stopping Timer...")
            addLog(LogType.SYNC, "Timer stopped.")
            
            // Trigger auto-export and upload
            if (settings.value.cloudSyncEnabled) {
                exportAndUploadCurrentMonth()
            }
        }
    }

    private fun exportAndUploadCurrentMonth() {
        viewModelScope.launch {
            try {
                if (googleDriveManager.getLastSignedInAccount() == null) {
                    addLog(LogType.WARNING, "Google Drive not connected. Skipping upload.")
                    return@launch
                }

                val now = LocalDate.now()
                val yearMonth = YearMonth.from(now)
                
                // Fetch ALL rows (days) for the month using SessionRepository
                // This ensures we get empty days as well, matching the Timesheet view
                val rows = sessionRepository.getTimesheetRows(yearMonth.year, yearMonth.monthValue).first()

                // Map TimesheetRow to TimesheetEntry
                val entries = rows.map { row ->
                    val sortedSessions = row.sessions.sortedBy { it.startTime1 }
                    val segments = mutableListOf<WorkSegment>()
                    var dailyTotalHours = 0.0

                    val pausesBySession = row.pauses.groupBy { it.sessionId }

                    sortedSessions.forEachIndexed { index, session ->
                        dailyTotalHours += session.totalHours
                        val sessionPauses = pausesBySession[session.id]?.sortedBy { it.startTime } ?: emptyList()

                        if (session.startTime1 != null) {
                            var currentWorkStart: LocalTime? = session.startTime1
                            val sessionEnd = session.stopTime1
                            
                            if (sessionPauses.isNotEmpty()) {
                                sessionPauses.forEach { pause ->
                                    if (currentWorkStart != null && currentWorkStart!!.isBefore(pause.startTime)) {
                                         segments.add(WorkSegment.Work(
                                             SessionRepository.formatTime(currentWorkStart),
                                             SessionRepository.formatTime(pause.startTime)
                                         ))
                                    }
                                    segments.add(WorkSegment.Pause(
                                        SessionRepository.formatTime(pause.startTime),
                                        SessionRepository.formatTime(pause.endTime),
                                        SessionRepository.formatPause(pause.durationMinutes)
                                    ))
                                    currentWorkStart = pause.endTime ?: pause.startTime
                                }
                                if (currentWorkStart != null && (sessionEnd == null || currentWorkStart!!.isBefore(sessionEnd))) {
                                    segments.add(WorkSegment.Work(
                                        SessionRepository.formatTime(currentWorkStart),
                                        SessionRepository.formatTime(sessionEnd)
                                    ))
                                }
                            } else {
                                segments.add(WorkSegment.Work(
                                    SessionRepository.formatTime(session.startTime1),
                                    SessionRepository.formatTime(session.stopTime1)
                                ))
                            }
                        }

                         // Logic for gap between sessions - Treat as Pause
                        if (index < sortedSessions.size - 1) {
                            val nextSession = sortedSessions[index + 1]
                            if (session.stopTime1 != null && nextSession.startTime1 != null) {
                                // Use minute-level precision for gap to match visual "HH:mm" output
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
                    
                    // Recalculate total hours and pauses for the entry if needed or trust the inputs
                    // Use a more robust total hours display if calculated is 0 but we have segments?
                    // For now, rely on dailyTotalHours which sums session.totalHours.

                    TimesheetEntry(
                        date = row.date,
                        segments = segments,
                        totalDailyHours = SessionRepository.formatHours(dailyTotalHours),
                        hasConflict = sortedSessions.any { it.hasConflict },
                        weekNumber = row.weekNumber
                    )
                }

                // Calculate weekly totals
                val weeklyTotals = rows.groupBy { it.weekNumber }.mapValues { (_, weekRows) ->
                     SessionRepository.formatHours(weekRows.sumOf { row -> row.sessions.sumOf { it.totalHours } })
                }
                
                val monthlyTotal = SessionRepository.formatHours(rows.sumOf { row -> row.sessions.sumOf { it.totalHours } })

                // Generate DOCX
                val file = exportManager.exportToDocx(
                    entries,
                    weeklyTotals,
                    monthlyTotal,
                    yearMonth
                )
                
                addLog(LogType.SYNC, "Exported ${file.name}")

                // Upload to Drive
                val fileId = googleDriveManager.uploadFile(file, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                
                if (fileId != null) {
                     addLog(LogType.SYNC, "Uploaded to Drive. ID: $fileId")
                } else {
                     addLog(LogType.ERROR, "Upload failed.")
                }

            } catch (e: Exception) {
                addLog(LogType.ERROR, "Export/Upload Error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun pauseTimer() {
        viewModelScope.launch {
            val currentState = _timerState.value
            if (!currentState.isRunning || currentState.isPaused) return@launch
            
            currentState.currentSessionId?.let { sessionId ->
                sessionRepository.addPause(sessionId)
            }
            
            _timerState.value = currentState.copy(
                isPaused = true,
                pausedTimeMillis = System.currentTimeMillis()
            )
            
            addLog(LogType.ACTION, "Pausing Timer...")
        }
    }

    fun resumeTimer() {
        viewModelScope.launch {
            val currentState = _timerState.value
            if (!currentState.isRunning || !currentState.isPaused) return@launch
            
            currentState.currentSessionId?.let { sessionId ->
                sessionRepository.getActivePause(sessionId)?.let { pause ->
                    sessionRepository.endPause(pause.id)
                }
                sessionRepository.resumeSession(sessionId)
            }
            
            val pausedDuration = System.currentTimeMillis() - currentState.pausedTimeMillis
            _timerState.value = currentState.copy(
                isPaused = false,
                totalPausedDuration = currentState.totalPausedDuration + pausedDuration
            )
            
            addLog(LogType.ACTION, "Resuming Timer...")
        }
    }

    private fun startTimerTick() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val currentState = _timerState.value
                if (currentState.isRunning && !currentState.isPaused) {
                    val elapsed = System.currentTimeMillis() - 
                        currentState.startTimeMillis - 
                        currentState.totalPausedDuration
                    _timerState.value = currentState.copy(elapsedTime = elapsed)
                }
            }
        }
    }

    fun toggleAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateAutoSync(enabled)
            _autoSyncEnabled.value = enabled
            addLog(LogType.SYSTEM, "Auto-Sync ${if (enabled) "enabled" else "disabled"}.")
        }
    }

    fun onOcrDetected(text: String) {
        addLog(LogType.OCR, "Detected Text: \"$text\"")
        detectLanguage(text)
        detectStatus(text)
    }

    private fun detectLanguage(text: String) {
        // Language detection based on common Uber app words
        val languageMap = mapOf(
            "Go" to DetectedLanguage("EN", "English"),
            "Start" to DetectedLanguage("EN", "English"),
            "ابدأ" to DetectedLanguage("AR", "Arabic"),
            "Los geht's" to DetectedLanguage("DE", "German"),
            "Aller" to DetectedLanguage("FR", "French"),
            "Inizia" to DetectedLanguage("IT", "Italian"),
            "Começar" to DetectedLanguage("PT", "Portuguese"),
            "Başla" to DetectedLanguage("TR", "Turkish"),
            "Начать" to DetectedLanguage("RU", "Russian"),
            "शुरू" to DetectedLanguage("HI", "Hindi"),
            "開始" to DetectedLanguage("JA", "Japanese"),
            "Empezar" to DetectedLanguage("ES", "Spanish")
        )

        languageMap.forEach { (keyword, language) ->
            if (text.contains(keyword, ignoreCase = true)) {
                _detectedLanguage.value = language
                addLog(LogType.LANGUAGE, "Detected Language: ${language.name} (${language.code})")
                return
            }
        }
    }

    private fun detectStatus(text: String) {
        val statusKeywords = mapOf(
            "online" to UberAppStatus.ONLINE,
            "go" to UberAppStatus.ONLINE,
            "ابدأ" to UberAppStatus.ONLINE,
            "offline" to UberAppStatus.OFFLINE,
            "stop" to UberAppStatus.OFFLINE,
            "busy" to UberAppStatus.BUSY,
            "trip" to UberAppStatus.BUSY
        )

        statusKeywords.forEach { (keyword, status) ->
            if (text.contains(keyword, ignoreCase = true)) {
                val previousStatus = _uberStatus.value
                _uberStatus.value = status
                
                if (previousStatus != status) {
                    addLog(LogType.ANALYSIS, "Status detected: ${status.name}.")
                    
                    if (_autoSyncEnabled.value) {
                        when (status) {
                            UberAppStatus.ONLINE -> {
                                if (!_timerState.value.isRunning) {
                                    startTimer()
                                }
                            }
                            UberAppStatus.OFFLINE -> {
                                if (_timerState.value.isRunning) {
                                    stopTimer()
                                }
                            }
                            else -> {}
                        }
                    }
                }
                return
            }
        }
    }

    fun addLog(type: LogType, message: String, details: String? = null) {
        val entry = DebugLogEntry(
            timestamp = LocalDateTime.now(),
            type = type,
            message = message,
            details = details
        )
        _debugLogs.value = (_debugLogs.value + entry).takeLast(100)
    }

    fun clearLogs() {
        _debugLogs.value = emptyList()
    }

    fun formatElapsedTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = millis / (1000 * 60 * 60)
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}

data class HomeUiState(
    val timerState: TimerState = TimerState(),
    val debugLogs: List<DebugLogEntry> = emptyList(),
    val detectedLanguage: DetectedLanguage = DetectedLanguage("EN", "English"),
    val uberStatus: UberAppStatus = UberAppStatus.UNKNOWN,
    val autoSyncEnabled: Boolean = true,
    val offlineCacheReady: Boolean = true,
    val cloudSyncActive: Boolean = true,
    val startTime: LocalTime? = null
)
