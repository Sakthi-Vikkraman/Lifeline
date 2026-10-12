package com.lifeline.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val startHour: Int = 7,
    val endHour: Int = 23,
    val sound: Boolean = true,
    /** Productive hours per day the dashboard and widgets count as "reps". */
    val dailyGoal: Int = 8,
    /** Productivity check-ins need a photo, so you can't type "studying" from bed. */
    val photoForProductive: Boolean = false,
    /** Level-up sound when a productive check-in is submitted. */
    val rewardSound: Boolean = true,
) {
    /** Whether [hour] is inside the alarm window; windows may wrap past midnight (e.g. 20 → 2). */
    fun inWindow(hour: Int) = if (startHour <= endHour) hour in startHour..endHour else hour >= startHour || hour <= endHour

    /** Active hours in day order starting at the window start. */
    fun activeHours(): List<Int> = (0 until 24).map { (startHour + it) % 24 }.filter(::inWindow)

    fun dayOrder(hour: Int) = (hour - startHour + 24) % 24
}

/** An hourly alarm that has fired and not yet been answered with a check-in. [line] is the coach's message for it. */
data class PendingAlarm(val date: String, val hour: Int, val line: String? = null)

/** SharedPreferences-backed settings and alarm state, exposed as flows so UI and receivers stay in sync. */
class Prefs private constructor(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("lifeline", Context.MODE_PRIVATE)

    /**
     * When hour accounting starts. Hours before this never count as missed, so installing (or upgrading
     * to a version that counts missed hours) doesn't retroactively punish you.
     */
    val trackingSince: Long = sp.getLong("trackingSince", 0L).takeIf { it > 0 }
        ?: System.currentTimeMillis().also { sp.edit().putLong("trackingSince", it).apply() }

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _pending = MutableStateFlow(readPending())
    val pending: StateFlow<PendingAlarm?> = _pending.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _settings.value = readSettings()
        _pending.value = readPending()
    }

    init {
        sp.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun readSettings() = Settings(
        startHour = sp.getInt("startHour", 7),
        endHour = sp.getInt("endHour", 23),
        sound = sp.getBoolean("sound", true),
        dailyGoal = sp.getInt("dailyGoal", 8),
        photoForProductive = sp.getBoolean("photoForProductive", false),
        rewardSound = sp.getBoolean("rewardSound", true),
    )

    private fun readPending(): PendingAlarm? {
        val date = sp.getString("pendingDate", null) ?: return null
        return PendingAlarm(date, sp.getInt("pendingHour", 0), sp.getString("pendingLine", null))
    }

    fun updateSettings(s: Settings) {
        sp.edit()
            .putInt("startHour", s.startHour).putInt("endHour", s.endHour).putBoolean("sound", s.sound)
            .putInt("dailyGoal", s.dailyGoal).putBoolean("photoForProductive", s.photoForProductive)
            .putBoolean("rewardSound", s.rewardSound)
            .commit()
        _settings.value = s
    }

    fun setPending(p: PendingAlarm?) {
        val e = sp.edit()
        if (p == null) e.remove("pendingDate").remove("pendingHour").remove("pendingLine")
        else e.putString("pendingDate", p.date).putInt("pendingHour", p.hour).putString("pendingLine", p.line)
        e.commit()
        _pending.value = p
    }

    /** Remembers which quote was shown last per list, so the same one never repeats back to back. */
    fun lastQuote(list: String) = sp.getInt("quote.$list", -1)
    fun setLastQuote(list: String, index: Int) { sp.edit().putInt("quote.$list", index).apply() }

    /** Coach notifications (evening warning, weekly report) fire at most once per key. */
    fun once(key: String): Boolean {
        if (sp.getBoolean("once.$key", false)) return false
        sp.edit().putBoolean("once.$key", true).apply()
        return true
    }

    companion object {
        @Volatile private var instance: Prefs? = null
        fun get(context: Context): Prefs = instance ?: synchronized(this) {
            instance ?: Prefs(context.applicationContext).also { instance = it }
        }
    }
}
