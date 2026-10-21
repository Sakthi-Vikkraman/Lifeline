package com.lifeline.app.alarm

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.lifeline.app.Actions
import com.lifeline.app.R
import com.lifeline.app.data.AlarmStyle
import com.lifeline.app.data.AppDatabase
import com.lifeline.app.data.Coach
import com.lifeline.app.data.PendingAlarm
import com.lifeline.app.data.Prefs
import com.lifeline.app.data.Repo
import com.lifeline.app.data.WeekReport
import com.lifeline.app.ui.AlarmActivity
import com.lifeline.app.ui.MainActivity
import com.lifeline.app.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private const val EXTRA_TRIGGER_AT = "triggerAt"
private const val ACTION_HOURLY = "com.lifeline.app.alarm.HOURLY"
private const val ACTION_PLAN_REMINDER = "com.lifeline.app.alarm.PLAN_REMINDER"
private const val PLAN_LEAD_MINUTES = 30L

/**
 * Schedules the single next hourly alarm (each fire schedules the following one), plus a morning reminder
 * to plan the day [PLAN_LEAD_MINUTES] before the first alarm.
 */
object AlarmScheduler {
    /** The next top-of-hour check-in alarm, after any pause ends; null while alarms are off indefinitely. */
    fun nextTrigger(context: Context, from: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        val settings = Prefs.get(context).settings.value
        var start = from
        settings.pauseAt(start.millis())?.let { pause ->
            if (pause.forever) return null
            start = maxOf(start, LocalDateTime.ofInstant(Instant.ofEpochMilli(pause.end - 1), ZoneId.systemDefault()))
        }
        var t = start.truncatedTo(ChronoUnit.HOURS).plusHours(1)
        repeat(24) {
            if (settings.slotRingingAt(t.hour) != null) return t
            t = t.plusHours(1)
        }
        return null
    }

    fun scheduleNext(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(receiverIntent(context, ACTION_HOURLY, 0L))
        // v1.0 scheduled its alarm without an action; clear it so upgrades don't ring twice.
        PendingIntent.getBroadcast(context, 0, Intent(context, AlarmReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
            ?.let(am::cancel)
        val next = nextTrigger(context)
        if (next != null) {
            val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val op = receiverIntent(context, ACTION_HOURLY, at)
            if (canExact(am)) {
                val show = PendingIntent.getActivity(
                    context, 1, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), op)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
            }
        }
        schedulePlanReminder(context, am)
    }

    private fun schedulePlanReminder(context: Context, am: AlarmManager) {
        am.cancel(receiverIntent(context, ACTION_PLAN_REMINDER, 0L))
        val settings = Prefs.get(context).settings.value
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atTime(settings.dayStart, 0).minusMinutes(PLAN_LEAD_MINUTES)
        if (at <= now) at = at.plusDays(1)
        // No plan nagging on rest days.
        while (settings.pauseAt(at.millis()) != null) {
            if (settings.pauseAt(at.millis())!!.forever) return
            at = at.plusDays(1)
        }
        val millis = at.millis()
        val op = receiverIntent(context, ACTION_PLAN_REMINDER, millis)
        if (canExact(am)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, op)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, op)
    }

