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
    /** Stretches when alarms were switched off. Hours inside one are untracked, never missed. */
    val pauses: List<Pause> = emptyList(),
    val alarmStyle: AlarmStyle = AlarmStyle.ALARM,
    /** Custom check-in blocks, sorted and non-overlapping. Empty = one check-in per hour of the window. */
    val sessions: List<Session> = emptyList(),
) {
    val sessionMode get() = sessions.isNotEmpty()

    /** Where the tracked day starts: the window start, or the first session. */
    val dayStart get() = sessions.firstOrNull()?.start ?: startHour

    /** The pause covering [at], if alarms are off then. */
    fun pauseAt(at: Long): Pause? = pauses.firstOrNull { at >= it.start && at < it.end }

    /** Whether any pause overlaps [from, to) (epoch millis). */
    fun pausedBetween(from: Long, to: Long) = pauses.any { it.start < to && it.end > from }

    /** Whether [hour] is tracked; hourly windows may wrap past midnight (e.g. 20 → 2). */
    fun inWindow(hour: Int) = when {
        sessionMode -> sessions.any { hour in it.start until it.end }
        startHour <= endHour -> hour in startHour..endHour
        else -> hour >= startHour || hour <= endHour
    }

    /** Tracked hours in day order starting at [dayStart]. */
    fun activeHours(): List<Int> = (0 until 24).map { (dayStart + it) % 24 }.filter(::inWindow)

    fun dayOrder(hour: Int) = (hour - dayStart + 24) % 24

    /** The check-in blocks of a day: the sessions, or one per active hour. */
    fun slots(): List<Session> = if (sessionMode) sessions else activeHours().map { Session(it, it + 1, "") }

    /** The block containing [hour], if it's tracked. */
    fun slotOf(hour: Int): Session? = slots().firstOrNull { hour in it.start until it.end }

    /** Hourly check-ins ring as the hour starts ("what are you doing?"); sessions ring as they end ("how did it go?"). */
    fun alarmHour(slot: Session) = if (sessionMode) slot.end else slot.start

    /** The block whose alarm rings at [hour] o'clock. */
    fun slotRingingAt(hour: Int): Session? = slots().firstOrNull { alarmHour(it) % 24 == hour }

    /** The hour an entry for [hour] is filed under: the start of its session (or the nearest one before it). */
    fun logHour(hour: Int): Int = if (!sessionMode) hour else (slotOf(hour) ?: sessions.lastOrNull { it.start <= hour } ?: sessions.first()).start

    /** "15:00" per hour, or "10–13 WORK" for a session. */
    fun slotLabel(hour: Int): String {
        val s = slotOf(hour)?.takeIf { sessionMode } ?: return "%02d:00".format(hour)
        return "%02d–%02d".format(s.start, s.end) + if (s.label.isNotBlank()) " ${s.label}" else ""
    }

    /** A session's name doubles as its standing plan when no plan was made for that day. */
    fun defaultGoal(hour: Int): String? = slotOf(hour)?.takeIf { sessionMode && it.start == hour }?.label?.takeIf { it.isNotBlank() }
}

enum class AlarmStyle {
    /** Full-screen, loops until a check-in is submitted. */
    ALARM,
    /** An ordinary notification: one sound, can be swiped away, log whenever. */
    NOTIFICATION,
}

/** A check-in block covering hours [start, end), within one day (end ≤ 24). */
data class Session(val start: Int, val end: Int, val label: String) {
    companion object {
        /** A starting point that matches a typical training-and-work day. */
        val EXAMPLE = listOf(
            Session(6, 7, "MORNING"), Session(7, 9, "GYM"), Session(10, 13, "WORK"),
            Session(14, 17, "WORK"), Session(18, 20, "SELF IMPROVEMENT"), Session(21, 22, "ENJOY"),
        )
    }
}

/** Alarms off from [start] until [end] (epoch millis); [end] is [FOREVER] until they're turned back on. */
data class Pause(val start: Long, val end: Long) {
    val forever get() = end == FOREVER

    companion object {
        const val FOREVER = Long.MAX_VALUE
    }
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
        pauses = sp.getString("pauses", null).orEmpty().split(',').mapNotNull { p ->
            val (s, e) = p.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            Pause(s.toLongOrNull() ?: return@mapNotNull null, e.toLongOrNull() ?: return@mapNotNull null)
        },
        alarmStyle = runCatching { AlarmStyle.valueOf(sp.getString("alarmStyle", null)!!) }.getOrDefault(AlarmStyle.ALARM),
        sessions = sp.getString("sessions", null).orEmpty().split('|').mapNotNull { s ->
            val parts = s.split('-', limit = 3).takeIf { it.size == 3 } ?: return@mapNotNull null
            val start = parts[0].toIntOrNull() ?: return@mapNotNull null
            val end = parts[1].toIntOrNull() ?: return@mapNotNull null
            Session(start, end, parts[2]).takeIf { start in 0..23 && end in start + 1..24 }
        },
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
            .putString("pauses", s.pauses.joinToString(",") { "${it.start}:${it.end}" })
            .putString("alarmStyle", s.alarmStyle.name)
            .putString("sessions", s.sessions.joinToString("|") { "${it.start}-${it.end}-${it.label.replace("|", "")}" })
            .commit()
        _settings.value = s
    }

    /** Switch alarms off until [until] ([Pause.FOREVER] = until turned back on). */
    fun pause(until: Long) {
        val now = System.currentTimeMillis()
        val s = settings.value
        // Keep a year of history for hour accounting; older pauses no longer matter.
        val kept = s.pauses.filter { it.end > now - 400L * 86_400_000 }.map { if (it.end > now) it.copy(end = now) else it }
        updateSettings(s.copy(pauses = kept + Pause(now, until)))
    }

    /** End the current pause now. */
    fun resume() {
        val now = System.currentTimeMillis()
        val s = settings.value
        updateSettings(s.copy(pauses = s.pauses.map { if (it.end > now) it.copy(end = now) else it }))
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
