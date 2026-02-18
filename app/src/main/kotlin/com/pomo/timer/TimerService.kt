package com.pomo.timer

import android.app.*
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class TimerService : Service() {

    companion object {
        const val CHANNEL_TIMER   = "pomo_timer"
        const val CHANNEL_ALARM   = "pomo_alarm"
        const val NOTIF_TIMER_ID  = 1001
        const val NOTIF_ALARM_ID  = 1002
        const val EXTRA_START_MODE    = "start_mode"
        const val EXTRA_START_SECONDS = "start_seconds"
        const val EXTRA_START_TASK    = "start_task"
    }

    inner class TimerBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }
    private val binder = TimerBinder()
    override fun onBind(intent: Intent): IBinder = binder

    var isRunning = false
        private set
    var remainingSeconds = 0
        private set
    var totalSeconds = 0
        private set
    var currentMode = PomodoroMode.FOCUS
        private set
    var currentTask = ""
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Actions.ACTION_PAUSE -> togglePause()
            Actions.ACTION_STOP  -> stopSelf()
            else -> {
                val modeName = intent?.getStringExtra(EXTRA_START_MODE) ?: PomodoroMode.FOCUS.name
                currentMode      = PomodoroMode.valueOf(modeName)
                totalSeconds     = intent?.getIntExtra(EXTRA_START_SECONDS, currentMode.totalSeconds) ?: currentMode.totalSeconds
                remainingSeconds = totalSeconds
                currentTask      = intent?.getStringExtra(EXTRA_START_TASK) ?: ""
                startForeground(NOTIF_TIMER_ID, buildTimerNotification())
                startTicking()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopTicking()
        super.onDestroy()
    }

    private fun startTicking() {
        isRunning = true
        broadcastTick()
        scheduleTick()
    }

    private fun scheduleTick() {
        val r = Runnable {
            if (!isRunning) return@Runnable
            remainingSeconds--
            broadcastTick()
            updateTimerNotification()
            if (remainingSeconds <= 0) onTimerComplete()
            else scheduleTick()
        }
        tickRunnable = r
        handler.postDelayed(r, 1000)
    }

    fun togglePause() {
        if (isRunning) {
            isRunning = false
            tickRunnable?.let { handler.removeCallbacks(it) }
        } else {
            isRunning = true
            scheduleTick()
        }
        broadcastTick()
        updateTimerNotification()
    }

    private fun stopTicking() {
        isRunning = false
        tickRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun broadcastTick() {
        sendBroadcast(Intent(Actions.TICK).apply {
            putExtra(Actions.EXTRA_REMAINING, remainingSeconds)
            putExtra(Actions.EXTRA_TOTAL,     totalSeconds)
            putExtra(Actions.EXTRA_MODE,      currentMode.name)
            putExtra(Actions.EXTRA_RUNNING,   isRunning)
        })
    }

    private fun onTimerComplete() {
        stopTicking()
        sendBroadcast(Intent(Actions.COMPLETED).apply {
            putExtra(Actions.EXTRA_MODE, currentMode.name)
        })
        showAlarmNotification()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_TIMER, getString(R.string.channel_timer_name), NotificationManager.IMPORTANCE_LOW
        ).apply { description = getString(R.string.channel_timer_desc); setShowBadge(false) })
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_ALARM, getString(R.string.channel_alarm_name), NotificationManager.IMPORTANCE_HIGH
        ).apply { description = getString(R.string.channel_alarm_desc); lockscreenVisibility = Notification.VISIBILITY_PUBLIC })
    }

    private fun buildTimerNotification(): Notification {
        val mins = remainingSeconds / 60
        val secs = remainingSeconds % 60
        val timeStr = "%02d:%02d".format(mins, secs)
        val mainIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val pauseIntent = PendingIntent.getService(this, 1, Intent(this, TimerService::class.java).apply { action = Actions.ACTION_PAUSE }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stopIntent  = PendingIntent.getService(this, 2, Intent(this, TimerService::class.java).apply { action = Actions.ACTION_STOP  }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_TIMER)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("${currentMode.sessionLabel} · $timeStr")
            .setContentText(currentTask.ifBlank { getString(R.string.app_name) })
            .setContentIntent(mainIntent)
            .setOngoing(true).setShowWhen(false).setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, if (isRunning) getString(R.string.notif_action_pause) else getString(R.string.start), pauseIntent)
            .addAction(android.R.drawable.ic_delete, getString(R.string.notif_action_stop), stopIntent)
            .build()
    }

    private fun updateTimerNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_TIMER_ID, buildTimerNotification())
    }

    private fun showAlarmNotification() {
        val alarmIntent = Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(Actions.EXTRA_MODE, currentMode.name)
        }
        val fullScreenPending = PendingIntent.getActivity(this, 100, alarmIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notif = NotificationCompat.Builder(this, CHANNEL_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (currentMode == PomodoroMode.FOCUS) getString(R.string.times_up) else getString(R.string.break_over))
            .setContentText("${currentMode.sessionLabel} complete")
            .setFullScreenIntent(fullScreenPending, true)
            .setContentIntent(fullScreenPending)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true).build()
        startActivity(alarmIntent)
        NotificationManagerCompat.from(this).notify(NOTIF_ALARM_ID, notif)
        stopSelf()
    }
}
