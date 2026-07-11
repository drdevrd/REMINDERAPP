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

    private lateinit var tvSettings: TextView
    private lateinit var btnSlot1: Button
    private lateinit var btnSlot2: Button
    private lateinit var btnSlot3: Button
    private lateinit var btnStop1: Button
    private lateinit var btnStop2: Button
    private lateinit var btnStop3: Button
    private lateinit var btnRename1: Button
    private lateinit var btnRename2: Button
    private lateinit var btnRename3: Button
    private lateinit var btnDefaultSettings: Button
    private lateinit var btnStopAll: Button

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private val recordingFile by lazy { File(filesDir, "reminder_recording.m4a").absolutePath }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("reminder_prefs", MODE_PRIVATE)

        tvSettings        = findViewById(R.id.tvSettings)
        btnSlot1          = findViewById(R.id.btnSlot1)
        btnSlot2          = findViewById(R.id.btnSlot2)
        btnSlot3          = findViewById(R.id.btnSlot3)
        btnStop1          = findViewById(R.id.btnStop1)
        btnStop2          = findViewById(R.id.btnStop2)
        btnStop3          = findViewById(R.id.btnStop3)
        btnRename1        = findViewById(R.id.btnRename1)
        btnRename2        = findViewById(R.id.btnRename2)
        btnRename3        = findViewById(R.id.btnRename3)
        btnDefaultSettings= findViewById(R.id.btnDefaultSettings)
        btnStopAll        = findViewById(R.id.btnStopAll)

        requestBatteryOptimizationExemption()
        updateUI()

        btnSlot1.setOnClickListener { showQuickDialog(1) }
        btnSlot2.setOnClickListener { showQuickDialog(2) }
        btnSlot3.setOnClickListener { showQuickDialog(3) }
        btnStop1.setOnClickListener { stopSlot(1) }
        btnStop2.setOnClickListener { stopSlot(2) }
        btnStop3.setOnClickListener { stopSlot(3) }
        btnRename1.setOnClickListener { showRenameDialog(1) }
        btnRename2.setOnClickListener { showRenameDialog(2) }
        btnRename3.setOnClickListener { showRenameDialog(3) }
        btnDefaultSettings.setOnClickListener { showDefaultSettings() }
        btnStopAll.setOnClickListener { stopAllSlots() }
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

    private fun showRenameDialog(slot: Int) {
        val input = EditText(this)
        input.setPadding(48, 24, 48, 24)
        input.setText(prefs.getString("slot${slot}_name", "Reminder $slot"))
        input.hint = "Button name"

        AlertDialog.Builder(this)
            .setTitle("Rename Reminder $slot")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "Reminder $slot" }
                prefs.edit().putString("slot${slot}_name", name).apply()
                updateUI()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showQuickDialog(slot: Int) {
        val input = EditText(this)
        input.hint = "Type your reminder..."
        input.setPadding(48, 24, 48, 24)
        input.setText(prefs.getString("slot${slot}_text", ""))

        val interval = prefs.getInt("interval_minutes", 1)
        val ringSec = prefs.getInt("ring_duration_sec", 30)
        val slotName = prefs.getString("slot${slot}_name", "Reminder $slot") ?: "Reminder $slot"

        AlertDialog.Builder(this)
            .setTitle(slotName)
            .setMessage("Repeats every $interval min  •  Rings for ${formatSec(ringSec)}")
            .setView(input)
            .setPositiveButton("START") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isEmpty()) {
                    Toast.makeText(this, "Please enter reminder text", Toast.LENGTH_SHORT).show()
                } else {
                    prefs.edit().putString("slot${slot}_text", text).apply()
                    startSlot(slot, text)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startSlot(slot: Int, text: String) {
        val ringSec = prefs.getInt("ring_duration_sec", 30)
        val intervalMin = prefs.getInt("interval_minutes", 1)
        val firstTrigger = System.currentTimeMillis() + intervalMin * 60 * 1000L

        val intent = Intent(this, AlarmReceiver::class.java)
        intent.putExtra("reminder_text", text)
        intent.putExtra("interval_minutes", intervalMin)
        intent.putExtra("ring_duration_sec", ringSec)
        intent.putExtra("slot", slot)

        val pi = PendingIntent.getBroadcast(
            this, slot, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val showPi = PendingIntent.getActivity(
            this, slot, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAlarmClock(AlarmManager.AlarmClockInfo(firstTrigger, showPi), pi)

        prefs.edit()
            .putBoolean("slot${slot}_running", true)
            .putLong("slot${slot}_next_trigger", firstTrigger)
            .apply()

        val persistIntent = Intent(this, PersistentService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(persistIntent)
        else startService(persistIntent)

        updateUI()
        Toast.makeText(this, "First reminder in $intervalMin min", Toast.LENGTH_SHORT).show()
    }

    private fun stopSlot(slot: Int) {
        val pi = PendingIntent.getBroadcast(
            this, slot, Intent(this, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)

        val stopIntent = Intent(this, ReminderService::class.java)
        stopIntent.action = "STOP"
        stopIntent.putExtra("slot", slot)
        startService(stopIntent)

        prefs.edit().putBoolean("slot${slot}_running", false).apply()

        // Stop persistent service only if all slots stopped
        if (!anySlotRunning()) {
            val persistStopIntent = Intent(this, PersistentService::class.java)
            persistStopIntent.action = "STOP"
            startService(persistStopIntent)
        }

        updateUI()
        Toast.makeText(this, "Reminder $slot stopped", Toast.LENGTH_SHORT).show()
    }

    private fun stopAllSlots() {
        for (slot in 1..3) {
            val pi = PendingIntent.getBroadcast(
                this, slot, Intent(this, AlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            (getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)
            prefs.edit().putBoolean("slot${slot}_running", false).apply()
        }
        val stopIntent = Intent(this, ReminderService::class.java)
        stopIntent.action = "STOP"
        startService(stopIntent)
        val persistStopIntent = Intent(this, PersistentService::class.java)
        persistStopIntent.action = "STOP"
        startService(persistStopIntent)
        updateUI()
        Toast.makeText(this, "All reminders stopped", Toast.LENGTH_SHORT).show()
    }

    private fun anySlotRunning(): Boolean {
        return (1..3).any { prefs.getBoolean("slot${it}_running", false) }
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
            if (!isRecording) startRecording(btnRecord, btnDeleteRec)
            else stopRecording(btnRecord, btnDeleteRec)
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
            Toast.makeText(this, "Recording...", Toast.LENGTH_SHORT).show()
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

    private fun updateUI() {
        val interval = prefs.getInt("interval_minutes", 1)
        val ringSec = prefs.getInt("ring_duration_sec", 30)
        tvSettings.text = "Default: every $interval min  •  rings ${formatSec(ringSec)}"

        for (slot in 1..3) {
            val running = prefs.getBoolean("slot${slot}_running", false)
            val text = prefs.getString("slot${slot}_text", "") ?: ""
            val name = prefs.getString("slot${slot}_name", "Reminder $slot") ?: "Reminder $slot"

            val slotBtn = when (slot) { 1 -> btnSlot1; 2 -> btnSlot2; else -> btnSlot3 }
            val stopBtn = when (slot) { 1 -> btnStop1; 2 -> btnStop2; else -> btnStop3 }
            val renameBtn = when (slot) { 1 -> btnRename1; 2 -> btnRename2; else -> btnRename3 }

            renameBtn.text = name
            if (running && text.isNotEmpty()) {
                slotBtn.text = "🔔 $name\n\"$text\""
                stopBtn.isEnabled = true
            } else {
                slotBtn.text = "▶  $name"
                stopBtn.isEnabled = false
            }
        }

        btnStopAll.isEnabled = anySlotRunning()
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
