package com.lifeline.app.data

import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime

/** One-shot reads that combine the database with settings, for receivers and widgets. */
object Repo {
    suspend fun day(context: Context, date: LocalDate, now: LocalDateTime = LocalDateTime.now()): DaySummary {
        val dao = AppDatabase.get(context).entries()
        val prefs = Prefs.get(context)
        return Stats.summarize(date, dao.entriesOn(date.toString()), dao.plansOn(date.toString()), prefs.settings.value, now, prefs.trackingSince)
    }

    suspend fun streak(context: Context, today: LocalDate, now: LocalDateTime = LocalDateTime.now()): Int {
        val dao = AppDatabase.get(context).entries()
        val prefs = Prefs.get(context)
        val from = today.minusDays(120)
        val days = Stats.summarizeRange(
            from, today, dao.between(from.toString(), today.toString()), dao.plansBetween(from.toString(), today.toString()),
            prefs.settings.value, now, prefs.trackingSince,
        )
        return Stats.winStreak(days, today)
    }

    suspend fun week(context: Context, start: LocalDate, now: LocalDateTime = LocalDateTime.now()): WeekReport {
        val dao = AppDatabase.get(context).entries()
        val prefs = Prefs.get(context)
        val from = start.minusDays(7).toString()
        val to = start.plusDays(6).toString()
        return WeekReport.build(start, dao.between(from, to), dao.plansBetween(from, to), prefs.settings.value, now, prefs.trackingSince)
    }
}
