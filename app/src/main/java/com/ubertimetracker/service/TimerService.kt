package com.ubertimetracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ubertimetracker.MainActivity
import com.ubertimetracker.R
import com.ubertimetracker.data.repository.SessionRepository
import com.ubertimetracker.data.repository.SettingsRepository
import com.ubertimetracker.util.UberEatsLanguageDetector.UberStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class TimerService : Service() {

    companion object {
        private const val CHANNEL_ID = "timer_channel"
        private const val NOTIFICATION_ID = 1001
        
        private val _timerState = MutableStateFlow(TimerState())
        val timerState: StateFlow<TimerState> = _timerState.asStateFlow()
        
        private val _elapsedTime = MutableStateFlow(0L)
        val elapsedTime: StateFlow<Long> = _elapsedTime.asStateFlow()
    }

    data class TimerState(
        val isRunning: Boolean = false,
        val isPaused: Boolean = false,
        val startTime: Long = 0L,
        val pausedTime: Long = 0L,
        val currentSessionId: Long? = null
    )

    @Inject
    lateinit var sessionRepository: SessionRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var timerJob: Job? = null
    private var autoSyncEnabled = false

    private val uberStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.ubertimetracker.UBER_STATUS_CHANGED" && autoSyncEnabled) {
                val statusName = intent.getStringExtra("status") ?: return
                val status = UberStatus.valueOf(statusName)
                handleUberStatusChange(status)
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerReceiver(
            uberStatusReceiver,
            IntentFilter("com.ubertimetracker.UBER_STATUS_CHANGED"),
            RECEIVER_NOT_EXPORTED
        )

        // Observe settings for auto-sync
        serviceScope.launch {
            settingsRepository.getSettings().collect { settings ->
                autoSyncEnabled = settings.autoSyncEnabled
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> startTimer()
            "PAUSE" -> pauseTimer()
            "RESUME" -> resumeTimer()
            "STOP" -> stopTimer()
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Uber Zeiterfassung",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Timer notification"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(elapsedMs: Long): Notification {
        val hours = TimeUnit.MILLISECONDS.toHours(elapsedMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMs) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedMs) % 60
        val timeText = String.format("%02d:%02d:%02d", hours, minutes, seconds)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Notification WITHOUT "running in background" text
        // Only shows: "Uber Zeiterfassung" title and "Sitzung läuft: HH:MM:SS"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Uber Zeiterfassung")
            .setContentText("Sitzung läuft: $timeText")
            .setSmallIcon(R.drawable.ic_timer)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun startTimer() {
        if (_timerState.value.isRunning) return
        
        val startTime = System.currentTimeMillis()
        
        serviceScope.launch {
            val sessionId = sessionRepository.createSession() // Defaults to now/now
            
            _timerState.value = TimerState(
                isRunning = true,
                isPaused = false,
                startTime = startTime,
                currentSessionId = sessionId
            )
            
            startForeground(NOTIFICATION_ID, buildNotification(0))
            startTimerJob()
        }
    }

    fun pauseTimer() {
        val state = _timerState.value
        if (!state.isRunning || state.isPaused) return
        
        timerJob?.cancel()
        
        serviceScope.launch {
            state.currentSessionId?.let { id ->
                sessionRepository.addPause(id)
            }
            
            _timerState.value = state.copy(
                isPaused = true,
                pausedTime = System.currentTimeMillis()
            )
        }
    }

    fun resumeTimer() {
        val state = _timerState.value
        if (!state.isRunning || !state.isPaused) return
        
        serviceScope.launch {
            state.currentSessionId?.let { id ->
                sessionRepository.getActivePause(id)?.let { pause ->
                    sessionRepository.endPause(pause.id)
                }
                sessionRepository.resumeSession(id)
            }

            val pauseDuration = System.currentTimeMillis() - state.pausedTime
            _timerState.value = state.copy(
                isPaused = false,
                startTime = state.startTime + pauseDuration,
                pausedTime = 0L
            )
            
            startTimerJob()
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
        val state = _timerState.value
        
        serviceScope.launch {
            state.currentSessionId?.let { id ->
                sessionRepository.stopSession(id)
            }
            
            _timerState.value = TimerState()
            _elapsedTime.value = 0L
            
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startTimerJob() {
        timerJob = serviceScope.launch {
            while (isActive) {
                val state = _timerState.value
                if (state.isRunning && !state.isPaused) {
                    val elapsed = System.currentTimeMillis() - state.startTime
                    _elapsedTime.value = elapsed
                    
                    // Update notification
                    val notification = buildNotification(elapsed)
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.notify(NOTIFICATION_ID, notification)
                }
                delay(1000)
            }
        }
    }

    private fun handleUberStatusChange(status: UberStatus) {
        when (status) {
            UberStatus.ONLINE -> {
                if (!_timerState.value.isRunning) {
                    startTimer()
                } else if (_timerState.value.isPaused) {
                    resumeTimer()
                }
            }
            UberStatus.OFFLINE -> {
                if (_timerState.value.isRunning && !_timerState.value.isPaused) {
                    pauseTimer()
                }
            }
            UberStatus.UNKNOWN -> {
                // Do nothing
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        serviceScope.cancel()
        try {
            unregisterReceiver(uberStatusReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
    }
}
