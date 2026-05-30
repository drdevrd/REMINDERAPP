package com.hshospital.reminder

import android.app.Notification
import android.net.Uri
import android.app.Notification
import android.net.UriChannel
import android.app.Notification
import android.net.UriManager
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
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

class ReminderService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: android.media.Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_RING = "reminder_ring_v2"
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
            cleanup()
            return START_NOT_STICKY
        }

        val text = intent?.getStringExtra("reminder_text") ?: "Reminder"
        val ringSec = intent?.getIntExtra("ring_duration_sec", 30) ?: 30

        // Wake lock held for exactly ringSec + 5 seconds — ensures handler fires while screen locked
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ReminderApp::RingWakeLock")
        wakeLock?.acquire((ringSec + 5) * 1000L)

        val stopPi = PendingIntent.getBroadcast(
            this, 1, Intent(this, StopReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openPi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_RING,
            NotificationCompat.Builder(this, CHANNEL_RING)
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
        )

        // Play ringtone — NOT looping, plays once
        val prefs = getSharedPreferences("reminder_prefs", MODE_PRIVATE)
        val savedUri = prefs.getString("ringtone_uri", null)
        val uri = if (savedUri != null) android.net.Uri.parse(savedUri)
                  else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                      ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(this, uri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ringtone?.isLooping = false  // play once, not loop
            ringtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        } else {
            @Suppress("DEPRECATION")
            ringtone?.streamType = AudioManager.STREAM_ALARM
        }
        ringtone?.play()

        // Vibrate only if phone vibration is ON
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        if (audioManager.ringerMode == AudioManager.RINGER_MODE_VIBRATE ||
            audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            val vibrateWhenRinging = android.provider.Settings.System.getInt(
                contentResolver,
                android.provider.Settings.System.VIBRATE_WHEN_RINGING, 0
            )
            if (vibrateWhenRinging == 1 || audioManager.ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
                vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(VIBRATOR_SERVICE) as Vibrator
                }
                val pattern = longArrayOf(0, 800, 400, 800, 400)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1)) // -1 = no repeat
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, -1)
                }
            }
        }

        // Stop after ringSec — wake lock ensures this runs even when screen is locked
        handler.postDelayed({
            cleanup()
        }, ringSec * 1000L)

        return START_NOT_STICKY
    }

    private fun cleanup() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
        handler.removeCallbacksAndMessages(null)
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_RING)
        nm.cancel(NOTIF_ONGOING)
        stopForeground(true)
        stopSelf()
    }

    private fun createChannels() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val ringChannel = NotificationChannel(CHANNEL_RING, "Reminder Alarm", NotificationManager.IMPORTANCE_HIGH)
        ringChannel.enableVibration(false)
        ringChannel.setSound(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        ringChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(ringChannel)

        val ongoingChannel = NotificationChannel(CHANNEL_ONGOING, "Reminder Status", NotificationManager.IMPORTANCE_LOW)
        ongoingChannel.setSound(null, null)
        nm.createNotificationChannel(ongoingChannel)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
