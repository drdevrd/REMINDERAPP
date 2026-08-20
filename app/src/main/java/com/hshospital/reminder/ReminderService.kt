package com.hshospital.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import java.io.File

class ReminderService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: android.media.Ringtone? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_RING    = "reminder_ring_v5"
        const val CHANNEL_ONGOING = "reminder_ongoing_v4"
        const val NOTIF_RING      = 2001
        const val NOTIF_ONGOING   = 2002
        const val ACTION_PLAY_RECORDING = "PLAY_RECORDING"
        const val ACTION_STOP           = "STOP"
    }

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP           -> { cleanup(); return START_NOT_STICKY }
            ACTION_PLAY_RECORDING -> { playRecording(); return START_NOT_STICKY }
        }

        val text        = intent?.getStringExtra("reminder_text") ?: "Reminder"
        val ringSec     = intent?.getIntExtra("ring_duration_sec", 30) ?: 30
        val intervalMin = intent?.getIntExtra("interval_minutes", 1) ?: 1

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
        val playPi = PendingIntent.getService(
            this, 2,
            Intent(this, ReminderService::class.java).apply { action = ACTION_PLAY_RECORDING },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val hasRecording = File(filesDir, "reminder_recording.m4a").exists()

        // Snooze PendingIntent
        val prefs2 = getSharedPreferences("reminder_prefs", MODE_PRIVATE)
        val snoozeHours = prefs2.getInt("snooze_hours", 2)
        val snoozeIntent = Intent(this, SnoozeReceiver::class.java).apply {
            putExtra("reminder_text", text)
            putExtra("ring_duration_sec", ringSec)
            putExtra("interval_minutes", intervalMin)
            putExtra("snooze_ms", snoozeHours * 60 * 60 * 1000L)
        }
        val snoozePi = PendingIntent.getBroadcast(
            this, 3, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozeLabel = when (snoozeHours) {
            24   -> "SNOOZE 1d"
            4    -> "SNOOZE 4h"
            else -> "SNOOZE 2h"
        }

        // Silent ongoing notification
        startForeground(NOTIF_ONGOING,
            NotificationCompat.Builder(this, CHANNEL_ONGOING)
                .setContentTitle("Reminder active")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentIntent(openPi)
                .addAction(android.R.drawable.ic_delete, "STOP", stopPi)
                .setOngoing(true)
                .setSilent(true)
                .setVibrate(longArrayOf(0L))
                .build()
        )

        // Ring notification — completely silent, sound handled manually
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val ringBuilder = NotificationCompat.Builder(this, CHANNEL_RING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(text)
            .setContentText("Every $intervalMin min  •  Tap STOP to dismiss")
            .setStyle(NotificationCompat.BigTextStyle()
                .setBigContentTitle(text)
                .bigText("Every $intervalMin min  •  Tap STOP to dismiss"))
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_media_next, snoozeLabel, snoozePi)
            .addAction(android.R.drawable.ic_delete, "STOP", stopPi)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(null)
            .setVibrate(longArrayOf(0L))
            .setColorized(true)
            .setColor(Color.WHITE)

        if (hasRecording) ringBuilder.addAction(android.R.drawable.ic_media_play, "PLAY", playPi)
        nm.notify(NOTIF_RING, ringBuilder.build())

        // Load saved ringtone URI — play manually, this is THE ONLY sound source
        val prefs = prefs2
        val savedUri = prefs.getString("ringtone_uri", null)
        val uri: Uri = if (savedUri != null) {
            Uri.parse(savedUri)
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        }

        ringtone = RingtoneManager.getRingtone(applicationContext, uri)
        ringtone?.let { rt ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                rt.isLooping = false
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

        // Vibrate only if user enabled it in Set Defaults
        if (prefs.getBoolean("vibrate_enabled", false)) {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 800, 400, 800, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        }

        handler.postDelayed({ cleanup() }, ringSec * 1000L)
        return START_NOT_STICKY
    }

    private fun playRecording() {
        val file = File(filesDir, "reminder_recording.m4a")
        if (!file.exists()) return
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                prepare(); start()
                setOnCompletionListener { release(); mediaPlayer = null }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun cleanup() {
        ringtone?.stop(); ringtone = null
        mediaPlayer?.release(); mediaPlayer = null
        vibrator?.cancel(); vibrator = null
        handler.removeCallbacksAndMessages(null)
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // Keep NOTIF_RING visible so user can tap STOP/SNOOZE/PLAY after ringing stops
        nm.cancel(NOTIF_ONGOING)
        stopForeground(false) // false = don't remove notification
        stopSelf()
    }

    private fun createChannels() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Ring channel — completely silent, no sound, no vibration
        val ringChannel = NotificationChannel(CHANNEL_RING, "Reminder Alarm", NotificationManager.IMPORTANCE_HIGH)
        ringChannel.setSound(null, null)
        ringChannel.enableVibration(false)
        ringChannel.vibrationPattern = longArrayOf(0L)
        ringChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(ringChannel)

        // Ongoing channel — silent
        val ongoingChannel = NotificationChannel(CHANNEL_ONGOING, "Reminder Status", NotificationManager.IMPORTANCE_LOW)
        ongoingChannel.setSound(null, null)
        ongoingChannel.enableVibration(false)
        nm.createNotificationChannel(ongoingChannel)
    }

    override fun onDestroy() { cleanup(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
