package com.hshospital.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

class ReminderService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: android.media.Ringtone? = null
    private var vibrator: Vibrator? = null

    companion object {
        const val CHANNEL_RING = "reminder_ring"
        const val CHANNEL_ONGOING = "reminder_ongoing"
        const val NOTIF_RING = 2001
        const val NOTIF_ONGOING = 2002
    }

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (intent?.action == "STOP") {
            ringtone?.stop()
            vibrator?.cancel()
            handler.removeCallbacksAndMessages(null)
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIF_RING)
            nm.cancel(NOTIF_ONGOING)
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        val text = intent?.getStringExtra("reminder_text") ?: "Reminder"
        val ringSec = intent?.getIntExtra("ring_duration_sec", 30) ?: 30

        val stopIntent = Intent(this, StopReceiver::class.java)
        val stopPi = PendingIntent.getBroadcast(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainActivity::class.java)
        val openPi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val foregroundNotif = NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setContentTitle("Reminder active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_delete, "STOP", stopPi)
            .setOngoing(true)
            .setSilent(true)
            .build()

        startForeground(NOTIF_ONGOING, foregroundNotif)

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val ringNotif = NotificationCompat.Builder(this, CHANNEL_RING)
            .setContentTitle("REMINDER")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_delete, "STOP", stopPi)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        nm.notify(NOTIF_RING, ringNotif)

        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(this, uri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ringtone?.isLooping = true
            ringtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        } else {
            @Suppress("DEPRECATION")
            ringtone?.streamType = AudioManager.STREAM_ALARM
        }
        ringtone?.play()

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 800, 400, 800, 400, 800, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(pattern, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                vibrator?.vibrate(
                    effect,
                    android.os.VibrationAttributes.Builder()
                        .setUsage(android.os.VibrationAttributes.USAGE_ALARM)
                        .build()
                )
            } else {
                vibrator?.vibrate(effect)
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }

        handler.postDelayed({
            ringtone?.stop()
            vibrator?.cancel()
            nm.cancel(NOTIF_RING)
            stopForeground(true)
            stopSelf()
        }, ringSec * 1000L)

        return START_NOT_STICKY
    }

    private fun createChannels() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val ringChannel = NotificationChannel(
            CHANNEL_RING,
            "Reminder Alarm",
            NotificationManager.IMPORTANCE_HIGH
        )
        ringChannel.enableVibration(true)
        ringChannel.setSound(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        ringChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(ringChannel)

        val ongoingChannel = NotificationChannel(
            CHANNEL_ONGOING,
            "Reminder Status",
            NotificationManager.IMPORTANCE_LOW
        )
        ongoingChannel.setSound(null, null)
        nm.createNotificationChannel(ongoingChannel)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        ringtone?.stop()
        vibrator?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
