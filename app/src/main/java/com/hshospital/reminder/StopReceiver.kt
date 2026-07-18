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
        val prefs = context.getSharedPreferences("reminder_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("slot1_running", false)
            .putBoolean("slot2_running", false)
            .putBoolean("scheduled_running", false)
            .putBoolean("scheduled2_running", false)
            .putBoolean("is_running", false)
            .apply()

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (slot in listOf(1, 2, MainActivity.SLOT_SCHEDULED, MainActivity.SLOT_SCHEDULED2)) {
            val pi = PendingIntent.getBroadcast(context, slot, Intent(context, AlarmReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            am.cancel(pi)
        }

        val stopServiceIntent = Intent(context, ReminderService::class.java).apply { action = "STOP" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(stopServiceIntent)
        else context.startService(stopServiceIntent)

        context.startService(Intent(context, PersistentService::class.java).apply { action = "STOP" })
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()
    }
}
