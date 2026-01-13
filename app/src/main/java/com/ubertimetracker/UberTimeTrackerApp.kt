package com.ubertimetracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class UberTimeTrackerApp : Application() {

    companion object {
        const val TIMER_CHANNEL_ID = "timer_channel"
        const val SYNC_CHANNEL_ID = "sync_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timerChannel = NotificationChannel(
                TIMER_CHANNEL_ID,
                getString(R.string.notification_channel_timer),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_timer_desc)
                setShowBadge(false)
            }

            val syncChannel = NotificationChannel(
                SYNC_CHANNEL_ID,
                "Sync Status",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows sync status notifications"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(timerChannel)
            notificationManager.createNotificationChannel(syncChannel)
        }
    }
}
