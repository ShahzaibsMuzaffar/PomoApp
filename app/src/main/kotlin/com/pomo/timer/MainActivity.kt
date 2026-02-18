package com.pomo.timer

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var chipGroup: ChipGroup
    private lateinit var chipFocus: Chip
    private lateinit var chipShort: Chip
    private lateinit var chipLong: Chip
    private lateinit var timerCircle: TimerCircleView
    private lateinit var tvTime: TextView
    private lateinit var tvSession: TextView
    private lateinit var dotViews: List<View>
    private lateinit var btnStart: MaterialButton
    private lateinit var btnReset: MaterialButton
    private lateinit var btnSkip: MaterialButton
    private lateinit var etTask: TextInputEditText
    private lateinit var tvAlarmName: TextView
    private lateinit var btnChooseRingtone: MaterialButton
    private lateinit var btnChooseFile: MaterialButton

    private lateinit var prefs: PomodoroPrefs
    private var currentMode   = PomodoroMode.FOCUS
    private var isRunning     = false
    private var totalSecs     = PomodoroMode.FOCUS.totalSeconds
    private var remainingSecs = totalSecs

    private var timerService: TimerService? = null
    private var serviceBound  = false

    private val serviceConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            timerService = (binder as TimerService.TimerBinder).getService()
            serviceBound  = true
            syncFromService()
        }
        override fun onServiceDisconnected(name: ComponentName) { timerService = null; serviceBound = false }
    }

    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Actions.TICK -> {
                    remainingSecs = intent.getIntExtra(Actions.EXTRA_REMAINING, 0)
                    totalSecs     = intent.getIntExtra(Actions.EXTRA_TOTAL, totalSecs)
                    isRunning     = intent.getBooleanExtra(Actions.EXTRA_RUNNING, false)
                    updateTimerUI()
                }
                Actions.COMPLETED -> {
                    onTimerCompleted(PomodoroMode.valueOf(intent.getStringExtra(Actions.EXTRA_MODE) ?: currentMode.name))
                }
            }
        }
    }

    private val ringtonePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                val name = RingtoneManager.getRingtone(this, uri)?.getTitle(this) ?: "Ringtone"
                saveAlarmSound(uri.toString(), name)
            } else saveAlarmSound(null, "Default Tone")
        }
    }

    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            saveAlarmSound(uri.toString(), getFileDisplayName(uri))
        }
    }

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = PomodoroPrefs(this)
        bindViews()
        setupModeChips()
        setupControls()
        setupAlarmPicker()
        restoreAlarmLabel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        updateTimerUI()
        handleAlarmReturn(intent)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleAlarmReturn(intent) }

    private fun handleAlarmReturn(intent: Intent?) {
        if (intent?.getBooleanExtra("dismissed", false) == true)
            onTimerCompleted(PomodoroMode.valueOf(intent.getStringExtra(Actions.EXTRA_MODE) ?: return))
        if (intent?.getBooleanExtra("snooze", false) == true) startSnooze()
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, TimerService::class.java), serviceConn, Context.BIND_AUTO_CREATE)
        val filter = IntentFilter().apply { addAction(Actions.TICK); addAction(Actions.COMPLETED) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            registerReceiver(tickReceiver, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(tickReceiver, filter)
    }

    override fun onStop() {
        if (serviceBound) { unbindService(serviceConn); serviceBound = false }
        unregisterReceiver(tickReceiver)
        super.onStop()
    }

    override fun onPause() { prefs.lastTask = etTask.text?.toString() ?: ""; super.onPause() }

    private fun bindViews() {
        chipGroup   = findViewById(R.id.chipGroup)
        chipFocus   = findViewById(R.id.chipFocus)
        chipShort   = findViewById(R.id.chipShort)
        chipLong    = findViewById(R.id.chipLong)
        timerCircle = findViewById(R.id.timerCircle)
        tvTime      = findViewById(R.id.tvTime)
        tvSession   = findViewById(R.id.tvSession)
        btnStart    = findViewById(R.id.btnStart)
        btnReset    = findViewById(R.id.btnReset)
        btnSkip     = findViewById(R.id.btnSkip)
        etTask      = findViewById(R.id.etTask)
        tvAlarmName = findViewById(R.id.tvAlarmName)
        btnChooseRingtone = findViewById(R.id.btnChooseRingtone)
        btnChooseFile     = findViewById(R.id.btnChooseFile)
        dotViews = listOf(findViewById(R.id.dot1), findViewById(R.id.dot2), findViewById(R.id.dot3), findViewById(R.id.dot4))
        etTask.setText(prefs.lastTask)
    }

    private fun setupModeChips() {
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val mode = when (checkedIds.firstOrNull()) {
                R.id.chipFocus -> PomodoroMode.FOCUS
                R.id.chipShort -> PomodoroMode.SHORT_BREAK
                R.id.chipLong  -> PomodoroMode.LONG_BREAK
                else           -> return@setOnCheckedStateChangeListener
            }
            if (mode != currentMode) switchMode(mode)
        }
    }

    private fun switchMode(mode: PomodoroMode) {
        stopTimer(); currentMode = mode; totalSecs = mode.totalSeconds; remainingSecs = totalSecs
        updateTimerUI(); updateAccentColor()
    }

    private fun setupControls() {
        btnStart.setOnClickListener { if (isRunning) pauseTimer() else startTimer() }
        btnReset.setOnClickListener { stopTimer(); remainingSecs = totalSecs; updateTimerUI() }
        btnSkip.setOnClickListener  { stopTimer(); onTimerCompleted(currentMode) }
    }

    private fun startTimer() {
        prefs.lastTask = etTask.text?.toString() ?: ""
        ContextCompat.startForegroundService(this, Intent(this, TimerService::class.java).apply {
            putExtra(TimerService.EXTRA_START_MODE,    currentMode.name)
            putExtra(TimerService.EXTRA_START_SECONDS, remainingSecs)
            putExtra(TimerService.EXTRA_START_TASK,    etTask.text?.toString() ?: "")
        })
        isRunning = true; updateStartButton()
    }

    private fun pauseTimer() {
        timerService?.togglePause() ?: startService(Intent(this, TimerService::class.java).apply { action = Actions.ACTION_PAUSE })
        isRunning = false; updateStartButton()
    }

    private fun stopTimer() {
        stopService(Intent(this, TimerService::class.java)); isRunning = false; updateStartButton()
    }

    private fun startSnooze() {
        currentMode = PomodoroMode.FOCUS; totalSecs = 5*60; remainingSecs = 5*60
        updateTimerUI(); startTimer()
    }

    private fun syncFromService() {
        val svc = timerService ?: return
        currentMode = svc.currentMode; totalSecs = svc.totalSeconds
        remainingSecs = svc.remainingSeconds; isRunning = svc.isRunning
        when (currentMode) {
            PomodoroMode.FOCUS       -> chipFocus.isChecked = true
            PomodoroMode.SHORT_BREAK -> chipShort.isChecked = true
            PomodoroMode.LONG_BREAK  -> chipLong.isChecked  = true
        }
        updateTimerUI(); updateAccentColor()
    }

    private fun onTimerCompleted(completedMode: PomodoroMode) {
        isRunning = false; updateStartButton()
        val next = when (completedMode) {
            PomodoroMode.FOCUS -> if (prefs.completedSessions > 0 && prefs.completedSessions % 4 == 0) PomodoroMode.LONG_BREAK else PomodoroMode.SHORT_BREAK
            else -> PomodoroMode.FOCUS
        }
        switchMode(next)
        when (next) {
            PomodoroMode.FOCUS       -> chipFocus.isChecked = true
            PomodoroMode.SHORT_BREAK -> chipShort.isChecked = true
            PomodoroMode.LONG_BREAK  -> chipLong.isChecked  = true
        }
        updateDots()
    }

    private fun updateTimerUI() {
        val m = String.format("%02d", remainingSecs / 60)
        val s = String.format("%02d", remainingSecs % 60)
        tvTime.text    = "$m:$s"
        tvSession.text = currentMode.sessionLabel
        val frac = if (totalSecs > 0) remainingSecs.toFloat() / totalSecs else 1f
        timerCircle.setProgress(frac, animate = false)
    }

    private fun updateStartButton() { btnStart.text = if (isRunning) getString(R.string.pause) else getString(R.string.start) }
    private fun updateAccentColor()  { timerCircle.setAccentColor(currentMode.color) }

    private fun updateDots() {
        val completed = prefs.completedSessions % 4
        dotViews.forEachIndexed { i, dot ->
            dot.isSelected  = i < completed
            dot.isActivated = i == completed && currentMode == PomodoroMode.FOCUS
        }
    }

    private fun setupAlarmPicker() {
        btnChooseRingtone.setOnClickListener {
            val current = prefs.alarmSoundUri?.let { Uri.parse(it) }
            ringtonePicker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,  false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Choose Alarm Sound")
                if (current != null) putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
            })
        }
        btnChooseFile.setOnClickListener {
            filePicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "audio/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            })
        }
    }

    private fun saveAlarmSound(uri: String?, name: String) { prefs.alarmSoundUri = uri; prefs.alarmSoundName = name; restoreAlarmLabel() }
    private fun restoreAlarmLabel() { tvAlarmName.text = prefs.alarmSoundName }

    private fun getFileDisplayName(uri: Uri): String {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(idx).let { name ->
                    listOf(".mp3",".m4a",".ogg",".flac",".wav",".aac").fold(name) { n, ext -> n.removeSuffix(ext) }
                }
            } ?: uri.lastPathSegment ?: "Audio File"
        } catch (e: Exception) { uri.lastPathSegment ?: "Audio File" }
    }
}
