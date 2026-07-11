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
        val prefs       = context.getSharedPreferences("reminder_prefs", Context.MODE_PRIVATE)
        val slot        = intent.getIntExtra("slot", 1)
        val intervalMin = intent.getIntExtra("interval_minutes", prefs.getInt("interval_minutes", 1))
        val ringSec     = intent.getIntExtra("ring_duration_sec", prefs.getInt("ring_duration_sec", 30))

        if (!prefs.getBoolean("slot${slot}_running", false)) return

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ReminderApp::AlarmWakeLock")
        wakeLock.acquire(60 * 1000L)

        val serviceIntent = Intent(context, ReminderService::class.java)
        serviceIntent.putExtra("reminder_text", text)
        serviceIntent.putExtra("ring_duration_sec", ringSec)
        serviceIntent.putExtra("interval_minutes", intervalMin)
        serviceIntent.putExtra("slot", slot)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        val nextTrigger = System.currentTimeMillis() + intervalMin * 60 * 1000L
        prefs.edit().putLong("slot${slot}_next_trigger", nextTrigger).apply()

        val nextIntent = Intent(context, AlarmReceiver::class.java)
        nextIntent.putExtra("reminder_text", text)
        nextIntent.putExtra("interval_minutes", intervalMin)
        nextIntent.putExtra("ring_duration_sec", ringSec)
        nextIntent.putExtra("slot", slot)

        val pi = PendingIntent.getBroadcast(
            context, slot, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val showPi = PendingIntent.getActivity(
            context, slot, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAlarmClock(AlarmManager.AlarmClockInfo(nextTrigger, showPi), pi)

        wakeLock.release()
    }
}
