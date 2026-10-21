package com.lifeline.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.widget.RemoteViews
import com.lifeline.app.R
import com.lifeline.app.alarm.AlarmScheduler
import com.lifeline.app.Actions
import com.lifeline.app.data.DaySummary
import com.lifeline.app.data.HourKind
import com.lifeline.app.data.PendingAlarm
import com.lifeline.app.data.Prefs
import com.lifeline.app.data.Repo
import com.lifeline.app.data.Settings
import com.lifeline.app.data.Tag
import com.lifeline.app.ui.AlarmActivity
import com.lifeline.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import kotlin.math.roundToInt

private const val VOLT = 0xFFC8FF00.toInt()
private const val BLAZE = 0xFFFF4D1A.toInt()
private const val DANGER = 0xFFFF3B3B.toInt()
private const val TEXT = 0xFFF4F4F2.toInt()
private const val MUTED = 0xFF8B8B93.toInt()
private const val SURFACE2 = 0xFF1D1D20.toInt()

/** Everything both widgets need, read once per refresh. */
private class Snapshot(
    val now: LocalDateTime,
    val settings: Settings,
    val pending: PendingAlarm?,
    val day: DaySummary,
    val streak: Int,
    val next: LocalDateTime?,
) {
    val paused = settings.pauseAt(System.currentTimeMillis()) != null
}

