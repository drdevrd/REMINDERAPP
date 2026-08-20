package com.hshospital.reminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class StopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {

        // Stop ReminderService
        val stopIntent = Intent(context, ReminderService::class.java).apply { action = "STOP" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(stopIntent)
        } else {
            context.startService(stopIntent)
        }

        // Cancel both notifications
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(ReminderService.NOTIF_RING)
        nm.cancel(ReminderService.NOTIF_ONGOING)
    }
}
