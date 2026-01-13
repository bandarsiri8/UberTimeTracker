package com.ubertimetracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ubertimetracker.service.TimerService

class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Start the TimerService to ensure it's listening for events and resuming any active sessions
            val serviceIntent = Intent(context, TimerService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