object Widgets {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Re-render every placed Lifeline widget. Call after anything that changes today's data or alarm state. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        scope.launch { renderAll(app) }
    }

    internal fun launch(block: suspend () -> Unit) = scope.launch { block() }

    internal suspend fun renderAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val todayIds = mgr.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
        val checkInIds = mgr.getAppWidgetIds(ComponentName(context, CheckInWidget::class.java))
        if (todayIds.isEmpty() && checkInIds.isEmpty()) return

        val snap = load(context)
        todayIds.forEach { mgr.updateAppWidget(it, todayViews(context, snap, mgr.getAppWidgetOptions(it))) }
        checkInIds.forEach { mgr.updateAppWidget(it, checkInViews(context, snap)) }
    }

    private suspend fun load(context: Context): Snapshot {
        val prefs = Prefs.get(context)
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        return Snapshot(
            now = now,
            settings = prefs.settings.value,
            pending = prefs.pending.value,
            day = Repo.day(context, today, now),
            streak = Repo.streak(context, today, now),
            next = AlarmScheduler.nextTrigger(context, now),
        )
    }

    private fun activityIntent(context: Context, requestCode: Int, intent: Intent) = PendingIntent.getActivity(
        context, requestCode, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openApp(context: Context) = activityIntent(context, 10, Intent(context, MainActivity::class.java))

    /** "+ LOG" opens a manual check-in; while an alarm is pending it goes straight to the alarm instead. */
    private fun logAction(context: Context, pending: Boolean) =
        if (pending) activityIntent(context, 12, Intent(context, AlarmActivity::class.java))
        else activityIntent(context, 11, Intent(context, MainActivity::class.java).setAction(Actions.LOG))

    private fun todayViews(context: Context, s: Snapshot, options: Bundle): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_today)
        val alarm = s.pending != null

        v.setInt(R.id.widget_root, "setBackgroundResource", if (alarm) R.drawable.widget_bg_alarm else R.drawable.widget_bg)
        when {
            alarm -> v.setTextViewText(R.id.status, "● CLOCK IN " + s.settings.slotLabel(s.pending!!.hour))
            s.paused -> v.setTextViewText(R.id.status, "ALARMS OFF")
            s.next != null -> v.setTextViewText(R.id.status, "NEXT CHECK-IN %02d:00".format(s.next.hour))
            else -> v.setTextViewText(R.id.status, "")
        }
        v.setTextColor(R.id.status, if (alarm) BLAZE else MUTED)

        v.setTextViewText(R.id.reps, "${s.day.productive}/${s.settings.dailyGoal}")
        v.setTextViewText(R.id.lost, "${s.day.lost}H")
        v.setTextViewText(R.id.plan, s.day.planScore?.let { "$it%" } ?: "—")
        v.setTextViewText(R.id.streak, "${s.streak}")
        v.setTextViewText(
            R.id.window,
            if (s.settings.sessionMode) "${s.settings.sessions.size} SESSIONS"
            else "%02d:00 — %02d:00".format(s.settings.startHour, s.settings.endHour),
        )

        val density = context.resources.displayMetrics.density
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 250
        v.setImageViewBitmap(R.id.strip, hourStrip(s, ((widthDp - 32) * density).roundToInt(), (18 * density).roundToInt(), density))

        v.setTextViewText(R.id.log_btn, if (alarm) "CLOCK IN" else "+ LOG")
        v.setInt(R.id.log_btn, "setBackgroundResource", if (alarm) R.drawable.widget_btn_alarm else R.drawable.widget_btn)
        v.setOnClickPendingIntent(R.id.log_btn, logAction(context, alarm))
        v.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        return v
    }

    private fun checkInViews(context: Context, s: Snapshot): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_checkin)
        val alarm = s.pending != null
        val goal = s.settings.dailyGoal

        v.setInt(R.id.widget_root, "setBackgroundResource", if (alarm) R.drawable.widget_bg_alarm else R.drawable.widget_bg)
        if (alarm) {
            v.setTextViewText(R.id.kicker, "● ALARM")
            v.setTextColor(R.id.kicker, BLAZE)
            val slot = s.settings.slotOf(s.pending!!.hour)?.takeIf { s.settings.sessionMode }
            v.setTextViewText(R.id.time, slot?.let { "%02d–%02d".format(it.start, it.end) } ?: "%02d:00".format(s.pending.hour))
            v.setTextViewText(R.id.summary, "LOG IT TO STOP IT")
            v.setTextColor(R.id.summary, BLAZE)
        } else {
            v.setTextViewText(R.id.kicker, if (s.paused) "ALARMS OFF" else "NEXT CHECK-IN")
            v.setTextColor(R.id.kicker, if (s.paused) BLAZE else MUTED)
            v.setTextViewText(R.id.time, s.next?.let { "%02d:00".format(it.hour) } ?: "—")
            if (s.day.tracked == 0) {
                v.setTextViewText(R.id.summary, "0/$goal REPS · LET'S GO")
                v.setTextColor(R.id.summary, MUTED)
            } else {
                v.setTextViewText(R.id.summary, "${s.day.productive}/$goal REPS · ${s.day.lost}H LOST")
                v.setTextColor(R.id.summary, if (s.day.won) VOLT else BLAZE)
            }
        }
        v.setTextViewText(R.id.log_btn, if (alarm) "CLOCK IN" else "+ LOG")
        v.setInt(R.id.log_btn, "setBackgroundResource", if (alarm) R.drawable.widget_btn_alarm else R.drawable.widget_btn)
        v.setOnClickPendingIntent(R.id.log_btn, logAction(context, alarm))
        v.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        return v
    }

    /**
     * One rounded cell per hour of the alarm window, matching the dashboard: logged hours are tinted
     * blaze → volt by their productive share, missed hours (which count as lost) are red, the current hour
     * is outlined white.
     */
    private fun hourStrip(s: Snapshot, width: Int, height: Int, density: Float): Bitmap {
        val hours = s.settings.activeHours()
        val bmp = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        if (hours.isEmpty()) return bmp
        val canvas = Canvas(bmp)
        val gap = 3 * density
        val cellW = (width - gap * (hours.size - 1)) / hours.size
        val radius = 3 * density
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * density }
        val byHour = s.day.hours.associateBy { it.hour }

        hours.forEachIndexed { i, hour ->
            val left = i * (cellW + gap)
            val rect = RectF(left, 0f, left + cellW, height.toFloat())
            val stat = byHour[hour]
            val list = stat?.entries.orEmpty()
            fill.color = when {
                list.isNotEmpty() -> mix(BLAZE, VOLT, list.count { it.tag == Tag.PRODUCTIVITY }.toFloat() / list.size)
                stat?.kind == HourKind.MISSED -> 0x55FF3B3B
                else -> SURFACE2
            }
            canvas.drawRoundRect(rect, radius, radius, fill)
            val outline = when {
                stat?.current == true -> TEXT
                stat?.kind == HourKind.MISSED -> DANGER
                else -> null
            }
            if (outline != null) {
                stroke.color = outline
                val inset = stroke.strokeWidth / 2
                canvas.drawRoundRect(RectF(rect.left + inset, inset, rect.right - inset, height - inset), radius, radius, stroke)
            }
        }
        return bmp
    }

    private fun mix(from: Int, to: Int, t: Float): Int {
        fun ch(shift: Int) = (((from shr shift) and 0xFF) + (((to shr shift) and 0xFF) - ((from shr shift) and 0xFF)) * t).roundToInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}

/** Both widgets render from the same snapshot, so either provider refreshes all of them. */
abstract class LifelineWidgetProvider : AppWidgetProvider() {
    private fun renderAsync(context: Context) {
        val result = goAsync()
        Widgets.launch {
            try { Widgets.renderAll(context.applicationContext) } finally { result.finish() }
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = renderAsync(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = renderAsync(context)
}

class TodayWidget : LifelineWidgetProvider()

class CheckInWidget : LifelineWidgetProvider()
