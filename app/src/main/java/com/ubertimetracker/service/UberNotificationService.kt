package com.ubertimetracker.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.ubertimetracker.util.UberEatsLanguageDetector
import com.ubertimetracker.util.UberEatsLanguageDetector.UberStatus

class UberNotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("UberNotificationService", "Listener connected")
        // Check current notifications immediately
        checkActiveNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        processNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // If the persistent "You are online" notification is removed, we might be offline.
        // But we should double check active notifications to be sure.
        checkActiveNotifications()
    }

    private fun checkActiveNotifications() {
        try {
            val notifications = activeNotifications
            var isOnline = false
            
            for (sbn in notifications) {
                if (UberEatsLanguageDetector.isUberEatsApp(sbn.packageName)) {
                    val status = getStatusFromNotification(sbn)
                    if (status == UberStatus.ONLINE) {
                        isOnline = true
                        break
                    }
                }
            }

            // Update main status
            // If we found an explicit ONLINE notification, set ONLINE.
            // If not, we don't necessarily set OFFLINE immediately as we might just not have a notification?
            // Actually, for Uber Driver, if you are Online, there IS a persistent notification.
            // So absence of it implies Offline (or app closed).
            
            if (isOnline) {
                UberAccessibilityService.updateStatus(UberStatus.ONLINE, this)
            } else {
                // Only set offline if we are sure? 
                // Let's be careful not to override AccessibilityService if it sees something else.
                // However, notification is usually the strongest signal for background.
                UberAccessibilityService.updateStatus(UberStatus.OFFLINE, this)
            }
        } catch (e: Exception) {
            Log.e("UberNotificationService", "Error checking notifications", e)
        }
    }

    private fun processNotification(sbn: StatusBarNotification?) {
        sbn ?: return
        if (!UberEatsLanguageDetector.isUberEatsApp(sbn.packageName)) return

        val status = getStatusFromNotification(sbn)
        if (status != UberStatus.UNKNOWN) {
            UberAccessibilityService.updateStatus(status, this)
        }
    }

    private fun getStatusFromNotification(sbn: StatusBarNotification): UberStatus {
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getString("android.text") ?: ""
        
        val fullText = "$title $text"
        
        // Use the existing detector
        return UberEatsLanguageDetector.detectStatus(fullText)
    }
}
