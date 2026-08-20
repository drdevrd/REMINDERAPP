package com.hshospital.reminder

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast

class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text        = intent.getStringExtra("reminder_text") ?: return
        val ringSec     = intent.getIntExtra("ring_duration_sec", 30)
        val intervalMin = intent.getIntExtra("interval_minutes", 1)
        val snoozeMs    = intent.getLongExtra("snooze_ms", 2 * 60 * 60 * 1000L)

        // Stop current ring immediately
        val stopIntent = Intent(context, ReminderService::class.java).apply { action = "STOP" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(stopIntent)
        else context.startService(stopIntent)

        // Cancel notifications
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(ReminderService.NOTIF_RING)
        nm.cancel(ReminderService.NOTIF_ONGOING)

        // Schedule ring after snooze duration
        val triggerMs = System.currentTimeMillis() + snoozeMs

        val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("reminder_text", text)
            putExtra("ring_duration_sec", ringSec)
            putExtra("interval_minutes", intervalMin)
            putExtra("slot", 99)
            putExtra("daily", false)
            putExtra("after_dnd", false)
        }
        val pi = PendingIntent.getBroadcast(
            context, 99, alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val showPi = PendingIntent.getActivity(
            context, 99, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Mark snooze slot as running so AlarmReceiver doesn't skip it
        context.getSharedPreferences("reminder_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("slot99_running", true).apply()

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerMs, showPi), pi)

        // Show toast confirmation
        val hours = (snoozeMs / (60 * 60 * 1000)).toInt()
        val label = if (hours >= 24) "1 day" else "$hours hours"
        Toast.makeText(context, "Snoozed for $label", Toast.LENGTH_SHORT).show()
    }
}
