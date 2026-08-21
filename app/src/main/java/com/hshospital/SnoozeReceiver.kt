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
        val text        = intent.getStringExtra("reminder_text") ?: "Reminder"
        val intervalMin = intent.getIntExtra("interval_minutes", 1)
        val snoozeMs    = intent.getLongExtra("snooze_ms", 2 * 60 * 60 * 1000L)
        val slot        = intent.getIntExtra("slot", -1)

        val prefs = context.getSharedPreferences("reminder_prefs", Context.MODE_PRIVATE)
        val ringSec = prefs.getInt(
            if (slot == MainActivity.SLOT_SCHEDULED || slot == MainActivity.SLOT_SCHEDULED2)
                "sched_ring_duration_sec" else "ring_duration_sec", 30
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Stop current ring
        val stopIntent = Intent(context, ReminderService::class.java).apply { action = "STOP" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(stopIntent)
        else context.startService(stopIntent)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(ReminderService.NOTIF_HIDDEN)
        nm.cancel(ReminderService.NOTIF_ALERT)

        // Cancel all repeating alarms so nothing rings until snooze ends
        for (s in listOf(1, 2, MainActivity.SLOT_SCHEDULED, MainActivity.SLOT_SCHEDULED2, 99)) {
            val pi = PendingIntent.getBroadcast(
                context, s, Intent(context, AlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(pi)
        }
        prefs.edit()
            .putBoolean("slot1_running", false)
            .putBoolean("slot2_running", false)
            .putBoolean("scheduled_running", false)
            .putBoolean("scheduled2_running", false)
            .putBoolean("slot99_running", true)
            .apply()

        // Schedule single ring after snooze
        val triggerMs = System.currentTimeMillis() + snoozeMs
        val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("reminder_text", text)
            putExtra("ring_duration_sec", ringSec)
            putExtra("interval_minutes", intervalMin)
            putExtra("slot", 99)
            putExtra("daily", false)
        }
        val pi = PendingIntent.getBroadcast(context, 99, alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val showPi = PendingIntent.getActivity(context, 99, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerMs, showPi), pi)

        val hours = (snoozeMs / (60 * 60 * 1000)).toInt()
        val label = if (hours >= 24) "1 day" else "$hours hours"
        Toast.makeText(context, "Snoozed for $label", Toast.LENGTH_SHORT).show()
    }
}
