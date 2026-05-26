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

        // Acquire wake lock so CPU stays awake to ring
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ReminderApp::AlarmWakeLock"
        )
        wakeLock.acquire(60 * 1000L) // hold for max 60 seconds

        // Start service to ring
        val serviceIntent = Intent(context, ReminderService::class.java)
        serviceIntent.putExtra("reminder_text", text)
        serviceIntent.putExtra("ring_duration_sec", ringSec)
        serviceIntent.putExtra("wake_lock", true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // Schedule NEXT alarm
        val nextTrigger = System.currentTimeMillis() + intervalMin * 60 * 1000L
        val nextIntent = Intent(context, AlarmReceiver::class.java)
        nextIntent.putExtra("reminder_text", text)
        nextIntent.putExtra("interval_minutes", intervalMin)
        nextIntent.putExtra("ring_duration_sec", ringSec)

        val pi = PendingIntent.getBroadcast(
            context, 0, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, pi)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, nextTrigger, pi)
        }

        wakeLock.release()
    }
}
