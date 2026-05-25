package com.hshospital.reminder

import android.app.*
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.*
import androidx.core.app.NotificationCompat

class ReminderService : Service() {

    companion object {
        const val CHANNEL_RING    = "reminder_ring"
        const val CHANNEL_ONGOING = "reminder_ongoing"
        const val NOTIF_RING      = 2001
        const val NOTIF_ONGOING   = 2002
    }

    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: android.media.Ringtone? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text    = intent?.getStringExtra("reminder_text") ?: "Reminder"
        val ringSec = intent?.getIntExtra("ring_duration_sec", 30) ?: 30

        // Build stop pending intent
        val stopIntent = Intent(this, StopReceiver::class.java)
        val stopPi = PendingIntent.getBroadcast(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build open-app pending intent
        val openPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Foreground notification (required to keep service alive)
        val foregroundNotif = NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setContentTitle("Reminder active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPi)
            .setOngoing(true)
            .setSilent(true)
            .build()

        startForeground(NOTIF_ONGOING, foregroundNotif)

        // RINGING notification — full sound + vibration
        val ringNotif = NotificationCompat.Builder(this, CHANNEL_RING)
            .setContentTitle("⏰ REMINDER")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_RING, ringNotif)

        // Play ringtone
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(this, uri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ringtone?.isLooping = true
        }
        ringtone?.play()

        // Vibrate
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 1000, 500, 1000, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(pattern, 0)
        }

        // Stop ringing after ring_duration_sec
        handler.postDelayed({
            ringtone?.stop()
            vib.cancel()
            nm.cancel(NOTIF_RING)
            stopForeground(true)
            stopSelf()
        }, ringSec * 1000L)

        return START_NOT_STICKY
    }

    private fun createChannels() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // High-priority alarm channel
        val ringChannel = NotificationChannel(
            CHANNEL_RING, "Reminder Alarm",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Rings when reminder fires"
            enableVibration(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        // Silent ongoing channel
        val ongoingChannel = NotificationChannel(
            CHANNEL_ONGOING, "Reminder Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows while reminder is active"
            setSound(null, null)
        }

        nm.createNotificationChannel(ringChannel)
        nm.createNotificationChannel(ongoingChannel)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        ringtone?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
