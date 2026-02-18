package com.pomo.timer

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

enum class PomodoroMode(
    val label: String,
    val sessionLabel: String,
    val minutes: Int,
    val colorHex: String,
    val glowAlpha: Int
) {
    FOCUS(
        label        = "Focus",
        sessionLabel = "Focus Session",
        minutes      = 25,
        colorHex     = "#F5A623",
        glowAlpha    = 18
    ),
    SHORT_BREAK(
        label        = "Short Break",
        sessionLabel = "Short Break",
        minutes      = 5,
        colorHex     = "#5ECF7A",
        glowAlpha    = 18
    ),
    LONG_BREAK(
        label        = "Long Break",
        sessionLabel = "Long Break",
        minutes      = 15,
        colorHex     = "#5EC4CF",
        glowAlpha    = 18
    );

    val color: Int get() = Color.parseColor(colorHex)
    val totalSeconds: Int get() = minutes * 60
}

object Actions {
    const val TICK          = "com.pomo.timer.TICK"
    const val COMPLETED     = "com.pomo.timer.COMPLETED"
    const val ACTION_PAUSE  = "com.pomo.timer.ACTION_PAUSE"
    const val ACTION_STOP   = "com.pomo.timer.ACTION_STOP"

    const val EXTRA_REMAINING = "remaining"
    const val EXTRA_TOTAL     = "total"
    const val EXTRA_MODE      = "mode"
    const val EXTRA_RUNNING   = "running"
}

class PomodoroPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pomo_prefs", Context.MODE_PRIVATE)

    var alarmSoundUri: String?
        get() = prefs.getString(KEY_ALARM_URI, null)
        set(v) = prefs.edit().putString(KEY_ALARM_URI, v).apply()

    var alarmSoundName: String
        get() = prefs.getString(KEY_ALARM_NAME, "Default Tone") ?: "Default Tone"
        set(v) = prefs.edit().putString(KEY_ALARM_NAME, v).apply()

    var completedSessions: Int
        get() = prefs.getInt(KEY_SESSIONS, 0)
        set(v) = prefs.edit().putInt(KEY_SESSIONS, v).apply()

    var lastTask: String
        get() = prefs.getString(KEY_TASK, "") ?: ""
        set(v) = prefs.edit().putString(KEY_TASK, v).apply()

    fun clearSessions() = prefs.edit().putInt(KEY_SESSIONS, 0).apply()

    companion object {
        private const val KEY_ALARM_URI  = "alarm_uri"
        private const val KEY_ALARM_NAME = "alarm_name"
        private const val KEY_SESSIONS   = "sessions"
        private const val KEY_TASK       = "task"
    }
}
