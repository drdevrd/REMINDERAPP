package com.hshospital.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
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
        const val CHANNEL_ALARM = "reminder_alarm_v7"
        const val NOTIF_ID      = 4001
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
        wakeLock?.acquire((ringSec + 15) * 1000L)

        // Full-screen alarm activity — this is what reliably shows over the lock screen
        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            putExtra("reminder_text", text)
            putExtra("interval_minutes", intervalMin)
            putExtra("ring_duration_sec", ringSec)
            putExtra("slot", slot)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION
            )
        }
        val fullScreenPi = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getBroadcast(
            this, 1, Intent(this, StopReceiver::class.java).apply { putExtra("slot", slot) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(text)
            .setContentText("Repeats every $intervalMin min")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPi, true)
            .addAction(android.R.drawable.ic_delete, "STOP", stopPi)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSound(null)
            .setVibrate(longArrayOf(0L))
            .build()

        // Must call startForeground first
        startForeground(NOTIF_ID, notif)

        // Directly launch too — belt and braces for OnePlus/OEM restrictions
        startActivity(fullScreenIntent)

        val prefs = getSharedPreferences("reminder_prefs", MODE_PRIVATE)
        val savedUri = prefs.getString("ringtone_uri", null)
        val uri: Uri = if (savedUri != null) Uri.parse(savedUri)
                       else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                           ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        ringtone = RingtoneManager.getRingtone(applicationContext, uri)
        ringtone?.let { rt ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                rt.isLooping = true
                rt.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            } else {
                @Suppress("DEPRECATION")
                rt.streamType = AudioManager.STREAM_ALARM
            }
            rt.play()
        }

        if (prefs.getBoolean("vibrate_enabled", false)) {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else { @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator }
            val pattern = longArrayOf(0, 800, 400, 800, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            else @Suppress("DEPRECATION") vibrator?.vibrate(pattern, 0)
        }

        // Auto-stop after ringSec if user never taps DONE
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
        if (wakeLock?.isHeld == true) wakeLock?.release(); wakeLock = null
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannels() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val alarmChannel = NotificationChannel(CHANNEL_ALARM, "Reminder Alarm", NotificationManager.IMPORTANCE_HIGH)
        alarmChannel.setSound(null, null)
        alarmChannel.enableVibration(false)
        alarmChannel.vibrationPattern = longArrayOf(0L)
        alarmChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(alarmChannel)
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