    private fun canExact(am: AlarmManager) = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    private fun receiverIntent(context: Context, action: String, triggerAt: Long): PendingIntent = PendingIntent.getBroadcast(
        context, if (action == ACTION_HOURLY) 0 else 2,
        Intent(context, AlarmReceiver::class.java).setAction(action).putExtra(EXTRA_TRIGGER_AT, triggerAt),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The coach line for an alarm at [date]/[hour], based on how the day has gone so far. */
    suspend fun coachLine(context: Context, date: LocalDate, hour: Int): String =
        Coach.alarmLine(Repo.day(context, date), hour, Prefs.get(context).settings.value)

    /**
     * The day a check-in ringing at [time] belongs to. Sessions ring as they end, so one ending at
     * midnight belongs to the day before.
     */
    fun slotDate(settings: com.lifeline.app.data.Settings, time: LocalDateTime): LocalDate =
        if (settings.sessionMode) time.minusHours(1).toLocalDate() else time.toLocalDate()

    /** Raise the alarm for [date]/[hour]: mark it pending and post the ringing notification. */
    fun raise(context: Context, date: String, hour: Int, line: String?) {
        Prefs.get(context).setPending(PendingAlarm(date, hour, line))
        AlarmNotifier.show(context)
        Widgets.refresh(context)
    }

    /** Called when a check-in answers the pending alarm. */
    fun resolve(context: Context) {
        Prefs.get(context).setPending(null)
        AlarmNotifier.cancel(context)
        Widgets.refresh(context)
    }

    /**
     * Switch every alarm and coach notification off until [until] ([Pause.FOREVER] = until turned back on).
     * A ringing alarm stops too; its hour counts as paused, not missed.
     */
    fun stop(context: Context, until: Long) {
        val prefs = Prefs.get(context)
        prefs.pause(until)
        prefs.setPending(null)
        AlarmNotifier.cancel(context)
        listOf(CoachNotifier.PLAN, CoachNotifier.WARNING, CoachNotifier.REVIEW).forEach { CoachNotifier.cancel(context, it) }
        scheduleNext(context)
        Widgets.refresh(context)
    }

    fun restart(context: Context) {
        Prefs.get(context).resume()
        scheduleNext(context)
        Widgets.refresh(context)
    }
}

private fun LocalDateTime.millis() = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AlarmScheduler.scheduleNext(app)
                val at = intent.getLongExtra(EXTRA_TRIGGER_AT, 0L).takeIf { it > 0 } ?: System.currentTimeMillis()
                val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(at), ZoneId.systemDefault())
                if (Prefs.get(app).settings.value.pauseAt(at) != null) return@launch // alarms are off
                if (intent.action == ACTION_PLAN_REMINDER) planReminder(app, time) else hourly(app, time)
            } finally {
                Widgets.refresh(app) // the hour rolled over: NOW / missed markers move
                result.finish()
            }
        }
    }

    private suspend fun planReminder(app: Context, time: LocalDateTime) {
        val day = time.plusMinutes(PLAN_LEAD_MINUTES).toLocalDate()
        if (AppDatabase.get(app).entries().plansOn(day.toString()).isEmpty()) {
            CoachNotifier.post(app, CoachNotifier.PLAN, "PLAN YOUR DAY", "Your day starts in 30 minutes. Give every hour a job.", Actions.PLAN)
        }
    }

    private suspend fun hourly(app: Context, time: LocalDateTime) {
        val prefs = Prefs.get(app)
        val settings = prefs.settings.value
        val slot = settings.slotRingingAt(time.hour) ?: return
        val today = AlarmScheduler.slotDate(settings, time)
        val date = today.toString()
        val dao = AppDatabase.get(app).entries()

        // Skip the alarm if this hour (or session) was already logged manually.
        val raised = dao.countForHours(date, slot.start, slot.end) == 0
        if (raised) AlarmScheduler.raise(app, date, slot.start, AlarmScheduler.coachLine(app, today, slot.start))

        val slots = settings.slots()
        val index = slots.indexOf(slot)
        val summary = Repo.day(app, today)

        // Streak protection: three hours (or one session) before the last alarm, warn if the day is being lost.
        val warnAt = if (settings.sessionMode) slots.size - 2 else (slots.size - 4).coerceAtLeast(0)
        if (index == warnAt && prefs.once("warn.$date")) {
            Coach.streakWarning(summary, settings)?.let {
                CoachNotifier.post(app, CoachNotifier.WARNING, "STREAK IN DANGER", it, Actions.LOG)
            }
        }

        if (index == slots.lastIndex) {
            // Night review: if the alarm rang, the alarm screen asks for it after the check-in.
            if (!raised && dao.reviewOn(date) == null) {
                CoachNotifier.post(app, CoachNotifier.REVIEW, "NIGHT REVIEW", "Two minutes: one win, one fail, one fix for tomorrow.", Actions.REVIEW)
            }
            if (today.dayOfWeek == DayOfWeek.SUNDAY && prefs.once("report.${WeekReport.weekStart(today)}")) {
                val report = Repo.week(app, WeekReport.weekStart(today))
                CoachNotifier.post(
                    app, CoachNotifier.REPORT, "WEEK GRADED: ${report.grade}",
                    "${report.productive} h productive · ${report.lost} h lost. Tap for your report card.", Actions.REPORT,
                )
            }
        }
    }
}

