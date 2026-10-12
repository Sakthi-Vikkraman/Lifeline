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
import com.lifeline.app.R
import com.lifeline.app.data.AppDatabase
import com.lifeline.app.data.PendingAlarm
import com.lifeline.app.data.Prefs
import com.lifeline.app.ui.AlarmActivity
import com.lifeline.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private const val EXTRA_TRIGGER_AT = "triggerAt"

/** Schedules the single next hourly alarm. Each fire schedules the following one. */
object AlarmScheduler {
    fun nextTrigger(context: Context, from: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        val settings = Prefs.get(context).settings.value
        var t = from.truncatedTo(ChronoUnit.HOURS).plusHours(1)
        repeat(24) {
            if (settings.inWindow(t.hour)) return t
            t = t.plusHours(1)
        }
        return null
    }

    fun scheduleNext(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val operation = receiverIntent(context, 0L)
        am.cancel(operation)
        val next = nextTrigger(context) ?: return
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val op = receiverIntent(context, at)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            val show = PendingIntent.getActivity(
                context, 1, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), op)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
        }
    }

    private fun receiverIntent(context: Context, triggerAt: Long): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_TRIGGER_AT, triggerAt),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Raise the alarm for [date]/[hour]: mark it pending and post the ringing notification. */
    fun raise(context: Context, date: String, hour: Int) {
        Prefs.get(context).setPending(PendingAlarm(date, hour))
        AlarmNotifier.show(context)
    }

    /** Called when a check-in answers the pending alarm. */
    fun resolve(context: Context) {
        Prefs.get(context).setPending(null)
        AlarmNotifier.cancel(context)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AlarmScheduler.scheduleNext(app)
                val at = intent.getLongExtra(EXTRA_TRIGGER_AT, 0L).takeIf { it > 0 } ?: System.currentTimeMillis()
                val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(at), ZoneId.systemDefault())
                val date = time.toLocalDate().toString()
                val settings = Prefs.get(app).settings.value
                // Skip if he already logged this hour manually.
                if (settings.inWindow(time.hour) && AppDatabase.get(app).entries().countForHour(date, time.hour) == 0) {
                    AlarmScheduler.raise(app, date, time.hour)
                }
            } finally {
                result.finish()
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
    }
}

object AlarmNotifier {
    private const val CHANNEL_LOUD = "checkin_alarm"
    private const val CHANNEL_SILENT = "checkin_silent"
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
        nm.createNotificationChannels(listOf(loud, silent))
    }

    /** Posts the ongoing alarm notification. [muted] swaps to the silent channel without clearing the alarm. */
    fun show(context: Context, muted: Boolean = false) {
        val pending = Prefs.get(context).pending.value ?: return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 33 && !nm.areNotificationsEnabled()) return

        val loud = !muted && Prefs.get(context).settings.value.sound
        val alarmIntent = PendingIntent.getActivity(
            context, 2,
            Intent(context, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val hourLabel = "%02d:00".format(pending.hour)
        val notification = NotificationCompat.Builder(context, if (loud) CHANNEL_LOUD else CHANNEL_SILENT)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFC8FF00.toInt())
            .setContentTitle("$hourLabel — CLOCK IN")
            .setContentText("What are you doing right now? Log it to kill the alarm.")
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
