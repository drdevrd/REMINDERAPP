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
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var tvStatus: TextView
    private lateinit var tvSettings: TextView
    private lateinit var btnQuick: Button
    private lateinit var btnScheduled: Button
    private lateinit var btnStop: Button
    private lateinit var btnDefaultSettings: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("reminder_prefs", MODE_PRIVATE)
        tvStatus = findViewById(R.id.tvStatus)
        tvSettings = findViewById(R.id.tvSettings)
        btnQuick = findViewById(R.id.btnQuick)
        btnScheduled = findViewById(R.id.btnScheduled)
        btnStop = findViewById(R.id.btnStop)
        btnDefaultSettings = findViewById(R.id.btnDefaultSettings)

        updateUI()

        btnQuick.setOnClickListener { showQuickDialog() }
        btnScheduled.setOnClickListener { showStep1Text() }
        btnStop.setOnClickListener { stopAll() }
        btnDefaultSettings.setOnClickListener { showDefaultSettings() }
    }

    private fun showQuickDialog() {
        val input = EditText(this)
        input.hint = "Type your reminder..."
        input.setPadding(48, 24, 48, 24)
        input.setText(prefs.getString("reminder_text", ""))

        val interval = prefs.getInt("interval_minutes", 1)
        val ringSec = prefs.getInt("ring_duration_sec", 30)

        AlertDialog.Builder(this)
            .setTitle("Quick Reminder")
            .setMessage("Repeats every $interval min, Rings for ${formatSec(ringSec)}")
            .setView(input)
            .setPositiveButton("START NOW") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isEmpty()) {
                    Toast.makeText(this, "Please enter reminder text", Toast.LENGTH_SHORT).show()
                } else {
                    prefs.edit().putString("reminder_text", text).apply()
                    startImmediately(text)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startImmediately(text: String) {
        val ringSec = prefs.getInt("ring_duration_sec", 30)
        val intervalMin = prefs.getInt("interval_minutes", 1)

        // Schedule first ring after 1 interval from now (do NOT ring immediately)
        val firstTrigger = System.currentTimeMillis() + intervalMin * 60 * 1000L
        scheduleRepeating(text, intervalMin, ringSec, firstTrigger)
        prefs.edit().putBoolean("is_running", true).apply()
        updateUI()
        Toast.makeText(this, "First reminder in $intervalMin min", Toast.LENGTH_SHORT).show()
    }

    private fun showDefaultSettings() {
        val intervalLabels = arrayOf("1 min", "2 min", "3 min", "5 min", "10 min", "15 min", "30 min")
        val intervalValues = intArrayOf(1, 2, 3, 5, 10, 15, 30)
        val ringLabels = arrayOf("5 sec", "10 sec", "20 sec", "30 sec", "1 min", "2 min", "5 min")
        val ringValues = intArrayOf(5, 10, 20, 30, 60, 120, 300)

        var selInterval = intervalValues.indexOfFirst { it == prefs.getInt("interval_minutes", 1) }
        if (selInterval < 0) selInterval = 0
        var selRing = ringValues.indexOfFirst { it == prefs.getInt("ring_duration_sec", 30) }
        if (selRing < 0) selRing = 3

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(48, 24, 48, 8)

        val tv1 = TextView(this)
        tv1.text = "Repeat every:"
        tv1.textSize = 14f
        layout.addView(tv1)

        val spinnerInterval = Spinner(this)
        spinnerInterval.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, intervalLabels)
        spinnerInterval.setSelection(selInterval)
        layout.addView(spinnerInterval)

        val tv2 = TextView(this)
        tv2.text = "Ring for:"
        tv2.textSize = 14f
        tv2.setPadding(0, 24, 0, 0)
        layout.addView(tv2)

        val spinnerRing = Spinner(this)
        spinnerRing.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, ringLabels)
        spinnerRing.setSelection(selRing)
        layout.addView(spinnerRing)

        AlertDialog.Builder(this)
            .setTitle("Set Defaults")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newInterval = intervalValues[spinnerInterval.selectedItemPosition]
                val newRing = ringValues[spinnerRing.selectedItemPosition]
                prefs.edit()
                    .putInt("interval_minutes", newInterval)
                    .putInt("ring_duration_sec", newRing)
                    .apply()
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
                updateUI()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showStep1Text() {
        val input = EditText(this)
        input.hint = "Type your reminder..."
        input.setPadding(48, 24, 48, 24)
        input.setText(prefs.getString("reminder_text", ""))

        AlertDialog.Builder(this)
            .setTitle("Step 1 of 4 - What to remind?")
            .setView(input)
            .setPositiveButton("Next") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isEmpty()) {
                    Toast.makeText(this, "Please enter text", Toast.LENGTH_SHORT).show()
                } else {
                    prefs.edit().putString("reminder_text", text).apply()
                    showStep2StartTime()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showStep2StartTime() {
        val cal = Calendar.getInstance()
        val tpd = TimePickerDialog(
            this,
            { _, hour, minute ->
                prefs.edit().putInt("start_hour", hour).putInt("start_minute", minute).apply()
                showStep3Interval()
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            false
        )
        tpd.setTitle("Step 2 of 4 - Start time")
        tpd.show()
    }

    private fun showStep3Interval() {
        val labels = arrayOf("1 min", "2 min", "3 min", "5 min", "10 min", "15 min", "30 min")
        val values = intArrayOf(1, 2, 3, 5, 10, 15, 30)
        var sel = values.indexOfFirst { it == prefs.getInt("interval_minutes", 1) }
        if (sel < 0) sel = 0

        AlertDialog.Builder(this)
            .setTitle("Step 3 of 4 - Repeat every")
            .setSingleChoiceItems(labels, sel) { _, i -> sel = i }
            .setPositiveButton("Next") { _, _ ->
                prefs.edit().putInt("interval_minutes", values[sel]).apply()
                showStep4RingDuration()
            }
            .setNegativeButton("Back") { _, _ -> showStep2StartTime() }
            .show()
    }

    private fun showStep4RingDuration() {
        val labels = arrayOf("5 sec", "10 sec", "20 sec", "30 sec", "1 min", "2 min", "5 min")
        val values = intArrayOf(5, 10, 20, 30, 60, 120, 300)
        var sel = values.indexOfFirst { it == prefs.getInt("ring_duration_sec", 30) }
        if (sel < 0) sel = 3

        AlertDialog.Builder(this)
            .setTitle("Step 4 of 4 - Ring for how long?")
            .setSingleChoiceItems(labels, sel) { _, i -> sel = i }
            .setPositiveButton("Start Reminder") { _, _ ->
                prefs.edit().putInt("ring_duration_sec", values[sel]).apply()
                scheduleFirstAlarm()
            }
            .setNegativeButton("Back") { _, _ -> showStep3Interval() }
            .show()
    }

    private fun scheduleFirstAlarm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle("Permission needed")
                    .setMessage("Allow exact alarms so reminder rings at the exact time you set.")
                    .setPositiveButton("Open Settings") { _, _ ->
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return
            }
        }

        val hour = prefs.getInt("start_hour", 0)
        val minute = prefs.getInt("start_minute", 0)
        val intervalMin = prefs.getInt("interval_minutes", 1)
        val ringSec = prefs.getInt("ring_duration_sec", 30)
        val text = prefs.getString("reminder_text", "") ?: ""

        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        scheduleRepeating(text, intervalMin, ringSec, cal.timeInMillis)
        prefs.edit().putBoolean("is_running", true).apply()
        updateUI()

        val h12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        val ampm = if (hour < 12) "AM" else "PM"
        Toast.makeText(this, "First ring at %02d:%02d %s".format(h12, minute, ampm), Toast.LENGTH_LONG).show()
    }

    private fun scheduleRepeating(text: String, intervalMin: Int, ringSec: Int, firstTrigger: Long = System.currentTimeMillis()) {
        val intent = Intent(this, AlarmReceiver::class.java)
        intent.putExtra("reminder_text", text)
        intent.putExtra("interval_minutes", intervalMin)
        intent.putExtra("ring_duration_sec", ringSec)

        val pi = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, firstTrigger, pi)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, firstTrigger, pi)
        }
    }

    private fun stopAll() {
        val pi = PendingIntent.getBroadcast(
            this, 0, Intent(this, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)

        val stopIntent = Intent(this, ReminderService::class.java)
        stopIntent.action = "STOP"
        startService(stopIntent)

        prefs.edit().putBoolean("is_running", false).apply()
        updateUI()
        Toast.makeText(this, "Reminder stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateUI() {
        val running = prefs.getBoolean("is_running", false)
        val text = prefs.getString("reminder_text", "") ?: ""
        val interval = prefs.getInt("interval_minutes", 1)
        val ringSec = prefs.getInt("ring_duration_sec", 30)

        tvSettings.text = "Default: every $interval min, rings ${formatSec(ringSec)}"

        if (running && text.isNotEmpty()) {
            tvStatus.text = "ACTIVE: $text"
            btnQuick.text = "Change Reminder"
            btnStop.isEnabled = true
        } else {
            tvStatus.text = "Tap QUICK or SCHEDULED"
            btnQuick.text = "Quick Reminder"
            btnStop.isEnabled = false
        }
    }

    private fun formatSec(sec: Int): String {
        return if (sec < 60) {
            "${sec}s"
        } else if (sec == 60) {
            "1 min"
        } else {
            "${sec / 60} min"
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }
}
