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
import androidx.core.app.ServiceCompat
import java.io.File

class ReminderService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: android.media.Ringtone? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_HIDDEN  = "reminder_hidden_v1"   // for foreground service only, invisible
        const val CHANNEL_ALERT   = "reminder_alert_v1"    // the real one user sees, plain notification
        const val NOTIF_HIDDEN    = 3001
        const val NOTIF_ALERT     = 3002
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
        val slot        = intent?.getIntExtra("slot", -1) ?: -1

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ReminderApp::RingWakeLock")
        wakeLock?.acquire((ringSec + 10) * 1000L)

        // Tiny hidden notification — required by Android to keep foreground service alive.
        // Minimum priority, no sound, no vibration — invisible to the user.
        val hiddenNotif = NotificationCompat.Builder(this, CHANNEL_HIDDEN)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
        startForeground(NOTIF_HIDDEN, hiddenNotif)

        // Post the REAL alert notification — plain notify(), completely independent of the service
        postAlertNotification(text, intervalMin, slot)

        // Play ringtone — plays through once (not looping), stops naturally
        val prefs = getSharedPreferences("reminder_prefs", MODE_PRIVATE)
        val savedUri = prefs.getString("ringtone_uri", null)
        val uri: Uri = if (savedUri != null) Uri.parse(savedUri)
                       else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                           ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        ringtone = RingtoneManager.getRingtone(applicationContext, uri)
        ringtone?.let { rt ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                rt.isLooping = false
                rt.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            } else {
                @Suppress("DEPRECATION")
                rt.streamType = AudioManager.STREAM_ALARM
            }
            rt.play()
        }

        // Vibrate only if user enabled it
        if (prefs.getBoolean("vibrate_enabled", false)) {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else { @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator }
            val pattern = longArrayOf(0, 800, 400, 800, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            else @Suppress("DEPRECATION") vibrator?.vibrate(pattern, -1)
        }

        // Mute after ringSec — stop ringtone/vibration, kill the hidden service notification,
        // but the ALERT notification posted above stays untouched in the bar
        handler.postDelayed({
            ringtone?.stop(); ringtone = null
            vibrator?.cancel(); vibrator = null
            if (wakeLock?.isHeld == true) wakeLock?.release(); wakeLock = null
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }, ringSec * 1000L)

        return START_NOT_STICKY
    }

    private fun postAlertNotification(text: String, intervalMin: Int, slot: Int) {
        val openPi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getBroadcast(
            this, 1, Intent(this, StopReceiver::class.java).apply { putExtra("slot", slot) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPi = PendingIntent.getService(
            this, 2,
            Intent(this, ReminderService::class.java).apply { action = ACTION_PLAY_RECORDING },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prefs = getSharedPreferences("reminder_prefs", MODE_PRIVATE)
        val snoozeHours = prefs.getInt("snooze_hours", 2)
        val snoozeMs    = snoozeHours * 60 * 60 * 1000L
        val snoozeLabel = when (snoozeHours) { 24 -> "SNOOZE 1d"; 4 -> "SNOOZE 4h"; else -> "SNOOZE 2h" }
        val snoozeIntent = Intent(this, SnoozeReceiver::class.java).apply {
            putExtra("reminder_text", text)
            putExtra("interval_minutes", intervalMin)
            putExtra("snooze_ms", snoozeMs)
            putExtra("slot", slot)
        }
        val snoozePi = PendingIntent.getBroadcast(
            this, 3, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hasRecording = File(filesDir, "reminder_recording.m4a").exists()

        val builder = NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(text)
            .setContentText("Every $intervalMin min  •  Tap a button below")
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_media_next, snoozeLabel, snoozePi)
            .addAction(android.R.drawable.ic_delete, "STOP", stopPi)
            .setAutoCancel(false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(null)
            .setVibrate(longArrayOf(0L))
            .setColorized(true)
            .setColor(Color.WHITE)

        if (hasRecording) builder.addAction(android.R.drawable.ic_media_play, "PLAY", playPi)

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ALERT, builder.build())
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
        if (wakeLock?.isHeld == true) wakeLock?.release(); wakeLock = null
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_HIDDEN)
        nm.cancel(NOTIF_ALERT)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannels() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val hiddenChannel = NotificationChannel(CHANNEL_HIDDEN, "Background Service", NotificationManager.IMPORTANCE_MIN)
        hiddenChannel.setSound(null, null)
        hiddenChannel.enableVibration(false)
        hiddenChannel.setShowBadge(false)
        nm.createNotificationChannel(hiddenChannel)

        val alertChannel = NotificationChannel(CHANNEL_ALERT, "Reminder Alert", NotificationManager.IMPORTANCE_HIGH)
        alertChannel.setSound(null, null)
        alertChannel.enableVibration(false)
        alertChannel.vibrationPattern = longArrayOf(0L)
        alertChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(alertChannel)
    }

    override fun onDestroy() {
        ringtone?.stop(); ringtone = null
        vibrator?.cancel(); vibrator = null
        if (wakeLock?.isHeld == true) wakeLock?.release()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
