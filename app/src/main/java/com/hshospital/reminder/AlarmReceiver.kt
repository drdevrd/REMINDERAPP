package com.hshospital.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val text        = intent.getStringExtra("reminder_text") ?: return
        val intervalMin = intent.getIntExtra("interval_minutes", 1)
        val ringSec     = intent.getIntExtra("ring_duration_sec", 30)

        val prefs = context.getSharedPreferences("reminder_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("is_running", false)) return

        // Wake lock
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ReminderApp::AlarmWakeLock")
        wakeLock.acquire(60 * 1000L)

        // Start ringing
        val serviceIntent = Intent(context, ReminderService::class.java)
        serviceIntent.putExtra("reminder_text", text)
        serviceIntent.putExtra("ring_duration_sec", ringSec)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // Schedule next alarm
        val nextTrigger = System.currentTimeMillis() + intervalMin * 60 * 1000L

        // Save next trigger time for watchdog
        prefs.edit().putLong("next_trigger", nextTrigger).apply()

        val nextIntent = Intent(context, AlarmReceiver::class.java)
        nextIntent.putExtra("reminder_text", text)
        nextIntent.putExtra("interval_minutes", intervalMin)
        nextIntent.putExtra("ring_duration_sec", ringSec)

        val pi = PendingIntent.getBroadcast(
            context, 0, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val showPi = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAlarmClock(AlarmManager.AlarmClockInfo(nextTrigger, showPi), pi)

        wakeLock.release()
    }
}
