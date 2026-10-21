package com.lifeline.app.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/**
 * How one hour of the alarm window went (see [Stats.kindOf]). MISSED hours ended without any check-in and count as lost time, so ignoring the
 * alarm is never cheaper than admitting what you did. PAUSED hours fell while alarms were switched off and
 * count for nothing either way.
 */
enum class HourKind { PRODUCTIVE, ENTERTAINMENT, MISSED, PAUSED, NOW, FUTURE, UNTRACKED }

/** [current] is true for the hour in progress, whether or not it has been logged yet. */
data class HourStat(val hour: Int, val kind: HourKind, val entries: List<Entry>, val plan: Plan?, val current: Boolean = false) {
    /** Null when there was no plan or nothing to judge it against yet. */
    val keptPlan: Boolean? = when {
        plan == null -> null
        // The latest answer wins; entries logged before the plan existed say nothing about it.
        entries.isNotEmpty() -> entries.sortedBy { it.createdAt }.lastOrNull { it.keptPlan != null }?.keptPlan
        kind == HourKind.MISSED -> false
        else -> null
    }
}

data class DaySummary(val date: LocalDate, val hours: List<HourStat>) {
    val productive = hours.count { it.kind == HourKind.PRODUCTIVE }
    val entertainment = hours.count { it.kind == HourKind.ENTERTAINMENT }
    val missed = hours.count { it.kind == HourKind.MISSED }
    val paused = hours.count { it.kind == HourKind.PAUSED }
    val lost = entertainment + missed
    val tracked = productive + lost
    val logs = hours.flatMap { it.entries }.distinctBy { it.id }.size
    val planned = hours.count { it.keptPlan != null }
    val kept = hours.count { it.keptPlan == true }

    /** Productive share of tracked hours, or null when nothing has been tracked yet. */
    val share: Float? = if (tracked == 0) null else productive.toFloat() / tracked
    val planScore: Int? = if (planned == 0) null else kept * 100 / planned
    val won get() = productive > lost
}

object Stats {
    /** Majority tag wins; a tie goes to the most recent check-in, so you can't pad an hour after slacking. */
    fun kindOf(entries: List<Entry>): HourKind {
        val p = entries.count { it.tag == Tag.PRODUCTIVITY }
        val e = entries.size - p
        val tag = when {
            p > e -> Tag.PRODUCTIVITY
            e > p -> Tag.ENTERTAINMENT
            else -> entries.maxBy { it.createdAt }.tag
        }
        return if (tag == Tag.PRODUCTIVITY) HourKind.PRODUCTIVE else HourKind.ENTERTAINMENT
    }

    /** Builds the hour-by-hour ledger for [date]. [entries] and [plans] may span several days. */
    fun summarize(
        date: LocalDate,
        entries: List<Entry>,
        plans: List<Plan>,
        settings: Settings,
        now: LocalDateTime,
        trackingSince: Long,
    ): DaySummary {
        val key = date.toString()
        val byHour = entries.filter { it.date == key }.groupBy { it.hour }
        val planByHour = plans.filter { it.date == key }.associateBy { it.hour }
        val since = LocalDateTime.ofInstant(Instant.ofEpochMilli(trackingSince), ZoneId.systemDefault())
        val sinceHour = since.withMinute(0).withSecond(0).withNano(0).plusHours(1)
        fun millis(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val hours = (settings.activeHours() + byHour.keys).distinct().sortedBy { settings.dayOrder(it) }.map { hour ->
            // In session mode every hour of a session shares its check-ins and is judged when the session ends.
            val slot = if (settings.sessionMode) settings.slotOf(hour) else null
            val span = slot?.let { it.start until it.end } ?: hour..hour
            val list = span.flatMap { byHour[it].orEmpty() }.sortedBy { it.createdAt }
            val start = date.atTime(span.first, 0)
            val end = date.atStartOfDay().plusHours(span.last + 1L)
            val kind = when {
                list.isNotEmpty() -> kindOf(list)
                settings.inWindow(hour) && settings.pausedBetween(millis(start), millis(end)) -> HourKind.PAUSED
                end <= now && settings.inWindow(hour) && start >= sinceHour -> HourKind.MISSED
                end <= now -> HourKind.UNTRACKED
                start <= now -> HourKind.NOW
                else -> HourKind.FUTURE
            }
            val plan = planByHour[span.first] ?: settings.defaultGoal(span.first)?.let { Plan(key, span.first, it) }
            val hourStart = date.atTime(hour, 0)
            HourStat(hour, kind, list, plan, current = hourStart <= now && now < hourStart.plusHours(1))
        }
        return DaySummary(date, hours)
    }

    fun summarizeRange(
        from: LocalDate, to: LocalDate, entries: List<Entry>, plans: List<Plan>, settings: Settings, now: LocalDateTime, trackingSince: Long,
    ): List<DaySummary> = generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }
        .map { summarize(it, entries, plans, settings, now, trackingSince) }.toList()

    /**
     * Consecutive won days, ending today if today is already won, otherwise yesterday. Rest days (alarms
     * off, nothing tracked) neither break nor extend the streak.
     */
    fun winStreak(days: List<DaySummary>, today: LocalDate): Int {
        val byDate = days.associateBy { it.date }
        var d = if (byDate[today]?.won == true) today else today.minusDays(1)
        var streak = 0
        while (true) {
            val day = byDate[d] ?: break
            if (day.won) streak++ else if (day.tracked > 0 || day.paused == 0) break
            d = d.minusDays(1)
        }
        return streak
    }

