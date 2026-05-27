package com.hshospital.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class PersistentService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    companion object {
        const val CHANNEL_PERSISTENT = "reminder_persistent"
        const val NOTIF_PERSISTENT = 3001
        const val WATCHDOG_INTERVAL = 60 * 1000L // check every 60 seconds
    }

    // Watchdog: re-arms alarm if it got killed
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            val prefs = getSharedPreferences("reminder_prefs", MODE_PRIVATE)
            if (prefs.getBoolean("is_running", false)) {
                val text        = prefs.getString("reminder_text", "") ?: ""
                val intervalMin = prefs.getInt("interval_minutes", 1)
                val ringSec     = prefs.getInt("ring_duration_sec", 30)
                val nextTrigger = prefs.getLong("next_trigger", 0L)

                // If next trigger is in the past, re-arm it
                if (nextTrigger > 0 && nextTrigger < System.currentTimeMillis() + 5000) {
                    reArmAlarm(text, intervalMin, ringSec)
                }
            }
            handler.postDelayed(this, WATCHDOG_INTERVAL)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            handler.removeCallbacks(watchdogRunnable)
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        // Minimal silent notification — lowest priority
        val notification = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_PERSISTENT)
            .setContentTitle("")
            .setContentText("")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .setShowWhen(false)
            .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_SECRET)
            .build()

        startForeground(NOTIF_PERSISTENT, notification)

        // Start watchdog
        handler.removeCallbacks(watchdogRunnable)
        handler.post(watchdogRunnable)

        return START_STICKY
    }

    private fun reArmAlarm(text: String, intervalMin: Int, ringSec: Int) {
        val nextTrigger = System.currentTimeMillis() + intervalMin * 60 * 1000L

        val intent = Intent(this, AlarmReceiver::class.java)
        intent.putExtra("reminder_text", text)
        intent.putExtra("interval_minutes", intervalMin)
        intent.putExtra("ring_duration_sec", ringSec)

        val pi = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val showPi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val am = getSystemService(ALARM_SERVICE) as AlarmManager
        am.setAlarmClock(AlarmManager.AlarmClockInfo(nextTrigger, showPi), pi)

        getSharedPreferences("reminder_prefs", MODE_PRIVATE)
            .edit().putLong("next_trigger", nextTrigger).apply()
    }

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_PERSISTENT,
            "Reminder Service",
            NotificationManager.IMPORTANCE_MIN
        )
        channel.setSound(null, null)
        channel.enableVibration(false)
        channel.setShowBadge(false)
        nm.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        handler.removeCallbacks(watchdogRunnable)
        // Restart self if killed
        val restartIntent = Intent(this, PersistentService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent)
        } else {
            startService(restartIntent)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
