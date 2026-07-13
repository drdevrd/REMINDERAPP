package com.hshospital.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val text        = intent.getStringExtra("reminder_text") ?: return
        val prefs       = context.getSharedPreferences("reminder_prefs", Context.MODE_PRIVATE)
        val slot        = intent.getIntExtra("slot", 1)
        val daily       = intent.getBooleanExtra("daily", false)
        val intervalMin = intent.getIntExtra("interval_minutes", prefs.getInt("interval_minutes", 1))
        val ringSec     = intent.getIntExtra("ring_duration_sec", prefs.getInt("ring_duration_sec", 30))

        val runningKey = if (slot == MainActivity.SLOT_SCHEDULED) "scheduled_running" else "slot${slot}_running"
        if (!prefs.getBoolean(runningKey, false)) return

        // Schedule next ring FIRST — every X minutes regardless of daily or not
        val nextTrigger = System.currentTimeMillis() + intervalMin * 60 * 1000L
        scheduleNext(context, prefs, slot, text, intervalMin, ringSec, daily, nextTrigger)

        // Check DND — skip ring but next already scheduled
        if (isInDnd(prefs)) return

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ReminderApp::AlarmWakeLock")
        wakeLock.acquire(60 * 1000L)

        val serviceIntent = Intent(context, ReminderService::class.java).apply {
            putExtra("reminder_text", text)
            putExtra("ring_duration_sec", ringSec)
            putExtra("interval_minutes", intervalMin)
            putExtra("slot", slot)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent)
        else context.startService(serviceIntent)

        wakeLock.release()
    }

    private fun isInDnd(prefs: android.content.SharedPreferences): Boolean {
        if (!prefs.getBoolean("dnd_enabled", false)) return false
        val now      = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val dndStart = prefs.getInt("dnd_start_hour", 22)
        val dndEnd   = prefs.getInt("dnd_end_hour", 7)
        return if (dndStart > dndEnd) now >= dndStart || now < dndEnd
               else now >= dndStart && now < dndEnd
    }

    private fun scheduleNext(
        context: Context,
        prefs: android.content.SharedPreferences,
        slot: Int, text: String,
        intervalMin: Int, ringSec: Int,
        daily: Boolean,
        nextTrigger: Long
    ) {
        val nextKey = if (slot == MainActivity.SLOT_SCHEDULED) "scheduled_next_trigger" else "slot${slot}_next_trigger"
        prefs.edit().putLong(nextKey, nextTrigger).apply()

        val nextIntent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("reminder_text", text)
            putExtra("interval_minutes", intervalMin)
            putExtra("ring_duration_sec", ringSec)
            putExtra("slot", slot)
            putExtra("daily", daily)
        }
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
    }
}