    fun lostPhrase(hours: Int): String = when {
        hours == 0 -> "Zero hours wasted. Beast mode."
        hours >= 48 -> "That's ${hours / 24} full days of your life."
        hours >= 24 -> "That's more than a full day of your life."
        hours >= 18 -> "That's almost a full day."
        hours >= 8 -> "That's a full workday, gone."
        hours >= 3 -> "That's a whole movie marathon."
        else -> "Keep it that low."
    }

    fun grade(share: Float?): String = when {
        share == null -> "—"
        share >= 0.75f -> "A"
        share >= 0.60f -> "B"
        share >= 0.45f -> "C"
        share >= 0.30f -> "D"
        else -> "F"
    }

    /** The most-logged entertainment, by normalized text, with how many times it was logged. */
    fun topWaster(entries: List<Entry>): Pair<String, Int>? = entries
        .filter { it.tag == Tag.ENTERTAINMENT && it.text.isNotBlank() }
        .groupBy { it.text.trim().lowercase().replace(Regex("\\s+"), " ") }
        .maxByOrNull { it.value.size }
        ?.let { (_, list) -> list.last().text.trim() to list.size }
}

/** Monday-to-Sunday report card, compared with the week before. */
data class WeekReport(
    val start: LocalDate,
    val days: List<DaySummary>,
    val previous: List<DaySummary>,
    val waster: Pair<String, Int>?,
) {
    val end: LocalDate = start.plusDays(6)
    val productive = days.sumOf { it.productive }
    val entertainment = days.sumOf { it.entertainment }
    val missed = days.sumOf { it.missed }
    val lost = entertainment + missed
    val tracked = productive + lost
    val share: Float? = if (tracked == 0) null else productive.toFloat() / tracked
    val grade = Stats.grade(share)
    val planned = days.sumOf { it.planned }
    val planScore: Int? = if (planned == 0) null else days.sumOf { it.kept } * 100 / planned
    val best = days.filter { it.productive > 0 }.maxByOrNull { it.productive }
    val worst = days.filter { it.lost > 0 }.maxByOrNull { it.lost }
    val prevProductive = previous.sumOf { it.productive }
    val prevLost = previous.sumOf { it.lost }
    val prevTracked = previous.sumOf { it.tracked }

    fun shareText(): String {
        val fmt = DateTimeFormatter.ofPattern("d MMM")
        val day = DateTimeFormatter.ofPattern("EEE")
        return buildString {
            appendLine("LIFELINE WEEKLY REPORT · ${start.format(fmt)} – ${end.format(fmt)}")
            appendLine("Grade: $grade")
            appendLine("Productive: $productive h · Lost: $lost h ($missed missed)")
            planScore?.let { appendLine("Plan kept: $it%") }
            best?.let { appendLine("Best day: ${it.date.format(day)} (${it.productive} h productive)") }
            worst?.let { appendLine("Worst day: ${it.date.format(day)} (${it.lost} h lost)") }
            waster?.let { appendLine("Top time-waster: ${it.first} (${it.second}×)") }
            if (prevTracked > 0) {
                val d = productive - prevProductive
                appendLine("vs last week: ${if (d >= 0) "+" else ""}$d h productive")
            }
            append("Hold me to it.")
        }
    }

    companion object {
        fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        fun build(start: LocalDate, entries: List<Entry>, plans: List<Plan>, settings: Settings, now: LocalDateTime, trackingSince: Long): WeekReport {
            val end = start.plusDays(6)
            val days = Stats.summarizeRange(start, end, entries, plans, settings, now, trackingSince)
            val previous = Stats.summarizeRange(start.minusDays(7), start.minusDays(1), entries, plans, settings, now, trackingSince)
            val inWeek = entries.filter { it.date >= start.toString() && it.date <= end.toString() }
            return WeekReport(start, days, previous, Stats.topWaster(inWeek))
        }
    }
}

/** Alarm copy that reacts to how the day is going. */
object Coach {
    fun alarmLine(summary: DaySummary, hour: Int, settings: Settings): String {
        val before = summary.hours.filter { settings.dayOrder(it.hour) < settings.dayOrder(hour) && it.kind != HourKind.UNTRACKED && it.kind != HourKind.PAUSED }
        val lastTwo = before.takeLast(2).map { it.kind }
        val plan = summary.hours.firstOrNull { it.hour == hour }?.plan
        val productiveRun = before.reversed().takeWhile { it.kind == HourKind.PRODUCTIVE }.size
        return when {
            lastTwo.size == 2 && lastTwo.all { it == HourKind.ENTERTAINMENT || it == HourKind.MISSED } ->
                "Two hours gone. Make this one count."
            lastTwo.lastOrNull() == HourKind.MISSED -> "You dodged the last check-in. Not this time."
            productiveRun >= 2 -> "$productiveRun productive hours straight. Don't break it."
            plan != null -> "The plan says: ${plan.goal}. Did you show up?"
            summary.productive >= settings.dailyGoal -> "Goal smashed. Everything now is bonus reps."
            before.isEmpty() -> "First rep of the day. Set the tone."
            else -> "What are you doing right now? Snap it or type it."
        }
    }

    /** Evening warning text, or null when the day is on track. */
    fun streakWarning(summary: DaySummary, settings: Settings): String? {
        if (summary.productive > summary.lost) return null
        val toGo = (settings.dailyGoal - summary.productive).coerceAtLeast(0)
        return "${summary.productive} productive vs ${summary.lost} lost. " +
            if (toGo > 0) "$toGo reps to your goal. Win the evening." else "Win the evening."
    }
}

fun Float.pct() = (this * 100).roundToInt()
