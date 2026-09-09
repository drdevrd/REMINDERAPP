package com.hshospital.reminder

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AlarmActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_alarm)

        val text        = intent.getStringExtra("reminder_text") ?: "Reminder"
        val intervalMin = intent.getIntExtra("interval_minutes", 1)
        val ringSec     = intent.getIntExtra("ring_duration_sec", 30)
        val slot        = intent.getIntExtra("slot", -1)

        findViewById<TextView>(R.id.tvAlarmText).text = text
        findViewById<TextView>(R.id.tvAlarmSub).text = "Repeats every $intervalMin min"

        val hasRecording = java.io.File(filesDir, "reminder_recording.m4a").exists()
        val btnPlay = findViewById<Button>(R.id.btnPlay)
        btnPlay.visibility = if (hasRecording) android.view.View.VISIBLE else android.view.View.GONE
        btnPlay.setOnClickListener {
            val i = Intent(this, ReminderService::class.java).apply { action = "PLAY_RECORDING" }
            startService(i)
        }

        findViewById<Button>(R.id.btnDone).setOnClickListener {
            val i = Intent(this, StopReceiver::class.java).apply { putExtra("slot", slot) }
            sendBroadcast(i)
            finish()
        }

        findViewById<Button>(R.id.btnSnooze).setOnClickListener {
            val prefs = getSharedPreferences("reminder_prefs", MODE_PRIVATE)
            val snoozeHours = prefs.getInt("snooze_hours", 2)
            val snoozeMs = snoozeHours * 60 * 60 * 1000L
            val i = Intent(this, SnoozeReceiver::class.java).apply {
                putExtra("reminder_text", text)
                putExtra("ring_duration_sec", ringSec)
                putExtra("interval_minutes", intervalMin)
                putExtra("snooze_ms", snoozeMs)
                putExtra("slot", slot)
            }
            sendBroadcast(i)
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Block back button — alarm must be dismissed via DONE or SNOOZE
    }
}
