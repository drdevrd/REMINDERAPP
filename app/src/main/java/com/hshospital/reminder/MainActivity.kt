package com.hshospital.reminder

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaRecorder
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
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
import java.io.File
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var tvStatus: TextView
    private lateinit var tvSettings: TextView
    private lateinit var btnQuick: Button
    private lateinit var btnScheduled: Button
    private lateinit var btnStop: Button
    private lateinit var btnDefaultSettings: Button

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private val recordingFile by lazy { File(filesDir, "reminder_recording.m4a").absolutePath }

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

        requestBatteryOptimizationExemption()
        updateUI()

        btnQuick.setOnClickListener { showQuickDialog() }
        btnScheduled.setOnClickListener { showStep1Text() }
        btnStop.setOnClickListener { stopAll() }
        btnDefaultSettings.setOnClickListener { showDefaultSettings() }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                AlertDialog.Builder(this)
                    .setTitle("Allow Background Alerts")
                    .setMessage("Tap Allow to ensure reminders ring when phone is locked.")
                    .setPositiveButton("Allow") { _, _ ->
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        intent.data = Uri.parse("package:$packageName")
                        startActivity(intent)
                    }
                    .setNegativeButton("Skip", null)
                    .show()
            }
        }
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
        val firstTrigger = System.currentTimeMillis() + intervalMin * 60 * 1000L
        scheduleRepeating(text, intervalMin, ringSec, firstTrigger)
        prefs.edit().putBoolean("is_running", true).apply()
        val persistIntent = Intent(this, PersistentService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(persistIntent)
        else startService(persistIntent)
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

        // Sound picker
        val tv3 = TextView(this)
        tv3.text = "Notification sound:"
        tv3.textSize = 14f
        tv3.setPadding(0, 24, 0, 0)
        layout.addView(tv3)

        val btnSound = Button(this)
        val savedUri = prefs.getString("ringtone_uri", null)
        val currentName = if (savedUri != null) {
            RingtoneManager.getRingtone(this, Uri.parse(savedUri))?.getTitle(this) ?: "Default Alarm"
        } else "Default Alarm"
        btnSound.text = currentName
        btnSound.setOnClickListener {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Reminder Sound")
            if (savedUri != null) {
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(savedUri))
            }
            startActivityForResult(intent, 999)
        }
        layout.addView(btnSound)

        // Voice recording
        val tv4 = TextView(this)
        tv4.text = "Voice recording (optional):"
        tv4.textSize = 14f
        tv4.setPadding(0, 24, 0, 0)
        layout.addView(tv4)

        val hasRecording = File(recordingFile).exists()
        val btnRecord = Button(this)
        btnRecord.text = if (hasRecording) "🎤 Re-record" else "🎤 Record Voice"
        layout.addView(btnRecord)

        val btnDeleteRec = Button(this)
        btnDeleteRec.text = "🗑 Delete Recording"
        btnDeleteRec.isEnabled = hasRecording
        layout.addView(btnDeleteRec)

        btnRecord.setOnClickListener {
            if (!isRecording) {
                startRecording(btnRecord, btnDeleteRec)
            } else {
                stopRecording(btnRecord, btnDeleteRec)
            }
        }

        btnDeleteRec.setOnClickListener {
            File(recordingFile).delete()
            btnDeleteRec.isEnabled = false
            btnRecord.text = "🎤 Record Voice"
            Toast.makeText(this, "Recording deleted", Toast.LENGTH_SHORT).show()
        }

        AlertDialog.Builder(this)
            .setTitle("Set Defaults")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                if (isRecording) stopRecording(btnRecord, btnDeleteRec)
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

    private fun startRecording(btn: Button, btnDelete: Button) {
        val ringSec = prefs.getInt("ring_duration_sec", 30)
        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(recordingFile)
                setMaxDuration(ringSec * 1000)
                prepare()
                start()
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        stopRecording(btn, btnDelete)
                    }
                }
            }
            isRecording = true
            btn.text = "🔴 Recording... (auto-stops in ${formatSec(ringSec)})"
            Toast.makeText(this, "Recording for ${formatSec(ringSec)}...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Mic permission needed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording(btn: Button, btnDelete: Button) {
        try {
            mediaRecorder?.apply { stop(); release() }
            mediaRecorder = null
            isRecording = false
            btn.text = "🎤 Re-record"
            btnDelete.isEnabled = true
            Toast.makeText(this, "Recording saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            isRecording = false
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 999 && resultCode == Activity.RESULT_OK) {
            val uri = data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                prefs.edit().putString("ringtone_uri", uri.toString()).apply()
                val name = RingtoneManager.getRingtone(this, uri)?.getTitle(this) ?: "Selected"
                Toast.makeText(this, "Sound: $name", Toast.LENGTH_SHORT).show()
            }
        }
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
        prefs.edit().putBoolean("is_running", true).putLong("next_trigger", cal.timeInMillis).apply()
        val persistIntent = Intent(this, PersistentService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(persistIntent)
        else startService(persistIntent)
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
        val showPi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAlarmClock(AlarmManager.AlarmClockInfo(firstTrigger, showPi), pi)
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

        val persistStopIntent = Intent(this, PersistentService::class.java)
        persistStopIntent.action = "STOP"
        startService(persistStopIntent)

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
        return if (sec < 60) "${sec}s" else if (sec == 60) "1 min" else "${sec / 60} min"
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    override fun onDestroy() {
        if (isRecording) {
            mediaRecorder?.apply { stop(); release() }
            mediaRecorder = null
        }
        super.onDestroy()
    }
}
