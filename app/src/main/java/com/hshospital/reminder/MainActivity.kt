package com.hshospital.reminder

import android.app.Activity
import android.app.AlarmManager
import android.app.DatePickerDialog
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
import android.widget.CheckBox
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
    private lateinit var btnScheduled: Button
    private lateinit var btnStopScheduled: Button
    private lateinit var btnDefaultSettings: Button
    private lateinit var btnStopAll: Button

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private val recordingFile by lazy { File(filesDir, "reminder_recording.m4a").absolutePath }

    companion object {
        const val SLOT_SCHEDULED = 10
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("reminder_prefs", MODE_PRIVATE)
        tvSettings         = findViewById(R.id.tvSettings)
        btnSlot1           = findViewById(R.id.btnSlot1)
        btnSlot2           = findViewById(R.id.btnSlot2)
        btnSlot3           = findViewById(R.id.btnSlot3)
        btnStop1           = findViewById(R.id.btnStop1)
        btnStop2           = findViewById(R.id.btnStop2)
        btnStop3           = findViewById(R.id.btnStop3)
        btnRename1         = findViewById(R.id.btnRename1)
        btnRename2         = findViewById(R.id.btnRename2)
        btnRename3         = findViewById(R.id.btnRename3)
        btnScheduled       = findViewById(R.id.btnScheduled)
        btnStopScheduled   = findViewById(R.id.btnStopScheduled)
        btnDefaultSettings = findViewById(R.id.btnDefaultSettings)
        btnStopAll         = findViewById(R.id.btnStopAll)
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
        btnScheduled.setOnClickListener { showScheduledDialog() }
        btnStopScheduled.setOnClickListener { stopScheduled() }
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
                        val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        i.data = Uri.parse("package:$packageName")
                        startActivity(i)
                    }
                    .setNegativeButton("Skip", null).show()
            }
        }
    }

    private fun showRenameDialog(slot: Int) {
        val input = EditText(this)
        input.setPadding(48, 24, 48, 24)
        input.setText(prefs.getString("slot${slot}_name", "Reminder $slot"))
        AlertDialog.Builder(this).setTitle("Rename Reminder $slot").setView(input)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit().putString("slot${slot}_name", input.text.toString().trim().ifEmpty { "Reminder $slot" }).apply()
                updateUI()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showQuickDialog(slot: Int) {
        val input = EditText(this)
        input.hint = "Type your reminder..."
        input.setPadding(48, 24, 48, 24)
        input.setText(prefs.getString("slot${slot}_text", ""))
        val interval = prefs.getInt("interval_minutes", 1)
        val ringSec  = prefs.getInt("ring_duration_sec", 30)
        val name     = prefs.getString("slot${slot}_name", "Reminder $slot") ?: "Reminder $slot"
        AlertDialog.Builder(this).setTitle(name)
            .setMessage("Every $interval min  •  Rings for ${formatSec(ringSec)}")
            .setView(input)
            .setPositiveButton("START") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isEmpty()) Toast.makeText(this, "Please enter reminder text", Toast.LENGTH_SHORT).show()
                else { prefs.edit().putString("slot${slot}_text", text).apply(); startSlot(slot, text) }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun startSlot(slot: Int, text: String) {
        val ringSec      = prefs.getInt("ring_duration_sec", 30)
        val intervalMin  = prefs.getInt("interval_minutes", 1)
        val firstTrigger = System.currentTimeMillis() + intervalMin * 60 * 1000L
        scheduleAlarm(slot, text, intervalMin, ringSec, firstTrigger, daily = false)
        prefs.edit().putBoolean("slot${slot}_running", true).putLong("slot${slot}_next_trigger", firstTrigger).apply()
        startPersistentService()
        updateUI()
        Toast.makeText(this, "First reminder in $intervalMin min", Toast.LENGTH_SHORT).show()
    }

    private fun stopSlot(slot: Int) {
        cancelAlarm(slot)
        startService(Intent(this, ReminderService::class.java).apply { action = "STOP" })
        prefs.edit().putBoolean("slot${slot}_running", false).apply()
        if (!anySlotRunning()) stopPersistentService()
        updateUI()
        Toast.makeText(this, "Reminder $slot stopped", Toast.LENGTH_SHORT).show()
    }

    private fun showScheduledDialog() {
        val input = EditText(this)
        input.hint = "Type your reminder..."
        input.setPadding(48, 24, 48, 24)
        input.setText(prefs.getString("scheduled_text", ""))
        val ringSec = prefs.getInt("ring_duration_sec", 30)
        AlertDialog.Builder(this).setTitle("Scheduled Reminder")
            .setMessage("Rings daily at set time  •  Rings for ${formatSec(ringSec)}")
            .setView(input)
            .setPositiveButton("Pick Date & Time") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isEmpty()) Toast.makeText(this, "Please enter reminder text", Toast.LENGTH_SHORT).show()
                else { prefs.edit().putString("scheduled_text", text).apply(); pickDate(text) }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun pickDate(text: String) {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            pickTime(text, year, month, day)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun pickTime(text: String, year: Int, month: Int, day: Int) {
        val cal = Calendar.getInstance()
        TimePickerDialog(this, { _, hour, minute ->
            val triggerCal = Calendar.getInstance()
            triggerCal.set(year, month, day, hour, minute, 0)
            triggerCal.set(Calendar.MILLISECOND, 0)
            if (triggerCal.timeInMillis <= System.currentTimeMillis()) triggerCal.add(Calendar.DAY_OF_YEAR, 1)
            val ringSec   = prefs.getInt("ring_duration_sec", 30)
            val triggerMs = triggerCal.timeInMillis
            prefs.edit()
                .putBoolean("scheduled_running", true)
                .putInt("scheduled_hour", hour)
                .putInt("scheduled_minute", minute)
                .putLong("scheduled_next_trigger", triggerMs)
                .apply()
            scheduleAlarm(SLOT_SCHEDULED, text, prefs.getInt("interval_minutes", 1), ringSec, triggerMs, daily = true)
            startPersistentService()
            updateUI()
            val h12  = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val ampm = if (hour < 12) "AM" else "PM"
            Toast.makeText(this, "Scheduled daily at %02d:%02d %s".format(h12, minute, ampm), Toast.LENGTH_LONG).show()
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
    }

    private fun stopScheduled() {
        cancelAlarm(SLOT_SCHEDULED)
        startService(Intent(this, ReminderService::class.java).apply { action = "STOP" })
        prefs.edit().putBoolean("scheduled_running", false).apply()
        if (!anySlotRunning()) stopPersistentService()
        updateUI()
        Toast.makeText(this, "Scheduled reminder stopped", Toast.LENGTH_SHORT).show()
    }

    private fun scheduleAlarm(slot: Int, text: String, intervalMin: Int, ringSec: Int, triggerMs: Long, daily: Boolean) {
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("reminder_text", text)
            putExtra("interval_minutes", intervalMin)
            putExtra("ring_duration_sec", ringSec)
            putExtra("slot", slot)
            putExtra("daily", daily)
        }
        val pi     = PendingIntent.getBroadcast(this, slot, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val showPi = PendingIntent.getActivity(this, slot, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager).setAlarmClock(AlarmManager.AlarmClockInfo(triggerMs, showPi), pi)
    }

    private fun cancelAlarm(slot: Int) {
        val pi = PendingIntent.getBroadcast(this, slot, Intent(this, AlarmReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)
    }

    private fun stopAllSlots() {
        listOf(1, 2, 3, SLOT_SCHEDULED).forEach { cancelAlarm(it) }
        startService(Intent(this, ReminderService::class.java).apply { action = "STOP" })
        prefs.edit().putBoolean("slot1_running", false).putBoolean("slot2_running", false)
            .putBoolean("slot3_running", false).putBoolean("scheduled_running", false).apply()
        stopPersistentService()
        updateUI()
        Toast.makeText(this, "All reminders stopped", Toast.LENGTH_SHORT).show()
    }

    private fun showDefaultSettings() {
        val intervalLabels = arrayOf("1 min","2 min","3 min","5 min","10 min","15 min","30 min")
        val intervalValues = intArrayOf(1,2,3,5,10,15,30)
        val ringLabels     = arrayOf("5 sec","10 sec","20 sec","30 sec","1 min","2 min","5 min")
        val ringValues     = intArrayOf(5,10,20,30,60,120,300)
        val hourLabels     = Array(24) { h -> if (h == 0) "12 AM" else if (h < 12) "$h AM" else if (h == 12) "12 PM" else "${h-12} PM" }

        var selInterval = intervalValues.indexOfFirst { it == prefs.getInt("interval_minutes",1) }.coerceAtLeast(0)
        var selRing     = ringValues.indexOfFirst { it == prefs.getInt("ring_duration_sec",30) }.coerceAtLeast(3)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(48,24,48,8)

        fun lbl(t: String) = TextView(this).also { it.text = t; it.textSize = 14f; it.setPadding(0,20,0,0); layout.addView(it) }
        fun spinner(labels: Array<String>, sel: Int) = Spinner(this).also {
            it.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
            it.setSelection(sel); layout.addView(it)
        }

        lbl("Repeat every (Quick Reminder):")
        val spInterval = spinner(intervalLabels, selInterval)

        lbl("Ring for:")
        val spRing = spinner(ringLabels, selRing)

        lbl("Notification sound:")
        val savedUri = prefs.getString("ringtone_uri", null)
        val btnSound = Button(this)
        btnSound.text = if (savedUri != null) RingtoneManager.getRingtone(this, Uri.parse(savedUri))?.getTitle(this) ?: "Default Alarm" else "Default Alarm"
        btnSound.setOnClickListener {
            val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            if (savedUri != null) i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(savedUri))
            startActivityForResult(i, 999)
        }
        layout.addView(btnSound)

        lbl("Do Not Disturb:")
        val cbDnd = CheckBox(this)
        cbDnd.text = "Enable DND (won't ring during these hours)"
        cbDnd.isChecked = prefs.getBoolean("dnd_enabled", false)
        cbDnd.textSize = 13f
        layout.addView(cbDnd)

        lbl("DND From:")
        val spDndStart = spinner(hourLabels, prefs.getInt("dnd_start_hour", 22))
        lbl("DND Until:")
        val spDndEnd   = spinner(hourLabels, prefs.getInt("dnd_end_hour", 7))

        lbl("Voice recording (optional):")
        val hasRec = File(recordingFile).exists()
        val btnRec = Button(this).also { it.text = if (hasRec) "🎤 Re-record" else "🎤 Record Voice"; layout.addView(it) }
        val btnDelRec = Button(this).also { it.text = "🗑 Delete Recording"; it.isEnabled = hasRec; layout.addView(it) }
        btnRec.setOnClickListener { if (!isRecording) startRecording(btnRec, btnDelRec) else stopRecording(btnRec, btnDelRec) }
        btnDelRec.setOnClickListener { File(recordingFile).delete(); btnDelRec.isEnabled = false; btnRec.text = "🎤 Record Voice" }

        AlertDialog.Builder(this).setTitle("Set Defaults").setView(layout)
            .setPositiveButton("Save") { _, _ ->
                if (isRecording) stopRecording(btnRec, btnDelRec)
                prefs.edit()
                    .putInt("interval_minutes",  intervalValues[spInterval.selectedItemPosition])
                    .putInt("ring_duration_sec", ringValues[spRing.selectedItemPosition])
                    .putBoolean("dnd_enabled",   cbDnd.isChecked)
                    .putInt("dnd_start_hour",    spDndStart.selectedItemPosition)
                    .putInt("dnd_end_hour",      spDndEnd.selectedItemPosition)
                    .apply()
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
                updateUI()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun startRecording(btn: Button, btnDel: Button) {
        val ringSec = prefs.getInt("ring_duration_sec", 30)
        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(recordingFile)
                setMaxDuration(ringSec * 1000)
                prepare(); start()
                setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stopRecording(btn, btnDel) }
            }
            isRecording = true
            btn.text = "🔴 Recording... (auto-stops in ${formatSec(ringSec)})"
        } catch (e: Exception) { Toast.makeText(this, "Mic permission needed", Toast.LENGTH_SHORT).show() }
    }

    private fun stopRecording(btn: Button, btnDel: Button) {
        try { mediaRecorder?.apply { stop(); release() }; mediaRecorder = null; isRecording = false; btn.text = "🎤 Re-record"; btnDel.isEnabled = true
            Toast.makeText(this, "Recording saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { isRecording = false }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 999 && resultCode == Activity.RESULT_OK) {
            val uri = data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) { prefs.edit().putString("ringtone_uri", uri.toString()).apply()
                Toast.makeText(this, "Sound saved", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun anySlotRunning() = (1..3).any { prefs.getBoolean("slot${it}_running", false) } || prefs.getBoolean("scheduled_running", false)
    private fun startPersistentService() { val i = Intent(this, PersistentService::class.java); if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i) }
    private fun stopPersistentService() { startService(Intent(this, PersistentService::class.java).apply { action = "STOP" }) }

    private fun updateUI() {
        val interval = prefs.getInt("interval_minutes", 1)
        val ringSec  = prefs.getInt("ring_duration_sec", 30)
        val dndOn    = prefs.getBoolean("dnd_enabled", false)
        val dndStart = prefs.getInt("dnd_start_hour", 22)
        val dndEnd   = prefs.getInt("dnd_end_hour", 7)
        val dndTxt   = if (dndOn) "  •  DND ${formatHour(dndStart)}-${formatHour(dndEnd)}" else ""
        tvSettings.text = "Every $interval min  •  ${formatSec(ringSec)}$dndTxt"

        for (slot in 1..3) {
            val running   = prefs.getBoolean("slot${slot}_running", false)
            val text      = prefs.getString("slot${slot}_text", "") ?: ""
            val name      = prefs.getString("slot${slot}_name", "Reminder $slot") ?: "Reminder $slot"
            val slotBtn   = when (slot) { 1 -> btnSlot1; 2 -> btnSlot2; else -> btnSlot3 }
            val stopBtn   = when (slot) { 1 -> btnStop1; 2 -> btnStop2; else -> btnStop3 }
            val renameBtn = when (slot) { 1 -> btnRename1; 2 -> btnRename2; else -> btnRename3 }
            renameBtn.text    = name
            slotBtn.text      = if (running && text.isNotEmpty()) "🔔 $name\n\"$text\"" else "▶  $name"
            stopBtn.isEnabled = running
        }

        val schedRunning = prefs.getBoolean("scheduled_running", false)
        val schedText    = prefs.getString("scheduled_text", "") ?: ""
        val schedHour    = prefs.getInt("scheduled_hour", -1)
        val schedMin     = prefs.getInt("scheduled_minute", 0)
        btnScheduled.text = if (schedRunning && schedHour >= 0)
            "🗓 Scheduled\n\"$schedText\"  Daily ${formatHour(schedHour)}:${"%02d".format(schedMin)}"
        else "🗓 Scheduled Reminder"
        btnStopScheduled.isEnabled = schedRunning
        btnStopAll.isEnabled = anySlotRunning()
    }

    private fun formatSec(sec: Int) = if (sec < 60) "${sec}s" else if (sec == 60) "1 min" else "${sec/60} min"
    private fun formatHour(h: Int): String { val a = if (h < 12) "AM" else "PM"; val h12 = if (h == 0) 12 else if (h > 12) h-12 else h; return "$h12$a" }

    override fun onResume() { super.onResume(); updateUI() }
    override fun onDestroy() { if (isRecording) { mediaRecorder?.apply { stop(); release() }; mediaRecorder = null }; super.onDestroy() }
}
