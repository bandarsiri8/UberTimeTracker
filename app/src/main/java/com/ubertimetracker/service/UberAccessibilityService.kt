package com.ubertimetracker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ubertimetracker.util.UberEatsLanguageDetector
import com.ubertimetracker.util.UberEatsLanguageDetector.UberStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class UberAccessibilityService : AccessibilityService() {

    companion object {
        private var instance: UberAccessibilityService? = null
        
        // Debug log - only shows Uber Eats status
        private val _debugLog = MutableStateFlow<List<DebugEntry>>(emptyList())
        val debugLog: StateFlow<List<DebugEntry>> = _debugLog.asStateFlow()
        
        // Current Uber Eats status
        private val _uberStatus = MutableStateFlow(UberStatus.UNKNOWN)
        val uberStatus: StateFlow<UberStatus> = _uberStatus.asStateFlow()
        
        // Service enabled status
        private val _isServiceEnabled = MutableStateFlow(false)
        val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()
        
        fun getInstance(): UberAccessibilityService? = instance
        
        fun clearDebugLog() {
            _debugLog.value = emptyList()
        }

        fun updateStatus(newStatus: UberStatus, context: android.content.Context? = null) {
            if (newStatus != _uberStatus.value && newStatus != UberStatus.UNKNOWN) {
                _uberStatus.value = newStatus
                val statusText = when (newStatus) {
                    UberStatus.ONLINE -> "Online (Notif)"
                    UberStatus.OFFLINE -> "Offline (Notif)"
                    UberStatus.UNKNOWN -> "Unknown"
                }
                
                val entry = DebugEntry(status = newStatus, message = statusText)
                val currentList = _debugLog.value.toMutableList()
                currentList.add(0, entry)
                if (currentList.size > 50) currentList.removeAt(currentList.lastIndex)
                _debugLog.value = currentList
                
                // Notify timer service
                if (instance != null) {
                    instance?.notifyTimerService(newStatus)
                } else if (context != null) {
                    // Fallback: Use provided context to broadcast
                    val intent = android.content.Intent("com.ubertimetracker.UBER_STATUS_CHANGED").apply {
                        putExtra("status", newStatus.name)
                    }
                    context.sendBroadcast(intent)
                }
            }
        }
    }

    data class DebugEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val status: UberStatus,
        val message: String
    )

    private var lastStatus: UberStatus = UberStatus.UNKNOWN
    private var lastStatusChangeTime: Long = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceEnabled.value = true
        
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
        serviceInfo = info
        
        addDebugEntry(UberStatus.UNKNOWN, "Service started")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        
        val packageName = event.packageName?.toString() ?: return
        
        // Only process Uber Eats app events
        if (!UberEatsLanguageDetector.isUberEatsApp(packageName)) {
            return
        }

        // Extract text from screen
        val screenText = extractScreenText(event.source)
        if (screenText.isBlank()) return

        // Detect status
        val newStatus = UberEatsLanguageDetector.detectStatus(screenText)
        
        // Only update if status changed and enough time has passed (debounce)
        val currentTime = System.currentTimeMillis()
        if (newStatus != lastStatus && newStatus != UberStatus.UNKNOWN) {
            if (currentTime - lastStatusChangeTime > 1000) { // 1 second debounce
                lastStatus = newStatus
                lastStatusChangeTime = currentTime
                _uberStatus.value = newStatus
                
                val statusText = when (newStatus) {
                    UberStatus.ONLINE -> "Online"
                    UberStatus.OFFLINE -> "Offline"
                    UberStatus.UNKNOWN -> "Unknown"
                }
                addDebugEntry(newStatus, statusText)
                
                // Notify timer service about status change
                notifyTimerService(newStatus)
            }
        }
    }

    private fun extractScreenText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        
        val builder = StringBuilder()
        
        // Get text from current node
        node.text?.let { builder.append(it).append(" ") }
        node.contentDescription?.let { builder.append(it).append(" ") }
        
        // Recursively get text from children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                builder.append(extractScreenText(child))
                // child.recycle() - Deprecated and unnecessary in Kotlin/GC world
            }
        }
        
        return builder.toString()
    }

    private fun addDebugEntry(status: UberStatus, message: String) {
        val entry = DebugEntry(
            status = status,
            message = message
        )
        
        // Keep only last 50 entries
        val currentList = _debugLog.value.toMutableList()
        currentList.add(0, entry)
        if (currentList.size > 50) {
            currentList.removeAt(currentList.lastIndex)
        }
        _debugLog.value = currentList
    }

    private fun notifyTimerService(status: UberStatus) {
        // Send broadcast to timer service
        val intent = android.content.Intent("com.ubertimetracker.UBER_STATUS_CHANGED").apply {
            putExtra("status", status.name)
        }
        sendBroadcast(intent)
    }

    override fun onInterrupt() {
        // Service interrupted
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isServiceEnabled.value = false
        addDebugEntry(UberStatus.UNKNOWN, "Service stopped")
    }
}
