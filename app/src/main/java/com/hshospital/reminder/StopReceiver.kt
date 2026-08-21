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
        val stopIntent = Intent(context, ReminderService::class.java).apply { action = "STOP" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(stopIntent)
        else context.startService(stopIntent)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(ReminderService.NOTIF_HIDDEN)
        nm.cancel(ReminderService.NOTIF_ALERT)

        val slot = intent.getIntExtra("slot", -1)
        if (slot != -1) {
            val prefs = context.getSharedPreferences("reminder_prefs", Context.MODE_PRIVATE)
            val runKey = when (slot) {
                MainActivity.SLOT_SCHEDULED  -> "scheduled_running"
                MainActivity.SLOT_SCHEDULED2 -> "scheduled2_running"
                99 -> "slot99_running"
                else -> "slot${slot}_running"
            }
            prefs.edit().putBoolean(runKey, false).apply()
            val pi = PendingIntent.getBroadcast(
                context, slot, Intent(context, AlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)
        }
    }
}
