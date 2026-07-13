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

        // Mark all slots stopped
        val prefs = context.getSharedPreferences("reminder_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("slot1_running", false)
            .putBoolean("slot2_running", false)
            .putBoolean("slot3_running", false)
            .putBoolean("scheduled_running", false)
            .putBoolean("is_running", false)
            .apply()

        // Cancel all pending alarms for all slots
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (slot in listOf(1, 2, 3, MainActivity.SLOT_SCHEDULED)) {
            val pi = PendingIntent.getBroadcast(
                context, slot, Intent(context, AlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(pi)
        }

        // Stop ReminderService — send STOP action
        val stopServiceIntent = Intent(context, ReminderService::class.java)
        stopServiceIntent.action = "STOP"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(stopServiceIntent)
        } else {
            context.startService(stopServiceIntent)
        }

        // Stop PersistentService
        val stopPersistIntent = Intent(context, PersistentService::class.java)
        stopPersistIntent.action = "STOP"
        context.startService(stopPersistIntent)

        // Cancel all notifications
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancelAll()
    }
}
