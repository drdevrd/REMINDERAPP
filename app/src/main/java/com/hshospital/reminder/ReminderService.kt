package com.hshospital.reminder

import android.app.*
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
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
    private var vibrator: Vibrator? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        // STOP action — called by StopReceiver
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

        val text    = intent?.getStringExtra("reminder_text") ?: "Reminder"
        val ringSec = intent?.getIntExtra("ring_duration_sec", 30) ?: 30

        val stopPi = PendingIntent.getBroadcast(
            this, 1, Intent(this, StopReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openPi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Foreground silent persistent notification
        startForeground(NOTIF_ONGOING,
            NotificationCompat.Builder(this, CHANNEL_ONGOING)
                .setContentTitle("Reminder active")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentIntent(openPi)
                .addAction(android.R.drawable.ic_delete, "STOP", stopPi)
                .setOngoing(true)
                .setSilent(true)
                .build()
        )

        // Ringing notification
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_RING,
            NotificationCompat.Builder(this, CHANNEL_RING)
                .setContentTitle("⏰ REMINDER")
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
        )

        // Play alarm ringtone — bypasses silent mode
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(this, uri)
        ringtone?.let { rt ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                rt.isLooping = true
                rt.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                rt.streamType = AudioManager.STREAM_ALARM
            }
            rt.play()
        }

        // Vibrate — bypasses silent/vibrate settings
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
                vibrator?.vibrate(effect, android.os.VibrationAttributes.Builder()
                    .setUsage(android.os.VibrationAttributes.USAGE_ALARM)
                    .build())
            } else {
                vibrator?.vibrate(effect)
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }

        // Auto-stop after ring_duration_sec
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
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RING, "Reminder Alarm", NotificationManager.IMPORTANCE_HIGH).apply {
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
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, "Reminder Status", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
            }
        )
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        ringtone?.stop()
        vibrator?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
