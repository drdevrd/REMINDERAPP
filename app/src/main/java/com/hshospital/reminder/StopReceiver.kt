package com.hshospital.reminder

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class StopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Only stop the currently ringing service — do NOT cancel alarms for other slots
        val prefs = context.getSharedPreferences("reminder_prefs", Context.MODE_PRIVATE)

        // Stop only the ReminderService (the ringing)
        val stopServiceIntent = Intent(context, ReminderService::class.java).apply { action = "STOP" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(stopServiceIntent)
        } else {
            context.startService(stopServiceIntent)
        }

        // Cancel only the ring notification — leave ongoing and alarms intact
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(ReminderService.NOTIF_RING)
        nm.cancel(ReminderService.NOTIF_ONGOING)
    }
}
