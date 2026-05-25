package com.hshospital.reminder

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {

        // 1. Mark stopped in prefs
        context.getSharedPreferences("reminder_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("is_running", false).apply()

        // 2. Cancel all pending alarms
        val alarmIntent = Intent(context, AlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 0, alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)

        // 3. Stop the foreground service
        val serviceIntent = Intent(context, ReminderService::class.java)
        serviceIntent.action = "STOP"
        context.startService(serviceIntent)

        // 4. Cancel ALL notifications directly
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancelAll()
    }
}
