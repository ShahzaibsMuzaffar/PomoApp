package com.pomo.timer

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.view.View
import android.view.WindowManager
import android.view.animation.*
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class AlarmActivity : AppCompatActivity() {

    private lateinit var prefs: PomodoroPrefs
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private lateinit var mode: PomodoroMode

    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var tvTask: TextView
    private lateinit var ivBell: ImageView
    private lateinit var btnDismiss: MaterialButton
    private lateinit var btnSnooze: MaterialButton
    private val rippleViews = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        setContentView(R.layout.activity_alarm)
        prefs = PomodoroPrefs(this)
        mode  = PomodoroMode.valueOf(intent.getStringExtra(Actions.EXTRA_MODE) ?: PomodoroMode.FOCUS.name)
        bindViews()
        applyModeTheme()
        startAlarmSound()
        startVibration()
        startRippleAnimation()
        startBellAnimation()
    }

    private fun bindViews() {
        tvTitle    = findViewById(R.id.tvAlarmTitle)
        tvSubtitle = findViewById(R.id.tvAlarmSubtitle)
        tvTask     = findViewById(R.id.tvAlarmTask)
        ivBell     = findViewById(R.id.ivAlarmBell)
        btnDismiss = findViewById(R.id.btnDismiss)
        btnSnooze  = findViewById(R.id.btnSnooze)
        rippleViews.add(findViewById(R.id.ripple1))
        rippleViews.add(findViewById(R.id.ripple2))
        rippleViews.add(findViewById(R.id.ripple3))
        rippleViews.add(findViewById(R.id.ripple4))
        val task = prefs.lastTask
        if (task.isNotBlank()) { tvTask.text = "✓  $task"; tvTask.visibility = View.VISIBLE }
        else tvTask.visibility = View.GONE
        btnDismiss.setOnClickListener { dismiss() }
        btnSnooze.setOnClickListener  { snooze() }
        btnSnooze.visibility = if (mode == PomodoroMode.FOCUS) View.VISIBLE else View.GONE
    }

    private fun applyModeTheme() {
        val color = mode.color
        tvTitle.text = when (mode) {
            PomodoroMode.FOCUS       -> "TIME'S UP"
            PomodoroMode.SHORT_BREAK -> "BREAK OVER"
            PomodoroMode.LONG_BREAK  -> "BREAK DONE"
        }
        tvSubtitle.text = "${mode.sessionLabel} Complete"
        tvTitle.setTextColor(color)
        btnDismiss.strokeColor = android.content.res.ColorStateList.valueOf(color)
        btnDismiss.setTextColor(color)
        rippleViews.forEach { it.background?.setTint(color) }
        ivBell.setColorFilter(color)
    }

    private fun startAlarmSound() {
        val uriString = prefs.alarmSoundUri
        val soundUri: Uri = if (uriString != null) Uri.parse(uriString)
            else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setDataSource(this@AlarmActivity, soundUri)
                isLooping = true
                prepare()
                setVolume(0.1f, 0.1f)
                start()
                crescendoVolume(20_000L)
            }
        } catch (e: Exception) {
            try {
                val fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                    setDataSource(this@AlarmActivity, fallback)
                    isLooping = true; prepare(); start()
                }
            } catch (e2: Exception) {}
        }
    }

    private fun crescendoVolume(durationMs: Long) {
        val steps = 40
        val intervalMs = durationMs / steps
        val handler = Handler(Looper.getMainLooper())
        var step = 0
        val r = object : Runnable {
            override fun run() {
                if (mediaPlayer == null || !mediaPlayer!!.isPlaying) return
                step++
                val vol = (step.toFloat() / steps).coerceAtMost(1f)
                mediaPlayer?.setVolume(vol, vol)
                if (step < steps) handler.postDelayed(this, intervalMs)
            }
        }
        handler.postDelayed(r, intervalMs)
    }

    private fun stopSound() {
        mediaPlayer?.apply { if (isPlaying) stop(); release() }
        mediaPlayer = null
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val pattern = longArrayOf(0, 400, 200, 400, 200, 800, 600)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitudes = intArrayOf(0, 180, 0, 180, 0, 255, 0)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopVibration() { vibrator?.cancel() }

    private fun startRippleAnimation() {
        rippleViews.forEachIndexed { i, view ->
            view.startAnimation(AnimationUtils.loadAnimation(this, R.anim.ripple_expand).apply { startOffset = i * 650L })
        }
    }

    private fun startBellAnimation() {
        ivBell.startAnimation(RotateAnimation(-14f, 14f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.15f).apply {
            duration = 420; interpolator = AccelerateDecelerateInterpolator()
            repeatCount = Animation.INFINITE; repeatMode = Animation.REVERSE
        })
    }

    private fun dismiss() {
        stopSound(); stopVibration()
        rippleViews.forEach { it.clearAnimation() }; ivBell.clearAnimation()
        if (mode == PomodoroMode.FOCUS) prefs.completedSessions = prefs.completedSessions + 1
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(Actions.EXTRA_MODE, mode.name); putExtra("dismissed", true)
        })
        finish()
    }

    private fun snooze() {
        stopSound(); stopVibration()
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("snooze", true); putExtra(Actions.EXTRA_MODE, mode.name)
        })
        finish()
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent) }
    override fun onDestroy() { stopSound(); stopVibration(); super.onDestroy() }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* blocked — must dismiss */ }
}
