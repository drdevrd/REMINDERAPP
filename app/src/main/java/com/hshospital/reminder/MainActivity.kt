package com.hshospital.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var tvStatus: TextView
    private lateinit var tvSettings: TextView
    private lateinit var btnSet: Button
    private lateinit var btnStop: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("reminder_prefs", MODE_PRIVATE)

        tvStatus   = findViewById(R.id.tvStatus)
        tvSettings = findViewById(R.id.tvSettings)
        btnSet     = findViewById(R.id.btnSet)
        btnStop    = findViewById(R.id.btnStop)

        updateUI()

        btnSet.setOnClickListener { showStep1_Text() }
        btnStop.setOnClickListener { stopAll() }
    }

    // ── STEP 1: What to remind ──────────────────────────────────────────────
    private fun showStep1_Text() {
        val input = EditText(this).apply {
            hint = "Type your reminder..."
            setPadding(48, 24, 48, 24)
            setText(prefs.getString("reminder_text", ""))
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle("Step 1 of 4 — What to remind?")
            .setView(input)
            .setPositiveButton("Next →") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isEmpty()) {
                    Toast.makeText(this, "Please enter reminder text", Toast.LENGTH_SHORT).show()
                } else {
                    prefs.edit().putString("reminder_text", text).apply()
                    showStep2_StartTime()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── STEP 2: Start time ──────────────────────────────────────────────────
    private fun showStep2_StartTime() {
        val cal = Calendar.getInstance()
        TimePickerDialog(this, { _, hour, minute ->
            prefs.edit()
                .putInt("start_hour", hour)
                .putInt("start_minute", minute)
                .apply()
            showStep3_Interval()
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).apply {
            setTitle("Step 2 of 4 — Start time (first ring)")
            show()
        }
    }

    // ── STEP 3: Repeat interval ─────────────────────────────────────────────
    private fun showStep3_Interval() {
        val labels  = arrayOf("1 min", "2 min", "3 min", "5 min", "10 min", "15 min", "30 min")
        val values  = intArrayOf(1, 2, 3, 5, 10, 15, 30)
        val current = prefs.getInt("interval_minutes", 1)
        var sel     = values.indexOfFirst { it == current }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Step 3 of 4 — Repeat every…")
            .setSingleChoiceItems(labels, sel) { _, i -> sel = i }
            .setPositiveButton("Next →") { _, _ ->
                prefs.edit().putInt("interval_minutes", values[sel]).apply()
                showStep4_RingDuration()
            }
            .setNegativeButton("Back") { _, _ -> showStep2_StartTime() }
            .show()
    }

    // ── STEP 4: Ring duration ───────────────────────────────────────────────
    private fun showStep4_RingDuration() {
        val labels  = arrayOf("10 seconds", "20 seconds", "30 seconds", "1 minute", "2 minutes", "5 minutes")
        val values  = intArrayOf(10, 20, 30, 60, 120, 300)   // seconds
        val current = prefs.getInt("ring_duration_sec", 30)
        var sel     = values.indexOfFirst { it == current }.coerceAtLeast(2)

        AlertDialog.Builder(this)
            .setTitle("Step 4 of 4 — Ring for how long?")
            .setSingleChoiceItems(labels, sel) { _, i -> sel = i }
            .setPositiveButton("Start Reminder") { _, _ ->
                prefs.edit().putInt("ring_duration_sec", values[sel]).apply()
                scheduleReminder()
            }
            .setNegativeButton("Back") { _, _ -> showStep3_Interval() }
            .show()
    }

    // ── Schedule first alarm ────────────────────────────────────────────────
    private fun scheduleReminder() {
        // On Android 12+ check exact alarm permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle("Permission needed")
                    .setMessage("Allow exact alarms so the reminder rings at the exact time you set.")
                    .setPositiveButton("Open Settings") { _, _ ->
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return
            }
        }

        val hour   = prefs.getInt("start_hour", 0)
        val minute = prefs.getInt("start_minute", 0)

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }

        val triggerMs = cal.timeInMillis

        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("reminder_text",    prefs.getString("reminder_text", ""))
            putExtra("interval_minutes", prefs.getInt("interval_minutes", 1))
            putExtra("ring_duration_sec",prefs.getInt("ring_duration_sec", 30))
        }
        val pi = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)

        prefs.edit().putBoolean("is_running", true).apply()
        updateUI()

        val fmt = if (hour < 12) "%02d:%02d AM" else "%02d:%02d PM"
        val h12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        Toast.makeText(this, "First ring at ${String.format(fmt, h12, minute)}", Toast.LENGTH_LONG).show()
    }

    // ── Stop everything ─────────────────────────────────────────────────────
    private fun stopAll() {
        // Cancel pending alarm
        val intent = Intent(this, AlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)

        // Stop foreground service
        stopService(Intent(this, ReminderService::class.java))

        prefs.edit().putBoolean("is_running", false).apply()
        updateUI()
        Toast.makeText(this, "Reminder stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateUI() {
        val running  = prefs.getBoolean("is_running", false)
        val text     = prefs.getString("reminder_text", "") ?: ""
        val hour     = prefs.getInt("start_hour", -1)
        val minute   = prefs.getInt("start_minute", 0)
        val interval = prefs.getInt("interval_minutes", 1)
        val ringSec  = prefs.getInt("ring_duration_sec", 30)

        if (running && text.isNotEmpty() && hour >= 0) {
            val h12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val ampm = if (hour < 12) "AM" else "PM"
            tvStatus.text   = "🔔 ACTIVE\n\"$text\""
            tvSettings.text = "Start: %02d:%02d %s  •  Every %d min  •  Rings %ds".format(h12, minute, ampm, interval, ringSec)
            btnSet.text     = "Change"
            btnStop.isEnabled = true
        } else {
            tvStatus.text   = "Tap SET to configure your reminder"
            tvSettings.text = ""
            btnSet.text     = "Set Reminder"
            btnStop.isEnabled = false
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }
}