/** Re-arms the alarm after reboot, app update, or clock/timezone changes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.scheduleNext(context)
        // A pending alarm survives reboot: put the ringing notification back.
        if (Prefs.get(context).pending.value != null) AlarmNotifier.show(context)
        Widgets.refresh(context)
    }
}

object AlarmNotifier {
    private const val CHANNEL_LOUD = "checkin_alarm"
    private const val CHANNEL_SILENT = "checkin_silent"
    private const val CHANNEL_NOTIFY = "checkin_notify"
    private const val NOTIFICATION_ID = 42

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val loud = NotificationChannel(CHANNEL_LOUD, "Hourly check-in alarm", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Rings every hour until you log what you're doing"
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val silent = NotificationChannel(CHANNEL_SILENT, "Hourly check-in (muted)", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Check-in reminder without sound"
            setSound(null, null)
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val notify = NotificationChannel(CHANNEL_NOTIFY, "Hourly check-in (notification only)", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "A normal check-in reminder, used when the full-screen alarm is turned off"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val coach = NotificationChannel(CoachNotifier.CHANNEL, "Coach", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Morning plan reminder, streak warnings, night review and the weekly report card"
        }
        nm.createNotificationChannels(listOf(loud, silent, notify, coach))
    }

    /**
     * Posts the ongoing alarm notification. [muted] swaps to the silent channel without clearing the alarm.
     * In [AlarmStyle.NOTIFICATION] style it's an ordinary notification instead: one sound, swipeable.
     */
    fun show(context: Context, muted: Boolean = false) {
        val pending = Prefs.get(context).pending.value ?: return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 33 && !nm.areNotificationsEnabled()) return

        val settings = Prefs.get(context).settings.value
        val alarmIntent = PendingIntent.getActivity(
            context, 2,
            Intent(context, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val hourLabel = settings.slotLabel(pending.hour)
        if (settings.alarmStyle == AlarmStyle.NOTIFICATION) {
            nm.notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(context, CHANNEL_NOTIFY)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(0xFFC8FF00.toInt())
                    .setContentTitle("$hourLabel — CHECK IN")
                    .setContentText(pending.line ?: "What are you doing right now? Tap to log it.")
                    .setStyle(NotificationCompat.BigTextStyle().bigText(pending.line ?: "What are you doing right now? Tap to log it."))
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setOnlyAlertOnce(muted)
                    .setAutoCancel(true)
                    .setContentIntent(alarmIntent)
                    .build(),
            )
            return
        }

        val loud = !muted && settings.sound
        val notification = NotificationCompat.Builder(context, if (loud) CHANNEL_LOUD else CHANNEL_SILENT)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFC8FF00.toInt())
            .setContentTitle("$hourLabel — CLOCK IN")
            .setContentText(pending.line ?: "What are you doing right now? Log it to kill the alarm.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(!loud)
            .setContentIntent(alarmIntent)
            .setFullScreenIntent(alarmIntent, true)
            .build()
        // Insistent: the sound loops until the notification is cancelled, i.e. until a check-in is submitted.
        if (loud) notification.flags = notification.flags or Notification.FLAG_INSISTENT
        nm.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
}

/** Ordinary (dismissable) coach notifications that open a specific screen of the app. */
object CoachNotifier {
    const val CHANNEL = "coach"
    const val PLAN = 43
    const val WARNING = 44
    const val REVIEW = 45
    const val REPORT = 46

    fun post(context: Context, id: Int, title: String, text: String, action: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 33 && !nm.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context, 100 + id,
            Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            id,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0xFFC8FF00.toInt())
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build(),
        )
    }

    fun cancel(context: Context, id: Int) {
        context.getSystemService(NotificationManager::class.java).cancel(id)
    }
}
